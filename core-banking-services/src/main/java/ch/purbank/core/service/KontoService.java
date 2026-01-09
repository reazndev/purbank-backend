package ch.purbank.core.service;

import ch.purbank.core.domain.*;
import ch.purbank.core.domain.enums.AuditAction;
import ch.purbank.core.domain.enums.AuditEntityType;
import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.MemberRole;
import ch.purbank.core.domain.enums.PaymentStatus;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import ch.purbank.core.domain.enums.TransactionType;
import ch.purbank.core.dto.*;
import ch.purbank.core.repository.*;
import ch.purbank.core.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KontoService {

    private final KontoRepository kontoRepository;
    private final KontoMemberRepository kontoMemberRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PendingKontoDeleteRepository pendingKontoDeleteRepository;
    private final PendingMemberInviteRepository pendingMemberInviteRepository;
    private final AuditLogService auditLogService;

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;
    private static final int MAX_KONTO_NAME_LENGTH = 100;

    @Transactional
    public Konto createKonto(String name, UUID userId, Currency currency, jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("Creating konto '{}' for user {} with currency {}", name, userId, currency);

        validateKontoName(name);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if user has reached max konto limit (200)
        long kontoCount = kontoMemberRepository.findByUser(user).stream()
                .filter(m -> m.getKonto().getStatus() == KontoStatus.ACTIVE)
                .count();

        if (kontoCount >= 200) {
            throw new IllegalArgumentException("Maximum konto limit of 200 reached");
        }

        Konto konto = new Konto();
        konto.setName(name);
        if (currency != null) {
            konto.setCurrency(currency);
        }

        konto = kontoRepository.save(konto);

        // Add creator as OWNER
        KontoMember member = new KontoMember();
        member.setKonto(konto);
        member.setUser(user);
        member.setRole(MemberRole.OWNER);
        kontoMemberRepository.save(member);

        log.info("Konto created with ID: {}", konto.getId());

        String ipAddress = auditLogService.extractIpAddress(httpRequest);
        auditLogService.logSuccess(
                AuditAction.KONTO_CREATED,
                AuditEntityType.KONTO,
                konto.getId(),
                user,
                ipAddress,
                String.format("Konto '%s' created with IBAN %s, currency %s", name, konto.getIban(), currency)
        );

        return konto;
    }

    private void validateKontoName(String name) {
                if (name == null) {
                        throw new IllegalArgumentException("Konto name cannot be null");
                }
                if (name.isBlank()) {
                        throw new IllegalArgumentException("Konto name cannot be empty");
                }
                if (name.length() > MAX_KONTO_NAME_LENGTH) {
                        throw new IllegalArgumentException("Konto name too long");
                }
        }

    @Transactional(readOnly = true)
    public List<KontoListItemDTO> getAllKontenForUser(UUID userId, Boolean includeClosed) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<KontoMember> memberships = kontoMemberRepository.findByUser(user);

        return memberships.stream()
                .filter(m -> {
                    // If includeClosed is null or false, show only open accounts
                    // If includeClosed is true, show only closed accounts
                    if (includeClosed == null || !includeClosed) {
                        return m.getKonto().getStatus() == KontoStatus.ACTIVE;
                    } else {
                        return m.getKonto().getStatus() == KontoStatus.CLOSED;
                    }
                })
                .map(m -> new KontoListItemDTO(
                        m.getKonto().getId(),
                        m.getKonto().getName(),
                        m.getKonto().getBalance(),
                        m.getRole(),
                        m.getKonto().getZinssatz(),
                        m.getKonto().getIban(),
                        m.getKonto().getCurrency()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KontoDetailDTO getKontoDetail(UUID kontoId, UUID userId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        return new KontoDetailDTO(
                konto.getId(),
                konto.getName(),
                konto.getBalance(),
                membership.getRole(),
                konto.getZinssatz(),
                konto.getIban(),
                konto.getCurrency(),
                konto.getStatus(),
                konto.getCreatedAt(),
                konto.getClosedAt());
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactions(UUID kontoId, UUID userId, Integer start, Integer end) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify user has access
        if (!kontoMemberRepository.existsByKontoAndUser(konto, user)) {
            throw new IllegalArgumentException("User is not a member of this konto");
        }

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
    public void updateTransactionNote(UUID kontoId, UUID transactionId, UUID userId, String note, jakarta.servlet.http.HttpServletRequest httpRequest) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER and MANAGER can update notes
        if (membership.getRole() == MemberRole.VIEWER) {
            throw new IllegalArgumentException("Viewers cannot update transaction notes");
        }

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!transaction.getKonto().getId().equals(kontoId)) {
            throw new IllegalArgumentException("Transaction does not belong to this konto");
        }

        transaction.setNote(note);
        transactionRepository.save(transaction);

        // Audit log transaction note update
        String ipAddress = auditLogService.extractIpAddress(httpRequest);
        auditLogService.logSuccess(
                AuditAction.TRANSACTION_UPDATED,
                AuditEntityType.TRANSACTION,
                transactionId,
                user,
                ipAddress,
                String.format("Transaction note updated on konto %s", konto.getIban())
        );
    }

    @Transactional
    public void updateKonto(UUID kontoId, UUID userId, UpdateKontoRequestDTO request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER can update konto details
        if (membership.getRole() != MemberRole.OWNER) {
            throw new IllegalArgumentException("Only owners can update konto details");
        }

        if (request == null) {
            throw new IllegalArgumentException("Update request must not be null");
        }

        if (request.getName() != null) {
            validateKontoName(request.getName());
            konto.setName(request.getName());
        }

        kontoRepository.save(konto);

        // Audit log konto update
        String ipAddress = auditLogService.extractIpAddress(httpRequest);
        auditLogService.logSuccess(
                AuditAction.KONTO_UPDATED,
                AuditEntityType.KONTO,
                kontoId,
                user,
                ipAddress,
                String.format("Konto details updated: %s", request.getName() != null ? "name changed to '" + request.getName() + "'" : "")
        );
    }

    @Transactional
    public String createPendingKontoDelete(UUID kontoId, UUID userId, String deviceId, String ipAddress) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember membership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        // Only OWNER can close konto
        if (membership.getRole() != MemberRole.OWNER) {
            throw new IllegalArgumentException("Only owners can close a konto");
        }

        if (!konto.canBeClosed()) {
            throw new IllegalArgumentException("Konto cannot be closed. Balance must be exactly 0");
        }

        // Invalidate any pending konto delete requests for this user
        List<PendingKontoDelete> existingPending = pendingKontoDeleteRepository.findByUserAndStatus(user, PendingPaymentStatus.PENDING);
        for (PendingKontoDelete existing : existingPending) {
            existing.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingKontoDeleteRepository.save(existing);
        }

        // Generate mobile verify code
        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        // Create pending konto delete
        PendingKontoDelete pendingDelete = PendingKontoDelete.builder()
                .user(user)
                .mobileVerifyCode(mobileVerifyCode)
                .kontoId(kontoId)
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .build();

        pendingKontoDeleteRepository.save(pendingDelete);
        log.info("Created pending konto delete for konto {}", kontoId);

        return mobileVerifyCode;
    }

    @Transactional
    protected void executeApprovedKontoDelete(PendingKontoDelete pendingDelete) {
        Konto konto = kontoRepository.findById(pendingDelete.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        if (!konto.canBeClosed()) {
            throw new IllegalArgumentException("Konto cannot be closed. Balance must be exactly 0");
        }

        // Cancel all pending payments
        List<Payment> pendingPayments = paymentRepository.findByKontoAndStatus(konto, PaymentStatus.PENDING);
        for (Payment payment : pendingPayments) {
            payment.cancel();
            paymentRepository.save(payment);
        }

        konto.close();
        kontoRepository.save(konto);

        log.info("Konto {} closed after approval", konto.getId());

        // Audit log konto closure
        auditLogService.logSuccess(
                AuditAction.KONTO_CLOSED,
                AuditEntityType.KONTO,
                konto.getId(),
                pendingDelete.getUser(),
                pendingDelete.getIpAddress(),
                String.format("Konto %s closed after mobile approval", konto.getIban())
        );
    }

    @Transactional
    public String createPendingMemberInvite(UUID kontoId, UUID userId, String contractNumber, MemberRole role, String deviceId, String ipAddress) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User inviter = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember inviterMembership = kontoMemberRepository.findByKontoAndUser(konto, inviter)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        if (inviterMembership.getRole() != MemberRole.OWNER) {
            throw new IllegalArgumentException("Only owners can invite members");
        }

        // Validate that user exists
        User invitee = userRepository.findAll().stream()
                .filter(u -> u.getContractNumber().equals(contractNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User with this contract number not found"));

        if (kontoMemberRepository.existsByKontoAndUser(konto, invitee)) {
            throw new IllegalArgumentException("User is already a member of this konto");
        }

        // Invalidate any pending member invite requests for this user
        List<PendingMemberInvite> existingPending = pendingMemberInviteRepository.findByUserAndStatus(inviter, PendingPaymentStatus.PENDING);
        for (PendingMemberInvite existing : existingPending) {
            existing.markCompleted(PendingPaymentStatus.EXPIRED);
            pendingMemberInviteRepository.save(existing);
        }

        String mobileVerifyCode = SecureTokenGenerator.generateToken(MOBILE_VERIFY_TOKEN_LENGTH);

        PendingMemberInvite pendingInvite = PendingMemberInvite.builder()
                .user(inviter)
                .mobileVerifyCode(mobileVerifyCode)
                .kontoId(kontoId)
                .contractNumber(contractNumber)
                .role(role)
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .build();

        pendingMemberInviteRepository.save(pendingInvite);
        log.info("Created pending member invite for konto {}", kontoId);

        return mobileVerifyCode;
    }

    @Transactional
    protected void executeApprovedMemberInvite(PendingMemberInvite pendingInvite) {
        Konto konto = kontoRepository.findById(pendingInvite.getKontoId())
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User invitee = userRepository.findAll().stream()
                .filter(u -> u.getContractNumber().equals(pendingInvite.getContractNumber()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User with this contract number not found"));

        if (kontoMemberRepository.existsByKontoAndUser(konto, invitee)) {
            throw new IllegalArgumentException("User is already a member of this konto");
        }

        // Create membership
        KontoMember member = new KontoMember();
        member.setKonto(konto);
        member.setUser(invitee);
        member.setRole(pendingInvite.getRole());
        kontoMemberRepository.save(member);

        log.info("User {} invited to konto {} with role {} after approval", invitee.getId(), konto.getId(), pendingInvite.getRole());

        // Audit log member addition
        auditLogService.logSuccess(
                AuditAction.KONTO_MEMBER_ADDED,
                AuditEntityType.KONTO_MEMBER,
                member.getId(),
                pendingInvite.getUser(),
                pendingInvite.getIpAddress(),
                String.format("User %s (%s) added to konto %s with role %s", invitee.getEmail(), invitee.getContractNumber(), konto.getIban(), pendingInvite.getRole())
        );
    }

    @Transactional
    public void removeMember(UUID kontoId, UUID userId, UUID memberId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember userMembership = kontoMemberRepository.findByKontoAndUser(konto, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        KontoMember targetMembership = kontoMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (!targetMembership.getKonto().getId().equals(kontoId)) {
            throw new IllegalArgumentException("Member does not belong to this konto");
        }

        boolean isSelfRemoval = targetMembership.getUser().getId().equals(userId);

        if (isSelfRemoval) {
            if (userMembership.getRole() == MemberRole.OWNER) {
                long ownerCount = kontoMemberRepository.countByKontoAndRole(konto, MemberRole.OWNER);
                if (ownerCount <= 1) {
                    throw new IllegalArgumentException(
                            "Cannot leave konto. There must be at least one other owner");
                }
            }
        } else {
            if (userMembership.getRole() != MemberRole.OWNER) {
                throw new IllegalArgumentException("Only owners can remove other members");
            }
        }

        kontoMemberRepository.delete(targetMembership);
        log.info("Member {} removed from konto {}", memberId, kontoId);

        // Audit log member removal
        String ipAddress = auditLogService.extractIpAddress(httpRequest);
        auditLogService.logSuccess(
                AuditAction.KONTO_MEMBER_REMOVED,
                AuditEntityType.KONTO_MEMBER,
                memberId,
                user,
                ipAddress,
                String.format("Member %s removed from konto %s%s", targetMembership.getUser().getEmail(), konto.getIban(), isSelfRemoval ? " (self-removal)" : "")
        );
    }

    @Transactional(readOnly = true)
    public List<MemberDTO> getMembers(UUID kontoId, UUID userId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify user has access (at least VIEWER can see members)
        if (!kontoMemberRepository.existsByKontoAndUser(konto, user)) {
            throw new IllegalArgumentException("User is not a member of this konto");
        }

        List<KontoMember> members = kontoMemberRepository.findByKonto(konto);

        return members.stream()
                .map(m -> new MemberDTO(
                        m.getId(),
                        m.getUser().getFirstName() + " " + m.getUser().getLastName(),
                        m.getUser().getEmail(),
                        m.getRole()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Transaction createTransaction(UUID kontoId, String iban, BigDecimal amount, String message, String note,
                                         TransactionType transactionType, Currency currency) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        if (konto.getStatus() != KontoStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot create transaction for closed konto");
        }

        Transaction transaction = new Transaction();
        transaction.setKonto(konto);
        transaction.setIban(iban);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(konto.getBalance());
        transaction.setMessage(message);
        transaction.setNote(note);
        transaction.setTransactionType(transactionType);
        transaction.setCurrency(currency);

        Transaction saved = transactionRepository.save(transaction);

        // Audit log transaction creation
        auditLogService.logSystem(
                AuditAction.TRANSACTION_CREATED,
                AuditEntityType.TRANSACTION,
                saved.getId(),
                String.format("Transaction created on konto %s: %s %s %s, type %s", konto.getIban(), amount, currency, iban, transactionType)
        );

        return saved;
    }

    // ===== ADMIN METHODS =====

    @Transactional(readOnly = true)
    public List<AdminKontoListItemDTO> getAllKontenForUserAdmin(UUID userId, Boolean includeClosed) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<KontoMember> memberships = kontoMemberRepository.findByUser(user);

        return memberships.stream()
                .filter(m -> {
                    if (includeClosed == null || !includeClosed) {
                        return m.getKonto().getStatus() == KontoStatus.ACTIVE;
                    } else {
                        return m.getKonto().getStatus() == KontoStatus.CLOSED;
                    }
                })
                .map(m -> new AdminKontoListItemDTO(
                        m.getKonto().getId(),
                        m.getKonto().getName(),
                        m.getKonto().getBalance(),
                        m.getRole(),
                        m.getKonto().getZinssatz(),
                        m.getKonto().getIban(),
                        m.getKonto().getCurrency(),
                        m.getKonto().getAccruedInterest(),
                        m.getKonto().getLastInterestCalcDate()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KontoDetailDTO getKontoDetailAdmin(UUID kontoId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        // Get first member for role display (admin doesn't have a specific role)
        KontoMember firstMember = kontoMemberRepository.findByKonto(konto).stream()
                .findFirst()
                .orElse(null);

        MemberRole displayRole = firstMember != null ? firstMember.getRole() : MemberRole.VIEWER;

        return new KontoDetailDTO(
                konto.getId(),
                konto.getName(),
                konto.getBalance(),
                displayRole,
                konto.getZinssatz(),
                konto.getIban(),
                konto.getCurrency(),
                konto.getStatus(),
                konto.getCreatedAt(),
                konto.getClosedAt());
    }

    @Transactional(readOnly = true)
    public AdminKontoDetailDTO getKontoDetailAdminExtended(UUID kontoId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        // Get first member for role display (admin doesn't have a specific role)
        KontoMember firstMember = kontoMemberRepository.findByKonto(konto).stream()
                .findFirst()
                .orElse(null);

        MemberRole displayRole = firstMember != null ? firstMember.getRole() : MemberRole.VIEWER;

        return new AdminKontoDetailDTO(
                konto.getId(),
                konto.getName(),
                konto.getBalance(),
                displayRole,
                konto.getZinssatz(),
                konto.getIban(),
                konto.getCurrency(),
                konto.getStatus(),
                konto.getCreatedAt(),
                konto.getClosedAt(),
                konto.getAccruedInterest(),
                konto.getLastInterestCalcDate());
    }

    @Transactional
    public void updateKontoAdmin(UUID kontoId, UpdateKontoRequestDTO request, BigDecimal balanceAdjustment) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        StringBuilder auditDetails = new StringBuilder();

        if (request == null) {
            throw new IllegalArgumentException("Update request must not be null");
        }

        if (request.getName() != null) {
            validateKontoName(request.getName());
            auditDetails.append("name changed to '").append(request.getName()).append("'; ");
            konto.setName(request.getName());
        }

        if (request.getZinssatz() != null) {
            auditDetails.append("interest rate changed from ").append(konto.getZinssatz())
                    .append(" to ").append(request.getZinssatz()).append("; ");
            konto.setZinssatz(request.getZinssatz());
            log.info("Admin updated konto {} zinssatz to {}", kontoId, request.getZinssatz());

            // Audit log interest rate change
            auditLogService.logSystem(
                    AuditAction.KONTO_INTEREST_RATE_CHANGED,
                    AuditEntityType.KONTO,
                    kontoId,
                    String.format("Admin changed interest rate for konto %s from %s to %s",
                            konto.getIban(), konto.getZinssatz(), request.getZinssatz())
            );
        }

        if (balanceAdjustment != null && balanceAdjustment.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal oldBalance = konto.getBalance();
            BigDecimal newBalance = oldBalance.add(balanceAdjustment);
            auditDetails.append("balance adjusted by ").append(balanceAdjustment)
                    .append(" from ").append(oldBalance).append(" to ").append(newBalance).append("; ");
            konto.setBalance(newBalance);
            log.info("Admin adjusted konto {} balance by {}, new balance: {}", kontoId, balanceAdjustment, newBalance);

            // Audit log balance adjustment
            auditLogService.logSystem(
                    AuditAction.KONTO_BALANCE_ADJUSTED,
                    AuditEntityType.KONTO,
                    kontoId,
                    String.format("Admin adjusted balance for konto %s by %s %s (from %s to %s)",
                            konto.getIban(), balanceAdjustment, konto.getCurrency(), oldBalance, newBalance)
            );
        }

        kontoRepository.save(konto);

        // Audit log general konto update if other changes were made
        if (auditDetails.length() > 0) {
            auditLogService.logSystem(
                    AuditAction.KONTO_UPDATED,
                    AuditEntityType.KONTO,
                    kontoId,
                    "Admin updated konto " + konto.getIban() + ": " + auditDetails.toString()
            );
        }
    }

    @Transactional
    public void closeKontoAdmin(UUID kontoId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        if (!konto.canBeClosed()) {
            throw new IllegalArgumentException("Konto cannot be closed. Balance must be exactly 0");
        }

        // Cancel all pending payments
        List<Payment> pendingPayments = paymentRepository.findByKontoAndStatus(konto, PaymentStatus.PENDING);
        for (Payment payment : pendingPayments) {
            payment.cancel();
            paymentRepository.save(payment);
        }

        konto.close();
        kontoRepository.save(konto);

        log.info("Admin closed konto {}", konto.getId());

        // Audit log konto closure by admin
        auditLogService.logSystem(
                AuditAction.KONTO_CLOSED,
                AuditEntityType.KONTO,
                kontoId,
                String.format("Admin closed konto %s", konto.getIban())
        );
    }

    @Transactional(readOnly = true)
    public List<MemberDTO> getMembersAdmin(UUID kontoId) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        List<KontoMember> members = kontoMemberRepository.findByKonto(konto);

        return members.stream()
                .map(m -> new MemberDTO(
                        m.getId(),
                        m.getUser().getFirstName() + " " + m.getUser().getLastName(),
                        m.getUser().getEmail(),
                        m.getRole()))
                .collect(Collectors.toList());
    }
}
