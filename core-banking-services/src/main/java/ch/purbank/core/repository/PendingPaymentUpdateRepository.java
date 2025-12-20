package ch.purbank.core.repository;

import ch.purbank.core.domain.PendingPaymentUpdate;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingPaymentUpdateRepository extends JpaRepository<PendingPaymentUpdate, UUID> {

    Optional<PendingPaymentUpdate> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<PendingPaymentUpdate> findByMobileVerifyCodeAndStatus(String mobileVerifyCode, PendingPaymentStatus status);

    List<PendingPaymentUpdate> findByUserAndStatus(User user, PendingPaymentStatus status);
}