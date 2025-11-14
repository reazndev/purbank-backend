package ch.purbank.core.service.impl;

import ch.purbank.core.domain.Audit;
import ch.purbank.core.exception.AuditNotFoundException;
import ch.purbank.core.repository.AuditRepository;
import ch.purbank.core.service.AuditService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;
import ch.purbank.core.exception.AuditNotFoundException;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;

    @Override
    @SuppressWarnings("null")
    public Audit createAudit(String action, @Nullable UUID userId, @Nullable String ipAddress,
            @Nullable String userAgent) {
        // Very basic here right now
        // TODO: call stack trace

        Audit audit = Audit.builder()
                .action(action)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return auditRepository.save(audit);
    }

    @Override
    @SuppressWarnings("null")
    public Audit viewAuditById(UUID auditId) {

        return auditRepository.findById(auditId)
                .orElseThrow(() -> new AuditNotFoundException(auditId));

    }

    @Override
    public List<Audit> viewAudits() {
        // TODO: Here we should probably allow to specify the number of Audits to return
        // and
        // the start position.
        return auditRepository.findAll();
    }
}
