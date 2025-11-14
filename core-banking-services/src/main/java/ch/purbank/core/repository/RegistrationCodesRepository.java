package ch.purbank.core.repository;

import ch.purbank.core.domain.RegistrationCodes;
import ch.purbank.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationCodesRepository extends JpaRepository<RegistrationCodes, UUID> {
    Optional<RegistrationCodes> findByRegistrationCodeAndStatus(String registrationCode, String status);

    List<RegistrationCodes> findByUserAndStatus(User user, String status);

    boolean existsByRegistrationCodeAndStatus(String registrationCode, String status);
}