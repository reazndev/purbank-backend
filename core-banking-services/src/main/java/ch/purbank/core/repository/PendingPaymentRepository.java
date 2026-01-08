package ch.purbank.core.repository;

import ch.purbank.core.domain.PendingPayment;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingPaymentRepository extends JpaRepository<PendingPayment, UUID> {

    Optional<PendingPayment> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<PendingPayment> findByMobileVerifyCodeAndStatus(String mobileVerifyCode, PendingPaymentStatus status);

    List<PendingPayment> findByUserAndStatus(User user, PendingPaymentStatus status);

    List<PendingPayment> findByStatusAndExpiresAtBefore(PendingPaymentStatus status, LocalDateTime expiresAt);

    @Query("SELECT pp FROM PendingPayment pp WHERE pp.status = :status AND pp.expiresAt < :now")
    List<PendingPayment> findExpiredPendingPayments(@Param("status") PendingPaymentStatus status, @Param("now") LocalDateTime now);
}