package ch.purbank.core.service;

import ch.purbank.core.domain.*;
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

    private static final int MOBILE_VERIFY_TOKEN_LENGTH = 64;

    @Transactional
    public Konto createKonto(String name, UUID userId, Currency currency) {
        log.info("Creating konto '{}' for user {} with currency {}", name, userId, currency);

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
        return konto;
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
    public void updateTransactionNote(UUID kontoId, UUID transactionId, UUID userId, String note) {
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
    }

    @Transactional
    public void updateKonto(UUID kontoId, UUID userId, UpdateKontoRequestDTO request) {
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

        if (request.getName() != null && !request.getName().isBlank()) {
            konto.setName(request.getName());
        }

        kontoRepository.save(konto);
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
    }

    @Transactional
    public void removeMember(UUID kontoId, UUID userId, UUID memberId) {
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

        return transactionRepository.save(transaction);
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

        if (request.getName() != null && !request.getName().isBlank()) {
            konto.setName(request.getName());
        }

        if (request.getZinssatz() != null) {
            konto.setZinssatz(request.getZinssatz());
            log.info("Admin updated konto {} zinssatz to {}", kontoId, request.getZinssatz());
        }

        if (balanceAdjustment != null && balanceAdjustment.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal newBalance = konto.getBalance().add(balanceAdjustment);
            konto.setBalance(newBalance);
            log.info("Admin adjusted konto {} balance by {}, new balance: {}", kontoId, balanceAdjustment, newBalance);
        }

        kontoRepository.save(konto);
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