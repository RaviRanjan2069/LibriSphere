package com.bookstore.ecommerce.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends verification emails through Brevo's transactional email API (https://api.brevo.com/v3/smtp/email).
 * It uses HTTPS, so it also works on networks that block SMTP ports. The sender address must be verified
 * in the Brevo account (Senders, domains & dedicated IPs → Senders).
 */
public class BrevoCodeMailer implements CodeMailer {

    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private static final Logger log = LoggerFactory.getLogger(BrevoCodeMailer.class);

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String from;
    private final ObjectMapper json = new ObjectMapper();

    public BrevoCodeMailer(String apiKey, String from) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ENDPOINT, apiKey, from);
    }

    BrevoCodeMailer(HttpClient http, URI endpoint, String apiKey, String from) {
        this.http = http;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code, int validMinutes) {
        send(email, CodeMailer.codeSubject(code), CodeMailer.codeText(code, validMinutes), CodeMailer.codeHtml(code, validMinutes));
    }

    @Override
    public void sendNotice(String email, String subject, String text) {
        send(email, subject, text, CodeMailer.noticeHtml(text));
    }

    @Override
    public void sendPasswordReset(String email, String link, int validMinutes) {
        send(email, CodeMailer.RESET_SUBJECT, CodeMailer.resetText(link, validMinutes), CodeMailer.resetHtml(link, validMinutes));
    }

    private void send(String to, String subject, String text, String html) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", "LibriSphere", "email", from),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", text,
                    "htmlContent", html));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Brevo explains rejections (bad key, unverified sender, ...) in "message"; it never contains the code
                log.error("Brevo rejected the email to {}: HTTP {} {}", CodeMailer.mask(to), response.statusCode(), reason(response.body()));
                throw new MailException("Brevo rejected the email (HTTP " + response.statusCode() + ")", null);
            }
        } catch (IOException e) {
            log.error("Email to {} failed: could not reach Brevo ({})", CodeMailer.mask(to), e.getClass().getSimpleName());
            throw new MailException("Could not reach Brevo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailException("Interrupted while sending email", e);
        }
    }

    private String reason(String body) {
        try {
            JsonNode node = json.readTree(body);
            return node.path("code").asText("") + " " + node.path("message").asText("");
        } catch (IOException e) {
            return "";
        }
    }
}
