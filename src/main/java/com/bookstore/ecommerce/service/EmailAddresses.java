package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validates email addresses and normalises them (trimmed, lower case) so each address has one spelling. */
public final class EmailAddresses {

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    private EmailAddresses() {
    }

    /**
     * True for addresses on domains reserved for testing and documentation (example.com, *.test, ...), such as the
     * demo accounts' addresses. Nobody owns them, so the app never sends email to them.
     */
    public static boolean isReservedForTesting(String email) {
        String domain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return domain.matches("(.+\\.)?example\\.(com|net|org)") || domain.matches(".+\\.(test|example|invalid|localhost)");
    }

    /** @throws ApiException 400 for the "email" field if the address isn't valid */
    public static String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw ApiException.badRequest("email", "Enter your email address.");
        }
        if (value.length() > 254 || !SHAPE.matcher(value).matches() || value.contains("..")) {
            throw ApiException.badRequest("email", "Enter a valid email address, like name@example.com.");
        }
        return value;
    }
}
