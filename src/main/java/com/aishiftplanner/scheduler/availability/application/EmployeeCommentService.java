package com.aishiftplanner.scheduler.availability.application;

import com.aishiftplanner.scheduler.audit.application.AuditService;
import com.aishiftplanner.scheduler.audit.domain.AuditAction;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.CommentResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.InterpretationResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.SubmitCommentRequest;
import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import com.aishiftplanner.scheduler.availability.domain.EmployeeComment;
import com.aishiftplanner.scheduler.availability.infrastructure.CommentInterpretationRepository;
import com.aishiftplanner.scheduler.availability.infrastructure.EmployeeCommentRepository;
import com.aishiftplanner.scheduler.employee.application.EmployeeService;
import com.aishiftplanner.scheduler.employee.domain.Employee;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.aishiftplanner.scheduler.shared.api.ApiException;
import com.aishiftplanner.scheduler.shared.api.ErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores employee comments and exposes their AI interpretations for manager review.
 *
 * <p>Note what this service does <em>not</em> do: it never calls the LLM itself. Comment
 * capture must keep working when Ollama is down, so interpretation is a separate, optional
 * step (see {@code ai} module) that reads comments after the fact.
 */
@Service
public class EmployeeCommentService {

    private final EmployeeCommentRepository commentRepository;
    private final CommentInterpretationRepository interpretationRepository;
    private final PlanningPeriodService planningPeriodService;
    private final EmployeeService employeeService;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public EmployeeCommentService(
            EmployeeCommentRepository commentRepository,
            CommentInterpretationRepository interpretationRepository,
            PlanningPeriodService planningPeriodService,
            EmployeeService employeeService,
            CurrentUserProvider currentUser,
            AuditService auditService,
            Clock clock) {
        this.commentRepository = commentRepository;
        this.interpretationRepository = interpretationRepository;
        this.planningPeriodService = planningPeriodService;
        this.employeeService = employeeService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CommentResponse submitOwn(UUID planningPeriodId, SubmitCommentRequest request) {
        Employee employee = employeeService.requireOwnEmployeeRecord();
        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);

        if (!period.acceptsAvailabilityAt(clock.instant())) {
            throw new ApiException(
                    ErrorCode.AVAILABILITY_DEADLINE_PASSED,
                    HttpStatus.CONFLICT,
                    "The availability deadline for this planning period has passed.");
        }

        EmployeeComment comment = new EmployeeComment(
                period.getOrganizationId(), planningPeriodId, employee.getId(), request.text().trim());
        EmployeeComment saved = commentRepository.save(comment);

        auditService.record(
                AuditAction.COMMENT_SUBMITTED,
                "EmployeeComment",
                saved.getId(),
                Map.of(
                        "planningPeriodId", planningPeriodId.toString(),
                        "employeeId", employee.getId().toString(),
                        // The text itself is not copied into the audit metadata: it is
                        // already stored verbatim on the comment, and duplicating personal
                        // free text into an append-only log complicates deletion requests.
                        "length", String.valueOf(saved.getOriginalText().length())));

        return toResponse(saved, interpretationRepository.findAllByCommentId(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listForPeriod(UUID planningPeriodId) {
        requireManager();
        planningPeriodService.loadInTenant(planningPeriodId);
        return commentRepository.findAllByPlanningPeriodIdOrderByCreatedAtDesc(planningPeriodId).stream()
                .map(c -> toResponse(c, interpretationRepository.findAllByCommentId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listOwn(UUID planningPeriodId) {
        Employee employee = employeeService.requireOwnEmployeeRecord();
        planningPeriodService.loadInTenant(planningPeriodId);
        return commentRepository
                .findAllByPlanningPeriodIdAndEmployeeIdOrderByCreatedAtDesc(planningPeriodId, employee.getId())
                .stream()
                .map(c -> toResponse(c, interpretationRepository.findAllByCommentId(c.getId())))
                .toList();
    }

    /** The manager's review queue: interpretations the model was not confident about. */
    @Transactional(readOnly = true)
    public List<InterpretationResponse> pendingReviews() {
        requireManager();
        return interpretationRepository
                .findAllByOrganizationIdAndReviewStatusOrderByConfidenceAsc(
                        currentUser.requireOrganizationId(), CommentInterpretation.ReviewStatus.PENDING)
                .stream()
                .map(EmployeeCommentService::toResponse)
                .toList();
    }

    /**
     * Accept or reject an AI interpretation.
     *
     * <p>This is the human decision that a machine interpretation is not allowed to make for
     * itself: only an accepted interpretation can ever become a hard constraint in the
     * solver (see {@code CommentInterpretation.isEnforceableAsHardConstraint}).
     */
    @Transactional
    public InterpretationResponse review(UUID interpretationId, boolean accept) {
        AuthenticatedUser caller = requireManager();
        CommentInterpretation interpretation = interpretationRepository
                .findByIdAndOrganizationId(interpretationId, caller.organizationId())
                .orElseThrow(() -> ApiException.notFound("Interpretation not found."));

        if (accept) {
            interpretation.accept(caller.userId());
        } else {
            interpretation.reject(caller.userId());
        }
        CommentInterpretation saved = interpretationRepository.save(interpretation);

        auditService.record(
                AuditAction.COMMENT_INTERPRETATION_REVIEWED,
                "CommentInterpretation",
                interpretationId,
                Map.of(
                        "decision", accept ? "ACCEPTED" : "REJECTED",
                        "confidence", saved.getConfidence().toPlainString(),
                        "hardConstraint", String.valueOf(saved.isHardConstraint())));

        return toResponse(saved);
    }

    private AuthenticatedUser requireManager() {
        AuthenticatedUser caller = currentUser.require();
        if (!caller.isManager()) {
            throw ApiException.forbidden("Only managers may perform this action.");
        }
        return caller;
    }

    public static CommentResponse toResponse(EmployeeComment comment, List<CommentInterpretation> interpretations) {
        return new CommentResponse(
                comment.getId(),
                comment.getPlanningPeriodId(),
                comment.getEmployeeId(),
                comment.getOriginalText(),
                comment.getCreatedAt(),
                interpretations.stream().map(EmployeeCommentService::toResponse).toList());
    }

    public static InterpretationResponse toResponse(CommentInterpretation interpretation) {
        return new InterpretationResponse(
                interpretation.getId(),
                interpretation.getCommentId(),
                interpretation.getInterpretedDate(),
                interpretation.getAvailabilityType(),
                interpretation.getPreferredStartTime(),
                interpretation.getPreferredEndTime(),
                interpretation.isHardConstraint(),
                interpretation.getConfidence(),
                interpretation.getSource(),
                interpretation.getInterpretation(),
                interpretation.getReviewStatus(),
                !interpretation.isConfidentEnoughToApplyAutomatically()
                        || interpretation.isHardConstraint());
    }
}
