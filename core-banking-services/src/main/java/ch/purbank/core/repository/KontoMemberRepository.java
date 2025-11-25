package ch.purbank.core.repository;

import ch.purbank.core.domain.Konto;
import ch.purbank.core.domain.KontoMember;
import ch.purbank.core.domain.User;
import ch.purbank.core.domain.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KontoMemberRepository extends JpaRepository<KontoMember, UUID> {

    List<KontoMember> findByUser(User user);

    List<KontoMember> findByKonto(Konto konto);

    Optional<KontoMember> findByKontoAndUser(Konto konto, User user);

    boolean existsByKontoAndUser(Konto konto, User user);

    @Query("SELECT COUNT(km) FROM KontoMember km WHERE km.konto = :konto AND km.role = :role")
    long countByKontoAndRole(@Param("konto") Konto konto, @Param("role") MemberRole role);
}