package ch.purbank.core.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import org.springframework.lang.Nullable;
import java.util.UUID;

@Entity
@Table(name = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Nullable
    @Column(name = "user_id", nullable = true)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Nullable
    @Column(name = "calling_class", nullable = true)
    private String callingClass;

    @Nullable
    @Column(name = "calling_method", nullable = true)
    private String callingMethod;

    @Nullable
    @Column(name = "ip_address", nullable = true)
    private String ipAddress;

    @Nullable
    @Column(name = "user_agent", nullable = true)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}