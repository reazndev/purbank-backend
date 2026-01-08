package ch.purbank.core.repository;

import ch.purbank.core.domain.PendingPaymentDelete;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingPaymentDeleteRepository extends JpaRepository<PendingPaymentDelete, UUID> {

    Optional<PendingPaymentDelete> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<PendingPaymentDelete> findByMobileVerifyCodeAndStatus(String mobileVerifyCode, PendingPaymentStatus status);

    List<PendingPaymentDelete> findByUserAndStatus(User user, PendingPaymentStatus status);
}