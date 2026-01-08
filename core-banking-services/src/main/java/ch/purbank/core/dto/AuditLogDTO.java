package ch.purbank.core.dto;

import ch.purbank.core.domain.AuditLog;
import ch.purbank.core.domain.enums.AuditAction;
import ch.purbank.core.domain.enums.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private UUID id;
    private LocalDateTime timestamp;
    private UUID userId;
    private String userEmail;
    private AuditAction action;
    private AuditEntityType entityType;
    private UUID entityId;
    private String ipAddress;
    private String details;
    private String status;
    private String errorMessage;

    public static AuditLogDTO fromEntity(AuditLog auditLog) {
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .timestamp(auditLog.getTimestamp())
                .userId(auditLog.getUserId())
                .userEmail(auditLog.getUserEmail())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .ipAddress(auditLog.getIpAddress())
                .details(auditLog.getDetails())
                .status(auditLog.getStatus())
                .errorMessage(auditLog.getErrorMessage())
                .build();
    }
}