package com.aishiftplanner.scheduler.auth.infrastructure;

import com.aishiftplanner.scheduler.auth.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Login lookup. Email is unique platform-wide so the tenant is derived from the row. */
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<User> findAllByOrganizationIdOrderByLastNameAscFirstNameAsc(UUID organizationId);

    boolean existsByEmailIgnoreCase(String email);
}
