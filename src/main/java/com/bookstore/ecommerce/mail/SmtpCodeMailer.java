package com.bookstore.ecommerce.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/** Sends verification emails over SMTP (e.g. Gmail with an App Password), as plain text plus a simple HTML version. */
public class SmtpCodeMailer implements CodeMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpCodeMailer.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpCodeMailer(JavaMailSender sender, String from) {
        this.sender = sender;
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
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "LibriSphere");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException | org.springframework.mail.MailException e) {
            log.error("Email to {} failed: {}", CodeMailer.mask(to), e.getClass().getSimpleName());
            throw new MailException("Could not send email", e);
        }
    }
}
