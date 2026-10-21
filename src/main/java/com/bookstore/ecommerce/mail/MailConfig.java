package com.bookstore.ecommerce.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks how codes are emailed:
 * 1. the Brevo API when BREVO_API_KEY is set (sender: BREVO_SENDER, a sender verified in Brevo);
 * 2. otherwise SMTP when MAIL_USERNAME and MAIL_PASSWORD are set (Gmail by default);
 * 3. otherwise development mode, which prints codes to the console.
 * Credentials come only from environment variables or a git-ignored config/application.properties.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public CodeMailer codeMailer(Environment env, ObjectProvider<JavaMailSender> mailSender,
                                 @Value("${app.mail.brevo.api-key:}") String brevoKey,
                                 @Value("${app.mail.brevo.sender:}") String brevoSender,
                                 @Value("${spring.mail.host:}") String host,
                                 @Value("${spring.mail.username:}") String username,
                                 @Value("${spring.mail.password:}") String password,
                                 @Value("${app.mail.from:}") String from) {
        if (!brevoKey.isBlank()) {
            if (brevoSender.isBlank()) {
                throw new IllegalStateException("Set BREVO_SENDER to a sender address verified in your Brevo account.");
            }
            log.info("Verification emails: Brevo API, sent from {}", brevoSender);
            return new BrevoCodeMailer(brevoKey.trim(), brevoSender.trim());
        }
        if (!username.isBlank() && !password.isBlank()) {
            log.info("Verification emails: SMTP via {} as {}", host, CodeMailer.mask(username));
            return new SmtpCodeMailer(mailSender.getObject(), from.isBlank() ? username : from);
        }
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Set BREVO_API_KEY (or MAIL_USERNAME and MAIL_PASSWORD) with the 'prod' profile.");
        }
        log.warn("=====================================================================================");
        log.warn(" EMAIL DEVELOPMENT MODE: verification codes are printed to this console, NOT emailed.");
        log.warn(" Set BREVO_API_KEY (or MAIL_USERNAME and MAIL_PASSWORD for SMTP) to send real emails.");
        log.warn("=====================================================================================");
        return new DevCodeMailer();
    }
}
