package ch.purbank.core.controller;

import ch.purbank.core.domain.Payment;
import ch.purbank.core.domain.User;
import ch.purbank.core.dto.*;
import ch.purbank.core.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Payment management endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "List pending payments", description = "Gets all pending payments for the user, optionally filtered by konto")
    public ResponseEntity<List<PaymentDTO>> listPayments(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Optional konto ID filter", required = false) @RequestParam(required = false) UUID konto) {

        List<PaymentDTO> payments = paymentService.getAllPendingPayments(currentUser.getId(), konto);
        return ResponseEntity.ok(payments);
    }

    @PostMapping
    @Operation(summary = "Create payment", description = "Creates a pending payment that requires mobile approval. Returns a mobile-verify code.")
    public ResponseEntity<PendingPaymentResponseDTO> createPayment(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Payment creation details", required = true) @Valid @RequestBody CreatePaymentRequestDTO request,
            HttpServletRequest httpRequest) {

        // Extract IP address from request
        String ipAddress = httpRequest.getRemoteAddr();
        String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            ipAddress = forwardedFor.split(",")[0].trim();
        }

        String mobileVerifyCode = paymentService.createPendingPayment(
                currentUser.getId(),
                request.getDeviceId(),
                ipAddress,
                request);

        return ResponseEntity.ok(new PendingPaymentResponseDTO(mobileVerifyCode, "PENDING_APPROVAL"));
    }

    @PatchMapping("/{paymentId}")
    @Operation(summary = "Update payment", description = "Updates a pending payment (only if not locked)")
    public ResponseEntity<GenericStatusResponse> updatePayment(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Payment UUID", required = true) @PathVariable UUID paymentId,
            @Parameter(description = "Update details", required = true) @Valid @RequestBody UpdatePaymentRequestDTO request) {

        paymentService.updatePayment(currentUser.getId(), paymentId, request);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }

    @DeleteMapping("/{paymentId}")
    @Operation(summary = "Cancel payment", description = "Cancels a pending payment (only if not locked)")
    public ResponseEntity<GenericStatusResponse> cancelPayment(
            @AuthenticationPrincipal User currentUser,
            @Parameter(description = "Payment UUID", required = true) @PathVariable UUID paymentId) {

        paymentService.cancelPayment(currentUser.getId(), paymentId);
        return ResponseEntity.ok(new GenericStatusResponse("OK"));
    }
}