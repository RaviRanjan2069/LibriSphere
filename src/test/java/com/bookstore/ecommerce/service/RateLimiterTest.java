package com.bookstore.ecommerce.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allowsUpToLimitWithinWindowThenRecovers() {
        AtomicLong now = new AtomicLong(1_000_000);
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(1);
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("k", 3, window));
        }
        assertFalse(limiter.tryAcquire("k", 3, window));
        assertTrue(limiter.tryAcquire("other", 3, window), "keys are independent");

        now.addAndGet(window.toMillis() + 1);
        assertTrue(limiter.tryAcquire("k", 3, window), "old hits expire");
    }

    @Test
    void isAllowedDoesNotRecord() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.isAllowed("k", 1, Duration.ofMinutes(1)));
        }
        limiter.hit("k");
        assertFalse(limiter.isAllowed("k", 1, Duration.ofMinutes(1)));
        limiter.reset("k");
        assertTrue(limiter.isAllowed("k", 1, Duration.ofMinutes(1)));
    }
}
