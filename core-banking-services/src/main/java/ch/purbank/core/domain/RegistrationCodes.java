package ch.purbank.core.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationCodes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(name = "registration-code", unique = true, nullable = false, updatable = false)
    private String registrationCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = true)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_user", nullable = false)
    private User user;

    @Column(name = "status", nullable = false)
    private String used = "OPEN";

    @Column(name = "used_at", nullable = false, updatable = false)
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        registrationCode = // TODO: generate token according to design
    }
}