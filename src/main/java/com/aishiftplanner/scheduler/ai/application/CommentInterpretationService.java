package com.aishiftplanner.scheduler.ai.application;

import com.aishiftplanner.scheduler.ai.domain.AiUnavailableException;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import com.aishiftplanner.scheduler.ai.domain.PromptSafety;
import com.aishiftplanner.scheduler.ai.infrastructure.AiProperties;
import com.aishiftplanner.scheduler.availability.domain.AvailabilityType;
import com.aishiftplanner.scheduler.availability.domain.CommentInterpretation;
import com.aishiftplanner.scheduler.availability.domain.EmployeeComment;
import com.aishiftplanner.scheduler.availability.infrastructure.CommentInterpretationRepository;
import com.aishiftplanner.scheduler.availability.infrastructure.EmployeeCommentRepository;
import com.aishiftplanner.scheduler.planning.application.PlanningPeriodService;
import com.aishiftplanner.scheduler.planning.domain.PlanningPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extracts structured availability information from free-text employee comments.
 *
 * <p>Example: <em>"Samstag kann ich arbeiten, aber bitte erst ab 17 Uhr, weil ich vorher Uni
 * habe."</em> becomes a dated, typed, time-bounded interpretation with a confidence score.
 *
 * <p>Three rules shape this class, and all three exist because a language model's output is a
 * suggestion, not a fact:
 *
 * <ol>
 *   <li><b>The original text is never modified or replaced.</b> Interpretations are stored
 *       alongside it.
 *   <li><b>Anything the model says outside the expected shape is discarded, not guessed at.</b>
 *       An unparseable date is dropped; it does not become "probably next Saturday".
 *   <li><b>Nothing becomes a hard constraint automatically.</b> Confidence gates whether an
 *       interpretation is applied as a soft hint; a human accepting it is what makes it
 *       binding (see {@code CommentInterpretation.isEnforceableAsHardConstraint}).
 * </ol>
 */
@Service
public class CommentInterpretationService {

    private static final Logger log = LoggerFactory.getLogger(CommentInterpretationService.class);

    private static final String JSON_SHAPE =
            """
            {
              "date": "YYYY-MM-DD or null if the comment names no specific date",
              "availability": "AVAILABLE | PREFERRED | UNAVAILABLE | null",
              "preferredStart": "HH:MM or null",
              "preferredEnd": "HH:MM or null",
              "hardConstraint": true if the employee says they CANNOT work, false if it is a wish,
              "confidence": a number between 0 and 1,
              "interpretation": "one short sentence restating what the employee meant, in their language"
            }
            """;

    private final LocalAiClient aiClient;
    private final EmployeeCommentRepository commentRepository;
    private final CommentInterpretationRepository interpretationRepository;
    private final PlanningPeriodService planningPeriodService;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public CommentInterpretationService(
            LocalAiClient aiClient,
            EmployeeCommentRepository commentRepository,
            CommentInterpretationRepository interpretationRepository,
            PlanningPeriodService planningPeriodService,
            ObjectMapper objectMapper,
            AiProperties properties) {
        this.aiClient = aiClient;
        this.commentRepository = commentRepository;
        this.interpretationRepository = interpretationRepository;
        this.planningPeriodService = planningPeriodService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Interprets every comment of a planning period that has not been interpreted yet.
     *
     * <p>Comments that fail are skipped rather than aborting the batch: one unparseable
     * comment must not stop a manager from getting the other thirty.
     */
    @Transactional
    public List<CommentInterpretation> interpretPending(UUID planningPeriodId) {
        PlanningPeriod period = planningPeriodService.loadInTenant(planningPeriodId);
        List<EmployeeComment> comments =
                commentRepository.findAllByPlanningPeriodIdOrderByCreatedAtDesc(planningPeriodId);

        List<CommentInterpretation> created = new ArrayList<>();
        for (EmployeeComment comment : comments) {
            if (!interpretationRepository.findAllByCommentId(comment.getId()).isEmpty()) {
                continue;
            }
            try {
                interpret(comment, period).ifPresent(created::add);
            } catch (AiUnavailableException ex) {
                // Stop the batch, but keep what already succeeded: retrying later will pick
                // up exactly the comments that were missed, because interpreted ones are
                // skipped above.
                log.warn("Stopping interpretation batch for period {}: {}", planningPeriodId, ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                log.warn("Skipping comment {} after an interpretation failure", comment.getId(), ex);
            }
        }
        return created;
    }

    /** Interprets one comment. Returns empty if the model's answer was not usable. */
    @Transactional
    public Optional<CommentInterpretation> interpret(EmployeeComment comment, PlanningPeriod period) {
        String systemPrompt = buildSystemPrompt(period);
        String raw = aiClient.completeJson(systemPrompt, PromptSafety.fence(comment.getOriginalText()), JSON_SHAPE);

        JsonNode node;
        try {
            node = objectMapper.readTree(stripCodeFence(raw));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Model returned unparseable JSON for comment {}", comment.getId());
            return Optional.empty();
        }

        BigDecimal confidence = parseConfidence(node.path("confidence"));
        String interpretationText = node.path("interpretation").asText("").trim();
        if (interpretationText.isEmpty()) {
            return Optional.empty();
        }

        CommentInterpretation interpretation = new CommentInterpretation(
                comment.getOrganizationId(),
                comment.getId(),
                interpretationText,
                confidence,
                CommentInterpretation.Source.LOCAL_LLM);

        // Each field is applied only if it parses AND makes sense for this period. A date
        // outside the planning period is far more likely to be a model error than a genuine
        // statement about another week, so it is dropped rather than stored.
        parseDate(node.path("date")).filter(period::covers).ifPresent(interpretation::setInterpretedDate);
        parseAvailabilityType(node.path("availability")).ifPresent(interpretation::setAvailabilityType);
        parseTime(node.path("preferredStart")).ifPresent(interpretation::setPreferredStartTime);
        parseTime(node.path("preferredEnd")).ifPresent(interpretation::setPreferredEndTime);

        boolean claimsHard = node.path("hardConstraint").asBoolean(false);
        interpretation.setHardConstraint(claimsHard);

        // Review status: anything claiming to be a hard constraint always goes to a human,
        // regardless of confidence. Everything below the configured threshold does too.
        boolean confidentEnough =
                confidence.doubleValue() >= properties.minimumConfidenceForAutoApply();
        interpretation.setReviewStatus(CommentInterpretation.ReviewStatus.PENDING);
        if (confidentEnough && !claimsHard) {
            // Still PENDING for the audit trail, but flagged as usable as a soft hint. It is
            // never silently promoted to a rule the solver must obey.
            log.debug(
                    "Interpretation for comment {} is confident ({}) and soft; usable without review",
                    comment.getId(),
                    confidence);
        }

        return Optional.of(interpretationRepository.save(interpretation));
    }

    private String buildSystemPrompt(PlanningPeriod period) {
        return """
               You extract scheduling information from short messages written by restaurant \
               staff about when they can work. The messages may be in German or English.

               The planning period runs from %s to %s. Only dates inside that range are \
               meaningful; if the message names a weekday, map it to the matching date in \
               that range.

               Set hardConstraint to true only when the person states they CANNOT work \
               (Ich kann nicht, geht nicht, bin im Urlaub). A wish or preference \
               (am liebsten, bitte eher, würde gern) is not a hard constraint.

               Set confidence honestly. If the message is vague, ambiguous, or you had to \
               guess at the date or time, use a low value. A low confidence is far more \
               useful than a confident guess, because a human reviews everything uncertain.

               %s
               """
                .formatted(period.getStartDate(), period.getEndDate(), PromptSafety.SYSTEM_GUARD);
    }

    /**
     * Strips markdown code fences some models wrap JSON in despite being told not to.
     *
     * <p>Cheap to do and it recovers an otherwise perfectly good answer; the alternative is
     * discarding a correct interpretation over three backticks.
     */
    private static String stripCodeFence(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static BigDecimal parseConfidence(JsonNode node) {
        if (!node.isNumber()) {
            // No confidence stated means no confidence earned. Defaulting high here would
            // let a malformed answer bypass review entirely.
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        double value = Math.max(0.0, Math.min(1.0, node.asDouble()));
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static Optional<LocalDate> parseDate(JsonNode node) {
        String text = node.asText(null);
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalTime> parseTime(JsonNode node) {
        String text = node.asText(null);
        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(text));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static Optional<AvailabilityType> parseAvailabilityType(JsonNode node) {
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AvailabilityType.valueOf(text.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
