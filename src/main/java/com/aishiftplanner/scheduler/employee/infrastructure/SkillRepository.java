package com.aishiftplanner.scheduler.employee.infrastructure;

import com.aishiftplanner.scheduler.employee.domain.Skill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Skill> findByOrganizationIdAndCode(UUID organizationId, String code);

    List<Skill> findAllByOrganizationIdOrderByCodeAsc(UUID organizationId);

    List<Skill> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
