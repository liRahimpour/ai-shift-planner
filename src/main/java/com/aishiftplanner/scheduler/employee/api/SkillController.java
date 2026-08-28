package com.aishiftplanner.scheduler.employee.api;

import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.CreateSkillRequest;
import com.aishiftplanner.scheduler.employee.api.EmployeeDtos.SkillResponse;
import com.aishiftplanner.scheduler.employee.application.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "Skills", description = "Extensible qualification catalogue per organization")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @Operation(summary = "List the organization's skill catalogue")
    public List<SkillResponse> list() {
        return skillService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Add a skill to the catalogue")
    public SkillResponse create(@Valid @RequestBody CreateSkillRequest request) {
        return skillService.create(request);
    }
}
