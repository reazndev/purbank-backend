package ch.purbank.core.service;

import ch.purbank.core.domain.AuditLog;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.AuditAction;
import ch.purbank.core.domain.enums.AuditEntityType;
import ch.purbank.core.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an audit event asynchronously. This method never throws exceptions to avoid disrupting business logic.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            User user,
            String ipAddress,
            String details,
            String status
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .userId(user != null ? user.getId() : null)
                    .userEmail(user != null ? user.getEmail() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ipAddress)
                    .details(details)
                    .status(status)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entityType={}, entityId={}",
                    action, entityType, entityId, e);
        }
    }

    @Async
    public void logSuccess(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            User user,
            String ipAddress,
            String details
    ) {
        logAsync(action, entityType, entityId, user, ipAddress, details, "SUCCESS");
    }

    @Async
    public void logFailure(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            User user,
            String ipAddress,
            String details,
            String errorMessage
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .userId(user != null ? user.getId() : null)
                    .userEmail(user != null ? user.getEmail() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ipAddress)
                    .details(details)
                    .status("FAILURE")
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entityType={}, entityId={}",
                    action, entityType, entityId, e);
        }
    }

    @Async
    public void logSystem(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String details
    ) {
        logAsync(action, entityType, entityId, null, null, details, "SUCCESS");
    }

    public String extractIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }

    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AuditLog> getAuditLogsWithFilters(
            UUID userId,
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return auditLogRepository.findWithFilters(
                userId, action, entityType, entityId, status, startDate, endDate, pageable
        );
    }
}