package com.aishiftplanner.scheduler.employee.application;

import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.CreateSkillRequest;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.SkillResponse;
import com.aishiftplanner.scheduler.employee.domain.Skill;
import com.aishiftplanner.scheduler.employee.infrastructure.SkillRepository;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final CurrentUserProvider currentUser;

    public SkillService(SkillRepository skillRepository, CurrentUserProvider currentUser) {
        this.skillRepository = skillRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list() {
        return skillRepository
                .findAllByOrganizationIdOrderByCodeAsc(currentUser.requireOrganizationId())
                .stream()
                .map(SkillService::toResponse)
                .toList();
    }

    @Transactional
    public SkillResponse create(CreateSkillRequest request) {
        UUID organizationId = currentUser.requireOrganizationId();
        if (skillRepository.existsByOrganizationIdAndCode(organizationId, request.code())) {
            throw ApiException.conflict(ErrorCode.ALREADY_EXISTS, "A skill with this code already exists.");
        }
        Skill skill = new Skill(organizationId, request.code(), request.name());
        skill.setDescription(request.description());
        return toResponse(skillRepository.save(skill));
    }

    /**
     * Verifies that every id in {@code skillIds} names a skill in the caller's organization.
     *
     * <p>Without this check, a client could attach another tenant's skill id to one of its
     * own employees — harmless-looking, but it would let the solver satisfy a skill
     * requirement with a qualification that does not exist in this organization, and it
     * would leak the existence of foreign ids.
     */
    @Transactional(readOnly = true)
    public void requireAllExistInTenant(Collection<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return;
        }
        Set<UUID> requested = Set.copyOf(skillIds);
        long found = skillRepository
                .findAllByOrganizationIdAndIdIn(currentUser.requireOrganizationId(), requested)
                .size();
        if (found != requested.size()) {
            throw ApiException.badRequest(
                    ErrorCode.VALIDATION_FAILED, "One or more skill ids are unknown in this organization.");
        }
    }

    static SkillResponse toResponse(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getOrganizationId(),
                skill.getCode(),
                skill.getName(),
                skill.getDescription(),
                skill.isActive());
    }
}
