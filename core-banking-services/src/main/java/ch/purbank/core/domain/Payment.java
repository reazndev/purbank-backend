package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.PaymentExecutionType;
import ch.purbank.core.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "payments")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "konto_id", nullable = false)
    @JsonIgnore
    private Konto konto;

    @Column(nullable = false)
    private String toIban;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String message; // Message to recipient

    @Column(columnDefinition = "TEXT")
    private String note; // Internal note

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentExecutionType executionType;

    @Column(nullable = false)
    private LocalDate executionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime executedAt;

    @Column
    private LocalDateTime lockedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (executionDate == null && executionType == PaymentExecutionType.INSTANT) {
            executionDate = LocalDate.now();
        }
    }

    public boolean canBeModified() {
        if (executionType == PaymentExecutionType.INSTANT) {
            return false; // Instant payments cannot be modified
        }
        return !locked && status == PaymentStatus.PENDING;
    }

    public boolean shouldBeLocked() {
        if (executionType == PaymentExecutionType.INSTANT || locked) {
            return false;
        }
        LocalDateTime lockTime = executionDate.atTime(0, 50); // 10 minutes before 1:00 AM, TODO: see todo in
                                                              // paymentservice
        return LocalDateTime.now().isAfter(lockTime);
    }

    public void lock() {
        this.locked = true;
        this.lockedAt = LocalDateTime.now();
    }

    public void execute() {
        this.status = PaymentStatus.EXECUTED;
        this.executedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }
}