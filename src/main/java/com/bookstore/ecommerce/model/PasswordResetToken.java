package com.bookstore.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A pending "reset your password" link. Only a SHA-256 hash of the link's token is stored (as the id), so the
 * database never holds a usable link. MongoDB deletes the document automatically once it expires.
 */
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private String tokenHash;
    @Indexed
    private Long userId;
    private Instant createdAt;
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public PasswordResetToken() {
    }

    public PasswordResetToken(String tokenHash, Long userId, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
