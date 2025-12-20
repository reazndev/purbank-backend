package ch.purbank.core.repository;

import ch.purbank.core.domain.PendingKontoDelete;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingKontoDeleteRepository extends JpaRepository<PendingKontoDelete, UUID> {

    Optional<PendingKontoDelete> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<PendingKontoDelete> findByMobileVerifyCodeAndStatus(String mobileVerifyCode, PendingPaymentStatus status);

    List<PendingKontoDelete> findByUserAndStatus(User user, PendingPaymentStatus status);
}