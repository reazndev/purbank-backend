package ch.purbank.core.repository;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.Payment;
import ch.purbank.core.domain.enums.PaymentExecutionType;
import ch.purbank.core.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByStatusOrderByExecutionDateAsc(PaymentStatus status);

    List<Payment> findByKontoAndStatus(Konto konto, PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.executionType = :type AND p.executionDate <= :date")
    List<Payment> findPaymentsDueForExecution(
            @Param("status") PaymentStatus status,
            @Param("type") PaymentExecutionType type,
            @Param("date") LocalDate date);
}