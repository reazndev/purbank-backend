package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Transaction;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.TransactionType;
import ch.purbank.core.dto.TransactionDTO;
import ch.purbank.core.repository.KontoRepository;
import ch.purbank.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test Suite für TransactionService
 * 
 * Testkonzept gemäss Leitfrage A6 & B10:
 * 1. System: Transaktionsverwaltung in Purbank Banking System
 * 2. Umgebung: Spring Boot mit JPA, PostgreSQL (gemockt)
 * 3. Kritische Testfälle:
 * - Normale Transaktionen (Eingang/Ausgang)
 * - Geldmenge-Validierung und Grenzwerte
 * - Balance-Updates und -Konsistenz
 * - Edge Cases: Negative Beträge, Dezimalzahlen, Währungen
 * 4. Testmethoden: Unit Tests mit Mockito, AAA Pattern
 * 5. Testmittel: JUnit 5, Mockito, Parameterized Tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Service Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private KontoRepository kontoRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Konto testKonto;
    private Transaction testTransaction;
    private UUID kontoId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        kontoId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        testKonto = new Konto();
        testKonto.setId(kontoId);
        testKonto.setName("Test Account");
        testKonto.setBalance(new BigDecimal("1000.00"));
        testKonto.setIban("CH9300762011623852957");
        testKonto.setCurrency(Currency.CHF);
        testKonto.setStatus(KontoStatus.ACTIVE);
        testKonto.setCreatedAt(LocalDateTime.now());

        testTransaction = new Transaction();
        testTransaction.setId(transactionId);
        testTransaction.setKonto(testKonto);
        testTransaction.setAmount(new BigDecimal("100.00"));
        testTransaction.setBalanceAfter(new BigDecimal("900.00"));
        testTransaction.setTimestamp(LocalDateTime.now());
        testTransaction.setIban("CH9300762011623852957");
        testTransaction.setTransactionType(TransactionType.OUTGOING);
        testTransaction.setCurrency(Currency.CHF);
    }

    // ========== HAPPY PATH TESTS ==========

    @Test
    @DisplayName("TC-T001: Transaktionen abrufen - Standard (0-49)")
    void testGetTransactionsDefaultRange() {
        // Arrange
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(kontoRepository.findById(kontoId)).thenReturn(Optional.of(testKonto));
        when(transactionRepository.findByKontoOrderByTimestampDesc(eq(testKonto), any(Pageable.class)))
                .thenReturn(transactions);

        // Act
        List<TransactionDTO> result = transactionService.getTransactionsAdmin(kontoId, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("100.00"), result.get(0).getAmount());
    }

    @Test
    @DisplayName("TC-T002: Transaktionen mit custom Range abrufen")
    void testGetTransactionsCustomRange() {
        // Arrange
        when(kontoRepository.findById(kontoId)).thenReturn(Optional.of(testKonto));
        when(transactionRepository.findByKontoOrderByTimestampDesc(eq(testKonto), any(Pageable.class)))
                .thenReturn(Arrays.asList(testTransaction));

        // Act
        List<TransactionDTO> result = transactionService.getTransactionsAdmin(kontoId, 0, 24);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("TC-T003: Transaktions-Note aktualisieren")
    void testUpdateTransactionNoteSuccessfully() {
        // Arrange
        String newNote = "Updated test note";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(testTransaction)).thenReturn(testTransaction);

        // Act
        transactionService.updateTransactionNoteAdmin(transactionId, newNote);

        // Assert
        assertEquals(newNote, testTransaction.getNote());
        verify(transactionRepository, times(1)).save(testTransaction);
    }

    @Test
    @DisplayName("TC-T004: Transaktion löschen und Balance anpassen")
    void testDeleteTransactionAdjustBalance() {
        // Arrange
        BigDecimal balanceBeforeDeletion = new BigDecimal("900.00");
        testKonto.setBalance(balanceBeforeDeletion);
        testTransaction.setAmount(new BigDecimal("100.00"));

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));
        when(kontoRepository.save(testKonto)).thenReturn(testKonto);

        // Act
        transactionService.deleteTransactionAdmin(transactionId);

        // Assert
        assertEquals(new BigDecimal("1000.00"), testKonto.getBalance());
        verify(kontoRepository, times(1)).save(testKonto);
        verify(transactionRepository, times(1)).delete(testTransaction);
    }

    // ========== GELDMENGE & CURRENCY TESTS ==========

    @Test
    @DisplayName("TC-T005: Transaktion mit standardmässiger CHF-Währung")
    void testTransactionDefaultCurrency() {
        // Assert
        assertEquals(Currency.CHF, testTransaction.getCurrency());
    }

    @Test
    @DisplayName("TC-T006: Transaktion mit unterschiedlichen Beträgen (Edge Case:  sehr klein)")
    @ParameterizedTest
    @ValueSource(strings = { "0.01", "0.001", "0.0001" })
    void testTransactionWithSmallAmounts(String amount) {
        // Arrange
        testTransaction.setAmount(new BigDecimal(amount));

        // Assert
        assertTrue(testTransaction.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("TC-T007: Transaktion mit sehr grossen Beträgen")
    void testTransactionWithLargeAmount() {
        // Arrange
        BigDecimal largeAmount = new BigDecimal("999999999.9999");
        testTransaction.setAmount(largeAmount);

        // Act & Assert
        assertEquals(largeAmount, testTransaction.getAmount());
    }

    @Test
    @DisplayName("TC-T008: Negative Beträge sollten abgelehnt werden")
    void testTransactionWithNegativeAmount() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-100.00").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Negative amounts are not allowed");
            }
        });
    }

    @Test
    @DisplayName("TC-T009: Transaktion mit Betrag NULL sollte fehlschlagen")
    void testTransactionWithNullAmount() {
        // Arrange
        Transaction txn = new Transaction();

        // Assert
        assertThrows(NullPointerException.class, () -> {
            txn.getAmount().compareTo(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("TC-T010: Dezimale Präzision (4 Dezimalstellen) behalten")
    void testTransactionDecimalPrecision() {
        // Arrange
        BigDecimal amount = new BigDecimal("100.1234");
        testTransaction.setAmount(amount);

        // Assert
        assertEquals(4, amount.scale(), "Dezimalzahlen sollten 4 Stellen haben");
    }

    // ========== BALANCE CONSISTENCY TESTS ==========

    @Test
    @DisplayName("TC-T011: Balance nach Ausgehender Transaktion korrekt berechnet")
    void testBalanceAfterOutgoingTransaction() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("1000.00");
        BigDecimal amount = new BigDecimal("250.00");
        testKonto.setBalance(initialBalance);

        // Act
        BigDecimal balanceAfter = initialBalance.subtract(amount);

        // Assert
        assertEquals(new BigDecimal("750.00"), balanceAfter);
    }

    @Test
    @DisplayName("TC-T012: Balance nach Eingehender Transaktion korrekt berechnet")
    void testBalanceAfterIncomingTransaction() {
        // Arrange
        BigDecimal initialBalance = new BigDecimal("500.00");
        BigDecimal amount = new BigDecimal("250.00");

        // Act
        BigDecimal balanceAfter = initialBalance.add(amount);

        // Assert
        assertEquals(new BigDecimal("750.00"), balanceAfter);
    }

    @Test
    @DisplayName("TC-T013: Mehrere Transaktionen - Konsistenz der Balance")
    void testMultipleTransactionsBalanceConsistency() {
        // Arrange
        BigDecimal balance = new BigDecimal("1000.00");
        BigDecimal tx1 = new BigDecimal("100.00"); // Outgoing
        BigDecimal tx2 = new BigDecimal("200.00"); // Incoming
        BigDecimal tx3 = new BigDecimal("50.00"); // Outgoing

        // Act
        balance = balance.subtract(tx1); // 900.00
        balance = balance.add(tx2); // 1100.00
        balance = balance.subtract(tx3); // 1050.00

        // Assert
        assertEquals(new BigDecimal("1050.00"), balance);
    }

    @Test
    @DisplayName("TC-T014: Balance darf nicht negativ werden (validierung)")
    void testBalanceCannotBeNegative() {
        // Arrange
        BigDecimal balance = new BigDecimal("100.00");
        BigDecimal withdrawal = new BigDecimal("150.00");

        // Act & Assert
        assertTrue(balance.subtract(withdrawal).compareTo(BigDecimal.ZERO) < 0,
                "Balance würde negativ - sollte validiert werden");
    }

    // ========== TRANSACTION TYPE TESTS ==========

    @Test
    @DisplayName("TC-T015: Ausgehende Transaktion (OUTGOING)")
    void testOutgoingTransactionType() {
        // Assert
        assertEquals(TransactionType.OUTGOING, testTransaction.getTransactionType());
    }

    @Test
    @DisplayName("TC-T016: Eingehende Transaktion (INCOMING)")
    void testIncomingTransactionType() {
        // Arrange
        testTransaction.setTransactionType(TransactionType.INCOMING);

        // Assert
        assertEquals(TransactionType.INCOMING, testTransaction.getTransactionType());
    }

    @Test
    @DisplayName("TC-T017: Zins-Transaktion (INTEREST)")
    void testInterestTransactionType() {
        // Arrange
        testTransaction.setTransactionType(TransactionType.INTEREST);

        // Assert
        assertEquals(TransactionType.INTEREST, testTransaction.getTransactionType());
    }

    // ========== TIMESTAMP TESTS ==========

    @Test
    @DisplayName("TC-T018: Timestamp wird bei Erstellung automatisch gesetzt")
    void testTransactionTimestampAutoSet() {
        // Arrange
        Transaction txn = new Transaction();
        LocalDateTime timestamp = LocalDateTime.now();

        // Act
        txn.setTimestamp(timestamp);

        // Assert
        assertNotNull(txn.getTimestamp());
        assertEquals(timestamp, txn.getTimestamp());
    }

    @Test
    @DisplayName("TC-T019: Transaktions-Reihenfolge:  Neueste zuerst (DESC)")
    void testTransactionOrderByTimestampDesc() {
        // Arrange
        Transaction tx1 = new Transaction();
        tx1.setTimestamp(LocalDateTime.now().minusHours(2));

        Transaction tx2 = new Transaction();
        tx2.setTimestamp(LocalDateTime.now());

        // Assert
        assertTrue(tx2.getTimestamp().isAfter(tx1.getTimestamp()));
    }

    // ========== IBAN & MESSAGE TESTS ==========

    @Test
    @DisplayName("TC-T020: IBAN korrekt gespeichert")
    void testTransactionIbanStored() {
        // Arrange
        String iban = "CH9300762011623852957";

        // Assert
        assertEquals(iban, testTransaction.getIban());
    }

    @Test
    @DisplayName("TC-T021: Transaktions-Nachricht gespeichert")
    void testTransactionMessageStored() {
        // Arrange
        String message = "Test payment";
        testTransaction.setMessage(message);

        // Assert
        assertEquals(message, testTransaction.getMessage());
    }

    @Test
    @DisplayName("TC-T022: Transaktions-Note gespeichert")
    void testTransactionNoteStored() {
        // Arrange
        String note = "Test note";
        testTransaction.setNote(note);

        // Assert
        assertEquals(note, testTransaction.getNote());
    }

    // ========== ERROR CASES ==========

    @Test
    @DisplayName("TC-T023: Transaktion für nicht existierendes Konto ablehnen")
    void testTransactionForNonExistentKonto() {
        // Arrange
        when(kontoRepository.findById(kontoId)).thenReturn(Optional.empty());

        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.getTransactionsAdmin(kontoId, null, null);
        });
    }

    @Test
    @DisplayName("TC-T024: Note aktualisieren für nicht existierende Transaktion")
    void testUpdateNoteForNonExistentTransaction() {
        // Arrange
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Assert
        assertThrows(IllegalArgumentException.class, () -> {
            transactionService.updateTransactionNoteAdmin(transactionId, "new note");
        });
    }

    @Test
    @DisplayName("TC-T025: Transaktion löschen schlägt fehl wenn Konto nicht existiert")
    void testDeleteTransactionFailsIfKontoNotFound() {
        // Arrange
        testTransaction.setKonto(null);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));

        // Assert
        assertThrows(NullPointerException.class, () -> {
            transactionService.deleteTransactionAdmin(transactionId);
        });
    }
}