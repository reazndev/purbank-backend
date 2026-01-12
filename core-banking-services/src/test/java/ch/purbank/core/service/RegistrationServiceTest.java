package ch.purbank.core.service;

import ch.purbank.core.domain.EmailVerification;
import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.EmailVerificationStatus;
import ch.purbank.core.domain.enums.RegistrationCodeStatus;
import ch.purbank.core.repository.EmailVerificationRepository;
import ch.purbank.core.repository.MobileDeviceRepository;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Korrigierter RegistrationServiceTest
 * Basierend auf dem tatsächlichen RegistrationService (Token-Flow)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Registration Service Tests (Token Flow)")
class RegistrationServiceTest {

    @Mock
    private RegistrationCodesRepository registrationCodesRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MobileDeviceRepository mobileDeviceRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private IpBlockService ipBlockService;

    @InjectMocks
    private RegistrationService registrationService;

    private User testUser;
    private RegistrationCodes openCode;
    private final String IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@purbank.ch");

        openCode = new RegistrationCodes();
        openCode.setUser(testUser);
        openCode.setStatus(RegistrationCodeStatus.OPEN);
        openCode.setRegistrationCode("VALID-CODE-123");
    }

    // ========== START REGISTRATION ==========

    @Test
    @DisplayName("TC-R001: Start Registration - Erfolgsfall")
    void testStartRegistrationSuccess() {
        // Arrange
        when(ipBlockService.isBlocked(IP)).thenReturn(false);
        when(registrationCodesRepository.findByRegistrationCodeAndStatus("VALID-CODE-123", RegistrationCodeStatus.OPEN))
                .thenReturn(Optional.of(openCode));

        // Act
        String token = registrationService.startRegistration("VALID-CODE-123", "CN123", IP);

        // Assert
        assertNotNull(token);
        assertEquals(64, token.length()); // EMAIL_TOKEN_LENGTH
        assertEquals(RegistrationCodeStatus.USED, openCode.getStatus());

        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq(testUser.getEmail()), anyString());
    }

    @Test
    @DisplayName("TC-R002: Start Registration - Ungültiger Code")
    void testStartRegistrationInvalidCode() {
        // Arrange
        when(registrationCodesRepository.findByRegistrationCodeAndStatus(anyString(), any()))
                .thenReturn(Optional.empty());

        // Act
        String token = registrationService.startRegistration("INVALID", "CN123", IP);

        // Assert
        assertNotNull(token); // Service generiert trotzdem einen Token (Security Obfuscation)
        verify(emailVerificationRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    // ========== VERIFY EMAIL CODE ==========

    @Test
    @DisplayName("TC-R003: Email Verifizierung - Erfolgsfall")
    void testVerifyEmailCodeSuccess() {
        // Arrange
        String token = "some-token";
        String code = "12345678";
        EmailVerification verification = new EmailVerification();
        verification.setUser(testUser);
        verification.setEmailCode(code);
        // Annahme: canAttempt() ist true per default (muss in EmailVerification
        // definiert sein)

        when(emailVerificationRepository.findByEmailVerifyTokenForUpdate(token))
                .thenReturn(Optional.of(verification));

        // Act
        Optional<String> completeToken = registrationService.verifyEmailCode(token, code, IP);

        // Assert
        assertTrue(completeToken.isPresent());
        assertEquals(64, completeToken.get().length());
        verify(emailVerificationRepository).save(verification);
    }

    // ========== COMPLETE REGISTRATION ==========

    @Test
    @DisplayName("TC-R004: Registrierung abschließen - Erfolgsfall")
    void testCompleteRegistrationSuccess() {
        // Arrange
        String completeToken = "complete-64-chars-token";
        EmailVerification v = new EmailVerification();
        v.setUser(testUser);
        v.setStatus(EmailVerificationStatus.VERIFIED);
        // Annahme: expired() liefert false

        when(emailVerificationRepository.findByCompleteToken(completeToken)).thenReturn(Optional.of(v));
        when(mobileDeviceRepository.existsByPublicKey(anyString())).thenReturn(false);

        // Act
        boolean result = registrationService.completeRegistration(
                completeToken, "test-public-key", "iPhone 15", IP);

        // Assert
        assertTrue(result);
        verify(mobileDeviceRepository).save(any());
        verify(emailService).sendRegistrationSuccessEmail(testUser.getEmail());
    }

    @Test
    @DisplayName("TC-R005: Registrierung abschließen - Public Key bereits vorhanden")
    void testCompleteRegistrationDuplicateKey() {
        // Arrange
        String completeToken = "token";
        EmailVerification v = new EmailVerification();
        v.setUser(testUser);
        v.setStatus(EmailVerificationStatus.VERIFIED);

        when(emailVerificationRepository.findByCompleteToken(completeToken)).thenReturn(Optional.of(v));
        when(mobileDeviceRepository.existsByPublicKey("existing-key")).thenReturn(true);

        // Act
        boolean result = registrationService.completeRegistration(
                completeToken, "existing-key", "Device", IP);

        // Assert
        assertFalse(result);
        verify(mobileDeviceRepository, never()).save(any());
    }
}