package com.aishiftplanner.scheduler.availability.api;

import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.InterpretationResponse;
import com.aishiftplanner.scheduler.availability.api.AvailabilityDtos.ReviewInterpretationRequest;
import com.aishiftplanner.scheduler.availability.application.EmployeeCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comment-interpretations")
@PreAuthorize("hasAnyRole('SHIFT_MANAGER', 'LOCATION_MANAGER', 'ORG_ADMIN', 'SUPER_ADMIN')")
@Tag(
        name = "Comment interpretations",
        description = "Manager review of AI-extracted structure from employee comments")
public class InterpretationController {

    private final EmployeeCommentService commentService;

    public InterpretationController(EmployeeCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/pending")
    @Operation(summary = "Interpretations awaiting review, least confident first")
    public List<InterpretationResponse> pending() {
        return commentService.pendingReviews();
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Accept or reject an interpretation; only accepted ones can act as hard constraints")
    public InterpretationResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewInterpretationRequest request) {
        return commentService.review(id, request.accept());
    }
}
