package ch.purbank.core.repository;

import ch.purbank.core.domain.MobileDevice;
import ch.purbank.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MobileDeviceRepository extends JpaRepository<MobileDevice, UUID> {
    List<MobileDevice> findByUserAndStatus(User user, String status);

    Optional<MobileDevice> findByPublicKey(String publicKey);

    boolean existsByPublicKey(String publicKey);
}