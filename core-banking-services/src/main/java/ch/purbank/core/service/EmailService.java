package ch.purbank.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void sendVerificationEmail(String email, String code) {
        // TODO: Implement actual email sending with your email provider
        log.info("=== EMAIL VERIFICATION ===");
        log.info("To: {}", email);
        log.info("Subject: Your Purbank Verification Code");
        log.info("Body: Your verification code is: {}", code);
        log.info("Code valid for 15 minutes");
        log.info("===========================");

        // In production, integrate with:
        // - SendGrid
        // - Amazon SES
        // - SMTP server
    }

    public void sendRegistrationSuccessEmail(String email) {
        // TODO: Implement actual email sending
        log.info("=== REGISTRATION SUCCESS ===");
        log.info("To: {}", email);
        log.info("Subject: Mobile App Registration Successful");
        log.info("Body: Your mobile app registration was successful!");
        log.info("===========================");
    }
}