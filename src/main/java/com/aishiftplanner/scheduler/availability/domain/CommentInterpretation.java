package com.aishiftplanner.scheduler.availability.domain;

import com.aishiftplanner.scheduler.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Structured data extracted from an {@link EmployeeComment}.
 *
 * <p>Every interpretation carries {@code confidence}, {@code source}, a human-readable
 * {@code interpretation} and whether it should count as a {@code hardConstraint} — the four
 * things a manager needs to decide whether to trust it.
 *
 * <p>The rule that shapes this class: <b>an uncertain interpretation never becomes a hard
 * rule on its own.</b> A model that is 60% sure someone meant "not before 17:00" must not be
 * able to make that a constraint the solver treats as inviolable; that is how a schedule
 * ends up unsolvable for reasons nobody can trace back to a human decision. Below the
 * confidence threshold, an interpretation stays {@code PENDING} and shows up in the
 * manager's review queue instead.
 */
@Entity
@Table(name = "comment_interpretations")
public class CommentInterpretation extends TenantScopedEntity {

    /**
     * Interpretations at or above this confidence may be applied as soft planning hints
     * without review. Nothing is ever promoted to a hard constraint automatically.
     */
    public static final BigDecimal AUTO_APPLY_CONFIDENCE_THRESHOLD = new BigDecimal("0.85");

    public enum Source {
        /** Produced by the local LLM. */
        LOCAL_LLM,
        /** Entered or corrected by a human. */
        MANUAL,
        /** Produced by deterministic parsing, no model involved. */
        RULE_BASED
    }

    public enum ReviewStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    @Column(name = "comment_id", nullable = false)
    private UUID commentId;

    @Column(name = "interpreted_date")
    private LocalDate interpretedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_type", length = 20)
    private AvailabilityType availabilityType;

    @Column(name = "preferred_start_time")
    private LocalTime preferredStartTime;

    @Column(name = "preferred_end_time")
    private LocalTime preferredEndTime;

    @Column(name = "hard_constraint", nullable = false)
    private boolean hardConstraint;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private Source source = Source.LOCAL_LLM;

    /** Plain-language restatement, shown to the manager during review. */
    @Column(name = "interpretation", nullable = false, columnDefinition = "text")
    private String interpretation;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    protected CommentInterpretation() {
        // for JPA
    }

    public CommentInterpretation(
            UUID organizationId, UUID commentId, String interpretation, BigDecimal confidence, Source source) {
        setOrganizationId(organizationId);
        this.commentId = commentId;
        this.interpretation = interpretation;
        this.confidence = confidence;
        this.source = source;
    }

    /** True if this interpretation is confident enough to be used without manager review. */
    public boolean isConfidentEnoughToApplyAutomatically() {
        return confidence.compareTo(AUTO_APPLY_CONFIDENCE_THRESHOLD) >= 0;
    }

    /**
     * Whether the solver may treat this as a hard constraint.
     *
     * <p>Requires both that the interpretation claims to be hard <em>and</em> that a human
     * accepted it. A model's own certainty is not sufficient authority to make a rule
     * inviolable.
     */
    public boolean isEnforceableAsHardConstraint() {
        return hardConstraint && reviewStatus == ReviewStatus.ACCEPTED;
    }

    public void accept(UUID reviewerUserId) {
        this.reviewStatus = ReviewStatus.ACCEPTED;
        this.reviewedBy = reviewerUserId;
    }

    public void reject(UUID reviewerUserId) {
        this.reviewStatus = ReviewStatus.REJECTED;
        this.reviewedBy = reviewerUserId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public LocalDate getInterpretedDate() {
        return interpretedDate;
    }

    public void setInterpretedDate(LocalDate interpretedDate) {
        this.interpretedDate = interpretedDate;
    }

    public AvailabilityType getAvailabilityType() {
        return availabilityType;
    }

    public void setAvailabilityType(AvailabilityType availabilityType) {
        this.availabilityType = availabilityType;
    }

    public LocalTime getPreferredStartTime() {
        return preferredStartTime;
    }

    public void setPreferredStartTime(LocalTime preferredStartTime) {
        this.preferredStartTime = preferredStartTime;
    }

    public LocalTime getPreferredEndTime() {
        return preferredEndTime;
    }

    public void setPreferredEndTime(LocalTime preferredEndTime) {
        this.preferredEndTime = preferredEndTime;
    }

    public boolean isHardConstraint() {
        return hardConstraint;
    }

    public void setHardConstraint(boolean hardConstraint) {
        this.hardConstraint = hardConstraint;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }
}
