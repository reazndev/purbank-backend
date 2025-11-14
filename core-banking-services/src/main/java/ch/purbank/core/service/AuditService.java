package ch.purbank.core.service;

import ch.purbank.core.domain.Audit;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    Audit createAudit(String action, @Nullable UUID userId, @Nullable String ipAddress, @Nullable String userAgent);

    Audit viewAuditById(UUID auditId);

    List<Audit> viewAudits();
}
