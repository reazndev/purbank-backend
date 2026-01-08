package ch.purbank.core.controller;

import ch.purbank.core.domain.Transaction;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.TransactionType;
import ch.purbank.core.dto.*;
import ch.purbank.core.service.KontoService;
import ch.purbank.core.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Transaction Management", description = "Admin endpoints for managing transactions")
public class AdminTransactionController {

    private final KontoService kontoService;
    private final TransactionService transactionService;

    @GetMapping("/konto/{kontoId}")
    @Operation(summary = "Get transactions for konto", description = "Admin: Gets all transactions for any konto")
    public ResponseEntity<List<TransactionDTO>> getTransactionsForKonto(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "Range in format 'start.end' (e.g., '0.49')", required = false) @RequestParam(required = false) String range) {

        Integer start = null;
        Integer end = null;

        if (range != null && range.contains(".")) {
            String[] parts = range.split("\\.");
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid range format. Use 'start.end' (e.g., '0.49')");
            }
        }

        List<TransactionDTO> transactions = transactionService.getTransactionsAdmin(kontoId, start, end);
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/konto/{kontoId}")
    @Operation(summary = "Create transaction", description = "Admin: Creates a new transaction for any konto")
    public ResponseEntity<Transaction> createTransaction(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId,
            @Parameter(description = "IBAN of counterparty", required = true) @RequestParam String iban,
            @Parameter(description = "Transaction amount", required = true) @RequestParam BigDecimal amount,
            @Parameter(description = "Transaction message", required = false) @RequestParam(required = false) String message,
            @Parameter(description = "Transaction note", required = false) @RequestParam(required = false) String note,
            @Parameter(description = "Transaction type", required = true) @RequestParam TransactionType transactionType,
            @Parameter(description = "Currency", required = true) @RequestParam Currency currency) {

        Transaction transaction = kontoService.createTransaction(kontoId, iban, amount, message, note, transactionType, currency);
        return ResponseEntity.ok(transaction);
    }

    @PatchMapping("/{transactionId}")
    @Operation(summary = "Update transaction note", description = "Admin: Updates the note field of any transaction")
    public ResponseEntity<GenericStatusResponse> updateTransactionNote(
            @Parameter(description = "Transaction UUID", required = true) @PathVariable UUID transactionId,
            @Parameter(description = "Note update", required = true) @Valid @RequestBody UpdateTransactionNoteDTO request) {

        transactionService.updateTransactionNoteAdmin(transactionId, request.getNote());
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete transaction", description = "Admin: Deletes a transaction")
    public ResponseEntity<GenericStatusResponse> deleteTransaction(
            @Parameter(description = "Transaction UUID", required = true) @PathVariable UUID transactionId) {

        transactionService.deleteTransactionAdmin(transactionId);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }
}