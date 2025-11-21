package ch.purbank.core.domain;

import java.time.LocalDateTime;

import ch.purbank.core.domain.enums.MobileDeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "mobile_devices")
@Data
public class MobileDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(unique = true, nullable = false, updatable = false, columnDefinition = "TEXT")
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

    public void onUpdate() {
        lastUsedAt = LocalDateTime.now();
    }
}
