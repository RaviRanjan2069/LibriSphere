package com.bookstore.ecommerce.api;

import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;

/** The shopping cart is a bookId -> quantity map kept in the HTTP session, so guests can shop before signing in. */
public final class SessionCart {

    private static final String KEY = "cart";

    private SessionCart() {
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, Integer> get(HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute(KEY);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(KEY, cart);
        }
        return cart;
    }

    public static int count(HttpSession session) {
        return get(session).values().stream().mapToInt(Integer::intValue).sum();
    }

    public static void clear(HttpSession session) {
        session.removeAttribute(KEY);
    }
}
