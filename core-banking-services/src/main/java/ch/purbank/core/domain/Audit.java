package ch.purbank.core.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String action;

    @Column(name = "user_id", nullable = true)
    private java.util.UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "calling_class", nullable = true)
    private String callingClass;

    @Column(name = "calling_method", nullable = true)
    private String callingMethod;

    @Column(name = "ip_address", nullable = true)
    private String ipAddress;

    @Column(name = "user_agent", nullable = true)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}