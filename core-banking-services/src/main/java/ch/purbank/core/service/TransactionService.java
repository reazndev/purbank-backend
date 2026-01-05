package ch.purbank.core.service;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Transaction;
import ch.purbank.core.dto.TransactionDTO;
import ch.purbank.core.repository.KontoRepository;
import ch.purbank.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final KontoRepository kontoRepository;

    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactionsAdmin(UUID kontoId, Integer start, Integer end) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        List<Transaction> transactions;
        if (start != null && end != null) {
            int size = end - start + 1;
            Pageable pageable = PageRequest.of(start / size, size);
            transactions = transactionRepository.findByKontoOrderByTimestampDesc(konto, pageable);
        } else {
            // Default: first 50
            Pageable pageable = PageRequest.of(0, 50);
            transactions = transactionRepository.findByKontoOrderByTimestampDesc(konto, pageable);
        }

        return transactions.stream()
                .map(t -> new TransactionDTO(
                        t.getId(),
                        t.getAmount(),
                        t.getBalanceAfter(),
                        t.getTimestamp(),
                        t.getIban(),
                        t.getTransactionType(),
                        t.getCurrency(),
                        t.getMessage(),
                        t.getNote()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateTransactionNoteAdmin(UUID transactionId, String note) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        transaction.setNote(note);
        transactionRepository.save(transaction);

        log.info("Admin updated transaction {} note", transactionId);
    }

    @Transactional
    public void deleteTransactionAdmin(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        Konto konto = transaction.getKonto();

        // Recalculate konto balance
        konto.setBalance(konto.getBalance().subtract(transaction.getAmount()));
        kontoRepository.save(konto);

        transactionRepository.delete(transaction);

        log.info("Admin deleted transaction {}", transactionId);
    }
}