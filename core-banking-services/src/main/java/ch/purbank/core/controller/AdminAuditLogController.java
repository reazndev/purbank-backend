package ch.purbank.core.controller;

import ch.purbank.core.domain.AuditLog;
import ch.purbank.core.domain.enums.AuditAction;
import ch.purbank.core.domain.enums.AuditEntityType;
import ch.purbank.core.dto.AuditLogDTO;
import ch.purbank.core.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Audit Logs", description = "Admin endpoints for viewing audit logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(
            summary = "Get audit logs",
            description = "Admin: Retrieve audit logs with optional filters and pagination. " +
                    "Returns logs in descending order by timestamp (newest first)."
    )
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @Parameter(description = "Filter by user ID")
            @RequestParam(required = false) UUID userId,

            @Parameter(description = "Filter by action type")
            @RequestParam(required = false) AuditAction action,

            @Parameter(description = "Filter by entity type")
            @RequestParam(required = false) AuditEntityType entityType,

            @Parameter(description = "Filter by entity ID")
            @RequestParam(required = false) UUID entityId,

            @Parameter(description = "Filter by status (SUCCESS, FAILURE, ERROR)")
            @RequestParam(required = false) String status,

            @Parameter(description = "Filter by start date (ISO format: yyyy-MM-dd'T'HH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

            @Parameter(description = "Filter by end date (ISO format: yyyy-MM-dd'T'HH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size
    ) {
        log.info("Admin requesting audit logs: page={}, size={}, userId={}, action={}, entityType={}, entityId={}, status={}, startDate={}, endDate={}",
                page, size, userId, action, entityType, entityId, status, startDate, endDate);

        Pageable pageable = PageRequest.of(page, size);

        Page<AuditLog> auditLogs;

        // If any filter is provided, use the filtered query
        if (userId != null || action != null || entityType != null || entityId != null ||
                status != null || startDate != null || endDate != null) {
            auditLogs = auditLogService.getAuditLogsWithFilters(
                    userId, action, entityType, entityId, status, startDate, endDate, pageable
            );
        } else {
            auditLogs = auditLogService.getAuditLogs(pageable);
        }

        Page<AuditLogDTO> dtos = auditLogs.map(AuditLogDTO::fromEntity);

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/actions")
    @Operation(
            summary = "Get available audit actions",
            description = "Admin: Returns list of all available audit action types"
    )
    public ResponseEntity<AuditAction[]> getAuditActions() {
        return ResponseEntity.ok(AuditAction.values());
    }

    @GetMapping("/entity-types")
    @Operation(
            summary = "Get available entity types",
            description = "Admin: Returns list of all available entity types"
    )
    public ResponseEntity<AuditEntityType[]> getEntityTypes() {
        return ResponseEntity.ok(AuditEntityType.values());
    }
}