package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.EmailVerificationStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification")
@Data
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(unique = true, nullable = false)
    private String emailVerifyToken;

    @Column(nullable = false)
    private String emailCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EmailVerificationStatus status = EmailVerificationStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private int resendCount = 0;

    @Column(unique = true)
    private String completeToken;

    @PrePersist
    protected void prePersist() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusMinutes(15);
    }

    public boolean expired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean canAttempt() {
        return !expired() && attempts < 3 && status == EmailVerificationStatus.PENDING;
    }

    public void markVerified(String completeToken) {
        status = EmailVerificationStatus.VERIFIED;
        this.completeToken = completeToken;
        this.expiresAt = LocalDateTime.now().plusHours(1);
    }

    public void invalidate() {
        status = EmailVerificationStatus.INVALID;
        expiresAt = LocalDateTime.now().minusMinutes(1);
    }

    public void incrementAttempts() {
        attempts++;
    }

    public boolean canResend() {
        return resendCount < 1 && status == EmailVerificationStatus.PENDING && !expired();
    }

    public void incrementResend() {
        resendCount++;
    }
}
