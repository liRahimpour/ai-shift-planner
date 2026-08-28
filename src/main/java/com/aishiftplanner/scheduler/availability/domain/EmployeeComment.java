package com.aishiftplanner.scheduler.availability.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Free text an employee wrote alongside their availability, e.g.
 * <em>"Samstag kann ich arbeiten, aber bitte erst ab 17 Uhr, weil ich vorher Uni habe."</em>
 *
 * <p>The original text is stored verbatim and never overwritten by an interpretation. Two
 * reasons: it is the authoritative record of what the person actually said (a manager must
 * be able to read it), and any AI-extracted structure is a derived artefact whose confidence
 * can be wrong — so it lives in {@link CommentInterpretation}, next to the source, rather
 * than replacing it.
 *
 * <p>Security note: comment text is <b>untrusted input</b>. It is passed to the local LLM as
 * data inside a clearly delimited block, never as instructions, and no tool permission is
 * ever derived from its content (see ADR-003 and the chat module's authorization).
 */
@Entity
@Table(name = "employee_comments")
public class EmployeeComment extends TenantScopedEntity {

    @Column(name = "planning_period_id", nullable = false)
    private UUID planningPeriodId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "original_text", nullable = false, columnDefinition = "text")
    private String originalText;

    protected EmployeeComment() {
        // for JPA
    }

    public EmployeeComment(UUID organizationId, UUID planningPeriodId, UUID employeeId, String originalText) {
        setOrganizationId(organizationId);
        this.planningPeriodId = planningPeriodId;
        this.employeeId = employeeId;
        this.originalText = originalText;
    }

    public UUID getPlanningPeriodId() {
        return planningPeriodId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }
}
