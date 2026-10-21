package com.bookstore.ecommerce.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * DEVELOPMENT ONLY. Nothing is emailed. Messages are written to the server console and kept in a small
 * in-memory "dev inbox", which the sign-up and sign-in forms read on this computer (see
 * DevMailboxController) to fill in codes automatically. Codes are still random, short-lived and checked
 * by the server exactly as in production. Startup fails if this mailer would be used while the "prod"
 * profile is active (see {@link MailConfig}).
 */
public class DevCodeMailer implements CodeMailer {

    public record DevMessage(String to, String code, Instant sentAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(DevCodeMailer.class);
    private static final int KEEP = 50;
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final ConcurrentLinkedDeque<DevMessage> inbox = new ConcurrentLinkedDeque<>();

    @Override
    public void sendCode(String email, String code, int validMinutes) {
        inbox.addFirst(new DevMessage(email, code, Instant.now()));
        while (inbox.size() > KEEP) {
            inbox.pollLast();
        }
        log.warn("[DEV EMAIL - not sent] to {}: Your LibriSphere verification code is {} (expires in {} minutes)",
                CodeMailer.mask(email), code, validMinutes);
    }

    @Override
    public void sendNotice(String email, String subject, String text) {
        log.warn("[DEV EMAIL - not sent] to {}: {}", CodeMailer.mask(email), subject);
    }

    @Override
    public void sendPasswordReset(String email, String link, int validMinutes) {
        log.warn("[DEV EMAIL - not sent] to {}: Reset your password: {} (expires in {} minutes)", CodeMailer.mask(email), link, validMinutes);
    }

    @Override
    public boolean isDevMode() {
        return true;
    }

    /** The most recent code "sent" to this address in the last few minutes, if any. */
    public Optional<DevMessage> latestCodeFor(String email) {
        Instant cutoff = Instant.now().minus(MAX_AGE);
        for (Iterator<DevMessage> it = inbox.iterator(); it.hasNext(); ) {
            DevMessage m = it.next();
            if (m.sentAt().isBefore(cutoff)) {
                break;
            }
            if (m.to().equalsIgnoreCase(email)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
