package com.bookstore.ecommerce.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * Delivers verification emails. Chosen by {@link MailConfig}: the Brevo API or SMTP when credentials are
 * configured, otherwise development mode.
 */
public interface CodeMailer {

    /** Sends a one-time code to {@code email}. */
    void sendCode(String email, String code, int validMinutes);

    /** Sends a plain informational email (used instead of a code when an address can't be used). */
    void sendNotice(String email, String subject, String text);

    /** Sends a "reset your password" email with a single-use link. */
    void sendPasswordReset(String email, String link, int validMinutes);

    /** True for the development mailer, which logs messages instead of sending them. */
    default boolean isDevMode() {
        return false;
    }

    /** Thrown when the mail server can't accept a message. The message never contains the code. */
    class MailException extends RuntimeException {
        public MailException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static String codeSubject(String code) {
        return code + " is your LibriSphere verification code";
    }

    static String codeText(String code, int validMinutes) {
        return "Your LibriSphere verification code is " + code + ".\n\n"
                + "It expires in " + validMinutes + " minutes. Never share this code with anyone; "
                + "LibriSphere staff will never ask for it.\n\n"
                + "If you didn't request this code, you can ignore this email.";
    }

    static String codeHtml(String code, int validMinutes) {
        return """
                <div style="font-family:Inter,Segoe UI,Arial,sans-serif;max-width:460px;margin:0 auto;padding:24px;color:#1B1A17">
                  <p style="font-size:18px;font-weight:700;margin:0 0 16px">LibriSphere</p>
                  <p style="margin:0 0 12px">Your verification code is:</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:8px;margin:0 0 16px;font-family:Consolas,monospace">%s</p>
                  <p style="margin:0 0 12px;color:#5A554D">It expires in %d minutes. Never share this code with anyone; LibriSphere staff will never ask for it.</p>
                  <p style="margin:0;color:#8C867C;font-size:13px">If you didn't request this code, you can ignore this email.</p>
                </div>""".formatted(code, validMinutes);
    }

    String RESET_SUBJECT = "Reset your LibriSphere password";

    static String resetText(String link, int validMinutes) {
        return "We received a request to reset the password for your LibriSphere account.\n\n"
                + "Choose a new password here:\n" + link + "\n\n"
                + "This link expires in " + validMinutes + " minutes and can only be used once.\n\n"
                + "If you didn't ask to reset your password, you can ignore this email; your password won't change.";
    }

    static String resetHtml(String link, int validMinutes) {
        String href = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Inter,Segoe UI,Arial,sans-serif;max-width:460px;margin:0 auto;padding:24px;color:#1B1A17">
                  <p style="font-size:18px;font-weight:700;margin:0 0 16px">LibriSphere</p>
                  <p style="margin:0 0 20px">We received a request to reset the password for your LibriSphere account.</p>
                  <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;padding:12px 22px;border-radius:10px;background:#1F5C4D;color:#FFFFFF;font-weight:600;text-decoration:none">Choose a new password</a></p>
                  <p style="margin:0 0 12px;color:#5A554D">This link expires in %d minutes and can only be used once.</p>
                  <p style="margin:0 0 12px;color:#8C867C;font-size:13px">If the button doesn't work, paste this address into your browser:<br><span style="word-break:break-all">%s</span></p>
                  <p style="margin:0;color:#8C867C;font-size:13px">If you didn't ask to reset your password, you can ignore this email; your password won't change.</p>
                </div>""".formatted(href, validMinutes, href);
    }

    static String noticeHtml(String text) {
        return "<div style=\"font-family:Inter,Segoe UI,Arial,sans-serif;max-width:460px;margin:0 auto;padding:24px;color:#1B1A17\">"
                + "<p style=\"font-size:18px;font-weight:700;margin:0 0 16px\">LibriSphere</p><p style=\"white-space:pre-line\">"
                + HtmlUtils.htmlEscape(text) + "</p></div>";
    }

    /** "ra•••@gmail.com": enough to tell addresses apart in logs without recording them. */
    static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) {
            return "•••";
        }
        return email.substring(0, Math.min(2, at)) + "•••" + email.substring(at);
    }
}
