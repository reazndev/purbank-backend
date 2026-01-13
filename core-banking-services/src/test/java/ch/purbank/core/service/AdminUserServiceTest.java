package ch.purbank.core.service;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.Role;
import ch.purbank.core.repository.RegistrationCodesRepository;
import ch.purbank.core.repository.UserRepository;
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
import static org.mockito.Mockito.*;

/**
 * Testkonzept für AdminUserService (Leitfrage A6)
 * 1. System: Administrative Benutzerverwaltung (Core-Modul)
 * 2. Umgebung: JUnit 5, Mockito Framework (isoliert von DB/Netzwerk)
 * 3. Nicht getestet: E-Mail-Versand-Inhalt (Integration Test),
 * Passwort-Komplexitätsregeln (Regex-Level)
 * 4. Testmittel: Mockito Mocks für Repositories & PasswordEncoder
 * 5. Methode: Black-Box-Testing der Service-Methoden
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Admin User Service - Vollständige Suite")
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationCodesRepository registrationCodesRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private AdminUserService adminUserService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("test@purbank.ch")
                .firstName("Max")
                .lastName("Muster")
                .role(Role.USER)
                .build();
    }

    // ========== USER CREATION (A14: Happy Path & Edge Cases) ==========

    @Test
    @DisplayName("TC-A001: User erfolgreich erstellen & Passwort hashen")
    void testCreateUserSuccess() {
        testUser.setPassword("plain");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = adminUserService.createUser(testUser);
        assertAll(
                () -> assertEquals("hashed", result.getPassword()),
                () -> verify(userRepository).save(any()));
    }

    @Test
    @DisplayName("TC-A002: User-Erstellung schlägt fehl bei existierender Email")
    void testCreateUserDuplicateEmail() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> adminUserService.createUser(testUser));
    }

    @Test
    @DisplayName("TC-A003: Vertragsnummer wird automatisch generiert wenn leer")
    void testCreateUserGeneratesContractNumber() {
        testUser.setContractNumber(null);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User created = adminUserService.createUser(testUser);
        assertNotNull(created.getContractNumber());
    }

    // ========== REGISTRATION CODES (A14: Logic Coverage) ==========

    @Test
    @DisplayName("TC-A004: Registration Code löschen (Erfolg)")
    void testDeleteRegistrationCodeSuccess() {
        UUID codeId = UUID.randomUUID();
        RegistrationCodes code = new RegistrationCodes();
        code.setUser(testUser);

        when(registrationCodesRepository.findById(codeId)).thenReturn(Optional.of(code));

        boolean result = adminUserService.deleteRegistrationCode(userId, codeId);
        assertTrue(result);
        verify(registrationCodesRepository).delete(code);
    }

    @Test
    @DisplayName("TC-A005: Registration Code löschen schlägt fehl (Falscher User)")
    void testDeleteRegistrationCodeWrongUser() {
        UUID codeId = UUID.randomUUID();
        User wrongUser = new User();
        wrongUser.setId(UUID.randomUUID());

        RegistrationCodes code = new RegistrationCodes();
        code.setUser(wrongUser);

        when(registrationCodesRepository.findById(codeId)).thenReturn(Optional.of(code));

        boolean result = adminUserService.deleteRegistrationCode(userId, codeId);
        assertFalse(result);
        verify(registrationCodesRepository, never()).delete(any());
    }

    @Test
    @DisplayName("TC-A006: Registration Code erstellen für nicht existierenden User")
    void testCreateCodeUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> adminUserService.createRegistrationCode(userId, "T", "D"));
    }

    // ========== GETTER & NULL SAFETY ==========

    @Test
    @DisplayName("TC-A007: Einzelnen User abrufen")
    void testGetUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        User found = adminUserService.getUser(userId);
        assertEquals(userId, found.getId());
    }

    @Test
    @DisplayName("TC-A008: Erstellung mit Null-User abfangen")
    void testCreateUserNullCheck() {
        assertThrows(NullPointerException.class, () -> adminUserService.createUser(null));
    }
}