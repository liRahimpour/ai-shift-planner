package com.aishiftplanner.scheduler.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentInterpretationTest {

    private static CommentInterpretation interpretation(String confidence, boolean hard) {
        CommentInterpretation result = new CommentInterpretation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Wants to start no earlier than 17:00 on Saturday",
                new BigDecimal(confidence),
                CommentInterpretation.Source.LOCAL_LLM);
        result.setHardConstraint(hard);
        return result;
    }

    @Test
    void highConfidenceMayBeAppliedWithoutReview() {
        assertThat(interpretation("0.92", false).isConfidentEnoughToApplyAutomatically()).isTrue();
        assertThat(interpretation("0.85", false).isConfidentEnoughToApplyAutomatically()).isTrue();
    }

    @Test
    void lowConfidenceMustBeReviewed() {
        assertThat(interpretation("0.84", false).isConfidentEnoughToApplyAutomatically()).isFalse();
        assertThat(interpretation("0.30", false).isConfidentEnoughToApplyAutomatically()).isFalse();
    }

    @Test
    void aConfidentInterpretationIsStillNotAHardConstraintUntilAHumanAcceptsIt() {
        // The central safety property of the AI layer: a model's own certainty is never
        // sufficient authority to make a rule inviolable. Without this, a confidently wrong
        // reading of "ich kann Samstag nicht vor 17 Uhr" could make a week unsolvable, and
        // nobody could point at the human who decided it.
        CommentInterpretation confident = interpretation("0.99", true);

        assertThat(confident.isEnforceableAsHardConstraint()).isFalse();

        confident.accept(UUID.randomUUID());

        assertThat(confident.isEnforceableAsHardConstraint()).isTrue();
    }

    @Test
    void rejectedInterpretationsNeverBecomeHardConstraints() {
        CommentInterpretation rejected = interpretation("0.99", true);
        rejected.reject(UUID.randomUUID());

        assertThat(rejected.isEnforceableAsHardConstraint()).isFalse();
        assertThat(rejected.getReviewStatus()).isEqualTo(CommentInterpretation.ReviewStatus.REJECTED);
    }

    @Test
    void acceptanceRecordsWhoDecided() {
        CommentInterpretation subject = interpretation("0.50", false);
        UUID reviewer = UUID.randomUUID();

        subject.accept(reviewer);

        assertThat(subject.getReviewedBy()).isEqualTo(reviewer);
    }
}
