package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.UserDto;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.AvatarService;
import com.bookstore.ecommerce.service.OtpService;
import com.bookstore.ecommerce.service.OtpService.Purpose;
import com.bookstore.ecommerce.service.EmailAddresses;
import com.bookstore.ecommerce.service.PasswordRules;
import com.bookstore.ecommerce.service.RateLimiter;
import com.bookstore.ecommerce.service.UserService;
import com.bookstore.ecommerce.service.UserService.AvatarChange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * The signed-in user's own account: details, profile picture, email, password and deleting the account.
 * Every route acts only on the session's user.
 */
@RestController
@RequestMapping("/api/users/me")
public class UserApiController {

    private final UserService userService;
    private final AvatarService avatarService;
    private final OtpService otpService;
    private final RateLimiter rateLimiter;

    public UserApiController(UserService userService, AvatarService avatarService, OtpService otpService, RateLimiter rateLimiter) {
        this.userService = userService;
        this.avatarService = avatarService;
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
    }

    public record CodeRequest(String code) {
    }

    public record DetailsRequest(String fullName, String gender, String vendorName) {
    }

    public record EmailChangeRequest(String email, String code) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword, String confirmPassword) {
    }

    public record PasswordRequest(String password) {
    }

    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Duration PASSWORD_CHECK_WINDOW = Duration.ofMinutes(15);

    @GetMapping
    public UserDto me(HttpSession session) {
        return UserDto.of(fresh(session));
    }

    /** Saves full name, gender and (for sellers) the shop name. The username can't be changed. */
    @PutMapping
    public Map<String, Object> updateDetails(@RequestBody DetailsRequest req, HttpSession session) {
        User current = fresh(session);
        String fullName = blankToEmpty(req.fullName()).trim();
        if (fullName.length() > 60) {
            throw ApiException.badRequest("fullName", "Keep your name under 60 characters.");
        }
        String gender = blankToEmpty(req.gender()).trim();
        if (!gender.isEmpty() && !GENDERS.contains(gender)) {
            throw ApiException.badRequest("gender", "Choose an option from the list.");
        }
        String shop = blankToEmpty(req.vendorName()).trim();
        if ("VENDOR".equals(current.getRole())) {
            if (shop.isEmpty()) {
                throw ApiException.badRequest("vendorName", "Enter your shop or publisher name.");
            }
            if (shop.length() > 80) {
                throw ApiException.badRequest("vendorName", "Keep the shop name under 80 characters.");
            }
        }
        User updated = userService.updateDetails(current.getId(), fullName.isEmpty() ? null : fullName,
                gender.isEmpty() ? null : gender, shop);
        session.setAttribute(SessionAuth.USER, updated);
        return Map.of("message", "Your details were saved.", "user", UserDto.of(updated));
    }

    /** Switches to a new email address, using a code sent to it by /api/auth/otp/send with purpose CHANGE_EMAIL. */
    @PostMapping("/email")
    public Map<String, Object> changeEmail(@RequestBody EmailChangeRequest req, HttpServletRequest request, HttpSession session) {
        User current = fresh(session);
        String email = EmailAddresses.normalize(req.email());
        otpService.verify(Purpose.CHANGE_EMAIL, email, req.code(), request.getRemoteAddr());
        if (userService.emailTaken(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another account already uses this email address.", "email");
        }
        User updated = userService.changeEmail(current.getId(), email);
        session.setAttribute(SessionAuth.USER, updated);
        return Map.of("message", "Email address changed and confirmed.", "user", UserDto.of(updated));
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest request, HttpSession session) {
        User current = fresh(session);
        String next = blankToEmpty(req.newPassword());
        PasswordRules.check(next, req.confirmPassword(), "newPassword");
        checkPassword(current, req.currentPassword(), "currentPassword");
        if (userService.passwordMatches(current, next)) {
            throw ApiException.badRequest("newPassword", "Choose a password different from your current one.");
        }
        User updated = userService.changePassword(current.getId(), next);
        request.changeSessionId();
        session.setAttribute(SessionAuth.USER, updated);
        return Map.of("message", "Your password was changed.", "user", UserDto.of(updated));
    }

    /** Deletes the signed-in buyer's account after re-checking the password, then signs out. */
    @DeleteMapping
    public Map<String, Object> deleteAccount(@RequestBody PasswordRequest req, HttpSession session) {
        User current = fresh(session);
        if ("VENDOR".equals(current.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Seller accounts can't be deleted here. Contact an administrator to close your shop.");
        }
        if (!"BUYER".equals(current.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator accounts can't be deleted.");
        }
        checkPassword(current, req.password(), "password");
        avatarService.delete(userService.deleteBuyer(current.getId()));
        session.invalidate();
        return Map.of("message", "Your account has been deleted.");
    }

    /** Re-checks the password before sensitive changes; wrong guesses are rate limited like sign-in. */
    private void checkPassword(User user, String rawPassword, String field) {
        String key = "password-check:" + user.getId();
        if (!rateLimiter.isAllowed(key, 10, PASSWORD_CHECK_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong passwords. Please wait a few minutes and try again.", field);
        }
        if (!userService.passwordMatches(user, rawPassword)) {
            rateLimiter.hit(key);
            throw ApiException.badRequest(field, "That password isn't right.");
        }
        rateLimiter.reset(key);
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }

    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadPicture(@RequestPart(value = "file", required = false) MultipartFile file, HttpSession session) {
        User current = SessionAuth.require(session);
        String key = avatarService.store(file);
        AvatarChange change;
        try {
            change = userService.setAvatar(current.getId(), key);
        } catch (RuntimeException e) {
            avatarService.delete(key);
            throw e;
        }
        avatarService.delete(change.previousKey());
        session.setAttribute(SessionAuth.USER, change.user());
        return Map.of("message", "Profile picture updated.", "user", UserDto.of(change.user()));
    }

    @DeleteMapping("/profile-picture")
    public Map<String, Object> removePicture(HttpSession session) {
        User current = SessionAuth.require(session);
        AvatarChange change = userService.setAvatar(current.getId(), null);
        avatarService.delete(change.previousKey());
        session.setAttribute(SessionAuth.USER, change.user());
        return Map.of("message", "Profile picture removed.", "user", UserDto.of(change.user()));
    }

    /**
     * Verifies the email already on the account, using a code from /api/auth/otp/send with purpose
     * VERIFY_EMAIL. Verified addresses can be used for email-code sign-in.
     */
    @PostMapping("/email/verify")
    public Map<String, Object> verifyEmail(@RequestBody CodeRequest req, HttpServletRequest request, HttpSession session) {
        User current = fresh(session);
        otpService.verify(Purpose.VERIFY_EMAIL, EmailAddresses.normalize(current.getEmail()), req.code(), request.getRemoteAddr());
        User updated = userService.markEmailVerified(current.getId());
        session.setAttribute(SessionAuth.USER, updated);
        return Map.of("message", "Email verified. You can now sign in with a code sent to it.", "user", UserDto.of(updated));
    }

    /** The session holds a copy of the user; read the stored one so the response reflects the latest data. */
    private User fresh(HttpSession session) {
        User current = SessionAuth.require(session);
        User stored = userService.findById(current.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue."));
        session.setAttribute(SessionAuth.USER, stored);
        return stored;
    }
}
