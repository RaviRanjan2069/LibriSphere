package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import com.bookstore.ecommerce.model.OtpChallenge;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.UserRepository;
import com.bookstore.ecommerce.mail.CodeMailer;
import com.bookstore.ecommerce.mail.CodeMailer.MailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * One-time codes sent by email. Codes are random, stored only as an HMAC, valid for a few minutes, and
 * deleted on first successful use. Abuse limits:
 * <ul>
 *   <li>per address and purpose: a resend cooldown and a cap on sends per hour</li>
 *   <li>per code: a cap on wrong guesses, after which a new code is needed</li>
 *   <li>per client IP: caps on sends and verification attempts per hour</li>
 * </ul>
 * Responses never reveal whether an address is registered. For a sign-in, an unknown or unverified
 * address simply gets no code. For a sign-up, an address that's already in use gets an email saying so
 * instead of a code.
 */
@Service
public class OtpService {

    public enum Purpose {
        /** Proving a new address while creating an account. */
        REGISTER,
        /** Signing in with a verified address. */
        LOGIN,
        /** A signed-in user verifying the address already on their account. */
        VERIFY_EMAIL,
        /** A signed-in user proving a new address before it replaces the one on their account. */
        CHANGE_EMAIL
    }

    public record SendResult(int resendInSeconds, int expiresInSeconds) {
    }

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int CODE_LENGTH = 6;
    private static final Duration SEND_WINDOW = Duration.ofHours(1);

    private final MongoTemplate mongo;
    private final UserRepository userRepository;
    private final CodeMailer mailer;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Striped locks so two sends for the same address can't both pass the cooldown check. */
    private final Object[] locks = java.util.stream.Stream.generate(Object::new).limit(64).toArray();
    private final byte[] hmacKey;

    private final Duration ttl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final int maxSendsPerHour;
    private final int ipSendsPerHour;
    private final int ipVerifiesPerHour;

    public OtpService(MongoTemplate mongo, UserRepository userRepository, CodeMailer mailer, RateLimiter rateLimiter,
                      @Value("${app.otp.secret:}") String secret,
                      @Value("${app.otp.ttl-seconds:300}") int ttlSeconds,
                      @Value("${app.otp.resend-seconds:60}") int resendSeconds,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.otp.max-sends-per-hour:5}") int maxSendsPerHour,
                      @Value("${app.otp.ip-sends-per-hour:20}") int ipSendsPerHour,
                      @Value("${app.otp.ip-verifies-per-hour:60}") int ipVerifiesPerHour) {
        this.mongo = mongo;
        this.userRepository = userRepository;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.clock = Clock.systemUTC();
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.resendCooldown = Duration.ofSeconds(resendSeconds);
        this.maxAttempts = maxAttempts;
        this.maxSendsPerHour = maxSendsPerHour;
        this.ipSendsPerHour = ipSendsPerHour;
        this.ipVerifiesPerHour = ipVerifiesPerHour;
        if (secret == null || secret.isBlank()) {
            // Fine for development: codes already sent stop working after a restart.
            log.warn("OTP_SECRET is not set; using a random key for this run. Set OTP_SECRET in production.");
            this.hmacKey = new byte[32];
            random.nextBytes(this.hmacKey);
        } else {
            this.hmacKey = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public boolean isDevMode() {
        return mailer.isDevMode();
    }

    public int getResendSeconds() {
        return (int) resendCooldown.toSeconds();
    }

    public int getTtlSeconds() {
        return (int) ttl.toSeconds();
    }

    public int getCodeLength() {
        return CODE_LENGTH;
    }

    /**
     * Sends a code to {@code email} (normalised) if the address is eligible for the purpose. Callers always get
     * the same answer either way, so the response doesn't reveal whether an address is registered.
     */
    public SendResult send(Purpose purpose, String email, String clientIp) {
        if (!rateLimiter.tryAcquire("otp-send-ip:" + clientIp, ipSendsPerHour, SEND_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many code requests from your network. Please try again later.", "email");
        }
        String id = OtpChallenge.idFor(purpose.name(), email);
        synchronized (locks[Math.floorMod(id.hashCode(), locks.length)]) {
            return sendLocked(purpose, email, id);
        }
    }

    private SendResult sendLocked(Purpose purpose, String email, String id) {
        Instant now = clock.instant();
        OtpChallenge challenge = Optional.ofNullable(mongo.findById(id, OtpChallenge.class)).orElseGet(OtpChallenge::new);

        if (challenge.getLastSentAt() != null) {
            long wait = Duration.between(now, challenge.getLastSentAt().plus(resendCooldown)).toSeconds();
            if (wait > 0) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + wait + " seconds before requesting another code.", "email");
            }
        }
        if (challenge.getWindowStart() == null || !now.isBefore(challenge.getWindowStart().plus(SEND_WINDOW))) {
            challenge.setWindowStart(now);
            challenge.setSendsInWindow(0);
        }
        if (challenge.getSendsInWindow() >= maxSendsPerHour) {
            long minutes = Math.max(1, Duration.between(now, challenge.getWindowStart().plus(SEND_WINDOW)).toMinutes());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes have been requested for this email. Try again in about " + minutes + " minutes.", "email");
        }

        boolean eligible = isEligible(purpose, email);
        String code = eligible ? randomCode() : null;
        String nonce = HexFormat.of().formatHex(randomBytes(16));

        challenge.setId(id);
        challenge.setEmail(email);
        challenge.setPurpose(purpose.name());
        challenge.setNonce(nonce);
        challenge.setCodeHash(code == null ? null : hash(nonce, code));
        challenge.setAttempts(0);
        challenge.setExpiresAt(now.plus(ttl));
        challenge.setLastSentAt(now);
        challenge.setSendsInWindow(challenge.getSendsInWindow() + 1);
        Instant windowEnd = challenge.getWindowStart().plus(SEND_WINDOW);
        challenge.setPurgeAt(windowEnd.isAfter(challenge.getExpiresAt()) ? windowEnd : challenge.getExpiresAt());
        mongo.save(challenge);

        try {
            if (eligible) {
                mailer.sendCode(email, code, (int) Math.max(1, ttl.toMinutes()));
            } else if (purpose == Purpose.REGISTER) {
                mailer.sendNotice(email, "You already have a LibriSphere account",
                        "Someone tried to create a new LibriSphere account with this email address, but it already has one. "
                                + "You can sign in instead, or reset access from the sign-in page. If this wasn't you, ignore this email.");
            }
        } catch (MailException e) {
            log.error("Verification email to {} failed: {}", CodeMailer.mask(email), e.getMessage());
            if (eligible) {
                // The code never arrived. Drop it, and let the user retry without waiting for the cooldown.
                mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                        new Update().unset("codeHash").unset("lastSentAt"), OtpChallenge.class);
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "We couldn't send the email right now. Please try again in a minute.", "email");
            }
        }
        return new SendResult((int) resendCooldown.toSeconds(), (int) ttl.toSeconds());
    }

    /**
     * Checks a code. On success the code is used up and can't be used again.
     *
     * @throws ApiException 400 with field "code" for a wrong or expired code, or 429 once the attempt limit is reached
     */
    public void verify(Purpose purpose, String email, String code, String clientIp) {
        String digits = code == null ? "" : code.trim();
        if (!digits.matches("\\d{" + CODE_LENGTH + "}")) {
            throw ApiException.badRequest("code", "Enter the " + CODE_LENGTH + "-digit code we sent you.");
        }
        if (!rateLimiter.tryAcquire("otp-verify-ip:" + clientIp, ipVerifiesPerHour, SEND_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification attempts from your network. Please try again later.", "code");
        }

        String id = OtpChallenge.idFor(purpose.name(), email);
        Instant now = clock.instant();
        // Count the attempt atomically before comparing, so parallel guesses can't exceed the limit
        OtpChallenge challenge = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("expiresAt").gt(now).and("attempts").lt(maxAttempts)),
                new Update().inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                OtpChallenge.class);

        if (challenge == null) {
            OtpChallenge existing = mongo.findById(id, OtpChallenge.class);
            if (existing != null && existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(now)
                    && existing.getAttempts() >= maxAttempts) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many incorrect attempts. Request a new code.", "code");
            }
            throw ApiException.badRequest("code", "This code has expired or was already used. Request a new one.");
        }

        boolean matches = challenge.getCodeHash() != null && MessageDigest.isEqual(
                challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(challenge.getNonce(), digits).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int left = maxAttempts - challenge.getAttempts();
            if (left <= 0) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts. Request a new code.", "code");
            }
            throw ApiException.badRequest("code", "That code isn't right. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }

        // Single use: only the request that deletes this exact code succeeds
        long deleted = mongo.remove(Query.query(Criteria.where("_id").is(id).and("nonce").is(challenge.getNonce())),
                OtpChallenge.class).getDeletedCount();
        if (deleted != 1) {
            throw ApiException.badRequest("code", "This code has expired or was already used. Request a new one.");
        }
    }

    private boolean isEligible(Purpose purpose, String email) {
        Optional<User> owner = userRepository.findByEmailIgnoreCase(email);
        return switch (purpose) {
            // Only verified addresses: codes are never sent to addresses nobody has proved they own
            case LOGIN -> owner.isPresent() && owner.get().isEmailVerified();
            case REGISTER -> owner.isEmpty();
            case VERIFY_EMAIL -> owner.isPresent();
            case CHANGE_EMAIL -> owner.isEmpty();
        };
    }

    private String randomCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }

    private String hash(String nonce, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal((nonce + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
