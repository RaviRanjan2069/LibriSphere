package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import com.bookstore.ecommerce.mail.CodeMailer;
import com.bookstore.ecommerce.model.PasswordResetToken;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * "Forgot password" links. A link carries a random 256-bit token; only its SHA-256 hash is stored. Links
 * are single use, expire after {@code app.password-reset.ttl-minutes}, and asking for a new one cancels
 * the old. They are only sent to verified addresses, and the response never reveals whether one was sent.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration WINDOW = Duration.ofHours(1);

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final UserService userService;
    private final CodeMailer mailer;
    private final RateLimiter rateLimiter;
    private final String publicUrl;
    private final Duration ttl;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(MongoTemplate mongo, UserRepository users, UserService userService, CodeMailer mailer,
                                RateLimiter rateLimiter,
                                @Value("${app.public-url:http://localhost:9090}") String publicUrl,
                                @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.mongo = mongo;
        this.users = users;
        this.userService = userService;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public int getTtlMinutes() {
        return (int) ttl.toMinutes();
    }

    /**
     * Emails a reset link if {@code email} (normalised) belongs to an account with a verified address.
     * Rate limited per network and per address; otherwise behaves the same whether or not an email is sent.
     */
    public void requestReset(String email, String clientIp) {
        if (!rateLimiter.tryAcquire("reset-ip:" + clientIp, 20, WINDOW)
                || !rateLimiter.tryAcquire("reset-email:" + email, 5, WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many reset requests. Please wait a while before asking for another link.", "email");
        }
        Optional<User> owner = users.findByEmailIgnoreCase(email).filter(User::isEmailVerified);
        if (owner.isEmpty()) {
            return;
        }
        User user = owner.get();
        String token = newToken();
        Instant now = clock.instant();
        // A new link replaces any earlier one
        mongo.remove(Query.query(Criteria.where("userId").is(user.getId())), PasswordResetToken.class);
        mongo.insert(new PasswordResetToken(hash(token), user.getId(), now, now.plus(ttl)));
        try {
            mailer.sendPasswordReset(user.getEmail(), publicUrl + "/reset-password?token=" + token, getTtlMinutes());
        } catch (CodeMailer.MailException e) {
            mongo.remove(Query.query(Criteria.where("_id").is(hash(token))), PasswordResetToken.class);
            log.error("Password reset email to {} failed: {}", CodeMailer.mask(user.getEmail()), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "We couldn't send the email right now. Please try again in a minute.", "email");
        }
    }

    /** True if the link is still usable (shown before asking for a new password). */
    public boolean isValid(String token) {
        return find(token).isPresent();
    }

    /** Sets the new password and uses the link up. The password must already have passed {@link PasswordRules}. */
    public void reset(String token, String newPassword) {
        String id = hashOrNull(token);
        PasswordResetToken stored = id == null ? null : mongo.findAndRemove(
                Query.query(Criteria.where("_id").is(id).and("expiresAt").gt(clock.instant())), PasswordResetToken.class);
        User user = stored == null ? null : users.findById(stored.getUserId()).orElse(null);
        if (user == null) {
            throw invalidLink();
        }
        userService.changePassword(user.getId(), newPassword);
        mongo.remove(Query.query(Criteria.where("userId").is(user.getId())), PasswordResetToken.class);
    }

    public static ApiException invalidLink() {
        return ApiException.badRequest("token", "This reset link has expired or was already used. Request a new one.");
    }

    private Optional<PasswordResetToken> find(String token) {
        String id = hashOrNull(token);
        return id == null ? Optional.empty() : Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(id).and("expiresAt").gt(clock.instant())), PasswordResetToken.class));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Tokens are 43 URL-safe characters; anything else can't be one of ours. */
    private static String hashOrNull(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}") ? hash(token) : null;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
