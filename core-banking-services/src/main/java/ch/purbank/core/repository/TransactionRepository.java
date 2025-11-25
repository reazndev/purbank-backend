package ch.purbank.core.repository;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByKontoOrderByTimestampDesc(Konto konto, Pageable pageable);

    List<Transaction> findByKontoOrderByTimestampDesc(Konto konto);
}