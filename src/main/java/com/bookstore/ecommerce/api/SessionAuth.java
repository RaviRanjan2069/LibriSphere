package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/** The signed-in user lives in the HTTP session under "currentUser". */
public final class SessionAuth {

    public static final String USER = "currentUser";

    private SessionAuth() {
    }

    public static User current(HttpSession session) {
        return (User) session.getAttribute(USER);
    }

    /** Returns the signed-in user, or fails with 401 (not signed in) / 403 (wrong role). */
    public static User require(HttpSession session, String... roles) {
        User user = current(session);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        if (roles.length > 0 && Arrays.stream(roles).noneMatch(r -> r.equals(user.getRole()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This page is only available to " + describe(roles) + " accounts.");
        }
        if ("VENDOR".equals(user.getRole()) && !user.isVendorApproved()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your seller account is pending approval.");
        }
        return user;
    }

    private static String describe(String[] roles) {
        return String.join(" or ", Arrays.stream(roles)
                .map(r -> switch (r) {
                    case "VENDOR" -> "seller";
                    case "ADMIN" -> "administrator";
                    default -> "buyer";
                })
                .toList());
    }
}
