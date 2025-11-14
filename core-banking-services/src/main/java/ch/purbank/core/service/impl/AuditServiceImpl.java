package ch.purbank.core.service.impl;

import ch.purbank.core.domain.Audit;
import ch.purbank.core.repository.AuditRepository;
import ch.purbank.core.service.AuditService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;

    @Override
    public Audit createAudit(String action, UUID userId) {
        Audit audit = new Audit();
        audit.setAction(action);
        audit.setUserId(userId);

        return auditRepository.save(audit);
    }
}
