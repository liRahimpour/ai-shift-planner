package com.aishiftplanner.scheduler.ai.api;

import com.aishiftplanner.scheduler.ai.application.CommentInterpretationService;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.InterpretationResponse;
import com.aishiftplanner.scheduler.availability.application.EmployeeCommentService;
import com.aishiftplanner.scheduler.chat.application.ScheduleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
@Tag(name = "AI", description = "Comment interpretation, replacement search and AI availability")
public class AiController {

    private final CommentInterpretationService interpretationService;
    private final ScheduleQueryService queryService;
    private final LocalAiClient localAiClient;

    public AiController(
            CommentInterpretationService interpretationService,
            ScheduleQueryService queryService,
            LocalAiClient localAiClient) {
        this.interpretationService = interpretationService;
        this.queryService = queryService;
        this.localAiClient = localAiClient;
    }

    @GetMapping("/status")
    @Operation(
            summary = "Whether AI features are currently usable",
            description = "Core scheduling works regardless of what this returns.")
    public AiStatusResponse status() {
        boolean available = localAiClient.isAvailable();
        return new AiStatusResponse(
                available,
                available ? "AVAILABLE" : "AI_TEMPORARILY_UNAVAILABLE",
                "Availability, planning, editing and publishing are unaffected by AI availability.");
    }

    public record AiStatusResponse(boolean available, String state, String note) {
    }

    @PostMapping("/planning-periods/{planningPeriodId}/interpret-comments")
    @Operation(
            summary = "Interpret the not-yet-interpreted comments of a planning period",
            description = "Results land in the review queue. Nothing becomes a hard planning "
                    + "constraint without a manager accepting it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interpreted (possibly zero comments)"),
        @ApiResponse(responseCode = "503", description = "The local AI service is unavailable")
    })
    public List<InterpretationResponse> interpretComments(@PathVariable UUID planningPeriodId) {
        return interpretationService.interpretPending(planningPeriodId).stream()
                .map(EmployeeCommentService::toResponse)
                .toList();
    }

    /**
     * The replacement assistant. Deliberately not an AI endpoint despite living here: the
     * ranking is deterministic and computed from the database, so it keeps working when the
     * model is down — which is exactly when someone is most likely to be calling in sick and
     * needing an answer.
     */
    @GetMapping("/planning-periods/{planningPeriodId}/shifts/{shiftId}/replacements")
    @Operation(
            summary = "Ranked replacement candidates for a shift",
            description = "Deterministic: same inputs, same ranking, every time. Each candidate "
                    + "comes with the facts behind their position. Works without the AI service.")
    public List<ScheduleQueryService.ReplacementCandidate> replacements(
            @PathVariable UUID planningPeriodId, @PathVariable UUID shiftId) {
        return queryService.findReplacements(planningPeriodId, shiftId);
    }
}
