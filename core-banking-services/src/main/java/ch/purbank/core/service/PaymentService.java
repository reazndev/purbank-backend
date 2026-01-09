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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PendingPaymentRepository pendingPaymentRepository;
    private final PendingPaymentUpdateRepository pendingPaymentUpdateRepository;
    private final PendingPaymentDeleteRepository pendingPaymentDeleteRepository;
    private final KontoRepository kontoRepository;
    private final KontoMemberRepository kontoMemberRepository;
    private final UserRepository userRepository;
    private final KontoService kontoService;
    private final CurrencyConversionService currencyConversionService;
    private final AuditLogService auditLogService;

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
        payment.setPaymentCurrency(request.getPaymentCurrency() != null ?
                request.getPaymentCurrency() : Currency.CHF);
        payment.setMessage(request.getMessage());
        payment.setNote(request.getNote());

        // Check if target IBAN is valid for INSTANT payments
        PaymentExecutionType executionType = request.getExecutionType();
        LocalDate executionDate;

        if (executionType == PaymentExecutionType.INSTANT) {
            Optional<Konto> targetKonto = kontoRepository.findByIban(request.getToIban());
            if (targetKonto.isEmpty()) {
                // Convert instant payment to normal payment for next day if target IBAN is invalid
                log.warn("Target IBAN {} not found for instant payment. Converting to normal payment for next day.", request.getToIban());
                executionType = PaymentExecutionType.NORMAL;
                executionDate = LocalDate.now().plusDays(1);
            } else {
                executionDate = LocalDate.now();
            }
        } else {
            if (request.getExecutionDate() == null) {
                throw new IllegalArgumentException("Execution date required for normal payments");
            }
            if (request.getExecutionDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Execution date cannot be in the past");
            }
            executionDate = request.getExecutionDate();
        }

        payment.setExecutionType(executionType);
        payment.setExecutionDate(executionDate);

        payment = paymentRepository.save(payment);

        // If INSTANT, execute immediately
        if (executionType == PaymentExecutionType.INSTANT) {
            executePayment(payment);
        }

        log.info("Payment created: {} for konto {} (type: {}, execution date: {})",
                payment.getId(), konto.getId(), executionType, executionDate);
        return payment;
    }

    @Transactional
    public String createPendingPaymentUpdate(UUID userId, UUID paymentId, String deviceId, String ipAddress, UpdatePaymentRequestDTO request) {
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

        // Validate update data
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (request.getExecutionDate() != null && request.getExecutionDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Execution date cannot be in the past");
        }

        // Invalidate any pending update requests for this user
        List<PendingPaymentUpdate> existingPending = pendingPaymentUpdateRepository.findByUserAndStatus(user, PendingPaymentStatus.PENDING);
        for (PendingPaymentUpdate existing : existingPending) {
            existing.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentUpdateRepository.save(existing);
        }

        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        PendingPaymentUpdate pendingUpdate = PendingPaymentUpdate.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .paymentId(paymentId)
                .toIban(request.getToIban())
                .amount(request.getAmount())
                .paymentCurrency(request.getPaymentCurrency())
                .message(request.getMessage())
                .note(request.getNote())
                .executionDate(request.getExecutionDate())
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .build();

        pendingPaymentUpdateRepository.save(pendingUpdate);
        log.info("Created pending payment update for payment {}", paymentId);

        return mobileVerifyCode;
    }

    @Transactional
    protected void executeApprovedPaymentUpdate(PendingPaymentUpdate pendingUpdate) {
        Payment payment = paymentRepository.findById(pendingUpdate.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (!payment.canBeModified()) {
            throw new IllegalArgumentException("Payment cannot be modified (locked or not pending)");
        }

        if (pendingUpdate.getToIban() != null) {
            payment.setToIban(pendingUpdate.getToIban());
        }
        if (pendingUpdate.getAmount() != null) {
            payment.setAmount(pendingUpdate.getAmount());
        }
        if (pendingUpdate.getPaymentCurrency() != null) {
            payment.setPaymentCurrency(pendingUpdate.getPaymentCurrency());
        }
        if (pendingUpdate.getMessage() != null) {
            payment.setMessage(pendingUpdate.getMessage());
        }
        if (pendingUpdate.getNote() != null) {
            payment.setNote(pendingUpdate.getNote());
        }
        if (pendingUpdate.getExecutionDate() != null) {
            payment.setExecutionDate(pendingUpdate.getExecutionDate());
        }

        paymentRepository.save(payment);
        log.info("Payment {} updated after approval", payment.getId());
    }

    @Transactional
    public String createPendingPaymentDelete(UUID userId, UUID paymentId, String deviceId, String ipAddress) {
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

        // Invalidate any pending delete requests for this user
        List<PendingPaymentDelete> existingPending = pendingPaymentDeleteRepository.findByUserAndStatus(user, PendingPaymentStatus.PENDING);
        for (PendingPaymentDelete existing : existingPending) {
            existing.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingPaymentDeleteRepository.save(existing);
        }

        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        PendingPaymentDelete pendingDelete = PendingPaymentDelete.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .paymentId(paymentId)
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .build();

        pendingPaymentDeleteRepository.save(pendingDelete);
        log.info("Created pending payment delete for payment {}", paymentId);

        return mobileVerifyCode;
    }

    @Transactional
    protected void executeApprovedPaymentDelete(PendingPaymentDelete pendingDelete) {
        Payment payment = paymentRepository.findById(pendingDelete.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (!payment.canBeModified()) {
            throw new IllegalArgumentException("Payment cannot be cancelled (locked or not pending)");
        }

        payment.cancel();
        paymentRepository.save(payment);
        log.info("Payment {} cancelled after approval", payment.getId());
    }

    @Transactional
    protected void executePayment(Payment payment) {
        try {
            // Step 1-2: Get source konto and its currency
            Konto sourceKonto = payment.getKonto();
            Currency sourceKontoCurrency = sourceKonto.getCurrency();
            Currency paymentCurrency = payment.getPaymentCurrency();
            BigDecimal paymentAmount = payment.getAmount();

            log.info("Executing payment {} - Amount: {} {}, Source Konto Currency: {}",
                    payment.getId(), paymentAmount, paymentCurrency, sourceKontoCurrency);

            // Step 3: Validate target IBAN exists BEFORE deducting money
            Optional<Konto> targetKontoOpt = kontoRepository.findByIban(payment.getToIban());
            if (targetKontoOpt.isEmpty()) {
                payment.fail();
                paymentRepository.save(payment);
                log.warn("Payment {} failed: target IBAN not found: {}", payment.getId(), payment.getToIban());
                return;
            }

            // Step 4: Convert payment to source konto currency
            BigDecimal kontoAmountToDeduct;
            if (!paymentCurrency.equals(sourceKontoCurrency)) {
                kontoAmountToDeduct = currencyConversionService.convert(
                        paymentAmount, paymentCurrency, sourceKontoCurrency);
                log.info("Converted {} {} to {} {} for deduction",
                        paymentAmount, paymentCurrency, kontoAmountToDeduct, sourceKontoCurrency);
            } else {
                kontoAmountToDeduct = paymentAmount;
            }

            // Step 5: Check if sufficient balance
            if (sourceKonto.getBalance().compareTo(kontoAmountToDeduct) < 0) {
                payment.fail();
                paymentRepository.save(payment);
                log.warn("Payment {} failed: insufficient funds (need {} {}, have {} {})",
                        payment.getId(), kontoAmountToDeduct, sourceKontoCurrency,
                        sourceKonto.getBalance(), sourceKontoCurrency);
                return;
            }

            // Step 6: Deduct from source konto
            sourceKonto.subtractFromBalance(kontoAmountToDeduct);
            kontoRepository.save(sourceKonto);

            // Step 7: Create outgoing transaction (preserve message and note from payment)
            kontoService.createTransaction(
                    sourceKonto.getId(),
                    payment.getToIban(),
                    kontoAmountToDeduct.negate(), // Negative for outgoing
                    payment.getMessage(), // Use original message
                    payment.getNote(), // Use original note
                    TransactionType.OUTGOING,
                    sourceKontoCurrency);

            // Step 8: Get target konto (already validated above)

            Konto targetKonto = targetKontoOpt.get();
            Currency targetKontoCurrency = targetKonto.getCurrency();

            // Step 9: Convert payment to target konto currency
            BigDecimal targetAmountToAdd;
            if (!paymentCurrency.equals(targetKontoCurrency)) {
                targetAmountToAdd = currencyConversionService.convert(
                        paymentAmount, paymentCurrency, targetKontoCurrency);
                log.info("Converted {} {} to {} {} for target konto",
                        paymentAmount, paymentCurrency, targetAmountToAdd, targetKontoCurrency);
            } else {
                targetAmountToAdd = paymentAmount;
            }

            // Step 10: Add to target konto
            targetKonto.addToBalance(targetAmountToAdd);
            kontoRepository.save(targetKonto);

            // Step 11: Create incoming transaction (preserve message, but note is null for receiver)
            kontoService.createTransaction(
                    targetKonto.getId(),
                    sourceKonto.getIban(),
                    targetAmountToAdd,
                    payment.getMessage(), // Use original message
                    null, // Note is null for receiver
                    TransactionType.INCOMING,
                    targetKontoCurrency);

            // Step 12: Mark payment as executed
            payment.execute();
            paymentRepository.save(payment);

            log.info("Payment {} executed successfully - Deducted: {} {}, Added: {} {}",
                    payment.getId(), kontoAmountToDeduct, sourceKontoCurrency,
                    targetAmountToAdd, targetKontoCurrency);

        } catch (Exception e) {
            payment.fail();
            paymentRepository.save(payment);
            log.error("Payment {} failed with exception: {}", payment.getId(), e.getMessage(), e);
        }
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
                .paymentCurrency(request.getPaymentCurrency() != null ?
                        request.getPaymentCurrency() : Currency.CHF)
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

        // Check if target IBAN is valid for INSTANT payments
        PaymentExecutionType executionType = pendingPayment.getExecutionType();
        LocalDate executionDate = pendingPayment.getExecutionDate();

        if (executionType == PaymentExecutionType.INSTANT) {
            Optional<Konto> targetKonto = kontoRepository.findByIban(pendingPayment.getToIban());
            if (targetKonto.isEmpty()) {
                // Convert instant payment to normal payment for next day if target IBAN is invalid
                log.warn("Target IBAN {} not found for instant payment. Converting to normal payment for next day.", pendingPayment.getToIban());
                executionType = PaymentExecutionType.NORMAL;
                executionDate = LocalDate.now().plusDays(1);
            }
        }

        // Create actual payment
        Payment payment = new Payment();
        payment.setKonto(konto);
        payment.setToIban(pendingPayment.getToIban());
        payment.setAmount(pendingPayment.getAmount());
        payment.setPaymentCurrency(pendingPayment.getPaymentCurrency());
        payment.setMessage(pendingPayment.getMessage());
        payment.setNote(pendingPayment.getNote());
        payment.setExecutionType(executionType);
        payment.setExecutionDate(executionDate);

        payment = paymentRepository.save(payment);

        // If INSTANT, execute immediately
        if (executionType == PaymentExecutionType.INSTANT) {
            executePayment(payment);
        }

        pendingPayment.markCompleted(PendingPaymentStatus.APPROVED);
        pendingPaymentRepository.save(pendingPayment);

        log.info("Payment {} created and approved from pending payment {} (type: {}, execution date: {})",
                payment.getId(), pendingPayment.getId(), executionType, executionDate);

        // Audit log payment approval and creation
        auditLogService.logSuccess(
                AuditAction.PAYMENT_APPROVED,
                AuditEntityType.PAYMENT,
                payment.getId(),
                user,
                pendingPayment.getIpAddress(),
                String.format("Payment of %s %s to %s approved and %s",
                        pendingPayment.getAmount(), pendingPayment.getPaymentCurrency(),
                        pendingPayment.getToIban(),
                        executionType == PaymentExecutionType.INSTANT ? "executed immediately" : "scheduled for " + executionDate)
        );

        return payment;
    }

    private PaymentDTO toDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getKonto().getId(),
                payment.getToIban(),
                payment.getAmount(),
                payment.getPaymentCurrency(),
                payment.getMessage(),
                payment.getNote(),
                payment.getExecutionType(),
                payment.getExecutionDate(),
                payment.getStatus(),
                payment.isLocked());
    }

    // ===== ADMIN METHODS =====

    @Transactional(readOnly = true)
    public List<PaymentDTO> getPendingPaymentsForKontoAdmin(UUID kontoId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        List<Payment> payments = paymentRepository.findByKontoAndStatus(konto, PaymentStatus.PENDING);

        return payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Payment createPaymentAdmin(UUID userId, CreatePaymentRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Konto konto = kontoRepository.findById(request.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

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
        payment.setPaymentCurrency(request.getPaymentCurrency() != null ?
                request.getPaymentCurrency() : Currency.CHF);
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

        log.info("Admin created payment: {} for konto {}", payment.getId(), konto.getId());
        return payment;
    }

    @Transactional
    public void updatePaymentAdmin(UUID paymentId, UpdatePaymentRequestDTO request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (!payment.canBeModified()) {
            throw new IllegalArgumentException("Payment cannot be modified (locked or not pending)");
        }

        // Validate update data
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (request.getExecutionDate() != null && request.getExecutionDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Execution date cannot be in the past");
        }

        // Update fields
        if (request.getToIban() != null) {
            payment.setToIban(request.getToIban());
        }
        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        if (request.getPaymentCurrency() != null) {
            payment.setPaymentCurrency(request.getPaymentCurrency());
        }
        if (request.getMessage() != null) {
            payment.setMessage(request.getMessage());
        }
        if (request.getNote() != null) {
            payment.setNote(request.getNote());
        }
        if (request.getExecutionDate() != null) {
            payment.setExecutionDate(request.getExecutionDate());
        }

        paymentRepository.save(payment);
        log.info("Admin updated payment {}", paymentId);
    }

    @Transactional
    public void cancelPaymentAdmin(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Only pending payments can be cancelled");
        }

        payment.cancel();
        paymentRepository.save(payment);

        log.info("Admin cancelled payment {}", paymentId);
    }
}