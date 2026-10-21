package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import com.bookstore.ecommerce.mail.CodeMailer;
import com.bookstore.ecommerce.model.SupportRequest;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.SupportRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Help & support requests. Users raise and manage their own; administrators answer and resolve them.
 * The support inbox is emailed about each new request and the user about each reply; a failed email never
 * loses the request.
 */
@Service
public class SupportService {

    /** Allowed values (key -> label shown in the form and in emails). */
    public static final Map<String, String> TOPICS = Map.of(
            "ORDER", "Order or delivery",
            "PAYMENT", "Payment or refund",
            "BOOK", "Book listing or details",
            "ACCOUNT", "Account or sign-in",
            "SELLER", "Selling on LibriSphere",
            "BUG", "Something isn't working",
            "COMPLAINT", "Complaint",
            "OTHER", "Other");
    public static final List<String> PRIORITIES = List.of("LOW", "NORMAL", "HIGH", "URGENT");
    public static final Map<String, String> AREAS = Map.of(
            "NONE", "Not sure / not needed",
            "BROWSE", "Browse & search",
            "BOOK_PAGE", "Book page",
            "CART", "Cart & checkout",
            "ORDERS", "My orders",
            "SELLER", "Seller dashboard",
            "ACCOUNT", "Profile & sign-in");

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportRequestRepository requests;
    private final CodeMailer mailer;
    private final RateLimiter rateLimiter;
    private final String supportEmail;

    public SupportService(SupportRequestRepository requests, CodeMailer mailer, RateLimiter rateLimiter,
                          @Value("${app.support.email:mentora.smartlearning@gmail.com}") String supportEmail) {
        this.requests = requests;
        this.mailer = mailer;
        this.rateLimiter = rateLimiter;
        this.supportEmail = supportEmail;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public List<SupportRequest> forUser(Long userId) {
        return requests.findByUserId(userId);
    }

    public List<SupportRequest> all() {
        return requests.findAllNewestFirst();
    }

    public SupportRequest create(User user, String topic, String priority, String area, String subject, String description) {
        if (!rateLimiter.tryAcquire("support:" + user.getId(), 10, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You've sent a lot of requests recently. Please wait a while before sending another.");
        }
        SupportRequest r = new SupportRequest();
        r.setUserId(user.getId());
        r.setUsername(user.getUsername());
        r.setEmail(user.getEmail());
        r.setRole(user.getRole());
        r.setTopic(topic);
        r.setPriority(priority);
        r.setArea(area);
        r.setSubject(subject);
        r.setDescription(description);
        r.setCreatedAt(LocalDateTime.now());
        SupportRequest saved = requests.save(r);
        notify(supportEmail, "New support request " + saved.getReference() + ": " + subject,
                "From " + user.getUsername() + " (" + user.getEmail() + ", " + user.getRole().toLowerCase() + ")\n"
                        + "Topic: " + TOPICS.get(topic) + " · Priority: " + priority.toLowerCase() + " · Where: " + AREAS.get(area) + "\n\n"
                        + description + "\n\nReply from the Support requests section of the Admin dashboard.");
        return saved;
    }

    public SupportRequest reopen(User user, Long id) {
        SupportRequest r = own(user, id);
        if (!"RESOLVED".equals(r.getStatus())) {
            throw ApiException.badRequest(null, "This request is already open.");
        }
        r.setStatus("OPEN");
        return requests.save(r);
    }

    public void delete(User user, Long id) {
        requests.delete(own(user, id));
    }

    /** An administrator answers a request and marks it resolved; the user is emailed the reply. */
    public SupportRequest resolve(User admin, Long id, String reply) {
        SupportRequest r = requests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support request not found."));
        r.setStatus("RESOLVED");
        r.setResolution(reply);
        r.setResolvedAt(LocalDateTime.now());
        r.setResolvedBy(admin.getUsername());
        SupportRequest saved = requests.save(r);
        notify(r.getEmail(), "Your request " + r.getReference() + " has been resolved",
                "Hello " + r.getUsername() + ",\n\nThe LibriSphere team has answered your request \"" + r.getSubject() + "\":\n\n"
                        + reply + "\n\nIf this didn't solve it, reopen the request from the Help & support page.");
        return saved;
    }

    /** Requests are only ever visible to and changeable by the user who raised them (404 otherwise). */
    private SupportRequest own(User user, Long id) {
        return requests.findById(id).filter(r -> r.getUserId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support request not found."));
    }

    private void notify(String to, String subject, String text) {
        if (EmailAddresses.isReservedForTesting(to)) {
            return;
        }
        try {
            mailer.sendNotice(to, subject, text);
        } catch (CodeMailer.MailException e) {
            log.warn("Support email to {} failed; the request is saved anyway", CodeMailer.mask(to));
        }
    }
}
