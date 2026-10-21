package com.bookstore.ecommerce.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class User implements SequencedDocument {

    @Id
    private Long id;
    @Indexed(unique = true)
    private String username;
    private String password; // BCrypt hash, never plaintext
    @Indexed(unique = true)
    private String email;
    private String role; // "BUYER", "VENDOR", "ADMIN"

    // Optional profile details; null when not given
    private String fullName;
    private String gender; // "MALE", "FEMALE", "OTHER"

    // True once the user has proved they own the email address with a one-time code
    private boolean emailVerified;
    private Instant emailVerifiedAt;

    // Storage key of the profile picture (e.g. "avatars/3f2a....jpg"); null means the default avatar
    private String avatarKey;

    // Vendor specific fields
    private String vendorName;
    private boolean vendorApproved = false;

    public User() {
    }

    public User(String username, String password, String email, String role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public User(String username, String password, String email, String role, String vendorName, boolean vendorApproved) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.vendorName = vendorName;
        this.vendorApproved = vendorApproved;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public boolean isVendorApproved() {
        return vendorApproved;
    }

    public void setVendorApproved(boolean vendorApproved) {
        this.vendorApproved = vendorApproved;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
