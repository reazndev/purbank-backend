package ch.purbank.core.repository;

import ch.purbank.core.domain.PendingMemberInvite;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.PendingPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingMemberInviteRepository extends JpaRepository<PendingMemberInvite, UUID> {

    Optional<PendingMemberInvite> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<PendingMemberInvite> findByMobileVerifyCodeAndStatus(String mobileVerifyCode, PendingPaymentStatus status);

    List<PendingMemberInvite> findByUserAndStatus(User user, PendingPaymentStatus status);
}