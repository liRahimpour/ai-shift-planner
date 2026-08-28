package com.aishiftplanner.scheduler.availability.infrastructure;

import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentInterpretationRepository extends JpaRepository<CommentInterpretation, UUID> {

    Optional<CommentInterpretation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<CommentInterpretation> findAllByCommentId(UUID commentId);

    /** The manager's review queue: everything the model was not confident enough about. */
    List<CommentInterpretation> findAllByOrganizationIdAndReviewStatusOrderByConfidenceAsc(
            UUID organizationId, CommentInterpretation.ReviewStatus reviewStatus);
}
