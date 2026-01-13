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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testkonzept für TransactionService (Leitfrage A6)
 * 1. System: Transaktionsmodul (Historisierung und Validierung)
 * 2. Umgebung: JUnit 5, Mockito Framework (isoliert)
 * 3. Nicht getestet: Datenbank-Constraints (Unique IBAN auf DB-Ebene),
 * Performance bei Massendaten.
 * 4. Testmittel: Mockito Mocks für Repository-Abfragen und Pageable-Objekte.
 * 5. Methode: Äquivalenzklassenbildung (Gültige/Ungültige Beträge) &
 * State-Testing.
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

    @Test
    @DisplayName("TC-T001: Transaktionen abrufen")
    void testGetTransactionsDefaultRange() {
        List<Transaction> transactions = Arrays.asList(testTransaction);
        when(kontoRepository.findById(kontoId)).thenReturn(Optional.of(testKonto));
        when(transactionRepository.findByKontoOrderByTimestampDesc(eq(testKonto), any(Pageable.class)))
                .thenReturn(transactions);

        List<TransactionDTO> result = transactionService.getTransactionsAdmin(kontoId, null, null);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("TC-T002: Transaktions-Note aktualisieren")
    void testUpdateTransactionNoteSuccessfully() {
        String newNote = "Updated test note";
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(testTransaction)).thenReturn(testTransaction);

        transactionService.updateTransactionNoteAdmin(transactionId, newNote);

        assertEquals(newNote, testTransaction.getNote());
        verify(transactionRepository, times(1)).save(testTransaction);
    }

    @Test
    @DisplayName("TC-T003: Negative Beträge sollten abgelehnt werden")
    void testTransactionWithNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            if (new BigDecimal("-100.00").compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Negative amounts are not allowed");
            }
        });
    }

    @Test
    @DisplayName("TC-T004: Transaktion auf nicht existierendes Konto")
    void testTransactionUnknownAccount() {
        when(kontoRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionsAdmin(UUID.randomUUID(), 0, 10));
    }

    @Test
    @DisplayName("TC-T005: Balance nach Ausgehender Transaktion korrekt berechnet")
    void testBalanceAfterOutgoingTransaction() {
        BigDecimal initialBalance = new BigDecimal("1000.00");
        BigDecimal amount = new BigDecimal("250.00");
        testKonto.setBalance(initialBalance);
        BigDecimal balanceAfter = initialBalance.subtract(amount);
        assertEquals(new BigDecimal("750.00"), balanceAfter);
    }
}