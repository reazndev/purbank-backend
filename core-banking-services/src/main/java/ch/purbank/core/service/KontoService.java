package ch.purbank.core.service;

import ch.purbank.core.domain.*;
import ch.purbank.core.domain.enums.KontoStatus;
import ch.purbank.core.domain.enums.MemberRole;
import ch.purbank.core.domain.enums.PaymentStatus;
import ch.purbank.core.dto.*;
import ch.purbank.core.repository.*;
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

    @Transactional
    public Konto createKonto(String name, UUID userId) {
        log.info("Creating konto '{}' for user {}", name, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Konto konto = new Konto();
        konto.setName(name);

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
    public List<KontoListItemDTO> getAllKontenForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<KontoMember> memberships = kontoMemberRepository.findByUser(user);

        return memberships.stream()
                .map(m -> new KontoListItemDTO(
                        m.getKonto().getId(),
                        m.getKonto().getName(),
                        m.getKonto().getBalance(),
                        m.getRole(),
                        m.getKonto().getIban()))
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
                        t.getFromIban(),
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
    public void closeKonto(UUID kontoId, UUID userId) {
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

        // Cancel all pending payments
        List<Payment> pendingPayments = paymentRepository.findByKontoAndStatus(konto, PaymentStatus.PENDING);
        for (Payment payment : pendingPayments) {
            payment.cancel();
            paymentRepository.save(payment);
        }

        konto.close();
        kontoRepository.save(konto);

        log.info("Konto {} closed by user {}", kontoId, userId);
    }

    @Transactional
    public void inviteMember(UUID kontoId, UUID userId, String contractNumber, MemberRole role) {
        /*
         * TODO: we should probably let users accept or reject invitations instead of
         * auto accepting. This probably needs QR CODE part 2 and notifications support
         * to work. For now just auto accept
         */
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        User inviter = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        KontoMember inviterMembership = kontoMemberRepository.findByKontoAndUser(konto, inviter)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this konto"));

        if (inviterMembership.getRole() != MemberRole.OWNER) {
            throw new IllegalArgumentException("Only owners can invite members");
        }

        User invitee = userRepository.findAll().stream()
                .filter(u -> u.getContractNumber().equals(contractNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User with this contract number not found"));

        if (kontoMemberRepository.existsByKontoAndUser(konto, invitee)) {
            throw new IllegalArgumentException("User is already a member of this konto");
        }

        // Create membership
        KontoMember member = new KontoMember();
        member.setKonto(konto);
        member.setUser(invitee);
        member.setRole(role);
        kontoMemberRepository.save(member);

        log.info("User {} invited to konto {} with role {}", invitee.getId(), kontoId, role);
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
    public Transaction createTransaction(UUID kontoId, String fromIban, BigDecimal amount, String message) {
        Konto konto = kontoRepository.findById(kontoId)
                .orElseThrow(() -> new IllegalArgumentException("Konto not found"));

        if (konto.getStatus() != KontoStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot create transaction for closed konto");
        }

        konto.addToBalance(amount);
        kontoRepository.save(konto);

        Transaction transaction = new Transaction();
        transaction.setKonto(konto);
        transaction.setFromIban(fromIban);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(konto.getBalance());
        transaction.setMessage(message);

        return transactionRepository.save(transaction);
    }
}