package ch.purbank.core.repository;

import ch.purbank.core.domain.AuthorisationRequest;
import ch.purbank.core.domain.enums.AuthorisationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorisationRequestRepository extends JpaRepository<AuthorisationRequest, UUID> {
    Optional<AuthorisationRequest> findByMobileVerifyCode(String mobileVerifyCode);

    Optional<AuthorisationRequest> findByUserIdAndStatus(UUID userId, AuthorisationStatus status);

    Optional<AuthorisationRequest> findByMobileVerifyCodeAndDeviceIdAndStatus(
            String mobileVerifyCode,
            String deviceId,
            AuthorisationStatus status);
}