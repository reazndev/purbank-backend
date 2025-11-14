package ch.purbank.core.repository;

import ch.purbank.core.domain.User;
import ch.purbank.core.domain.Audit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {
}