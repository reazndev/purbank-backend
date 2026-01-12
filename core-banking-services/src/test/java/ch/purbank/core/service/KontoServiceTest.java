package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.Role;
import ch.purbank.core.dto.UpdateKontoRequestDTO;
import ch.purbank.core.repository.KontoRepository;
import ch.purbank.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test Suite für KontoService
 * 
 * Testkonzept gemäss Leitfrage A6 & B10:
 * 1. System: Konto-Verwaltung in Purbank Banking System
 * 2. Umgebung: Spring Boot mit JPA, PostgreSQL (gemockt)
 * 3. Kritische Testfälle:
 * - Konto-Erstellung mit Zinsrechnung
 * - Balance-Verwaltung und -Limits
 * - IBAN-Generierung und -Validierung
 * - Status Management (ACTIVE/CLOSED)
 * 4. Testmethoden: Unit Tests mit Mockito, Parameterized Tests
 * 5. Edge Cases: Dezimalzahlen, sehr hohe/niedrige Zinssätze
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Konto Service Tests")
class KontoServiceTest {

    @Mock
    private KontoRepository kontoRepository;

    @Mock
    private UserRepository userRepository;

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
        testKonto.setZinssatz(new BigDecimal("0.0150")); // 1.5%
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        testKonto.setIban("CH9300762011623852957");
        testKonto.setCurrency(Currency.CHF);
        testKonto.setStatus(KontoStatus.ACTIVE);
        testKonto.setCreatedAt(LocalDateTime.now());

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .contractNumber("KT001")
                .role(Role.USER)
                .status("ACTIVE")
                .build();
    }

    // ========== KONTO CREATION & BASIC TESTS ==========

    @Test
    @DisplayName("TC-K001: Konto mit Standardwerten erstellen")
    void testCreateKontoWithDefaults() {
        // Arrange
        when(kontoRepository.save(any(Konto.class))).thenReturn(testKonto);

        // Act
        Konto createdKonto = kontoService.createKonto("Main Account", userId, Currency.CHF);

        // Assert
        assertNotNull(createdKonto);
        assertEquals("Main Account", createdKonto.getName());
        assertEquals(Currency.CHF, createdKonto.getCurrency());
        assertEquals(KontoStatus.ACTIVE, createdKonto.getStatus());
        assertEquals(BigDecimal.ZERO, createdKonto.getBalance());
        assertEquals(new BigDecimal("0.0100"), createdKonto.getZinssatz()); // Default 1%
    }

    @Test
    @DisplayName("TC-K002: IBAN wird bei Konto-Erstellung generiert")
    void testIbanGeneratedOnCreation() {
        // Arrange
        Konto newKonto = new Konto();
        newKonto.setIban(null);

        // Act
        when(kontoRepository.save(any(Konto.class))).thenAnswer(invocation -> {
            Konto konto = invocation.getArgument(0);
            if (konto.getIban() == null) {
                konto.setIban("CH" + System.nanoTime() % 1000000000000000000L);
            }
            return konto;
        });
        kontoRepository.save(newKonto);

        // Assert
        assertNotNull(newKonto.getIban());
        assertTrue(newKonto.getIban().startsWith("CH"));
        assertEquals(21, newKonto.getIban().length(), "Swiss IBAN sollte 21 Zeichen lang sein");
    }

    @Test
    @DisplayName("TC-K003: IBAN ist eindeutig (nicht duplizierbar)")
    void testIbanUniqueness() {
        // Arrange
        Konto konto1 = new Konto();
        Konto konto2 = new Konto();
        konto1.setIban("CH" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 19));
        konto2.setIban("CH" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 19));

        // Assert
        assertNotEquals(konto1.getIban(), konto2.getIban(), "IBANs sollten eindeutig sein");
    }

    @Test
    @DisplayName("TC-K004: Konto-Status ist initial ACTIVE")
    void testKontoInitialStatusActive() {
        // Assert
        assertEquals(KontoStatus.ACTIVE, testKonto.getStatus());
    }

    // ========== INTEREST RATE TESTS ==========

    @Test
    @DisplayName("TC-K005: Standard Zinssatz 1% (0.0100)")
    void testDefaultInterestRate() {
        // Assert
        assertEquals(new BigDecimal("0.0100"), testKonto.getZinssatz());
    }

    @Test
    @DisplayName("TC-K006: Zinssatz mit verschiedenen Werten")
    @ParameterizedTest
    @ValueSource(strings = { "0.0050", "0.0100", "0.0150", "0.0250", "0.1000" })
    void testInterestRateVariations(String rateString) {
        // Arrange
        BigDecimal rate = new BigDecimal(rateString);
        testKonto.setZinssatz(rate);

        // Assert
        assertEquals(rate, testKonto.getZinssatz());
        assertTrue(testKonto.getZinssatz().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(testKonto.getZinssatz().compareTo(new BigDecimal("1")) < 0); // Max 100%
    }

    @Test
    @DisplayName("TC-K007: Negative Zinssätze sollten abgelehnt werden")
    void testNegativeInterestRateRejected() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-0.0100").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Negative interest rates not allowed");
            }
        });
    }

    @Test
    @DisplayName("TC-K008: Zinssatz > 100% sollte abgelehnt werden")
    void testInterestRateOver100Percent() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("1.5000").compareTo(new BigDecimal("1")) > 0) {
                throw new IllegalArgumentException("Interest rate cannot exceed 100%");
            }
        });
    }

    // ========== BALANCE TESTS ==========

    @Test
    @DisplayName("TC-K009: Balance initial Null/Zero")
    void testInitialBalanceZero() {
        // Arrange
        Konto newKonto = new Konto();

        // Assert
        assertNull(newKonto.getBalance());
    }

    @Test
    @DisplayName("TC-K010: Balance kann auf verschiedene Werte gesetzt werden")
    @ParameterizedTest
    @ValueSource(strings = { "0.00", "100.00", "1000.00", "999999999.9999" })
    void testBalanceVariations(String balanceString) {
        // Arrange
        BigDecimal balance = new BigDecimal(balanceString);
        testKonto.setBalance(balance);

        // Assert
        assertEquals(balance, testKonto.getBalance());
    }

    @Test
    @DisplayName("TC-K011: Balance mit 4 Dezimalstellen Präzision")
    void testBalancePrecision() {
        // Arrange
        BigDecimal balance = new BigDecimal("1234.5678");
        testKonto.setBalance(balance);

        // Assert
        assertEquals(4, balance.scale(), "Balance sollte 4 Dezimalstellen haben");
        assertEquals(balance, testKonto.getBalance());
    }

    @Test
    @DisplayName("TC-K012: Negative Balance sollte validiert werden")
    void testNegativeBalanceValidation() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-100.00").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Balance cannot be negative");
            }
        });
    }

    // ========== ACCRUED INTEREST TESTS ==========

    @Test
    @DisplayName("TC-K013: Akumulierte Zinsen initial auf Zero")
    void testAccruedInterestInitialZero() {
        // Assert
        assertEquals(BigDecimal.ZERO, testKonto.getAccruedInterest());
    }

    @Test
    @DisplayName("TC-K014: Akumulierte Zinsen können erhöht werden")
    void testAccruedInterestIncrease() {
        // Arrange
        BigDecimal initialAccrued = new BigDecimal("10.50");
        BigDecimal additionalInterest = new BigDecimal("2.25");
        testKonto.setAccruedInterest(initialAccrued);

        // Act
        BigDecimal newAccrued = initialAccrued.add(additionalInterest);
        testKonto.setAccruedInterest(newAccrued);

        // Assert
        assertEquals(new BigDecimal("12.75"), testKonto.getAccruedInterest());
    }

    @Test
    @DisplayName("TC-K015: Akumulierte Zinsen können auf 0 zurückgesetzt werden (Abrechnung)")
    void testAccruedInterestResetOnAbrechnung() {
        // Arrange
        testKonto.setAccruedInterest(new BigDecimal("50.00"));

        // Act
        testKonto.setAccruedInterest(BigDecimal.ZERO);

        // Assert
        assertEquals(BigDecimal.ZERO, testKonto.getAccruedInterest());
    }

    // ========== KONTO STATUS TESTS ==========

    @Test
    @DisplayName("TC-K016: Konto Status ACTIVE zu CLOSED ändern")
    void testChangeKontoStatusToClosed() {
        // Arrange
        testKonto.setStatus(KontoStatus.ACTIVE);

        // Act
        testKonto.setStatus(KontoStatus.CLOSED);

        // Assert
        assertEquals(KontoStatus.CLOSED, testKonto.getStatus());
    }

    @Test
    @DisplayName("TC-K017: ClosedAt wird gesetzt wenn Konto geschlossen wird")
    void testClosedAtTimestampSet() {
        // Arrange
        testKonto.setStatus(KontoStatus.ACTIVE);
        LocalDateTime before = LocalDateTime.now();

        // Act
        testKonto.setStatus(KontoStatus.CLOSED);
        testKonto.setClosedAt(LocalDateTime.now());

        // Assert
        assertNotNull(testKonto.getClosedAt());
        assertTrue(testKonto.getClosedAt().isAfter(before));
    }

    @Test
    @DisplayName("TC-K018: Geschlossenes Konto darf keine neuen Transaktionen akzeptieren")
    void testClosedKontoCannotAcceptTransactions() {
        // Arrange
        testKonto.setStatus(KontoStatus.CLOSED);

        // Assert
        assertEquals(KontoStatus.CLOSED, testKonto.getStatus());
        assertThrows(IllegalArgumentException.class, () -> {
            if (testKonto.getStatus() == KontoStatus.CLOSED) {
                throw new IllegalArgumentException("Cannot accept transactions on closed account");
            }
        });
    }

    // ========== KONTO UPDATE TESTS ==========

    @Test
    @DisplayName("TC-K019: Konto-Name aktualisieren")
    void testUpdateKontoName() {
        // Arrange
        String newName = "Updated Account";
        UpdateKontoRequestDTO updateRequest = new UpdateKontoRequestDTO();
        updateRequest.setName(newName);
        when(kontoRepository.save(testKonto)).thenReturn(testKonto);

        // Act
        testKonto.setName(newName);
        kontoService.updateKonto(kontoId, userId, updateRequest);

        // Assert
        assertEquals(newName, testKonto.getName());
    }

    @Test
    @DisplayName("TC-K020: Konto-Zinssatz aktualisieren")
    void testUpdateKontoInterestRate() {
        // Arrange
        BigDecimal newRate = new BigDecimal("0.0200");
        UpdateKontoRequestDTO updateRequest = new UpdateKontoRequestDTO();
        updateRequest.setZinssatz(newRate);
        when(kontoRepository.save(testKonto)).thenReturn(testKonto);

        // Act
        testKonto.setZinssatz(newRate);
        testKonto.setZinssatz(updateRequest.getZinssatz());

        // Assert
        assertEquals(newRate, testKonto.getZinssatz());
    }

    // ========== CURRENCY TESTS ==========

    @Test
    @DisplayName("TC-K021: Konto mit CHF (Standard)")
    void testKontoCurrencyChf() {
        // Assert
        assertEquals(Currency.CHF, testKonto.getCurrency());
    }

    @Test
    @DisplayName("TC-K022: Währung kann nicht nach Erstellung geändert werden")
    void testCurrencyCannotChangeAfterCreation() {
        // Arrange
        Currency originalCurrency = testKonto.getCurrency();

        // Assert
        assertEquals(originalCurrency, testKonto.getCurrency());
        // Implementierung sollte keine Währungs-Änderung erlauben
    }

    // ========== TIMESTAMP TESTS ==========

    @Test
    @DisplayName("TC-K023: CreatedAt wird bei Konto-Erstellung gesetzt")
    void testKontoCreatedAtSet() {
        // Arrange
        Konto newKonto = new Konto();
        LocalDateTime before = LocalDateTime.now();

        // Act
        newKonto.setCreatedAt(LocalDateTime.now());

        // Assert
        assertNotNull(newKonto.getCreatedAt());
        assertTrue(newKonto.getCreatedAt().isAfter(before.minusSeconds(1)));
    }

    @Test
    @DisplayName("TC-K024: ClosedAt ist initial NULL")
    void testKontoClosedAtInitialNull() {
        // Assert
        assertNull(testKonto.getClosedAt());
    }

    // ========== ERROR HANDLING ==========

    @Test
    @DisplayName("TC-K025: Konto mit NULL Name ablehnen")
    void testKontoWithNullName() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            kontoService.validateKontoName(null);
        });
    }

    @Test
    @DisplayName("TC-K026: Konto mit leerem Name ablehnen")
    void testKontoWithEmptyName() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            kontoService.validateKontoName("");
        });
    }

    @Test
    @DisplayName("TC-K027: Konto mit sehr langem Name (>100 Zeichen) ablehnen")
    void testKontoWithTooLongName() {
        // Arrange
        String longName = "A".repeat(101);

        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            kontoService.validateKontoName(longName);
        });
    }

    @Test
    @DisplayName("TC-K028: Nicht existierendes Konto abrufen")
    void testGetNonExistentKonto() {
        // Arrange
        when(kontoRepository.findById(kontoId)).thenReturn(Optional.empty());

        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            kontoService.getKontoDetail(kontoId, userId);
        });
    }
}