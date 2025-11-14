package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.MobileDeviceStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "mobile_devices")
@Data
public class MobileDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(unique = true, nullable = false)
    private String publicKey;

    @Column(nullable = false)
    private String deviceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MobileDeviceStatus status = MobileDeviceStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column()
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUsedAt = LocalDateTime.now();
    }
}
