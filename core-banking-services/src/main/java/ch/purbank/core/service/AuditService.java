package ch.purbank.core.service;

import ch.purbank.core.domain.Audit;
import ch.purbank.core.repository.AuditRepository;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    Audit createAudit(String action, UUID userId);
}
