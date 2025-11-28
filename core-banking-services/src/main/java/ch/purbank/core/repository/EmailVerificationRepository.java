package ch.purbank.core.repository;

import ch.purbank.core.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailVerification e where e.emailVerifyToken = :token")
    Optional<EmailVerification> findByEmailVerifyTokenForUpdate(@Param("token") String token);

    Optional<EmailVerification> findByEmailVerifyToken(String emailVerifyToken);

    Optional<EmailVerification> findByCompleteToken(String completeToken);
}
