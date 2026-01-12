package ch.purbank.core.service;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.RegistrationCodeStatus;
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
 * Test Suite für AdminUserService
 *
 * Testkonzept gemäss Leitfrage A6 & B10:
 * 1. System: Administrative Benutzerverwaltung & Registration Codes
 * 2. Umgebung: Spring Boot Unit Test (JUnit 5, Mockito)
 * 3. Testfälle: User-Erstellung, Vertragsnummer-Logik, Code-Management
 * 4. Testmethoden: Mockito Unit Tests, Fokus auf korrekte Persistierung
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Admin User Service Tests")
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
                .build();
    }

    // ========== USER CREATION TESTS (Leitfrage A14) ==========

    @Test
    @DisplayName("TC-A001: User erfolgreich erstellen mit Passwort-Hashing")
    void testCreateUserSuccess() {
        // Arrange
        testUser.setPassword("plainPassword");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        User created = adminUserService.createUser(testUser);

        // Assert
        assertEquals("hashedPassword", created.getPassword());
        assertEquals(Role.USER, created.getRole(), "Sollte Standard-Rolle USER erhalten");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("TC-A002: User-Erstellung schlägt fehl bei existierender Email")
    void testCreateUserDuplicateEmail() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> adminUserService.createUser(testUser));
    }

    @Test
    @DisplayName("TC-A003: Vertragsnummer wird automatisch generiert wenn leer")
    void testCreateUserGeneratesContractNumber() {
        // Arrange
        testUser.setContractNumber(null);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByContractNumber(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        User created = adminUserService.createUser(testUser);

        // Assert
        assertNotNull(created.getContractNumber());
        assertTrue(created.getContractNumber().length() >= 8);
    }

    // ========== REGISTRATION CODE TESTS ==========

    @Test
    @DisplayName("TC-A004: Registration Code erfolgreich erstellen")
    void testCreateRegistrationCode() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(registrationCodesRepository.save(any(RegistrationCodes.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        RegistrationCodes code = adminUserService.createRegistrationCode(userId, "Title", "Desc");

        // Assert
        assertNotNull(code);
        assertEquals(RegistrationCodeStatus.OPEN, code.getStatus());
        assertEquals(testUser, code.getUser());
    }

    @Test
    @DisplayName("TC-A005: Registration Code löschen (Erfolg)")
    void testDeleteRegistrationCodeSuccess() {
        // Arrange
        UUID codeId = UUID.randomUUID();
        RegistrationCodes code = new RegistrationCodes();
        code.setUser(testUser);

        when(registrationCodesRepository.findById(codeId)).thenReturn(Optional.of(code));

        // Act
        boolean result = adminUserService.deleteRegistrationCode(userId, codeId);

        // Assert
        assertTrue(result);
        verify(registrationCodesRepository).delete(code);
    }

    @Test
    @DisplayName("TC-A006: Registration Code löschen schlägt fehl (Falscher User)")
    void testDeleteRegistrationCodeWrongUser() {
        // Arrange
        UUID codeId = UUID.randomUUID();
        User wrongUser = new User();
        wrongUser.setId(UUID.randomUUID());

        RegistrationCodes code = new RegistrationCodes();
        code.setUser(wrongUser);

        when(registrationCodesRepository.findById(codeId)).thenReturn(Optional.of(code));

        // Act
        boolean result = adminUserService.deleteRegistrationCode(userId, codeId);

        // Assert
        assertFalse(result);
        verify(registrationCodesRepository, never()).delete(any());
    }

    // ========== GETTER TESTS ==========

    @Test
    @DisplayName("TC-A007: Einzelnen User abrufen")
    void testGetUser() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        User found = adminUserService.getUser(userId);

        // Assert
        assertNotNull(found);
        assertEquals(userId, found.getId());
    }
}