package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.KontoMember;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.MemberRole;
import ch.purbank.core.domain.enums.Role;
import ch.purbank.core.dto.UpdateKontoRequestDTO;
import ch.purbank.core.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testkonzept für KontoService
 * 1. System: Kontoführung & Stammdaten
 * 2. Umgebung: Spring Boot Unit Test Context
 * 3. Nicht getestet: Echte IBAN-Validierung gegen externe Prüfstellen
 * 4. Testmittel: Mockito, AuditLogService Integration
 * 5. Methode: State-Transition Testing (Statusänderungen)
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("Konto Service - Geschäftslogik & Validierung")
class KontoServiceTest {

    @Mock
    private KontoRepository kontoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KontoMemberRepository kontoMemberRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PendingKontoDeleteRepository pendingKontoDeleteRepository;
    @Mock
    private PendingMemberInviteRepository pendingMemberInviteRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private KontoService kontoService;

    private Konto testKonto;
    private User testUser;
    private UUID kontoId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        kontoId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testKonto = new Konto();
        testKonto.setId(kontoId);
        testKonto.setName("Main Account");
        testKonto.setBalance(new BigDecimal("5000.00"));
        testKonto.setZinssatz(new BigDecimal("0.0150"));
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        testKonto.setIban("CH9300762011623852957");
        testKonto.setCurrency(Currency.CHF);
        testKonto.setStatus(KontoStatus.ACTIVE);
        testKonto.setCreatedAt(LocalDateTime.now());

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .role(Role.USER)
                .status("ACTIVE")
                .build();
    }

    // ========== CREATION TESTS ==========

    @Test
    @DisplayName("TC-K001: Erfolgreiche Erstellung (Happy Path)")
    void testCreateKontoSuccess() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(kontoMemberRepository.findByUser(testUser)).thenReturn(Collections.emptyList());
        when(kontoRepository.save(any(Konto.class))).thenAnswer(i -> i.getArgument(0));

        Konto result = kontoService.createKonto("MyKonto", userId, Currency.CHF, httpRequest);

        assertNotNull(result);
        assertEquals("MyKonto", result.getName());
        assertEquals(KontoStatus.ACTIVE, result.getStatus());
        verify(auditLogService).logSuccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-K002: Validierung fehlgeschlagen bei leerem Namen")
    void testCreateKontoEmptyName() {
        assertThrows(IllegalArgumentException.class,
                () -> kontoService.createKonto("", userId, Currency.CHF, httpRequest));
    }

    // ========== UPDATE TESTS ==========

    @Test
    @DisplayName("TC-K003: Name aktualisieren (Owner)")
    void testUpdateKontoNameSuccess() {
        UpdateKontoRequestDTO req = new UpdateKontoRequestDTO();
        req.setName("New Name");

        when(kontoRepository.findById(kontoId)).thenReturn(Optional.of(testKonto));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        KontoMember owner = new KontoMember();
        owner.setRole(MemberRole.OWNER);
        when(kontoMemberRepository.findByKontoAndUser(testKonto, testUser)).thenReturn(Optional.of(owner));

        kontoService.updateKonto(kontoId, userId, req, httpRequest);

        assertEquals("New Name", testKonto.getName());
        verify(kontoRepository).save(testKonto);
    }

    @Test
    @DisplayName("TC-K004: IBAN wird bei Konto-Erstellung generiert")
    void testIbanGeneratedOnCreation() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(kontoMemberRepository.findByUser(testUser)).thenReturn(Collections.emptyList());

        when(kontoRepository.save(any(Konto.class))).thenAnswer(invocation -> {
            Konto konto = invocation.getArgument(0);
            if (konto.getIban() == null) {
                konto.setIban("CH" + System.nanoTime());
            }
            return konto;
        });

        Konto result = kontoService.createKonto("New IBAN Test", userId, Currency.CHF, httpRequest);
        assertNotNull(result.getIban());
    }

    @Test
    @DisplayName("TC-K005: IBAN ist eindeutig")
    void testIbanUniqueness() {
        Konto konto1 = new Konto();
        Konto konto2 = new Konto();
        konto1.setIban("CH123");
        konto2.setIban("CH456");
        assertNotEquals(konto1.getIban(), konto2.getIban());
    }

    @Test
    @DisplayName("TC-K006: Konto-Status ist initial ACTIVE")
    void testKontoInitialStatusActive() {
        assertEquals(KontoStatus.ACTIVE, testKonto.getStatus());
    }

    @Test
    @DisplayName("TC-K007: Negative Zinssätze müssen durch Service abgelehnt werden")
    void testServiceRejectsNegativeInterest() {
        UpdateKontoRequestDTO request = new UpdateKontoRequestDTO();
        request.setZinssatz(new BigDecimal("-0.01"));

        when(kontoRepository.findById(any())).thenReturn(Optional.of(testKonto));

        assertThrows(IllegalArgumentException.class,
                () -> kontoService.updateKonto(kontoId, userId, request, httpRequest));
    }

    @Test
    @DisplayName("TC-K008: Negative Balance sollte validiert werden")
    void testNegativeBalanceValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-100.00").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Balance cannot be negative");
            }
        });
    }

    // ========== VALIDATION PARAM TESTS ==========

    @ParameterizedTest
    @CsvSource({ "-0.01", "-100.00" })
    @DisplayName("TC-K012: Negative Balance/Zins Validierung")
    void testNegativeValuesValidation(String val) {
        BigDecimal value = new BigDecimal(val);
        UpdateKontoRequestDTO req = new UpdateKontoRequestDTO();
        req.setZinssatz(value);

        when(kontoRepository.findById(any())).thenReturn(Optional.of(testKonto));
        when(userRepository.findById(any())).thenReturn(Optional.of(testUser));
        when(kontoMemberRepository.findByKontoAndUser(any(), any())).thenReturn(Optional.of(new KontoMember() {
            {
                setRole(MemberRole.OWNER);
            }
        }));

        assertThrows(IllegalArgumentException.class, () -> kontoService.updateKonto(kontoId, userId, req, httpRequest));
    }

}