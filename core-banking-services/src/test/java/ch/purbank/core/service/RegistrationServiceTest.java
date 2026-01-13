package ch.purbank.core.service;

import ch.purbank.core.domain.EmailVerification;
import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.RegistrationCodeStatus;
import ch.purbank.core.repository.EmailVerificationRepository;
import ch.purbank.core.repository.MobileDeviceRepository;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
import ch.purbank.core.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testkonzept für RegistrationService (Leitfrage A6)
 * 1. System: Self-Service Registrierung & Token-Flow
 * 2. Umgebung: Unit Test mit simulierter Security-Umgebung (JwtService Mock)
 * 3. Nicht getestet: SMTP-Server-Erreichbarkeit, tatsächliche IP-Geolocation.
 * 4. Testmittel: Mocks für IP-Blocking, E-Mail-Service und Repositories.
 * 5. Methode: Error Guessing & Positive/Negative Path Testing.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("Registration Service - Security Token Flow")
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
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

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

        lenient().when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        lenient().when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token");
    }

    @Test
    @DisplayName("TC-R001: Start Registration - Erfolgsfall")
    void testStartRegistrationSuccess() {
        when(ipBlockService.isBlocked(IP)).thenReturn(false);
        when(registrationCodesRepository.findByRegistrationCodeAndStatus("VALID-CODE-123", RegistrationCodeStatus.OPEN))
                .thenReturn(Optional.of(openCode));

        String token = registrationService.startRegistration("VALID-CODE-123", "CN123", IP);

        assertNotNull(token);
        assertEquals(64, token.length());
        assertEquals(RegistrationCodeStatus.USED, openCode.getStatus());

        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq(testUser.getEmail()), anyString());
    }

    @Test
    @DisplayName("TC-R002: Start Registration - Ungültiger Code")
    void testStartRegistrationInvalidCode() {
        when(registrationCodesRepository.findByRegistrationCodeAndStatus(anyString(), any()))
                .thenReturn(Optional.empty());

        String token = registrationService.startRegistration("INVALID", "CN123", IP);

        assertNotNull(token);
        verify(emailVerificationRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    @DisplayName("TC-R003: Email Verifizierung mit abgelaufenem Token")
    void testVerifyEmailExpiredToken() {
        when(emailVerificationRepository.findByEmailVerifyTokenForUpdate(anyString()))
                .thenReturn(Optional.empty());

        Optional<String> result = registrationService.verifyEmailCode("token", "123456", "IP");

        assertTrue(result.isEmpty());
    }

}