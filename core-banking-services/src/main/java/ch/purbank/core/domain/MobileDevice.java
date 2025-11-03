package ch.purbank.core.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_device")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(unique = true, nullable = false)
    private String publicKey;

    @Column(name = "device-name", nullable = false)
    private String deviceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_user", nullable = false)
    private User user;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_used_at", nullable = false, updatable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}