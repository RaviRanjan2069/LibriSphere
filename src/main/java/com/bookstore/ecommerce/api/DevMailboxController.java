package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.mail.CodeMailer;
import com.bookstore.ecommerce.mail.DevCodeMailer;
import com.bookstore.ecommerce.service.EmailAddresses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;

/**
 * DEVELOPMENT ONLY: lets the sign-up and sign-in forms fill in the code automatically while no mail
 * account is configured. It answers only in development mode and only to requests from this computer.
 * Everywhere else it is a plain 404, and as soon as MAIL_USERNAME and MAIL_PASSWORD are set it never
 * answers at all.
 */
@RestController
public class DevMailboxController {

    private final CodeMailer mailer;

    public DevMailboxController(CodeMailer mailer) {
        this.mailer = mailer;
    }

    @GetMapping("/api/dev/mailbox/latest")
    public Map<String, Object> latest(@RequestParam(required = false) String email, HttpServletRequest request) {
        if (!(mailer instanceof DevCodeMailer dev) || !isLoopback(request.getRemoteAddr())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found.");
        }
        return dev.latestCodeFor(EmailAddresses.normalize(email))
                .map(m -> Map.<String, Object>of("code", m.code(), "sentAt", m.sentAt().toString()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No code has been sent to this address."));
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
