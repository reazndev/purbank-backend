package ch.purbank.core.repository;

import ch.purbank.core.domain.Konto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KontoRepository extends JpaRepository<Konto, UUID> {
    Optional<Konto> findByIban(String iban);
}