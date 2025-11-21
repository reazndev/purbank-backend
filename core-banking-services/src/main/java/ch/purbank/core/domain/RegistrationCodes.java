package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.RegistrationCodeStatus;
import ch.purbank.core.security.SecureTokenGenerator;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "registration_codes")
@Data
public class RegistrationCodes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(name = "registration_code", unique = true, nullable = false, updatable = false)
    private String registrationCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RegistrationCodeStatus status = RegistrationCodeStatus.OPEN;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (registrationCode == null) {
            registrationCode = SecureTokenGenerator.generateToken(10);
        }
    }

    public void markUsed() {
        this.status = RegistrationCodeStatus.USED;
        this.usedAt = LocalDateTime.now();
    }
}
