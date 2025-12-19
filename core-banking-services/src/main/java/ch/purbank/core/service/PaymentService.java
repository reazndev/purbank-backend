package ch.purbank.core.service;

import ch.purbank.core.domain.*;
import ch.purbank.core.domain.enums.*;
import ch.purbank.core.dto.*;
import ch.purbank.core.repository.*;
import ch.purbank.core.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final KontoRepository kontoRepository;
    private final KontoMemberRepository kontoMemberRepository;
    private final UserRepository userRepository;
    private final KontoService kontoService;

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;

    @Transactional(readOnly = true)
    public List<PaymentDTO> getAllPendingPayments(UUID userId, UUID kontoIdFilter) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Payment> payments;

        if (kontoIdFilter != null) {
            Konto konto = kontoRepository.findById(kontoIdFilter)
                    .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

            // Verify user has access
            if (!kontoMemberRepository.existsByKontoAndUser(konto, user)) {
                throw new IllegalArgumentException("User is not a member of this konto");
            }

            payments = paymentRepository.findByKontoAndStatus(konto, PaymentStatus.PENDING);
        } else {
            // Get all pending payments for konten where user is a member
            List<KontoMember> memberships = kontoMemberRepository.findByUser(user);
            payments = memberships.stream()
                    .flatMap(m -> paymentRepository.findByKontoAndStatus(m.getKonto(), PaymentStatus.PENDING).stream())
                    .collect(Collectors.toList());
        }

        return payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Payment createPayment(UUID userId, CreatePaymentRequestDTO request) {
        // TODO: handle mobile-verify
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Konto konto = kontoRepository.findById(request.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER and MANAGER can create payments
        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("Viewers cannot create payments");
        }

        if (konto.getStatus() != KontoStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot create payment for closed konto");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // For INSTANT payments, check if konto has sufficient funds
        if (request.getExecutionType() == PaymentExecutionType.INSTANT) {
            if (konto.getBalance().compareTo(request.getAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient funds for instant payment");
            }
        }

        Payment payment = new Payment();
        payment.setKonto(konto);
        payment.setToIban(request.getToIban());
        payment.setAmount(request.getAmount());
        payment.setMessage(request.getMessage());
        payment.setNote(request.getNote());
        payment.setExecutionType(request.getExecutionType());

        if (request.getExecutionType() == PaymentExecutionType.INSTANT) {
            payment.setExecutionDate(LocalDate.now());
        } else {
            if (request.getExecutionDate() == null) {
                throw new IllegalArgumentException("Execution date required for normal payments");
            }
            if (request.getExecutionDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Execution date cannot be in the past");
            }
            payment.setExecutionDate(request.getExecutionDate());
        }

        payment = paymentRepository.save(payment);

        // If INSTANT, execute immediately
        if (request.getExecutionType() == PaymentExecutionType.INSTANT) {
            executePayment(payment);
        }

        log.info("Payment created: {} for konto {}", payment.getId(), konto.getId());
        return payment;
    }

    @Transactional
    public void updatePayment(UUID userId, UUID paymentId, UpdatePaymentRequestDTO request) {
        // TODO: handle mobile-verify
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        Konto konto = payment.getKonto();

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER and MANAGER can update payments
        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("Viewers cannot update payments");
        }

        if (!payment.canBeModified()) {
            throw new IllegalArgumentException("Payment cannot be modified (locked or not pending)");
        }

        if (request.getToIban() != null) {
            payment.setToIban(request.getToIban());
        }
        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment amount must be positive");
            }
            payment.setAmount(request.getAmount());
        }
        if (request.getMessage() != null) {
            payment.setMessage(request.getMessage());
        }
        if (request.getNote() != null) {
            payment.setNote(request.getNote());
        }
        if (request.getExecutionDate() != null) {
            if (request.getExecutionDate().isBefore(LocalDate.now())) { // TODO: maybe only allow the next business day,
                // or we keep it and it will just be executed on
                // the next business day.
                throw new IllegalArgumentException("Execution date cannot be in the past");
            }
            payment.setExecutionDate(request.getExecutionDate());
        }

        paymentRepository.save(payment);
        log.info("Payment {} updated", paymentId);
    }

    @Transactional
    public void cancelPayment(UUID userId, UUID paymentId) {
        // TODO: handle mobile-verify
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        Konto konto = payment.getKonto();

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER and MANAGER can cancel payments
        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("Viewers cannot cancel payments");
        }

        if (!payment.canBeModified()) {
            throw new IllegalArgumentException("Payment cannot be cancelled (locked or not pending)");
        }

        payment.cancel();
        paymentRepository.save(payment);
        log.info("Payment {} cancelled", paymentId);
    }

    @Transactional
    protected void executePayment(Payment payment) {
        Konto konto = payment.getKonto();

        // Check if konto has sufficient funds
        if (konto.getBalance().compareTo(payment.getAmount()) < 0) {
            payment.fail();
            paymentRepository.save(payment);
            log.warn("Payment {} failed: insufficient funds", payment.getId());
            return;
        }

        // Deduct from konto
        konto.subtractFromBalance(payment.getAmount());
        kontoRepository.save(konto);

        // Create outgoing transaction
        kontoService.createTransaction(
                konto.getId(),
                konto.getIban(),
                payment.getAmount().negate(), // Negative for outgoing
                "Payment to " + payment.getToIban());

        // Mark payment as executed
        payment.execute();
        paymentRepository.save(payment);

        log.info("Payment {} executed successfully", payment.getId());
    }

    // Scheduled job to process payments at 1:00 AM daily
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processScheduledPayments() {
        /*
         * TODO: very much a WIP
         * - unlock failed payments
         * - also handle recurring payments
         * - lock payments earlier? e.g 1 hour?
         */
        log.info("Processing scheduled payments...");

        // Lock payments that should be locked
        List<Payment> pendingPayments = paymentRepository.findByStatusOrderByExecutionDateAsc(PaymentStatus.PENDING);
        for (Payment payment : pendingPayments) {
            if (payment.shouldBeLocked() && !payment.isLocked()) {
                payment.lock();
                paymentRepository.save(payment);
            }
        }

        // Execute payments due today
        List<Payment> duePayments = paymentRepository.findPaymentsDueForExecution(
                PaymentStatus.PENDING,
                PaymentExecutionType.NORMAL,
                LocalDate.now());

        log.info("Found {} payments to execute", duePayments.size());

        for (Payment payment : duePayments) {
            try {
                executePayment(payment);
            } catch (Exception e) {
                log.error("Failed to execute payment {}", payment.getId(), e);
            }
        }

        log.info("Scheduled payment processing complete");
    }

    // Scheduled job to clean up expired pending payments every 5 minutes
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    @Transactional
    public void cleanupExpiredPendingPayments() {
        log.debug("Cleaning up expired pending payments...");

        List<PendingPayment> expiredPayments = pendingPaymentRepository
                .findExpiredPendingPayments(PendingPaymentStatus.PENDING, java.time.LocalDateTime.now());

        int count = 0;
        for (PendingPayment pendingPayment : expiredPayments) {
            pendingPayment.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentRepository.save(pendingPayment);
            count++;
        }

        if (count > 0) {
            log.info("Marked {} pending payments as expired", count);
        }
    }

    @Transactional
    public String createPendingPayment(UUID userId, String deviceId, String ipAddress, CreatePaymentRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Konto konto = kontoRepository.findById(request.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER and MANAGER can create payments
        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("Viewers cannot create payments");
        }

        if (konto.getStatus() != KontoStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot create payment for closed konto");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // For INSTANT payments, check if konto has sufficient funds
        if (request.getExecutionType() == PaymentExecutionType.INSTANT) {
            if (konto.getBalance().compareTo(request.getAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient funds for instant payment");
            }
        }

        // Determine execution date
        LocalDate executionDate;
        if (request.getExecutionType() == PaymentExecutionType.INSTANT) {
            executionDate = LocalDate.now();
        } else {
            if (request.getExecutionDate() == null) {
                throw new IllegalArgumentException("Execution date required for normal payments");
            }
            if (request.getExecutionDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Execution date cannot be in the past");
            }
            executionDate = request.getExecutionDate();
        }

        // Invalidate any pending payment requests for this user
        List<PendingPayment> existingPending = pendingPaymentRepository.findByUserAndStatus(user, PendingPaymentStatus.PENDING);
        for (PendingPayment existing : existingPending) {
            existing.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentRepository.save(existing);
        }

        // Generate mobile verify code
        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        // Create pending payment
        PendingPayment pendingPayment = PendingPayment.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .kontoId(request.getKontoId())
                .toIban(request.getToIban())
                .amount(request.getAmount())
                .message(request.getMessage())
                .note(request.getNote())
                .executionType(request.getExecutionType())
                .executionDate(executionDate)
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .build();

        pendingPaymentRepository.save(pendingPayment);

        log.info("Pending payment created: {} for user {} requiring mobile approval", pendingPayment.getId(), userId);
        return mobileVerifyCode;
    }

    @Transactional
    public Payment executeApprovedPayment(PendingPayment pendingPayment) {
        // Validate pending payment status
        if (pendingPayment.getStatus() != PendingPaymentStatus.PENDING) {
            throw new IllegalArgumentException("Pending payment is not in PENDING status");
        }

        if (pendingPayment.isExpired()) {
            throw new IllegalArgumentException("Pending payment has expired");
        }

        Konto konto = kontoRepository.findById(pendingPayment.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        // Verify user still has access to konto
        User user = pendingPayment.getUser();
        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is no longer a member of this konto"));

        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("User no longer has permission to create payments");
        }

        if (konto.getStatus() != KontoStatus.ACTIVE) {
            throw new IllegalArgumentException("Konto is no longer active");
        }

        // For INSTANT payments, check if konto still has sufficient funds
        if (pendingPayment.getExecutionType() == PaymentExecutionType.INSTANT) {
            if (konto.getBalance().compareTo(pendingPayment.getAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient funds for instant payment");
            }
        }

        // Create actual payment
        Payment payment = new Payment();
        payment.setKonto(konto);
        payment.setToIban(pendingPayment.getToIban());
        payment.setAmount(pendingPayment.getAmount());
        payment.setMessage(pendingPayment.getMessage());
        payment.setNote(pendingPayment.getNote());
        payment.setExecutionType(pendingPayment.getExecutionType());
        payment.setExecutionDate(pendingPayment.getExecutionDate());

        payment = paymentRepository.save(payment);

        // If INSTANT, execute immediately
        if (pendingPayment.getExecutionType() == PaymentExecutionType.INSTANT) {
            executePayment(payment);
        }

        pendingPayment.markCompleted(PendingPaymentStatus.APPROVED);
        pendingPaymentRepository.save(pendingPayment);

        log.info("Payment {} created and approved from pending payment {}", payment.getId(), pendingPayment.getId());
        return payment;
    }

    private PaymentDTO toDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getKonto().getId(),
                payment.getToIban(),
                payment.getAmount(),
                payment.getMessage(),
                payment.getNote(),
                payment.getExecutionType(),
                payment.getExecutionDate(),
                payment.getStatus(),
                payment.isLocked());
    }
}