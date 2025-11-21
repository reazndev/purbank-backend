package ch.purbank.core.service;

import ch.purbank.core.domain.EmailVerification;
import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.EmailVerificationStatus;
import ch.purbank.core.domain.enums.MobileDeviceStatus;
import ch.purbank.core.repository.EmailVerificationRepository;
import ch.purbank.core.repository.MobileDeviceRepository;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.security.SecureTokenGenerator;
import org.springframework.dao.DataIntegrityViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final RegistrationCodesRepository registrationCodesRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final MobileDeviceRepository mobileDeviceRepository;
    private final EmailService emailService;
    private final IpBlockService ipBlockService;

    private static final int EMAIL_CODE_LENGTH = 8;
    private static final int EMAIL_TOKEN_LENGTH = 64;
    private static final int COMPLETE_TOKEN_LENGTH = 64;
    private static final int TOKEN_VALIDITY_HOURS = 1;

    private String logToken(String token) {
        return token != null && token.length() > 8 ? token.substring(0, 4) + "..." : "null";
    }

    @Transactional
    public String startRegistration(String registrationCode, String contractNumber, String ipAddress) {
        log.info("startRegistration initiated from ip={}", ipAddress);

        if (ipAddress != null && ipBlockService.isBlocked(ipAddress)) {
            log.warn("ip blocked {}", ipAddress);
            return SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH);
        }

        Optional<RegistrationCodes> codeOpt = registrationCodesRepository
                .findByRegistrationCodeAndStatus(registrationCode, "OPEN");

        String resultToken = SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH);

        if (codeOpt.isEmpty()) {
            log.warn("Invalid registration code attempted.");
            return resultToken;
        }

        RegistrationCodes code = codeOpt.get();
        User user = code.getUser();

        code.markUsed();
        registrationCodesRepository.save(code);

        String emailVerifyToken = SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH);
        String emailCode = SecureTokenGenerator.generateNumeric(EMAIL_CODE_LENGTH);

        EmailVerification verification = new EmailVerification();
        verification.setEmailVerifyToken(emailVerifyToken);
        verification.setEmailCode(emailCode);
        verification.setUser(user);
        emailVerificationRepository.save(verification);

        emailService.sendVerificationEmail(user.getEmail(), emailCode);
        log.info("Email verification created for user={} token={}", user.getId(), logToken(emailVerifyToken));
        return emailVerifyToken;
    }

    @Transactional
    public Optional<String> verifyEmailCode(String emailVerifyToken, String emailCode, String ipAddress) {
        log.info("verifyEmailCode token={} ip={}", logToken(emailVerifyToken), ipAddress);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository
                .findByEmailVerifyTokenForUpdate(emailVerifyToken);

        if (verificationOpt.isEmpty()) {
            log.warn("Email verification token not found.");
            return Optional.empty();
        }

        EmailVerification verification = verificationOpt.get();

        if (!verification.canAttempt()) {
            log.warn("Email verification not valid for token: {}", logToken(emailVerifyToken));
            return Optional.empty();
        }

        if (verification.getEmailCode().equals(emailCode)) {
            String completeToken = SecureTokenGenerator.generateToken(COMPLETE_TOKEN_LENGTH);
            verification.markVerified(completeToken);
            emailVerificationRepository.save(verification);
            log.info("Email verified for user={}, completeToken={}", verification.getUser().getId(),
                    logToken(completeToken));
            return Optional.of(completeToken);
        } else {
            verification.incrementAttempts();
            if (!verification.canAttempt()) {
                verification.invalidate();
                if (ipAddress != null) {
                    log.warn("Too many wrong attempts from ip {}, blocking", ipAddress);
                    ipBlockService.blockIp(ipAddress, 60);
                }
            }
            emailVerificationRepository.save(verification);
            log.warn("Invalid email code attempt for token={}, attempts={}", logToken(emailVerifyToken),
                    verification.getAttempts());
            return Optional.empty();
        }
    }

    @Transactional
    public boolean resendEmailCode(String emailVerifyToken) {
        log.info("resendEmailCode token={}", logToken(emailVerifyToken));

        Optional<EmailVerification> verificationOpt = emailVerificationRepository
                .findByEmailVerifyTokenForUpdate(emailVerifyToken);

        if (verificationOpt.isEmpty())
            return false;

        EmailVerification v = verificationOpt.get();

        if (!v.canResend()) {
            log.warn("Cannot resend for token {}, resendCount={}", logToken(emailVerifyToken), v.getResendCount());
            return false;
        }

        String newCode = SecureTokenGenerator.generateNumeric(EMAIL_CODE_LENGTH);
        v.setEmailCode(newCode);
        v.incrementResend();
        v.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        emailVerificationRepository.save(v);

        emailService.sendVerificationEmail(v.getUser().getEmail(), newCode);
        log.info("Resent verification code to user {}", v.getUser().getId());
        return true;
    }

    @Transactional
    public boolean completeRegistration(String completeToken, String publicKey, String deviceName, String ip) {
        log.info("completeRegistration token={} ip={}", logToken(completeToken), ip);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository.findByCompleteToken(completeToken);
        if (verificationOpt.isEmpty()) {
            log.warn("Complete token not found.");
            return false;
        }

        EmailVerification v = verificationOpt.get();

        if (v.getStatus() == null || v.getStatus() != EmailVerificationStatus.VERIFIED) {
            log.warn("Complete token not in verified state: {}", v.getStatus());
            return false;
        }

        if (v.expired()) {
            log.warn("Complete token expired.");
            return false;
        }

        if (mobileDeviceRepository.existsByPublicKey(publicKey)) {
            log.warn("Public key already exists.");
            return false;
        }

        ch.purbank.core.domain.MobileDevice device = new ch.purbank.core.domain.MobileDevice();
        device.setPublicKey(publicKey);
        device.setDeviceName(deviceName != null ? deviceName : "Mobile Device");
        device.setUser(v.getUser());
        device.setStatus(MobileDeviceStatus.ACTIVE);

        try {
            mobileDeviceRepository.save(device);
        } catch (DataIntegrityViolationException e) {
            log.warn("Public key uniqueness constraint violated during save.", e);
            return false;
        }

        v.invalidate();
        emailVerificationRepository.save(v);

        emailService.sendRegistrationSuccessEmail(v.getUser().getEmail());
        log.info("Registration complete for user {}", v.getUser().getId());
        return true;
    }
}