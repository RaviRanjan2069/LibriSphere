package com.bookstore.ecommerce.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DevCodeMailerTest {

    @Test
    void keepsLatestCodePerAddress() {
        DevCodeMailer mailer = new DevCodeMailer();
        mailer.sendCode("a@example.com", "111111", 5);
        mailer.sendCode("b@example.com", "222222", 5);
        mailer.sendCode("a@example.com", "333333", 5);
        assertEquals("333333", mailer.latestCodeFor("A@example.com").orElseThrow().code());
        assertEquals("222222", mailer.latestCodeFor("b@example.com").orElseThrow().code());
        assertTrue(mailer.latestCodeFor("c@example.com").isEmpty());
    }

    @Test
    void noticesAreNotCodes() {
        DevCodeMailer mailer = new DevCodeMailer();
        mailer.sendNotice("a@example.com", "You already have an account", "text");
        assertTrue(mailer.latestCodeFor("a@example.com").isEmpty());
    }
}
