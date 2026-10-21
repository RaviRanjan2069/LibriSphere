package com.bookstore.ecommerce.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window counters kept in memory, keyed by strings such as "otp-send-ip:127.0.0.1".
 * That is enough for a single server; a multi-node deployment would move these into a shared store.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records a hit and returns true, or returns false (recording nothing) if the limit is already reached. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            long now = clock.millis();
            prune(times, now, window);
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** True while fewer than {@code limit} hits were recorded in the window. Does not record a hit. */
    public boolean isAllowed(String key, int limit, Duration window) {
        Deque<Long> times = hits.get(key);
        if (times == null) {
            return true;
        }
        synchronized (times) {
            prune(times, clock.millis(), window);
            return times.size() < limit;
        }
    }

    public void hit(String key) {
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.millis());
        }
        if (hits.size() > 10_000) {
            hits.values().removeIf(d -> {
                synchronized (d) {
                    prune(d, clock.millis(), Duration.ofDays(1));
                    return d.isEmpty();
                }
            });
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    private static void prune(Deque<Long> times, long now, Duration window) {
        long cutoff = now - window.toMillis();
        while (!times.isEmpty() && times.peekFirst() <= cutoff) {
            times.pollFirst();
        }
    }
}
