package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.Currency;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pending_payment_updates", indexes = {
        @Index(name = "idx_pending_update_mobile_verify", columnList = "mobile_verify_code")
})
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PendingPaymentUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "mobile_verify_code", unique = true, nullable = false)
    private String mobileVerifyCode;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    // Updated fields (nullable = fields that might not be updated)
    @Column(name = "to_iban")
    private String toIban;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_currency")
    private Currency paymentCurrency;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "execution_date")
    private LocalDate executionDate;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PendingPaymentStatus status = PendingPaymentStatus.PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusMinutes(30); // 30 minute expiration for updates
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void markCompleted(PendingPaymentStatus newStatus) {
        this.status = newStatus;
        this.completedAt = LocalDateTime.now();
    }
}