package ch.purbank.core.service;

import ch.purbank.core.domain.EmailVerification;
import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.repository.EmailVerificationRepository;
import ch.purbank.core.repository.MobileDeviceRepository;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.security.SecureTokenGenerator;
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
    private final IpBlockService ipBlockService; // for blocking IPs on abuse

    private static final int EMAIL_CODE_LENGTH = 8;
    private static final int EMAIL_TOKEN_LENGTH = 64; // email verify token
    private static final int COMPLETE_TOKEN_LENGTH = 64; // complete token
    private static final int TOKEN_VALIDITY_HOURS = 1; // complete token validity

    /**
     * startRegistration MUST always return a token-like string; if code invalid ->
     * returns dummy token and does NOT persist.
     * If code valid -> mark registration code used (always), create
     * EmailVerification and send email with numeric code.
     */
    @Transactional
    public String startRegistration(String registrationCode, String contractNumber, String ipAddress) {
        log.info("startRegistration code={} contract={} ip={}", registrationCode, contractNumber, ipAddress);

        // Optional: check IP blocked
        if (ipAddress != null && ipBlockService.isBlocked(ipAddress)) {
            log.warn("ip blocked {}", ipAddress);
            return SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH); // dummy
        }

        Optional<RegistrationCodes> codeOpt = registrationCodesRepository
                .findByRegistrationCodeAndStatus(registrationCode, "OPEN");

        // always return a token-like string
        String resultToken = SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH);

        if (codeOpt.isEmpty()) {
            // invalid code: return dummy token, do not persist anything
            log.warn("Invalid registration code attempted: {}", registrationCode);
            return resultToken;
        }

        RegistrationCodes code = codeOpt.get();
        User user = code.getUser();

        // Mark registration code as used (requirement: registration code is consumed at
        // first use)
        code.markUsed();
        registrationCodesRepository.save(code);

        // Create EmailVerification entry
        String emailVerifyToken = SecureTokenGenerator.generateToken(EMAIL_TOKEN_LENGTH);
        String emailCode = SecureTokenGenerator.generateNumeric(EMAIL_CODE_LENGTH);

        EmailVerification verification = new EmailVerification();
        verification.setEmailVerifyToken(emailVerifyToken);
        verification.setEmailCode(emailCode);
        verification.setUser(user);
        // createdAt & expiresAt set by entity @PrePersist
        emailVerificationRepository.save(verification);

        // send email
        emailService.sendVerificationEmail(user.getEmail(), emailCode);
        log.info("Email verification created for user={} token={}", user.getId(), emailVerifyToken);

        return emailVerifyToken;
    }

    /**
     * Verify the email code. Uses pessimistic locking on the email verification row
     * to prevent race conditions.
     * If verified -> sets completeToken and returns it. If wrong -> increments
     * attempts and invalidates if limit reached.
     */
    @Transactional
    public Optional<String> verifyEmailCode(String emailVerifyToken, String emailCode, String ipAddress) {
        log.info("verifyEmailCode token={} ip={}", emailVerifyToken, ipAddress);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository
                .findByEmailVerifyTokenForUpdate(emailVerifyToken);

        if (verificationOpt.isEmpty()) {
            log.warn("Email verification token not found: {}", emailVerifyToken);
            return Optional.empty();
        }

        EmailVerification verification = verificationOpt.get();

        if (!verification.canAttempt()) {
            log.warn("Email verification not valid for token: {}", emailVerifyToken);
            return Optional.empty();
        }

        if (verification.getEmailCode().equals(emailCode)) {
            // success
            String completeToken = SecureTokenGenerator.generateToken(COMPLETE_TOKEN_LENGTH);
            verification.markVerified(completeToken);
            // set complete-token expiry to 1 hour from now (done in markVerified)
            emailVerificationRepository.save(verification);
            log.info("Email verified for user={}, completeToken={}", verification.getUser().getId(), completeToken);
            return Optional.of(completeToken);
        } else {
            // wrong code
            verification.incrementAttempts();
            if (!verification.canAttempt()) {
                // Invalidate registration entirely per requirements
                verification.invalidate();
                // Optionally block IP if provided
                if (ipAddress != null) {
                    log.warn("Too many wrong attempts from ip {}, blocking", ipAddress);
                    ipBlockService.blockIp(ipAddress, 60); // block 60 minutes (configurable)
                }
            }
            emailVerificationRepository.save(verification);
            log.warn("Invalid email code attempt for token={}, attempts={}", emailVerifyToken,
                    verification.getAttempts());
            return Optional.empty();
        }
    }

    /**
     * Resend email code: allowed only once per emailVerifyToken and only if not
     * expired and status pending.
     * Does not count towards the 3 wrong attempts limit per requirement.
     */
    @Transactional
    public boolean resendEmailCode(String emailVerifyToken) {
        log.info("resendEmailCode token={}", emailVerifyToken);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository
                .findByEmailVerifyTokenForUpdate(emailVerifyToken);

        if (verificationOpt.isEmpty())
            return false;

        EmailVerification v = verificationOpt.get();

        if (!v.canResend()) {
            log.warn("Cannot resend for token {}, resendCount={}", emailVerifyToken, v.getResendCount());
            return false;
        }

        // generate new code, reset expiry, increment resend count (allowed only once)
        String newCode = SecureTokenGenerator.generateNumeric(EMAIL_CODE_LENGTH);
        v.setEmailCode(newCode);
        v.incrementResend();
        v.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        emailVerificationRepository.save(v);

        emailService.sendVerificationEmail(v.getUser().getEmail(), newCode);
        log.info("Resent verification code to user {}", v.getUser().getId());
        return true;
    }

    /**
     * Complete registration: client sends publicKey + deviceName + completeToken.
     * Creates MobileDevice, ensures publicKey unique, invalidates the verification.
     */
    @Transactional
    public boolean completeRegistration(String completeToken, String publicKey, String deviceName, String ip) {
        log.info("completeRegistration token={} ip={}", completeToken, ip);

        Optional<EmailVerification> verificationOpt = emailVerificationRepository.findByCompleteToken(completeToken);
        if (verificationOpt.isEmpty()) {
            log.warn("Complete token not found {}", completeToken);
            return false;
        }

        EmailVerification v = verificationOpt.get();

        // must be verified and not expired (complete token lifetime was set on
        // markVerified)
        if (v.getStatus() == null || !v.getStatus().name().equals("VERIFIED")) {
            log.warn("Complete token not in verified state {}", completeToken);
            return false;
        }

        if (v.expired()) {
            log.warn("Complete token expired {}", completeToken);
            return false;
        }

        // ensure public key uniqueness
        if (mobileDeviceRepository.existsByPublicKey(publicKey)) {
            log.warn("Public key already exists");
            return false;
        }

        // create device
        ch.purbank.core.domain.MobileDevice device = new ch.purbank.core.domain.MobileDevice();
        device.setPublicKey(publicKey);
        device.setDeviceName(deviceName != null ? deviceName : "Mobile Device");
        device.setUser(v.getUser());
        device.setStatus(ch.purbank.core.domain.enums.MobileDeviceStatus.ACTIVE);
        mobileDeviceRepository.save(device);

        // Invalidate verification (shorten expiry and mark invalid)
        v.invalidate();
        emailVerificationRepository.save(v);

        // send registration success email
        emailService.sendRegistrationSuccessEmail(v.getUser().getEmail());
        log.info("Registration complete for user {}", v.getUser().getId());
        return true;
    }
}
