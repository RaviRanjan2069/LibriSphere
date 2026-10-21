package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import com.bookstore.ecommerce.model.Order;
import com.bookstore.ecommerce.model.SupportRequest;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.OrderRepository;
import com.bookstore.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public boolean usernameTaken(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public boolean emailTaken(String email) {
        return userRepository.findByEmailIgnoreCase(email).isPresent();
    }

    /** Saves a new account. {@code rawPassword} is hashed here; the plain text is never stored. */
    public User registerUser(User user, String rawPassword) {
        if (usernameTaken(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists!");
        }
        if (emailTaken(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists!");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));

        // Simple security logic: if registering a vendor, they are not approved by default
        if ("VENDOR".equalsIgnoreCase(user.getRole())) {
            user.setVendorApproved(false);
        } else {
            user.setVendorApproved(true);
        }

        return userRepository.save(user);
    }

    /** Signs in with a username or an email address. */
    public Optional<User> loginUser(String usernameOrEmail, String password) {
        Optional<User> userOpt = usernameOrEmail.contains("@")
                ? userRepository.findByEmailIgnoreCase(usernameOrEmail)
                : userRepository.findByUsername(usernameOrEmail);
        if (userOpt.isPresent() && userOpt.get().getPassword() != null
                && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return userOpt;
        }
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(password, dummyHash()); // same work either way, so timing doesn't reveal unknown accounts
        }
        return Optional.empty();
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("not-a-real-password");
        }
        return dummyHash;
    }

    /** Marks the account's email as verified after the user entered a one-time code sent to it. */
    public User markEmailVerified(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    /** Sets (or with null, clears) the profile picture key and returns the updated user and the key it replaced. */
    public AvatarChange setAvatar(Long userId, String key) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        String previous = user.getAvatarKey();
        user.setAvatarKey(key);
        return new AvatarChange(userRepository.save(user), previous);
    }

    public record AvatarChange(User user, String previousKey) {
    }

    /** True if {@code rawPassword} is the account's current password. */
    public boolean passwordMatches(User user, String rawPassword) {
        return user.getPassword() != null && rawPassword != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /** Saves the editable profile details. {@code vendorName} is ignored for accounts that aren't sellers. */
    public User updateDetails(Long userId, String fullName, String gender, String vendorName) {
        User user = load(userId);
        user.setFullName(fullName);
        user.setGender(gender);
        if ("VENDOR".equals(user.getRole())) {
            user.setVendorName(vendorName);
        }
        return userRepository.save(user);
    }

    /** Replaces the email with a new address the user has just proved they own. */
    public User changeEmail(Long userId, String email) {
        User user = load(userId);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    public User changePassword(Long userId, String rawPassword) {
        User user = load(userId);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /**
     * Deletes a buyer account and returns its profile picture key (for the caller to remove the file).
     * Past orders stay with the sellers who fulfilled them, keeping the name and address given at checkout,
     * but no longer point at the account. Refused while any ordered item is still pending or on its way.
     */
    public String deleteBuyer(Long userId) {
        User user = load(userId);
        if (!"BUYER".equals(user.getRole())) {
            throw new IllegalStateException("Only buyer accounts can be deleted.");
        }
        boolean inProgress = orderRepository.findByBuyerId(userId).stream()
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> "PENDING".equals(item.getStatus()) || "SHIPPED".equals(item.getStatus()));
        if (inProgress) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You have orders that haven't been delivered yet. Cancel them, or wait until they arrive, then try again.");
        }
        mongoTemplate.updateMulti(Query.query(Criteria.where("buyer").is(userId)), new Update().unset("buyer"), Order.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), SupportRequest.class);
        userRepository.deleteById(userId);
        return user.getAvatarKey();
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    public List<User> getPendingVendors() {
        return userRepository.findByRole("VENDOR").stream()
                .filter(user -> !user.isVendorApproved())
                .collect(Collectors.toList());
    }

    public List<User> getAdmins() {
        return userRepository.findByRole("ADMIN");
    }

    public List<User> getAllVendors() {
        return userRepository.findByRole("VENDOR");
    }

    public void approveVendor(Long vendorId) {
        User vendor = userRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        if ("VENDOR".equalsIgnoreCase(vendor.getRole())) {
            vendor.setVendorApproved(true);
            userRepository.save(vendor);
        }
    }

    public long getVendorCount() {
        return userRepository.findByRole("VENDOR").size();
    }

    public long getBuyerCount() {
        return userRepository.findByRole("BUYER").size();
    }
}
