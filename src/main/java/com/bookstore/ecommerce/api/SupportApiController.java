package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.api.Dtos.SupportRequestDto;
import com.bookstore.ecommerce.model.SupportRequest;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.service.SupportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Help & support for any signed-in user: raise requests and see, reopen or delete their own. */
@RestController
@RequestMapping("/api/support")
public class SupportApiController {

    private final SupportService support;

    public SupportApiController(SupportService support) {
        this.support = support;
    }

    public record NewRequest(String topic, String priority, String area, String subject, String description) {
    }

    @GetMapping
    public Map<String, Object> mine(HttpSession session) {
        User user = SessionAuth.require(session);
        List<SupportRequest> requests = support.forUser(user.getId());
        return Map.of(
                "supportEmail", support.getSupportEmail(),
                "replyTo", user.getEmail(),
                "openCount", requests.stream().filter(r -> "OPEN".equals(r.getStatus())).count(),
                "requests", requests.stream().map(SupportRequestDto::of).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody NewRequest req, HttpSession session) {
        User user = SessionAuth.require(session);
        String topic = trim(req.topic());
        if (!SupportService.TOPICS.containsKey(topic)) {
            throw ApiException.badRequest("topic", "Choose a topic.");
        }
        String priority = trim(req.priority()).isEmpty() ? "NORMAL" : trim(req.priority());
        if (!SupportService.PRIORITIES.contains(priority)) {
            throw ApiException.badRequest("priority", "Choose a priority from the list.");
        }
        String area = trim(req.area()).isEmpty() ? "NONE" : trim(req.area());
        if (!SupportService.AREAS.containsKey(area)) {
            throw ApiException.badRequest("area", "Choose an option from the list.");
        }
        String subject = trim(req.subject());
        if (subject.length() < 4) {
            throw ApiException.badRequest("subject", "Add a short summary (at least 4 characters).");
        }
        if (subject.length() > 120) {
            throw ApiException.badRequest("subject", "Keep the subject under 120 characters.");
        }
        String description = trim(req.description());
        if (description.length() < 10) {
            throw ApiException.badRequest("description", "Describe the problem in a few words (at least 10 characters).");
        }
        if (description.length() > 5000) {
            throw ApiException.badRequest("description", "Keep the description under 5000 characters.");
        }
        SupportRequest saved = support.create(user, topic, priority, area, subject, description);
        return Map.of("message", "Request " + saved.getReference() + " sent. We'll reply to " + user.getEmail() + ".",
                "request", SupportRequestDto.of(saved));
    }

    @PostMapping("/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable Long id, HttpSession session) {
        SupportRequest r = support.reopen(SessionAuth.require(session), id);
        return Map.of("message", "Request " + r.getReference() + " reopened.", "request", SupportRequestDto.of(r));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpSession session) {
        support.delete(SessionAuth.require(session), id);
        return Map.of("message", "Request deleted.");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
