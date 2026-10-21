package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.UserDto;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.AvatarService;
import com.bookstore.ecommerce.service.OtpService;
import com.bookstore.ecommerce.service.OtpService.Purpose;
import com.bookstore.ecommerce.service.EmailAddresses;
import com.bookstore.ecommerce.service.PasswordResetService;
import com.bookstore.ecommerce.service.PasswordRules;
import com.bookstore.ecommerce.service.RateLimiter;
import com.bookstore.ecommerce.service.UserService;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    /** Session attribute: the email address this browser proved it owns during sign-up, and until when. */
    static final String VERIFIED_EMAIL = "verifiedEmail";
    private static final Duration REGISTRATION_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration REMEMBER_ME = Duration.ofDays(30);

    private final UserService userService;
    private final OtpService otpService;
    private final AvatarService avatarService;
    private final RateLimiter rateLimiter;
    private final PasswordResetService passwordResetService;

    public AuthApiController(UserService userService, OtpService otpService, AvatarService avatarService, RateLimiter rateLimiter,
                             PasswordResetService passwordResetService) {
        this.userService = userService;
        this.otpService = otpService;
        this.avatarService = avatarService;
        this.rateLimiter = rateLimiter;
        this.passwordResetService = passwordResetService;
    }

    /**
     * {@code remember}: keep the user signed in for 30 days instead of until the browser closes.
     * {@code role}: "ADMIN" from the admin sign-in tab, which only administrators may use.
     */
    public record LoginRequest(String username, String password, Boolean remember, String role) {
    }

    public record OtpSendRequest(String email, String purpose) {
    }

    public record EmailCodeRequest(String email, String code, Boolean remember) {
    }

    public record ForgotPasswordRequest(String email) {
    }

    public record ResetTokenRequest(String token) {
    }

    public record PasswordResetRequest(String token, String newPassword, String confirmPassword) {
    }

    record VerifiedEmail(String email, Instant until) implements Serializable {
    }

    /** Who is signed in (user is null for guests) and how many items are in the cart. */
    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Map<String, Object> body = new HashMap<>();
        body.put("user", UserDto.of(SessionAuth.current(session)));
        body.put("cartCount", SessionCart.count(session));
        return body;
    }

    /** Settings the sign-in and sign-up forms need: code length, timers, and whether email is in development mode. */
    @GetMapping("/otp/config")
    public Map<String, Object> otpConfig() {
        return Map.of(
                "devMode", otpService.isDevMode(),
                "codeLength", otpService.getCodeLength(),
                "resendSeconds", otpService.getResendSeconds(),
                "expiresSeconds", otpService.getTtlSeconds());
    }

    /** Password sign-in with a username or email address. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response,
                                     HttpSession session) {
        String identifier = blankToEmpty(req.username()).trim();
        String ip = request.getRemoteAddr();
        String accountKey = "login-fail:" + ip + ":" + identifier.toLowerCase(Locale.ROOT);
        String ipKey = "login-fail-ip:" + ip;
        if (!rateLimiter.isAllowed(accountKey, 10, LOGIN_FAILURE_WINDOW) || !rateLimiter.isAllowed(ipKey, 50, LOGIN_FAILURE_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
        User user = userService.loginUser(identifier, blankToEmpty(req.password())).orElse(null);
        if (user == null) {
            rateLimiter.hit(accountKey);
            rateLimiter.hit(ipKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        rateLimiter.reset(accountKey);
        if ("ADMIN".equals(req.role()) && !"ADMIN".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This sign-in is only for administrators. Use the Sign in tab instead.");
        }
        return signIn(user, Boolean.TRUE.equals(req.remember()), request, response, session);
    }

    /**
     * Sends a one-time code by email. purpose: REGISTER (sign-up), LOGIN (code sign-in) or
     * VERIFY_EMAIL (signed in; the code goes to the account's own address), CHANGE_EMAIL (signed in;
     * the code goes to the new address, which no other account may use).
     */
    @PostMapping("/otp/send")
    public Map<String, Object> sendOtp(@RequestBody OtpSendRequest req, HttpServletRequest request, HttpSession session) {
        Purpose purpose = parsePurpose(req.purpose());
        String email = switch (purpose) {
            case VERIFY_EMAIL -> EmailAddresses.normalize(SessionAuth.require(session).getEmail());
            case CHANGE_EMAIL -> newAddressFor(SessionAuth.require(session), req.email());
            default -> EmailAddresses.normalize(req.email());
        };
        OtpService.SendResult result = otpService.send(purpose, email, request.getRemoteAddr());
        String message = switch (purpose) {
            case LOGIN -> "If this email belongs to a verified account, we've sent it a sign-in code.";
            default -> "We've emailed you a 6-digit verification code.";
        };
        return Map.of("message", message, "resendIn", result.resendInSeconds(), "expiresIn", result.expiresInSeconds());
    }

    /** Checks a sign-up code. On success this browser session may register an account with that address. */
    @PostMapping("/otp/verify")
    public Map<String, Object> verifyOtp(@RequestBody EmailCodeRequest req, HttpServletRequest request, HttpSession session) {
        String email = EmailAddresses.normalize(req.email());
        otpService.verify(Purpose.REGISTER, email, req.code(), request.getRemoteAddr());
        session.setAttribute(VERIFIED_EMAIL, new VerifiedEmail(email, Instant.now().plus(REGISTRATION_WINDOW)));
        return Map.of("verified", true, "email", email, "message", "Email address verified.");
    }

    /**
     * "Forgot password": emails a single-use reset link to the address, if it belongs to an account with a
     * verified email. The answer is the same either way, so it doesn't reveal which addresses have accounts.
     */
    @PostMapping("/password/forgot")
    public Map<String, Object> forgotPassword(@RequestBody ForgotPasswordRequest req, HttpServletRequest request) {
        String email = EmailAddresses.normalize(req.email());
        passwordResetService.requestReset(email, request.getRemoteAddr());
        return Map.of("message", "If an account with a verified email matches " + email + ", we've sent it a link to reset the password.",
                "expiresInMinutes", passwordResetService.getTtlMinutes());
    }

    /** Whether a reset link can still be used, so the page can say so before asking for a new password. */
    @PostMapping("/password/reset/check")
    public Map<String, Object> checkResetLink(@RequestBody ResetTokenRequest req) {
        if (!passwordResetService.isValid(req.token())) {
            throw PasswordResetService.invalidLink();
        }
        return Map.of("valid", true);
    }

    /** Sets a new password with the token from a reset link. The password is checked first, so a typo doesn't use the link up. */
    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@RequestBody PasswordResetRequest req) {
        PasswordRules.check(req.newPassword(), req.confirmPassword(), "newPassword");
        passwordResetService.reset(req.token(), req.newPassword());
        return Map.of("message", "Your password has been reset. Sign in with your new password.");
    }

    /** Signs in with a verified email address and the code just sent to it. */
    @PostMapping("/login/otp")
    public Map<String, Object> loginWithOtp(@RequestBody EmailCodeRequest req, HttpServletRequest request, HttpServletResponse response,
                                            HttpSession session) {
        String email = EmailAddresses.normalize(req.email());
        otpService.verify(Purpose.LOGIN, email, req.code(), request.getRemoteAddr());
        // LOGIN codes are only issued to verified addresses, but check again in case the account changed since
        User user = userService.findByEmail(email).filter(User::isEmailVerified)
                .orElseThrow(() -> ApiException.badRequest("code", "This code has expired or was already used. Request a new one."));
        return signIn(user, Boolean.TRUE.equals(req.remember()), request, response, session);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    /**
     * Creates an account. Sent as multipart/form-data so a profile picture can come with it. The email
     * address must have been verified with /otp/verify in this session, and must not have changed since.
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestParam(required = false) String role,
                                        @RequestParam(required = false) String vendorShopName,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String email,
                                        @RequestParam(required = false) String password,
                                        @RequestParam(required = false) String confirmPassword,
                                        @RequestPart(value = "avatar", required = false) MultipartFile avatar,
                                        HttpSession session) {
        String accountRole = "VENDOR".equals(role) ? "VENDOR" : "BUYER";  // never let a sign-up create an admin
        String name = blankToEmpty(username).trim();
        String pass = blankToEmpty(password);
        String shop = blankToEmpty(vendorShopName).trim();

        if ("VENDOR".equals(accountRole) && shop.isEmpty()) {
            throw ApiException.badRequest("vendorShopName", "Shop or publisher name is required for seller accounts.");
        }
        if (shop.length() > 80) {
            throw ApiException.badRequest("vendorShopName", "Keep the shop name under 80 characters.");
        }
        if (!name.matches("[A-Za-z0-9_.-]{3,30}")) {
            throw ApiException.badRequest("username", "Use 3–30 letters, numbers, dots, dashes or underscores (no spaces).");
        }
        String mail = EmailAddresses.normalize(email);
        PasswordRules.check(pass, confirmPassword, "password");

        Object verified = session.getAttribute(VERIFIED_EMAIL);
        if (!(verified instanceof VerifiedEmail v) || !v.email().equals(mail) || v.until().isBefore(Instant.now())) {
            throw ApiException.badRequest("email", "Verify your email address with the code we send you before creating your account.");
        }
        if (userService.usernameTaken(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is taken. Try another.", "username");
        }
        if (userService.emailTaken(mail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists. Sign in instead.", "email");
        }

        User user = new User(name, null, mail, accountRole);
        if ("VENDOR".equals(accountRole)) {
            user.setVendorName(shop);
        }
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());

        String avatarKey = avatar == null || avatar.isEmpty() ? null : avatarService.store(avatar);
        user.setAvatarKey(avatarKey);
        try {
            userService.registerUser(user, pass);
        } catch (RuntimeException e) {
            avatarService.delete(avatarKey); // don't leave an orphaned picture behind
            throw e;
        }
        session.removeAttribute(VERIFIED_EMAIL);

        String message = "VENDOR".equals(accountRole)
                ? "Account created. An administrator will review your seller application before you can sign in."
                : "Account created. You can now sign in.";
        return Map.of("message", message, "role", accountRole);
    }

    private Map<String, Object> signIn(User user, boolean remember, HttpServletRequest request, HttpServletResponse response,
                                       HttpSession session) {
        if ("VENDOR".equals(user.getRole()) && !user.isVendorApproved()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your seller account is waiting for administrator approval.");
        }
        String sessionId = request.changeSessionId(); // new session id on sign-in prevents session fixation
        session.setAttribute(SessionAuth.USER, user);
        if (remember) {
            rememberSession(sessionId, request, response, session);
        }
        return me(session);
    }

    /**
     * "Keep me signed in": the session lasts 30 days without activity, and its cookie survives closing the
     * browser. Sent after the container's own session cookie, so the browser keeps this persistent one.
     */
    private static void rememberSession(String sessionId, HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        session.setMaxInactiveInterval((int) REMEMBER_ME.toSeconds());
        SessionCookieConfig config = request.getServletContext().getSessionCookieConfig();
        String path = config.getPath() != null ? config.getPath()
                : request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        ResponseCookie cookie = ResponseCookie.from(config.getName() != null ? config.getName() : "JSESSIONID", sessionId)
                .path(path)
                .maxAge(REMEMBER_ME)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** The normalised address a signed-in user wants to switch to; it must differ from theirs and be unused. */
    private String newAddressFor(User user, String email) {
        String mail = EmailAddresses.normalize(email);
        if (mail.equalsIgnoreCase(user.getEmail())) {
            throw ApiException.badRequest("email", "That's already your email address.");
        }
        if (userService.emailTaken(mail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another account already uses this email address.", "email");
        }
        return mail;
    }

    private static Purpose parsePurpose(String value) {
        try {
            return Purpose.valueOf(blankToEmpty(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("purpose", "Unknown code purpose.");
        }
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }
}
