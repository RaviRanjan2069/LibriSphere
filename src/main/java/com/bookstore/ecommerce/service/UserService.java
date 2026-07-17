package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User registerUser(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists!");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists!");
        }
        
        // Simple security logic: if registering a vendor, they are not approved by default
        if ("VENDOR".equalsIgnoreCase(user.getRole())) {
            user.setVendorApproved(false);
        } else {
            user.setVendorApproved(true);
        }
        
        return userRepository.save(user);
    }

    public Optional<User> loginUser(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            return userOpt;
        }
        return Optional.empty();
    }

    public List<User> getPendingVendors() {
        return userRepository.findByRole("VENDOR").stream()
                .filter(user -> !user.isVendorApproved())
                .collect(Collectors.toList());
    }

    public List<User> getAllVendors() {
        return userRepository.findByRole("VENDOR");
    }

    @Transactional
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
