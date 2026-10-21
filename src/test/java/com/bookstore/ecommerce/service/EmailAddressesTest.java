package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressesTest {

    @Test
    void normalisesCaseAndWhitespace() {
        assertEquals("ravi.r+books@gmail.com", EmailAddresses.normalize("  Ravi.R+Books@GMAIL.com "));
    }

    @Test
    void rejectsInvalidAddresses() {
        for (String bad : new String[]{"", "   ", "ravi", "ravi@", "@gmail.com", "ravi@gmail", "ra vi@gmail.com", "ravi@@gmail.com",
                "ravi..x@gmail.com", "<script>@x.com", null}) {
            ApiException e = assertThrows(ApiException.class, () -> EmailAddresses.normalize(bad), String.valueOf(bad));
            assertEquals("email", e.getField());
        }
    }
}
