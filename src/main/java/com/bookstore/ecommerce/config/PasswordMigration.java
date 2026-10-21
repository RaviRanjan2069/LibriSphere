package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Accounts created before password hashing was added stored their password as plain text. On startup,
 * replace each of those with its BCrypt hash. Users keep signing in with the same password.
 */
@Component
@Order(2) // after DataInitializer
public class PasswordMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordMigration.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigration(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static boolean isHashed(String password) {
        return password != null && password.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }

    @Override
    public void run(String... args) {
        int upgraded = 0;
        for (User user : userRepository.findAll()) {
            if (user.getPassword() != null && !isHashed(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                userRepository.save(user);
                upgraded++;
            }
        }
        if (upgraded > 0) {
            log.info("Hashed {} plain-text password(s) with BCrypt.", upgraded);
        }
    }
}
