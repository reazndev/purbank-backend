package ch.purbank.core.domain;

import ch.purbank.core.domain.enums.AuditAction;
import ch.purbank.core.domain.enums.AuditEntityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
        @Index(name = "idx_audit_entity_id", columnList = "entity_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id")
    private UUID userId; // Nullable for system operations

    @Column(name = "user_email")
    private String userEmail; // just in case even though currently we don't allow email changes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50)
    private AuditEntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId; // Nullable for operations not tied to a specific entity

    @Column(name = "ip_address", length = 45) // IPv6 max length, just future proofing
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON or descriptive text with operation details

    @Column(nullable = false, length = 20)
    private String status; // SUCCESS, FAILURE, ERROR

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage; // error details if status failure or error

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}