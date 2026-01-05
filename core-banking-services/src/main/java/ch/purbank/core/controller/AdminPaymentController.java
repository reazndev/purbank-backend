package ch.purbank.core.controller;

import ch.purbank.core.domain.Payment;
import ch.purbank.core.dto.*;
import ch.purbank.core.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Payment Management", description = "Admin endpoints for managing payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get pending payments for user", description = "Admin: Gets all pending payments for a user (includes all konten they have access to)")
    public ResponseEntity<List<PaymentDTO>> getPendingPaymentsForUser(
            @Parameter(description = "User UUID", required = true) @PathVariable UUID userId,
            @Parameter(description = "Optional konto ID filter", required = false) @RequestParam(required = false) UUID konto) {

        List<PaymentDTO> payments = paymentService.getAllPendingPayments(userId, konto);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/konto/{kontoId}")
    @Operation(summary = "Get pending payments for konto", description = "Admin: Gets all pending payments for any konto")
    public ResponseEntity<List<PaymentDTO>> getPendingPaymentsForKonto(
            @Parameter(description = "Konto UUID", required = true) @PathVariable UUID kontoId) {

        List<PaymentDTO> payments = paymentService.getPendingPaymentsForKontoAdmin(kontoId);
        return ResponseEntity.ok(payments);
    }

    @PostMapping
    @Operation(summary = "Create payment", description = "Admin: Creates a new payment for any user/konto (no mobile approval required)")
    public ResponseEntity<Payment> createPayment(
            @Parameter(description = "User UUID (for context)", required = true) @RequestParam UUID userId,
            @Parameter(description = "Payment creation details", required = true) @Valid @RequestBody CreatePaymentRequestDTO request) {

        Payment payment = paymentService.createPaymentAdmin(userId, request);
        return ResponseEntity.ok(payment);
    }

    @PatchMapping("/{paymentId}")
    @Operation(summary = "Update payment", description = "Admin: Updates a pending payment (no mobile approval required)")
    public ResponseEntity<GenericStatusResponse> updatePayment(
            @Parameter(description = "Payment UUID", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Update details", required = true) @Valid @RequestBody UpdatePaymentRequestDTO request) {

        paymentService.updatePaymentAdmin(paymentId, request);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{paymentId}")
    @Operation(summary = "Cancel payment", description = "Admin: Cancels/deletes a pending payment (no mobile approval required)")
    public ResponseEntity<GenericStatusResponse> cancelPayment(
            @Parameter(description = "Payment UUID", required = true) @PathVariable UUID paymentId) {

        paymentService.cancelPaymentAdmin(paymentId);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }
}