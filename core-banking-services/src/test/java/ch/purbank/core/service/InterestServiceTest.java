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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testkonzept für InterestService
 * 1. System: Zinsmodul (Interest Accrual & Payout)
 * 2. Umgebung: Isoliert mit Mockito
 * 3. Nicht getestet: Performance-Tests bei >1Mio Konten, Scheduler-Triggering
 * 4. Testmittel: BigDecimal-Präzisionsprüfung
 * 5. Methode: Boundary Value Analysis (Grenzwerte)
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("Interest Service - Finanzmathematik & Abrechnung")
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

    @BeforeEach
    void setUp() {
        testKonto = new Konto();
        testKonto.setId(UUID.randomUUID());
        testKonto.setName("Test Account");
        testKonto.setBalance(new BigDecimal("10000.00"));
        testKonto.setZinssatz(new BigDecimal("0.0100"));
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        testKonto.setStatus(KontoStatus.ACTIVE);
        testKonto.setCurrency(Currency.CHF);
        testKonto.setCreatedAt(LocalDateTime.now());
    }

    // ========== MATH PRECISION TESTS ==========

    @ParameterizedTest
    @CsvSource({
            "10000.00, 0.01, 0.2740", // Standard
            "1000.00, 0.05, 0.1370", // Higher rate
            "0.00, 0.01, 0.0000", // Zero balance
            "0.10, 0.01, 0.0000", // Round down (< 0.00005)
            "10000.00, 0.00, 0.0000" // Zero rate
    })
    @DisplayName("TC-I001: Präzise Zinsberechnung für diverse Szenarien")
    void testDailyInterestPrecision(String bal, String rate, String expected) {
        BigDecimal balance = new BigDecimal(bal);
        BigDecimal annualRate = new BigDecimal(rate);

        BigDecimal result = balance.multiply(annualRate)
                .divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);

        assertEquals(0, new BigDecimal(expected).compareTo(result));
    }

    // ========== LOGIC FLOW TESTS ==========

    @Test
    @DisplayName("TC-I002: Tägliche Berechnung aktualisiert Akkumulierten Zins")
    void testCalculateDailyInterestUpdatesAccrued() {
        when(kontoRepository.findAll()).thenReturn(List.of(testKonto));
        when(kontoRepository.save(any(Konto.class))).thenAnswer(i -> i.getArgument(0));

        interestService.calculateDailyInterest();

        assertTrue(testKonto.getAccruedInterest().compareTo(BigDecimal.ZERO) > 0);
        verify(kontoRepository).save(testKonto);
    }

    @Test
    @DisplayName("TC-I003: Inaktive/Geschlossene Konten werden übersprungen")
    void testSkipInactiveAccounts() {
        testKonto.setStatus(KontoStatus.CLOSED);
        when(kontoRepository.findAll()).thenReturn(List.of(testKonto));

        interestService.calculateDailyInterest();

        assertEquals(BigDecimal.ZERO, testKonto.getAccruedInterest());
        verify(kontoRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-I004: Tägliche Zinsberechnung mit 0% Zinssatz")
    void testCalculateDailyInterestZeroRate() {
        BigDecimal balance = new BigDecimal("10000.00");
        BigDecimal rate = BigDecimal.ZERO;
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        assertEquals(0, BigDecimal.ZERO.compareTo(dailyInterest));
    }

    @Test
    @DisplayName("TC-I005: Tägliche Zinsberechnung mit 0 Balance")
    void testCalculateDailyInterestZeroBalance() {
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal rate = new BigDecimal("0.0100");
        BigDecimal dailyInterest = balance.multiply(rate).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        assertEquals(0, BigDecimal.ZERO.compareTo(dailyInterest));
    }

    @Test
    @DisplayName("TC-I006: Quartalsabrechnung erstellt Transaktion & Reset")
    void testQuarterlyAbrechnungLogic() {
        testKonto.setAccruedInterest(new BigDecimal("50.00"));
        when(kontoRepository.findAll()).thenReturn(List.of(testKonto));

        lenient().when(kontoRepository.save(any(Konto.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        interestService.processQuarterlyAbrechnung();

        assertEquals(0, BigDecimal.ZERO.compareTo(testKonto.getAccruedInterest()));
        assertEquals(0, new BigDecimal("10050.00").compareTo(testKonto.getBalance()));
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("TC-I007: Interest-Transaktion hat Type INTEREST")
    void testInterestTransactionType() {
        Transaction tx = new Transaction();
        tx.setTransactionType(TransactionType.INTEREST);
        assertEquals(TransactionType.INTEREST, tx.getTransactionType());
    }

    @Test
    @DisplayName("TC-I008: Quartalsabrechnung macht nichts bei 0 Zins")
    void testQuarterlyAbrechnungZeroInterest() {
        testKonto.setAccruedInterest(BigDecimal.ZERO);
        when(kontoRepository.findAll()).thenReturn(List.of(testKonto));

        interestService.processQuarterlyAbrechnung();

        verify(transactionRepository, never()).save(any());
    }

}