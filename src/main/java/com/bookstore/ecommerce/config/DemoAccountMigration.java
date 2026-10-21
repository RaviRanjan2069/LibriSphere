package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Moves the demo accounts from their original, real-looking email addresses (which belong to strangers, e.g.
 * buyer@gmail.com) to reserved example.com addresses that nobody owns, so password resets and codes never email
 * a stranger. Only changes a demo account that still has its original address.
 */
@Component
@Order(2)
public class DemoAccountMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountMigration.class);

    // username -> { original address, new address }
    private static final Map<String, String[]> DEMO_EMAILS = Map.of(
            "admin", new String[]{"admin@bookstore.com", "admin@example.com"},
            "buyer", new String[]{"buyer@gmail.com", "buyer@example.com"},
            "vendor_a", new String[]{"sales@apexbooks.com", "apexbooks@example.com"},
            "vendor_b", new String[]{"contact@beaconbooks.net", "beaconbookshop@example.com"},
            "vendor_c", new String[]{"admin@cosmosreads.org", "cosmosreads@example.com"});

    private final MongoTemplate mongo;

    public DemoAccountMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) {
        int moved = 0;
        for (Map.Entry<String, String[]> demo : DEMO_EMAILS.entrySet()) {
            Query original = Query.query(Criteria.where("username").is(demo.getKey()).and("email").is(demo.getValue()[0]));
            moved += (int) mongo.updateFirst(original,
                    new Update().set("email", demo.getValue()[1]).set("emailVerified", false).unset("emailVerifiedAt"),
                    User.class).getModifiedCount();
        }
        if (moved > 0) {
            log.info("Moved {} demo accounts to example.com email addresses", moved);
        }
    }
}
