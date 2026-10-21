package com.bookstore.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The current one-time code for an email address and purpose. There is at most one per pair, with id
 * "PURPOSE:name@example.com". Only an HMAC of the code is stored. MongoDB deletes the document once
 * {@code purgeAt} passes, and that stays after the code's own expiry so the hourly send count survives it.
 */
@Document(collection = "otp_challenges")
public class OtpChallenge {

    @Id
    private String id;
    private String email;
    private String purpose;

    /** HMAC-SHA256 of nonce + code; null when no code was sent (the address isn't eligible for this purpose). */
    private String codeHash;
    /** Random per-send value; ties the hash to this send and lets a successful verify delete exactly this code. */
    private String nonce;
    private Instant expiresAt;
    private int attempts;

    private Instant lastSentAt;
    private Instant windowStart;
    private int sendsInWindow;

    @Indexed(expireAfterSeconds = 0)
    private Instant purgeAt;

    public static String idFor(String purpose, String email) {
        return purpose + ":" + email;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public int getSendsInWindow() {
        return sendsInWindow;
    }

    public void setSendsInWindow(int sendsInWindow) {
        this.sendsInWindow = sendsInWindow;
    }

    public Instant getPurgeAt() {
        return purgeAt;
    }

    public void setPurgeAt(Instant purgeAt) {
        this.purgeAt = purgeAt;
    }
}
