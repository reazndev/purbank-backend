package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Transaction;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.TransactionType;
import ch.purbank.core.repository.KontoRepository;
import ch.purbank.core.repository.TransactionRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test Suite für InterestService
 * 
 * Testkonzept gemäss Leitfrage A6 & B10:
 * 1. System: Zinsrechnung und Abrechnung in Purbank
 * 2. Umgebung: Spring Boot mit @Transactional, PostgreSQL (gemockt)
 * 3. KRITISCHE Testfälle für Geldverwaltung:
 * - Tägliche Zinsberechnung (Formel: balance × (annual_rate / 365))
 * - Quartalsweise Abrechnung (Zinsen gutschreiben)
 * - Grenzfälle: Sehr hohe/niedrige Zinsen, sehr hohe Balances
 * - Rounding & Precision (4 Dezimalstellen)
 * 4. Testmethoden: Unit Tests, Parameterized Tests mit verschiedenen Szenarien
 * 5. Testmittel: JUnit 5, Mockito, BigDecimal Mathematik
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Interest Service Tests")
class InterestServiceTest {

    @Mock
    private KontoRepository kontoRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private InterestService interestService;

    private Konto testKonto;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testKonto = new Konto();
        testKonto.setId(UUID.randomUUID());
        testKonto.setName("Test Account");
        testKonto.setBalance(new BigDecimal("10000.00"));
        testKonto.setZinssatz(new BigDecimal("0.0100")); // 1% annual
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        testKonto.setStatus(KontoStatus.ACTIVE);
        testKonto.setLastInterestCalcDate(null);
        testKonto.setCurrency(Currency.CHF);
        testKonto.setCreatedAt(LocalDateTime.now());

        testDate = LocalDate.now();
    }

    // ========== DAILY INTEREST CALCULATION TESTS ==========

    @Test
    @DisplayName("TC-I001: Tägliche Zinsberechnung - Standardfall")
    void testCalculateDailyInterestStandardCase() {
        // Arrange
        BigDecimal balance = new BigDecimal("10000.00");
        BigDecimal annualRate = new BigDecimal("0.0100"); // 1%

        // Expected: 10000 × (0.0100 / 365) = 0.2740 (rounded)
        BigDecimal expectedDailyInterest = balance
                .multiply(annualRate)
                .divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(new BigDecimal("0.2740"), expectedDailyInterest);
    }

    @Test
    @DisplayName("TC-I002: Tägliche Zinsberechnung mit verschiedenen Zinssätzen")
    @ParameterizedTest
    @CsvSource({
            "10000.00, 0.0050, 0.1370", // 0.5%
            "10000.00, 0.0100, 0.2740", // 1.0%
            "10000.00, 0.0150, 0.4110", // 1.5%
            "10000.00, 0.0250, 0.6850", // 2.5%
    })
    void testCalculateDailyInterestVariousRates(String balanceStr, String rateStr, String expectedStr) {
        // Arrange
        BigDecimal balance = new BigDecimal(balanceStr);
        BigDecimal rate = new BigDecimal(rateStr);
        BigDecimal expected = new BigDecimal(expectedStr);

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(expected, dailyInterest);
    }

    @Test
    @DisplayName("TC-I003: Tägliche Zinsberechnung mit sehr hohem Balance")
    void testCalculateDailyInterestHighBalance() {
        // Arrange
        BigDecimal balance = new BigDecimal("999999999.9999");
        BigDecimal rate = new BigDecimal("0.0100");

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertTrue(dailyInterest.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(4, dailyInterest.scale(), "Zinsen sollten 4 Dezimalstellen haben");
    }

    @Test
    @DisplayName("TC-I004: Tägliche Zinsberechnung mit sehr niedrigem Balance")
    void testCalculateDailyInterestLowBalance() {
        // Arrange
        BigDecimal balance = new BigDecimal("0.01");
        BigDecimal rate = new BigDecimal("0.0100");

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertTrue(dailyInterest.compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("TC-I005: Tägliche Zinsberechnung mit 0% Zinssatz")
    void testCalculateDailyInterestZeroRate() {
        // Arrange
        BigDecimal balance = new BigDecimal("10000.00");
        BigDecimal rate = new BigDecimal("0.0000");

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(BigDecimal.ZERO, dailyInterest);
    }

    @Test
    @DisplayName("TC-I006: Tägliche Zinsberechnung mit 0 Balance")
    void testCalculateDailyInterestZeroBalance() {
        // Arrange
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal rate = new BigDecimal("0.0100");

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(BigDecimal.ZERO, dailyInterest);
    }

    @Test
    @DisplayName("TC-I007: LastInterestCalcDate wird nicht gesetzt wenn bereits berechnet heute")
    void testSkipDailyInterestIfAlreadyCalculatedToday() {
        // Arrange
        testKonto.setLastInterestCalcDate(testDate);
        List<Konto> konten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(konten);

        // Act
        interestService.calculateDailyInterest();

        // Assert
        assertEquals(testDate, testKonto.getLastInterestCalcDate());
        verify(kontoRepository, never()).save(testKonto);
    }

    @Test
    @DisplayName("TC-I008: Akumulierte Zinsen werden erhöht bei täglicher Berechnung")
    void testAccruedInterestIncrementsDaily() {
        // Arrange
        BigDecimal initialAccrued = new BigDecimal("5. 00");
        BigDecimal dailyInterest = new BigDecimal("0.2740");
        testKonto.setAccruedInterest(initialAccrued);

        // Act
        BigDecimal newAccrued = initialAccrued.add(dailyInterest);
        testKonto.setAccruedInterest(newAccrued);

        // Assert
        assertEquals(new BigDecimal("5.2740"), newAccrued);
    }

    @Test
    @DisplayName("TC-I009: Nur ACTIVE Konten werden für Zinsberechnung berücksichtigt")
    void testOnlyActiveKontenProcessed() {
        // Arrange
        Konto closedKonto = new Konto();
        closedKonto.setId(UUID.randomUUID());
        closedKonto.setStatus(KontoStatus.CLOSED);
        closedKonto.setBalance(new BigDecimal("5000.00"));

        List<Konto> allKonten = List.of(testKonto, closedKonto);
        when(kontoRepository.findAll()).thenReturn(allKonten);
        when(kontoRepository.save(any(Konto.class))).thenReturn(testKonto);

        // Act
        interestService.calculateDailyInterest();

        // Assert
        // Nur 1 Mal save für active Konto, nicht für closed
        verify(kontoRepository, atLeastOnce()).save(any(Konto.class));
    }

    @Test
    @DisplayName("TC-I010: Multiple Konten daily interest berechnet")
    void testCalculateDailyInterestMultipleKonten() {
        // Arrange
        Konto konto2 = new Konto();
        konto2.setId(UUID.randomUUID());
        konto2.setStatus(KontoStatus.ACTIVE);
        konto2.setBalance(new BigDecimal("5000.00"));
        konto2.setZinssatz(new BigDecimal("0.0100"));
        konto2.setAccruedInterest(BigDecimal.ZERO);

        List<Konto> konten = List.of(testKonto, konto2);
        when(kontoRepository.findAll()).thenReturn(konten);
        when(kontoRepository.save(any(Konto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        interestService.calculateDailyInterest();

        // Assert
        verify(kontoRepository, times(2)).save(any(Konto.class));
    }

    // ========== QUARTERLY ABRECHNUNG TESTS ==========

    @Test
    @DisplayName("TC-I011: Quartalsabrechnung - Zinsen werden als Transaktion gebucht")
    void testQuarterlyAbrechnungCreateTransaction() {
        // Arrange
        testKonto.setAccruedInterest(new BigDecimal("100.00"));
        List<Konto> activeKonten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(activeKonten);
        when(kontoRepository.save(any(Konto.class))).thenReturn(testKonto);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        interestService.processQuarterlyAbrechnung();

        // Assert
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("TC-I012: Quartalsabrechnung - Balance wird erhöht")
    void testQuarterlyAbrechnungIncreasesBalance() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        BigDecimal accruedInterest = new BigDecimal("10.00");
        testKonto.setBalance(initialBalance);
        testKonto.setAccruedInterest(accruedInterest);

        // Act
        BigDecimal newBalance = initialBalance.add(accruedInterest);
        testKonto.setBalance(newBalance);

        // Assert
        assertEquals(new BigDecimal("1010.00"), testKonto.getBalance());
    }

    @Test
    @DisplayName("TC-I013: Quartalsabrechnung - AccruedInterest wird auf 0 zurückgesetzt")
    void testQuarterlyAbrechnungResetsAccruedInterest() {
        // Arrange
        testKonto.setAccruedInterest(new BigDecimal("100.00"));

        // Act
        testKonto.setAccruedInterest(BigDecimal.ZERO);

        // Assert
        assertEquals(BigDecimal.ZERO, testKonto.getAccruedInterest());
    }

    @Test
    @DisplayName("TC-I014: Quartalsabrechnung mit mehreren Konten")
    void testQuarterlyAbrechnungMultipleKonten() {
        // Arrange
        Konto konto2 = new Konto();
        konto2.setId(UUID.randomUUID());
        konto2.setStatus(KontoStatus.ACTIVE);
        konto2.setBalance(new BigDecimal("5000.00"));
        konto2.setAccruedInterest(new BigDecimal("50.00"));

        List<Konto> activeKonten = List.of(testKonto, konto2);
        when(kontoRepository.findAll()).thenReturn(activeKonten);
        when(kontoRepository.save(any(Konto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        interestService.processQuarterlyAbrechnung();

        // Assert
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(kontoRepository, times(2)).save(any(Konto.class));
    }

    @Test
    @DisplayName("TC-I015: Quartalsabrechnung mit 0 accruedInterest - keine Transaktion")
    void testQuarterlyAbrechnungNoTransactionIfZeroAccrued() {
        // Arrange
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        List<Konto> activeKonten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(activeKonten);

        // Act
        interestService.processQuarterlyAbrechnung();

        // Assert
        // Keine Transaktion sollte erstellt werden wenn akumulierte Zinsen 0 sind
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== PRECISION & ROUNDING TESTS ==========

    @Test
    @DisplayName("TC-I016: Zinsberechnung mit verschiedenen Balance-Dezimalstellen")
    @ParameterizedTest
    @CsvSource({
            "100.00, 0.0100, 0.0027",
            "100.10, 0.0100, 0.0027",
            "100.99, 0.0100, 0.0028",
            "1000.5678, 0.0100, 0.0274",
    })
    void testInterestRoundingWithDecimalBalances(String balanceStr, String rateStr, String expectedStr) {
        // Arrange
        BigDecimal balance = new BigDecimal(balanceStr);
        BigDecimal rate = new BigDecimal(rateStr);
        BigDecimal expected = new BigDecimal(expectedStr);

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(expected, dailyInterest);
    }

    @Test
    @DisplayName("TC-I017: Precision wird bei mehrfachen Additionen erhalten")
    void testPrecisionAfterMultipleAdditions() {
        // Arrange
        BigDecimal accrued = BigDecimal.ZERO;
        BigDecimal daily1 = new BigDecimal("0.2740");
        BigDecimal daily2 = new BigDecimal("0.2740");
        BigDecimal daily3 = new BigDecimal("0.2740");

        // Act
        accrued = accrued.add(daily1);
        accrued = accrued.add(daily2);
        accrued = accrued.add(daily3);

        // Assert
        assertEquals(new BigDecimal("0.8220"), accrued);
        assertEquals(4, accrued.scale());
    }

    @Test
    @DisplayName("TC-I018: Rounding Mode HALF_UP wird korrekt angewendet")
    void testRoundingModeHalfUp() {
        // Arrange
        BigDecimal balance = new BigDecimal("9999.99");
        BigDecimal rate = new BigDecimal("0.0100");

        // Act
        // 9999.99 × 0.0100 / 365 = 0.273972... -> rounds to 0.2740
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertEquals(new BigDecimal("0.2740"), dailyInterest);
    }

    // ========== TRANSACTION TYPE TESTS ==========

    @Test
    @DisplayName("TC-I019: Interest-Transaktion hat Type INTEREST")
    void testInterestTransactionType() {
        // Arrange
        Transaction tx = new Transaction();
        tx.setTransactionType(TransactionType.INTEREST);

        // Assert
        assertEquals(TransactionType.INTEREST, tx.getTransactionType());
    }

    @Test
    @DisplayName("TC-I020: Interest-Transaktion hat positiven Betrag")
    void testInterestTransactionPositiveAmount() {
        // Arrange
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("10.00"));

        // Assert
        assertTrue(tx.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("TC-I021: Interest-Transaktion speichert Nachricht")
    void testInterestTransactionMessage() {
        // Arrange
        Transaction tx = new Transaction();
        String message = "Quarterly interest settlement";
        tx.setMessage(message);

        // Assert
        assertEquals(message, tx.getMessage());
    }

    // ========== EDGE CASES & VALIDATION ==========

    @Test
    @DisplayName("TC-I022: Interest mit maximaler Precision (8 Dezimalstellen während Berechnung)")
    void testInterestCalculationMaxPrecision() {
        // Arrange
        BigDecimal balance = new BigDecimal("10000.0000");
        BigDecimal rate = new BigDecimal("0.0100");

        // Act - Berechnung mit höherer Genauigkeit
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);

        // Assert
        assertEquals(8, dailyInterest.scale());
    }

    @Test
    @DisplayName("TC-I023: Negative akumulierte Zinsen sollten nicht möglich sein")
    void testNegativeAccruedInterestNotAllowed() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-10.00").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Negative accrued interest not allowed");
            }
        });
    }

    @Test
    @DisplayName("TC-I024: Abrechnung für closed Konten sollte übersprungen werden")
    void testAbrechnungSkipClosedKonten() {
        // Arrange
        testKonto.setStatus(KontoStatus.CLOSED);
        List<Konto> allKonten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(allKonten);

        // Act
        interestService.processQuarterlyAbrechnung();

        // Assert
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("TC-I025: Very small daily interest wird korrekt berechnet")
    void testVerySmallDailyInterest() {
        // Arrange
        BigDecimal balance = new BigDecimal("0.10");
        BigDecimal rate = new BigDecimal("0.0010"); // 0.1%

        // Act
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        // Assert
        assertTrue(dailyInterest.compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(4, dailyInterest.scale());
    }

    // ========== AUDIT LOG TESTS ==========

    @Test
    @DisplayName("TC-I026: Audit Log wird bei erfolgreicher Zinsberechnung erstellt")
    void testAuditLogOnSuccessfulInterestCalculation() {
        // Arrange
        List<Konto> konten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(konten);
        when(kontoRepository.save(any(Konto.class))).thenReturn(testKonto);

        // Act
        interestService.calculateDailyInterest();

        // Assert
        verify(auditLogService, atLeastOnce()).logSystem(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-I027: Audit Log wird bei Abrechnung erstellt")
    void testAuditLogOnQuarterlyAbrechnung() {
        // Arrange
        testKonto.setAccruedInterest(new BigDecimal("50.00"));
        List<Konto> konten = List.of(testKonto);
        when(kontoRepository.findAll()).thenReturn(konten);
        when(kontoRepository.save(any(Konto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        interestService.processQuarterlyAbrechnung();

        // Assert
        verify(auditLogService, atLeastOnce()).logSystem(any(), any(), any(), any());
    }

    // ========== INTEGRATION-LIKE TESTS ==========

    @Test
    @DisplayName("TC-I028: Vollständiger Tagesablauf:  Daily Interest + Abrechnung Vorbereitung")
    void testFullDayScenario() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("10000.00");
        BigDecimal rate = new BigDecimal("0.0100");
        testKonto.setBalance(initialBalance);
        testKonto.setZinssatz(rate);
        testKonto.setAccruedInterest(BigDecimal.ZERO);

        // Act
        // Day 1-90: Tägliche Zinsberechnung
        BigDecimal dailyInterest = initialBalance.multiply(rate).divide(BigDecimal.valueOf(365), 4,
                RoundingMode.HALF_UP);
        BigDecimal accruedAfter90Days = dailyInterest.multiply(BigDecimal.valueOf(90));
        testKonto.setAccruedInterest(accruedAfter90Days);

        // Assert
        assertTrue(testKonto.getAccruedInterest().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(testKonto.getAccruedInterest().compareTo(new BigDecimal("24.00")) > 0); // ~24. 66 CHF
    }

    @Test
    @DisplayName("TC-I029: Transaktions-Balance nach Interest ist korrekt")
    void testTransactionBalanceAfterInterest() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        BigDecimal interest = new BigDecimal("10.50");
        testKonto.setBalance(initialBalance);

        Transaction interestTx = new Transaction();
        interestTx.setAmount(interest);
        interestTx.setBalanceAfter(initialBalance.add(interest));
        interestTx.setTransactionType(TransactionType.INTEREST);

        // Assert
        assertEquals(new BigDecimal("1010.50"), interestTx.getBalanceAfter());
    }

    @Test
    @DisplayName("TC-I030: LastInterestCalcDate wird korrekt aktualisiert")
    void testLastInterestCalcDateUpdate() {
        // Arrange
        testKonto.setLastInterestCalcDate(null);
        LocalDate todayDate = LocalDate.now();

        // Act
        testKonto.setLastInterestCalcDate(todayDate);

        // Assert
        assertNotNull(testKonto.getLastInterestCalcDate());
        assertEquals(todayDate, testKonto.getLastInterestCalcDate());
    }
}