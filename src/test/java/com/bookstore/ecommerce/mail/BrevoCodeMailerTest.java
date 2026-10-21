package com.bookstore.ecommerce.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the Brevo mailer against a local stand-in for the Brevo API. */
class BrevoCodeMailerTest {

    private HttpServer server;
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"messageId\":\"<1@smtp-relay.mailin.fr>\"}";
    private BrevoCodeMailer mailer;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
        mailer = new BrevoCodeMailer(HttpClient.newHttpClient(), endpoint, "test-key", "ravirajput2069@gmail.com");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheCodeWithTheApiKeyAndVerifiedSender() throws Exception {
        mailer.sendCode("reader@example.com", "123456", 5);

        assertEquals("test-key", apiKey.get());
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertEquals("ravirajput2069@gmail.com", sent.at("/sender/email").asText());
        assertEquals("LibriSphere", sent.at("/sender/name").asText());
        assertEquals("reader@example.com", sent.at("/to/0/email").asText());
        assertEquals("123456 is your LibriSphere verification code", sent.at("/subject").asText());
        assertTrue(sent.at("/htmlContent").asText().contains("123456"));
        assertTrue(sent.at("/textContent").asText().contains("expires in 5 minutes"));
    }

    @Test
    void sendsThePasswordResetLink() throws Exception {
        mailer.sendPasswordReset("reader@example.com", "http://localhost:9090/reset-password?token=abc&x=1", 30);
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertEquals("Reset your LibriSphere password", sent.at("/subject").asText());
        assertTrue(sent.at("/htmlContent").asText().contains("href=\"http://localhost:9090/reset-password?token=abc&amp;x=1\""));
        assertTrue(sent.at("/textContent").asText().contains("expires in 30 minutes"));
    }

    @Test
    void escapesNoticeTextInHtml() throws Exception {
        mailer.sendNotice("reader@example.com", "Hello", "<b>not bold</b>");
        String html = new ObjectMapper().readTree(body.get()).at("/htmlContent").asText();
        assertTrue(html.contains("&lt;b&gt;not bold&lt;/b&gt;"));
    }

    @Test
    void rejectionsBecomeMailExceptions() {
        status = 400;
        reply = "{\"code\":\"invalid_parameter\",\"message\":\"sender not valid\"}";
        assertThrows(CodeMailer.MailException.class, () -> mailer.sendCode("reader@example.com", "123456", 5));
    }

    @Test
    void unreachableApiBecomesMailException() {
        server.stop(0);
        assertThrows(CodeMailer.MailException.class, () -> mailer.sendCode("reader@example.com", "123456", 5));
    }
}
