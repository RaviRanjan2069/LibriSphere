package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.mail.CodeMailer;
import com.bookstore.ecommerce.model.OtpChallenge;
import com.bookstore.ecommerce.model.User;
import com.bookstore.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-up, sign-in and profile flows through the real controllers and MongoDB, against a throwaway
 * "librisphere_unittest" database (dropped afterwards). Emails go to an in-memory outbox.
 */
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/librisphere_unittest",
        "app.uploads.dir=target/test-uploads",
        "app.otp.resend-seconds=0",
        "app.otp.ip-sends-per-hour=1000",
        "app.otp.max-sends-per-hour=1000",
        "app.otp.ip-verifies-per-hour=1000",
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthFlowIntegrationTest {

    /** Captures emails instead of sending them. */
    static class Outbox implements CodeMailer {
        final List<String[]> messages = new ArrayList<>(); // {to, code-or-null, subject}

        public synchronized void sendCode(String email, String code, int validMinutes) {
            messages.add(new String[]{email, code, "code"});
        }

        public synchronized void sendNotice(String email, String subject, String text) {
            messages.add(new String[]{email, null, subject});
        }

        public synchronized void sendPasswordReset(String email, String link, int validMinutes) {
            messages.add(new String[]{email, link, "reset"});
        }

        synchronized String lastCodeFor(String email) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i)[0].equals(email)) {
                    return messages.get(i)[1];
                }
            }
            return null;
        }

        synchronized int countFor(String email) {
            return (int) messages.stream().filter(m -> m[0].equals(email)).count();
        }
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean
        @Primary
        Outbox outboxMailer() {
            return new Outbox();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired Outbox outbox;
    @Autowired UserRepository users;
    @Autowired MongoTemplate mongo;
    final ObjectMapper json = new ObjectMapper();
    static final AtomicInteger seq = new AtomicInteger(10);

    @BeforeEach
    void clearOutbox() {
        outbox.messages.clear();
    }

    @AfterAll
    void dropDatabase() {
        mongo.getDb().drop();
    }

    // ---- helpers ----

    static String freshName() {
        return "user" + seq.incrementAndGet();
    }

    static String emailOf(String name) {
        return name + "@librisphere-tests.com";
    }

    MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder b) {
        return b.header("X-Requested-With", "XMLHttpRequest");
    }

    MvcResult postJson(String url, Object body, MockHttpSession session) throws Exception {
        return mvc.perform(withCsrf(post(url)).session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    MvcResult register(MockHttpSession session, String role, String username, String email, String password, String confirm, byte[] avatar) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/auth/register");
        if (avatar != null) {
            b.file(new MockMultipartFile("avatar", "me.png", "image/png", avatar));
        }
        b.param("role", role).param("username", username).param("email", email)
                .param("password", password).param("confirmPassword", confirm);
        if ("VENDOR".equals(role)) {
            b.param("vendorShopName", "Test Shop");
        }
        return mvc.perform(withCsrf(b).session(session)).andReturn();
    }

    MvcResult register(MockHttpSession session, String username, String email, String password, String confirm, byte[] avatar) throws Exception {
        return register(session, "BUYER", username, email, password, confirm, avatar);
    }

    /** Sends and verifies a sign-up code for the address in this session. */
    void verifyForSignUp(MockHttpSession session, String email) throws Exception {
        assertEquals(200, postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), session).getResponse().getStatus());
        String code = outbox.lastCodeFor(email);
        assertNotNull(code, "a code was emailed");
        assertEquals(200, postJson("/api/auth/otp/verify", Map.of("email", email, "code", code), session).getResponse().getStatus());
    }

    // ---- tests ----

    @Test
    void stateChangingRequestsNeedTheCsrfHeader() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"buyer\",\"password\":\"buyer123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"buyer\",\"password\":\"buyer123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()); // reads don't need it
    }

    @Test
    void demoPasswordsAreHashedAndStillWork() throws Exception {
        User buyer = users.findByUsername("buyer").orElseThrow();
        assertTrue(buyer.getPassword().startsWith("$2"), "BCrypt hash stored");
        assertEquals(200, postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123"), new MockHttpSession()).getResponse().getStatus());
        assertEquals(200, postJson("/api/auth/login", Map.of("username", "BUYER@EXAMPLE.COM", "password", "buyer123"), new MockHttpSession()).getResponse().getStatus(),
                "email sign-in, case-insensitive");
        assertEquals(401, postJson("/api/auth/login", Map.of("username", "buyer", "password", "wrong"), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void responsesAndDatabaseNeverContainTheCode() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = emailOf(freshName());
        MvcResult sent = postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), s);
        String code = outbox.lastCodeFor(email);
        assertFalse(sent.getResponse().getContentAsString().contains(code), "OTP not echoed");
        org.bson.Document stored = mongo.getCollection("otp_challenges")
                .find(new org.bson.Document("_id", OtpChallenge.idFor("REGISTER", email))).first();
        assertNotNull(stored);
        assertFalse(stored.toJson().contains(code), "OTP not stored in plain text");

        MvcResult me = postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123"), s);
        assertFalse(me.getResponse().getContentAsString().contains("password"));
        assertFalse(me.getResponse().getContentAsString().contains("$2"));
    }

    @Test
    void fullSignUpThenPasswordAndEmailCodeSignIn() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String name = freshName();
        String email = emailOf(name);
        verifyForSignUp(s, email);

        MvcResult created = register(s, name, email.toUpperCase(), "s3cret-pass", "s3cret-pass", png());
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());

        User saved = users.findByUsername(name).orElseThrow();
        assertEquals(email, saved.getEmail(), "stored lower-case");
        assertTrue(saved.isEmailVerified());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertNotNull(saved.getAvatarKey());

        MvcResult login = postJson("/api/auth/login", Map.of("username", name, "password", "s3cret-pass"), new MockHttpSession());
        assertEquals(200, login.getResponse().getStatus());
        assertTrue(body(login).at("/user/emailVerified").asBoolean());
        assertTrue(body(login).at("/user/avatarUrl").asText().startsWith("/media/avatars/"));

        MvcResult img = mvc.perform(get(body(login).at("/user/avatarUrl").asText())).andExpect(status().isOk()).andReturn();
        assertEquals("nosniff", img.getResponse().getHeader("X-Content-Type-Options"));

        // email-code sign-in
        MockHttpSession p = new MockHttpSession();
        assertEquals(200, postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "LOGIN"), p).getResponse().getStatus());
        String code = outbox.lastCodeFor(email);
        MvcResult codeLogin = postJson("/api/auth/login/otp", Map.of("email", email, "code", code), p);
        assertEquals(200, codeLogin.getResponse().getStatus(), codeLogin.getResponse().getContentAsString());
        assertEquals(name, body(codeLogin).at("/user/username").asText());

        // single use
        assertEquals(400, postJson("/api/auth/login/otp", Map.of("email", email, "code", code), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void registrationRequiresVerifiedMatchingEmail() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String name = freshName();
        String email = emailOf(name);
        MvcResult r = register(s, name, email, "password1", "password1", null);
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("email", body(r).get("field").asText());

        verifyForSignUp(s, email);
        r = register(s, name, emailOf(freshName()), "password1", "password1", null);
        assertEquals(400, r.getResponse().getStatus(), "changed address after verifying");
        assertEquals("email", body(r).get("field").asText());

        r = register(new MockHttpSession(), name, email, "password1", "password1", null);
        assertEquals(400, r.getResponse().getStatus(), "verification is per browser session");
    }

    @Test
    void wrongCodesAreLimitedAndNeverVerify() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = emailOf(freshName());
        postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), s);
        String code = outbox.lastCodeFor(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 4; i >= 1; i--) {
            MvcResult r = postJson("/api/auth/otp/verify", Map.of("email", email, "code", wrong), s);
            assertEquals(400, r.getResponse().getStatus());
            assertTrue(body(r).get("error").asText().contains(i + (i == 1 ? " attempt" : " attempts") + " left"), body(r).toString());
        }
        assertEquals(429, postJson("/api/auth/otp/verify", Map.of("email", email, "code", wrong), s).getResponse().getStatus());
        assertEquals(429, postJson("/api/auth/otp/verify", Map.of("email", email, "code", code), s).getResponse().getStatus(),
                "even the right code no longer works once locked");
        MvcResult bad = postJson("/api/auth/otp/verify", Map.of("email", email, "code", "12ab"), s);
        assertEquals(400, bad.getResponse().getStatus());
        assertEquals("code", body(bad).get("field").asText());
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = emailOf(freshName());
        postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), s);
        String code = outbox.lastCodeFor(email);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(OtpChallenge.idFor("REGISTER", email))),
                new Update().set("expiresAt", Instant.now().minusSeconds(1)), OtpChallenge.class);
        MvcResult r = postJson("/api/auth/otp/verify", Map.of("email", email, "code", code), s);
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(body(r).get("error").asText().contains("expired"));
    }

    @Test
    void unknownOrUnverifiedEmailGetsNoCodeAndSameResponse() throws Exception {
        String unknown = emailOf(freshName());
        MvcResult r = postJson("/api/auth/otp/send", Map.of("email", unknown, "purpose", "LOGIN"), new MockHttpSession());
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(0, outbox.countFor(unknown), "nothing sent to an unregistered address");

        // demo accounts exist but never proved their address: no codes are sent to them either
        MvcResult demo = postJson("/api/auth/otp/send", Map.of("email", "buyer@example.com", "purpose", "LOGIN"), new MockHttpSession());
        assertEquals(body(r).get("message"), body(demo).get("message"), "identical response");
        assertEquals(0, outbox.countFor("buyer@example.com"));

        for (String guess : new String[]{"000000", "123456"}) {
            assertEquals(400, postJson("/api/auth/login/otp", Map.of("email", unknown, "code", guess), new MockHttpSession()).getResponse().getStatus());
        }
    }

    @Test
    void duplicateEmailAndUsernameAreRefused() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String name = freshName();
        String email = emailOf(name);
        verifyForSignUp(s, email);
        assertEquals(201, register(s, name, email, "password1", "password1", null).getResponse().getStatus());

        // the address is now taken: a new sign-up gets a notice email, not a code, so it can't verify
        MockHttpSession t = new MockHttpSession();
        MvcResult send = postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), t);
        assertEquals(200, send.getResponse().getStatus(), "same response as for a free address");
        assertEquals(2, outbox.countFor(email), "the sign-up code, then one notice");
        assertNull(outbox.lastCodeFor(email), "notice only, no code");

        // duplicate username with a fresh verified address
        MockHttpSession u = new MockHttpSession();
        String email2 = emailOf(freshName());
        verifyForSignUp(u, email2);
        MvcResult r = register(u, name, email2, "password1", "password1", null);
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("username", body(r).get("field").asText());
    }

    @Test
    void usernameAndPasswordValidation() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String name = freshName();
        String email = emailOf(name);
        verifyForSignUp(s, email);
        assertEquals("username", body(register(s, "Ravi Rajput", email, "password1", "password1", null)).get("field").asText(), "spaces refused");
        assertEquals("password", body(register(s, name, email, "short", "short", null)).get("field").asText());
        assertEquals("confirmPassword", body(register(s, name, email, "longenough1", "different1", null)).get("field").asText());
    }

    @Test
    void signUpCannotCreateAdminAndSellersStillNeedApproval() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String name = freshName();
        String email = emailOf(name);
        verifyForSignUp(s, email);
        assertEquals(201, register(s, "VENDOR", name, email, "password1", "password1", null).getResponse().getStatus());
        assertFalse(users.findByUsername(name).orElseThrow().isVendorApproved());

        assertEquals(403, postJson("/api/auth/login", Map.of("username", name, "password", "password1"), new MockHttpSession()).getResponse().getStatus());
        MockHttpSession p = new MockHttpSession();
        postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "LOGIN"), p);
        assertEquals(403, postJson("/api/auth/login/otp", Map.of("email", email, "code", outbox.lastCodeFor(email)), p).getResponse().getStatus());

        MockHttpSession a = new MockHttpSession();
        String admin = freshName();
        verifyForSignUp(a, emailOf(admin));
        assertEquals(201, register(a, "ADMIN", admin, emailOf(admin), "password1", "password1", null).getResponse().getStatus());
        assertEquals("BUYER", users.findByUsername(admin).orElseThrow().getRole());
    }

    @Test
    void profilePictureNeedsSignInAndCanBeReplacedAndRemoved() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", png());
        mvc.perform(withCsrf(multipart("/api/users/me/profile-picture").file(file))).andExpect(status().isUnauthorized());

        MockHttpSession s = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "vendor_a", "password", "vendor123"), s);
        String url1 = body(mvc.perform(withCsrf(multipart("/api/users/me/profile-picture").file(file)).session(s))
                .andExpect(status().isOk()).andReturn()).at("/user/avatarUrl").asText();
        String url2 = body(mvc.perform(withCsrf(multipart("/api/users/me/profile-picture").file(file)).session(s))
                .andExpect(status().isOk()).andReturn()).at("/user/avatarUrl").asText();
        assertNotEquals(url1, url2);
        mvc.perform(get(url1)).andExpect(status().isNotFound());

        MockMultipartFile fake = new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes());
        mvc.perform(withCsrf(multipart("/api/users/me/profile-picture").file(fake)).session(s))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("avatar"));

        mvc.perform(withCsrf(delete("/api/users/me/profile-picture")).session(s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.avatarUrl").isEmpty());
        mvc.perform(get(url2)).andExpect(status().isNotFound());
    }

    @Test
    void existingAccountVerifiesItsEmailThenSignsInWithCode() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String adminEmail = "site-admin@librisphere-tests.com";
        mongo.updateFirst(Query.query(Criteria.where("username").is("admin")), new Update().set("email", adminEmail).set("emailVerified", false), User.class);
        postJson("/api/auth/login", Map.of("username", "admin", "password", "admin123"), s);
        // the request body can't redirect the code to another address
        assertEquals(200, postJson("/api/auth/otp/send", Map.of("email", "attacker@librisphere-tests.com", "purpose", "VERIFY_EMAIL"), s).getResponse().getStatus());
        assertEquals(0, outbox.countFor("attacker@librisphere-tests.com"));
        String code = outbox.lastCodeFor(adminEmail);
        assertNotNull(code);
        MvcResult r = postJson("/api/users/me/email/verify", Map.of("code", code), s);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(body(r).at("/user/emailVerified").asBoolean());

        MockHttpSession p = new MockHttpSession();
        postJson("/api/auth/otp/send", Map.of("email", adminEmail, "purpose", "LOGIN"), p);
        MvcResult login = postJson("/api/auth/login/otp", Map.of("email", adminEmail, "code", outbox.lastCodeFor(adminEmail)), p);
        assertEquals("ADMIN", body(login).at("/user/role").asText(), "role preserved for code sign-in");
        mvc.perform(get("/api/admin/dashboard").session(p)).andExpect(status().isOk());

        assertEquals(401, postJson("/api/auth/otp/send", Map.of("purpose", "VERIFY_EMAIL"), new MockHttpSession()).getResponse().getStatus(),
                "VERIFY_EMAIL needs a signed-in user");
    }

    @Test
    void devMailboxIsClosedWhenRealMailIsConfigured() throws Exception {
        String email = emailOf(freshName());
        postJson("/api/auth/otp/send", Map.of("email", email, "purpose", "REGISTER"), new MockHttpSession());
        mvc.perform(get("/api/dev/mailbox/latest").param("email", email)).andExpect(status().isNotFound());
    }

    MvcResult sendJson(MockHttpServletRequestBuilder request, Object body, MockHttpSession session) throws Exception {
        return mvc.perform(withCsrf(request).session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    /** Signs up a new buyer (password "s3cret-pass") and returns a session signed in as them. */
    MockHttpSession newBuyer(String name) throws Exception {
        MockHttpSession s = new MockHttpSession();
        verifyForSignUp(s, emailOf(name));
        assertEquals(201, register(s, name, emailOf(name), "s3cret-pass", "s3cret-pass", null).getResponse().getStatus());
        MockHttpSession signedIn = new MockHttpSession();
        assertEquals(200, postJson("/api/auth/login", Map.of("username", name, "password", "s3cret-pass"), signedIn).getResponse().getStatus());
        return signedIn;
    }

    @Test
    void profileDetailsAreValidatedAndSaved() throws Exception {
        String name = freshName();
        MockHttpSession s = newBuyer(name);
        MvcResult saved = sendJson(put("/api/users/me"), Map.of("fullName", "  Ravi Rajput ", "gender", "MALE"), s);
        assertEquals(200, saved.getResponse().getStatus(), saved.getResponse().getContentAsString());
        assertEquals("Ravi Rajput", body(saved).at("/user/fullName").asText());
        User stored = users.findByUsername(name).orElseThrow();
        assertEquals("Ravi Rajput", stored.getFullName());
        assertEquals("MALE", stored.getGender());

        assertEquals("gender", body(sendJson(put("/api/users/me"), Map.of("gender", "PIRATE"), s)).at("/field").asText());
        assertEquals("fullName", body(sendJson(put("/api/users/me"), Map.of("fullName", "x".repeat(61)), s)).at("/field").asText());
        assertEquals(401, sendJson(put("/api/users/me"), Map.of("fullName", "Nobody"), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void emailChangeNeedsACodeSentToTheNewUnusedAddress() throws Exception {
        String name = freshName();
        MockHttpSession s = newBuyer(name);
        assertEquals(409, postJson("/api/auth/otp/send", Map.of("email", "buyer@example.com", "purpose", "CHANGE_EMAIL"), s).getResponse().getStatus(),
                "address used by another account");
        assertEquals(400, postJson("/api/auth/otp/send", Map.of("email", emailOf(name), "purpose", "CHANGE_EMAIL"), s).getResponse().getStatus(),
                "same address");
        assertEquals(401, postJson("/api/auth/otp/send", Map.of("email", "x@librisphere-tests.com", "purpose", "CHANGE_EMAIL"), new MockHttpSession())
                .getResponse().getStatus(), "needs sign-in");

        String next = "new-" + name + "@librisphere-tests.com";
        assertEquals(200, postJson("/api/auth/otp/send", Map.of("email", next, "purpose", "CHANGE_EMAIL"), s).getResponse().getStatus());
        String code = outbox.lastCodeFor(next);
        assertNotNull(code, "code goes to the new address");
        assertEquals(400, postJson("/api/users/me/email", Map.of("email", next, "code", "000000".equals(code) ? "111111" : "000000"), s)
                .getResponse().getStatus());
        MvcResult changed = postJson("/api/users/me/email", Map.of("email", next, "code", code), s);
        assertEquals(200, changed.getResponse().getStatus(), changed.getResponse().getContentAsString());
        User stored = users.findByUsername(name).orElseThrow();
        assertEquals(next, stored.getEmail());
        assertTrue(stored.isEmailVerified());
    }

    @Test
    void passwordChangeChecksTheCurrentPassword() throws Exception {
        String name = freshName();
        MockHttpSession s = newBuyer(name);
        Map<String, String> wrong = Map.of("currentPassword", "nope-nope", "newPassword", "brand-new-pass", "confirmPassword", "brand-new-pass");
        assertEquals("currentPassword", body(postJson("/api/users/me/password", wrong, s)).at("/field").asText());
        Map<String, String> mismatch = Map.of("currentPassword", "s3cret-pass", "newPassword", "brand-new-pass", "confirmPassword", "other-pass");
        assertEquals("confirmPassword", body(postJson("/api/users/me/password", mismatch, s)).at("/field").asText());

        Map<String, String> ok = Map.of("currentPassword", "s3cret-pass", "newPassword", "brand-new-pass", "confirmPassword", "brand-new-pass");
        assertEquals(200, postJson("/api/users/me/password", ok, s).getResponse().getStatus());
        assertEquals(401, postJson("/api/auth/login", Map.of("username", name, "password", "s3cret-pass"), new MockHttpSession()).getResponse().getStatus());
        assertEquals(200, postJson("/api/auth/login", Map.of("username", name, "password", "brand-new-pass"), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void buyerDeletesTheirAccountWithTheirPassword() throws Exception {
        String name = freshName();
        MockHttpSession s = newBuyer(name);
        assertEquals("password", body(sendJson(delete("/api/users/me"), Map.of("password", "wrong-pass"), s)).at("/field").asText());
        assertTrue(users.findByUsername(name).isPresent());

        MvcResult deleted = sendJson(delete("/api/users/me"), Map.of("password", "s3cret-pass"), s);
        assertEquals(200, deleted.getResponse().getStatus(), deleted.getResponse().getContentAsString());
        assertTrue(users.findByUsername(name).isEmpty());
        assertTrue(s.isInvalid(), "signed out");
        assertEquals(401, postJson("/api/auth/login", Map.of("username", name, "password", "s3cret-pass"), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void deletionIsRefusedForStaffAndForUndeliveredOrders() throws Exception {
        MockHttpSession admin = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "admin", "password", "admin123"), admin);
        assertEquals(403, sendJson(delete("/api/users/me"), Map.of("password", "admin123"), admin).getResponse().getStatus());
        MockHttpSession seller = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "vendor_a", "password", "vendor123"), seller);
        assertEquals(403, sendJson(delete("/api/users/me"), Map.of("password", "vendor123"), seller).getResponse().getStatus());

        String name = freshName();
        MockHttpSession s = newBuyer(name);
        Long bookId = mongo.findAll(com.bookstore.ecommerce.model.Book.class).stream()
                .filter(b -> b.getStock() > 0).findFirst().orElseThrow().getId();
        assertEquals(200, postJson("/api/cart/items", Map.of("bookId", bookId, "quantity", 1), s).getResponse().getStatus());
        MvcResult order = postJson("/api/orders", Map.of("buyerName", "Test Buyer", "contactNumber", "+91 98765 43210",
                "shippingAddress", "12 Test Street, Pune 411001"), s);
        assertTrue(order.getResponse().getStatus() < 300, order.getResponse().getContentAsString());
        assertEquals(409, sendJson(delete("/api/users/me"), Map.of("password", "s3cret-pass"), s).getResponse().getStatus(),
                "an order is still pending");
        assertTrue(users.findByUsername(name).isPresent());
    }

    /** The token from the most recent reset link emailed to this address. */
    String resetTokenFor(String email) {
        String link = outbox.lastCodeFor(email);
        assertNotNull(link, "a reset link was emailed");
        assertTrue(link.startsWith("http://localhost:9090/reset-password?token="), link);
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    void forgottenPasswordIsResetWithAnEmailedLink() throws Exception {
        String name = freshName();
        newBuyer(name);
        String email = emailOf(name);
        MvcResult asked = postJson("/api/auth/password/forgot", Map.of("email", email.toUpperCase()), new MockHttpSession());
        assertEquals(200, asked.getResponse().getStatus(), asked.getResponse().getContentAsString());
        String token = resetTokenFor(email);
        assertNull(mongo.findById(token, com.bookstore.ecommerce.model.PasswordResetToken.class), "only a hash of the token is stored");

        assertEquals(200, postJson("/api/auth/password/reset/check", Map.of("token", token), new MockHttpSession()).getResponse().getStatus());
        // a bad new password is refused without using the link up
        MvcResult weak = postJson("/api/auth/password/reset", Map.of("token", token, "newPassword", "short", "confirmPassword", "short"), new MockHttpSession());
        assertEquals("newPassword", body(weak).at("/field").asText());
        MvcResult reset = postJson("/api/auth/password/reset",
                Map.of("token", token, "newPassword", "fresh-pass-1", "confirmPassword", "fresh-pass-1"), new MockHttpSession());
        assertEquals(200, reset.getResponse().getStatus(), reset.getResponse().getContentAsString());

        assertEquals(401, postJson("/api/auth/login", Map.of("username", name, "password", "s3cret-pass"), new MockHttpSession()).getResponse().getStatus());
        assertEquals(200, postJson("/api/auth/login", Map.of("username", name, "password", "fresh-pass-1"), new MockHttpSession()).getResponse().getStatus());
        MvcResult reused = postJson("/api/auth/password/reset",
                Map.of("token", token, "newPassword", "other-pass-2", "confirmPassword", "other-pass-2"), new MockHttpSession());
        assertEquals("token", body(reused).at("/field").asText(), "links are single use");
        assertEquals(400, postJson("/api/auth/password/reset/check", Map.of("token", token), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void aNewResetLinkCancelsTheOldOneAndExpiredLinksFail() throws Exception {
        String name = freshName();
        newBuyer(name);
        String email = emailOf(name);
        postJson("/api/auth/password/forgot", Map.of("email", email), new MockHttpSession());
        String first = resetTokenFor(email);
        postJson("/api/auth/password/forgot", Map.of("email", email), new MockHttpSession());
        String second = resetTokenFor(email);
        assertNotEquals(first, second);
        assertEquals(400, postJson("/api/auth/password/reset/check", Map.of("token", first), new MockHttpSession()).getResponse().getStatus());

        mongo.updateMulti(new Query(), new Update().set("expiresAt", Instant.now().minusSeconds(1)), com.bookstore.ecommerce.model.PasswordResetToken.class);
        MvcResult expired = postJson("/api/auth/password/reset",
                Map.of("token", second, "newPassword", "fresh-pass-1", "confirmPassword", "fresh-pass-1"), new MockHttpSession());
        assertEquals("token", body(expired).at("/field").asText());
        assertEquals(400, postJson("/api/auth/password/reset/check", Map.of("token", "not-a-token"), new MockHttpSession()).getResponse().getStatus());
    }

    @Test
    void forgotPasswordEmailsEveryAddressButNeverTestAddresses() throws Exception {
        // no account: a note saying so (not a link), with the same answer as for a real account
        String unknown = emailOf(freshName());
        MvcResult r = postJson("/api/auth/password/forgot", Map.of("email", unknown), new MockHttpSession());
        assertEquals(200, r.getResponse().getStatus(), "same answer, so it doesn't reveal which addresses have accounts");
        assertEquals(1, outbox.countFor(unknown));
        assertNull(outbox.lastCodeFor(unknown), "a notice, not a reset link");

        // an account whose email was never verified still gets a link, and using it confirms the address
        String name = freshName();
        newBuyer(name);
        mongo.updateFirst(Query.query(Criteria.where("username").is(name)), new Update().set("emailVerified", false), User.class);
        postJson("/api/auth/password/forgot", Map.of("email", emailOf(name)), new MockHttpSession());
        String token = resetTokenFor(emailOf(name));
        assertEquals(200, postJson("/api/auth/password/reset",
                Map.of("token", token, "newPassword", "fresh-pass-1", "confirmPassword", "fresh-pass-1"), new MockHttpSession()).getResponse().getStatus());
        assertTrue(users.findByUsername(name).orElseThrow().isEmailVerified());

        // demo accounts use reserved example.com addresses, which are never emailed
        assertEquals("buyer@example.com", users.findByUsername("buyer").orElseThrow().getEmail());
        assertEquals(200, postJson("/api/auth/password/forgot", Map.of("email", "buyer@example.com"), new MockHttpSession()).getResponse().getStatus());
        assertEquals(0, outbox.countFor("buyer@example.com"));
    }

    @Test
    void keepMeSignedInMakesTheSessionLastThirtyDays() throws Exception {
        MockHttpSession remembered = new MockHttpSession();
        MvcResult r = postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123", "remember", true), remembered);
        assertEquals(200, r.getResponse().getStatus());
        String cookie = r.getResponse().getHeaders("Set-Cookie").stream().filter(c -> c.contains("Max-Age")).findFirst().orElse("");
        assertTrue(cookie.contains("Max-Age=2592000"), cookie);
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"), cookie);
        assertEquals(30 * 24 * 3600, remembered.getMaxInactiveInterval());

        MockHttpSession plain = new MockHttpSession();
        MvcResult p = postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123"), plain);
        assertTrue(p.getResponse().getHeaders("Set-Cookie").stream().noneMatch(c -> c.contains("Max-Age")),
                "without it, the session ends when the browser closes");
    }

    @Test
    void adminTabOnlyLetsAdministratorsIn() throws Exception {
        MockHttpSession admin = new MockHttpSession();
        MvcResult ok = postJson("/api/auth/login", Map.of("username", "admin", "password", "admin123", "role", "ADMIN"), admin);
        assertEquals(200, ok.getResponse().getStatus());
        assertEquals("ADMIN", body(ok).at("/user/role").asText());

        MockHttpSession buyer = new MockHttpSession();
        assertEquals(403, postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123", "role", "ADMIN"), buyer)
                .getResponse().getStatus());
        mvc.perform(get("/api/orders").session(buyer)).andExpect(status().isUnauthorized()); // not signed in
    }

    @Test
    void adminsCanCreateAdminsAndNobodyElseCan() throws Exception {
        String name = freshName();
        Map<String, String> newAdmin = Map.of("username", name, "email", emailOf(name), "password", "admin-pass-9", "confirmPassword", "admin-pass-9");

        MockHttpSession buyer = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123"), buyer);
        assertEquals(403, postJson("/api/admin/admins", newAdmin, buyer).getResponse().getStatus());
        assertEquals(401, postJson("/api/admin/admins", newAdmin, new MockHttpSession()).getResponse().getStatus());

        MockHttpSession admin = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "admin", "password", "admin123"), admin);
        Map<String, String> mismatch = Map.of("username", name, "email", emailOf(name), "password", "admin-pass-9", "confirmPassword", "other");
        assertEquals("confirmPassword", body(postJson("/api/admin/admins", mismatch, admin)).at("/field").asText());
        MvcResult created = postJson("/api/admin/admins", newAdmin, admin);
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());

        User stored = users.findByUsername(name).orElseThrow();
        assertEquals("ADMIN", stored.getRole());
        assertTrue(stored.getPassword().startsWith("$2"), "password stored as a BCrypt hash");
        assertEquals("username", body(postJson("/api/admin/admins", newAdmin, admin)).at("/field").asText(), "user id taken");

        MvcResult login = postJson("/api/auth/login", Map.of("username", name, "password", "admin-pass-9", "role", "ADMIN"), new MockHttpSession());
        assertEquals(200, login.getResponse().getStatus());
        assertTrue(body(mvc.perform(get("/api/admin/dashboard").session(admin)).andReturn()).at("/admins").toString().contains(name));
    }

    @Test
    void supportRequestsAreRaisedAnsweredReopenedAndPrivate() throws Exception {
        String name = freshName();
        MockHttpSession user = newBuyer(name);
        assertEquals(401, mvc.perform(get("/api/support")).andReturn().getResponse().getStatus());

        // validation
        assertEquals("topic", body(postJson("/api/support", Map.of("subject", "Broken", "description", "It does not work at all"), user)).at("/field").asText());
        assertEquals("description", body(postJson("/api/support",
                Map.of("topic", "ORDER", "subject", "Late order", "description", "late"), user)).at("/field").asText());

        outbox.messages.clear();
        MvcResult created = postJson("/api/support", Map.of("topic", "ORDER", "priority", "HIGH", "area", "ORDERS",
                "subject", "Order arrived damaged", "description", "The cover of the book in order #LBS-1 was torn."), user);
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        long id = body(created).at("/request/id").asLong();
        assertTrue(body(created).at("/request/reference").asText().startsWith("SUP-"));
        assertEquals(1, outbox.countFor("mentora.smartlearning@gmail.com"), "the support inbox is told about it");

        MvcResult mine = mvc.perform(get("/api/support").session(user)).andReturn();
        assertEquals(1, body(mine).at("/openCount").asInt());
        assertEquals("Order arrived damaged", body(mine).at("/requests/0/subject").asText());

        // another user can't see, reopen or delete it
        MockHttpSession other = newBuyer(freshName());
        assertEquals(0, body(mvc.perform(get("/api/support").session(other)).andReturn()).at("/requests").size());
        assertEquals(404, mvc.perform(withCsrf(delete("/api/support/" + id)).session(other)).andReturn().getResponse().getStatus());

        // only administrators answer, and the user is emailed the reply
        assertEquals(403, postJson("/api/admin/support/" + id + "/resolve", Map.of("reply", "Fixed"), user).getResponse().getStatus());
        MockHttpSession admin = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "admin", "password", "admin123"), admin);
        assertTrue(body(mvc.perform(get("/api/admin/dashboard").session(admin)).andReturn()).at("/supportRequests").toString().contains("Order arrived damaged"));
        MvcResult resolved = postJson("/api/admin/support/" + id + "/resolve", Map.of("reply", "Sorry! A replacement is on its way."), admin);
        assertEquals(200, resolved.getResponse().getStatus(), resolved.getResponse().getContentAsString());
        assertEquals(1, outbox.countFor(emailOf(name)), "the customer is emailed the reply");
        JsonNode after = body(mvc.perform(get("/api/support").session(user)).andReturn());
        assertEquals("RESOLVED", after.at("/requests/0/status").asText());
        assertEquals("Sorry! A replacement is on its way.", after.at("/requests/0/resolution").asText());

        assertEquals(200, postJson("/api/support/" + id + "/reopen", Map.of(), user).getResponse().getStatus());
        assertEquals(1, body(mvc.perform(get("/api/support").session(user)).andReturn()).at("/openCount").asInt());
        assertEquals(200, mvc.perform(withCsrf(delete("/api/support/" + id)).session(user)).andReturn().getResponse().getStatus());
        assertEquals(0, body(mvc.perform(get("/api/support").session(user)).andReturn()).at("/requests").size());
    }

    @Autowired com.bookstore.ecommerce.config.CoverMigration coverMigration;

    @Test
    void openLibraryCoversSwitchToStoredCopiesWhenAvailable() throws Exception {
        com.bookstore.ecommerce.model.Book stored = new com.bookstore.ecommerce.model.Book();
        stored.setTitle("Dune");
        stored.setCoverUrl("https://covers.openlibrary.org/b/isbn/9780441013593-L.jpg");
        com.bookstore.ecommerce.model.Book missing = new com.bookstore.ecommerce.model.Book();
        missing.setTitle("No stored cover");
        missing.setCoverUrl("https://covers.openlibrary.org/b/isbn/0000000000000-L.jpg");
        stored = mongo.save(stored);
        missing = mongo.save(missing);

        coverMigration.run();

        assertEquals("/covers/9780441013593.jpg", mongo.findById(stored.getId(), com.bookstore.ecommerce.model.Book.class).getCoverUrl());
        assertEquals("https://covers.openlibrary.org/b/isbn/0000000000000-L.jpg",
                mongo.findById(missing.getId(), com.bookstore.ecommerce.model.Book.class).getCoverUrl(), "left alone without a stored copy");
        mvc.perform(get("/covers/9780441013593.jpg")).andExpect(status().isOk());
        mongo.remove(stored);
        mongo.remove(missing);
    }

    @Test
    void roleRestrictionsStillApply() throws Exception {
        MockHttpSession buyer = new MockHttpSession();
        postJson("/api/auth/login", Map.of("username", "buyer", "password", "buyer123"), buyer);
        mvc.perform(get("/api/admin/dashboard").session(buyer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vendor/dashboard").session(buyer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }
}
