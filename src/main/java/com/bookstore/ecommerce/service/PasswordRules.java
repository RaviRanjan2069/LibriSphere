package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;

import java.nio.charset.StandardCharsets;

/** The password rules shared by sign-up, password change and password reset. */
public final class PasswordRules {

    private PasswordRules() {
    }

    /**
     * Checks a new password and its confirmation.
     *
     * @throws ApiException 400 for {@code field} (length) or "confirmPassword" (mismatch)
     */
    public static void check(String password, String confirmPassword, String field) {
        String pass = password == null ? "" : password;
        if (pass.length() < 8) {
            throw ApiException.badRequest(field, "Use at least 8 characters.");
        }
        if (pass.getBytes(StandardCharsets.UTF_8).length > 72) { // BCrypt only uses the first 72 bytes
            throw ApiException.badRequest(field, "Use at most 72 characters.");
        }
        if (!pass.equals(confirmPassword == null ? "" : confirmPassword)) {
            throw ApiException.badRequest("confirmPassword", "The passwords don't match.");
        }
    }
}
