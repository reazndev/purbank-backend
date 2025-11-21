package ch.purbank.core.service;

import ch.purbank.core.config.EmailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailConfig emailConfig;

    @Async
    public void sendVerificationEmail(String recipientEmail, String code) {
        log.info("Sending verification email to: {}", recipientEmail);

        try {
            Context context = new Context();
            context.setVariable("verificationCode", code);
            context.setVariable("email", recipientEmail);

            String htmlContent = templateEngine.process("email/verification", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailConfig.getFrom(), emailConfig.getFromName());
            helper.setTo(recipientEmail);
            helper.setSubject(emailConfig.getVerificationSubject());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", recipientEmail);

        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", recipientEmail, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to: {}", recipientEmail, e);
        }
    }

    @Async
    public void sendRegistrationSuccessEmail(String recipientEmail) {
        log.info("Sending registration success email to: {}", recipientEmail);

        try {
            Context context = new Context();
            context.setVariable("email", recipientEmail);

            String htmlContent = templateEngine.process("email/registration-success", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailConfig.getFrom(), emailConfig.getFromName());
            helper.setTo(recipientEmail);
            helper.setSubject(emailConfig.getSuccessSubject());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Registration success email sent successfully to: {}", recipientEmail);

        } catch (MessagingException e) {
            log.error("Failed to send registration success email to: {}", recipientEmail, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to: {}", recipientEmail, e);
        }
    }
}