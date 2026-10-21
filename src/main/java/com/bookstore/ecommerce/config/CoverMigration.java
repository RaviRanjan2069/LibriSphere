package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Points books that use an Open Library cover at the copy stored with the site (static/covers/&lt;ISBN&gt;.jpg),
 * when there is one. Open Library's image server is slow or unreachable on some networks, so the stored copies
 * load more reliably. Runs on every start and only changes books that still use an Open Library ISBN cover.
 */
@Component
@Order(2)
public class CoverMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CoverMigration.class);
    private static final Pattern OPEN_LIBRARY_ISBN = Pattern.compile("^https?://covers\\.openlibrary\\.org/b/isbn/([0-9Xx]+)-[SML]\\.jpg$");

    private final MongoTemplate mongo;

    public CoverMigration(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) {
        int moved = 0;
        for (Book book : mongo.find(Query.query(Criteria.where("coverUrl").regex("^https?://covers\\.openlibrary\\.org/")), Book.class)) {
            Matcher m = OPEN_LIBRARY_ISBN.matcher(book.getCoverUrl());
            if (!m.matches() || !new ClassPathResource("static/covers/" + m.group(1) + ".jpg").exists()) {
                continue;
            }
            mongo.updateFirst(Query.query(Criteria.where("_id").is(book.getId())),
                    new Update().set("coverUrl", "/covers/" + m.group(1) + ".jpg"), Book.class);
            moved++;
        }
        if (moved > 0) {
            log.info("Switched {} book covers to the copies stored with the site", moved);
        }
    }
}
