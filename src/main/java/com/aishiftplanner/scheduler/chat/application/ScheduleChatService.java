package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.ai.domain.AiChatTurn;
import com.aishiftplanner.scheduler.ai.domain.AiMessage;
import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import com.aishiftplanner.scheduler.ai.domain.PromptSafety;
import com.aishiftplanner.scheduler.ai.infrastructure.AiProperties;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import com.aishiftplanner.scheduler.auth.application.CurrentUserProvider;
import com.aishiftplanner.scheduler.chat.api.ChatDtos.ChatResponse;
import com.aishiftplanner.scheduler.chat.api.ChatDtos.ToolInvocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The chat loop: question → tool calls against the real database → grounded answer.
 *
 * <p>The loop is the security boundary. On every iteration the model may ask for a tool; the
 * registry decides whether this particular caller may use it; only then does anything run.
 * The model never sees a database, never receives a bulk export, and never obtains data by
 * asking nicely — the permission check does not consult it.
 *
 * <p>Every tool result is returned to the caller alongside the answer
 * ({@link ChatResponse#toolsUsed()}). That is not debugging output: it is what lets a manager
 * check an answer against the facts it came from, which is the difference between a tool they
 * can rely on and one they have to second-guess.
 */
@Service
public class ScheduleChatService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleChatService.class);

    private final LocalAiClient aiClient;
    private final ChatToolRegistry toolRegistry;
    private final CurrentUserProvider currentUser;
    private final AiProperties properties;

    public ScheduleChatService(
            LocalAiClient aiClient,
            ChatToolRegistry toolRegistry,
            CurrentUserProvider currentUser,
            AiProperties properties) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public ChatResponse ask(String question, String planningPeriodId) {
        AuthenticatedUser user = currentUser.require();
        List<AiToolSpec> permittedTools = toolRegistry.specsFor(user);

        List<AiMessage> conversation = new ArrayList<>();
        conversation.add(AiMessage.user(PromptSafety.fence(question)));

        List<ToolInvocation> toolsUsed = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(user, planningPeriodId);

        for (int iteration = 0; iteration < properties.maxToolCallsPerConversation(); iteration++) {
            AiChatTurn turn = aiClient.chat(systemPrompt, conversation, permittedTools);

            if (!turn.wantsTool()) {
                return new ChatResponse(turn.answer(), toolsUsed, false);
            }

            Optional<ChatTool> tool = toolRegistry.resolvePermitted(user, turn.toolName());
            if (tool.isEmpty()) {
                // Told plainly, without confirming whether the tool exists. Feeding this back
                // lets the model recover by choosing a permitted tool or saying it cannot help.
                conversation.add(AiMessage.toolResult(
                        turn.toolName(),
                        "{\"error\":\"That tool is not available to you.\"}"));
                continue;
            }

            String result;
            try {
                result = tool.get().execute(user, turn.toolArguments());
            } catch (RuntimeException ex) {
                log.warn("Chat tool {} failed for user {}", turn.toolName(), user.userId(), ex);
                result = "{\"error\":\"That query could not be completed.\"}";
            }

            toolsUsed.add(new ToolInvocation(turn.toolName(), turn.toolArguments(), result));
            conversation.add(AiMessage.toolResult(turn.toolName(), result));
        }

        // The loop is bounded so a confused model cannot hammer the database indefinitely.
        // Saying so honestly beats returning whatever half-formed thing it had at the limit.
        log.info("Chat for user {} hit the tool-call limit", user.userId());
        return new ChatResponse(
                "I could not answer that within the allowed number of lookups. "
                        + "Try asking something more specific.",
                toolsUsed,
                true);
    }

    private String buildSystemPrompt(AuthenticatedUser user, String planningPeriodId) {
        String role = user.isManager() ? "a shift manager" : "an employee";
        return """
               You help %s of a gastronomy business with questions about the shift schedule. \
               Answer in the language the question was asked in.

               You have no knowledge of this business's schedule yourself. Every fact in your \
               answer must come from a tool result in this conversation. If the tools do not \
               give you what you need, say so plainly - never invent a name, a time, an hour \
               count or a reason. Made-up rota information is worse than no answer, because \
               someone will act on it.

               When a tool needs a planningPeriodId, use: %s

               Be brief and concrete. Prefer naming people and times over describing them.

               %s
               """
                .formatted(
                        role,
                        planningPeriodId == null ? "(none provided - ask the user which period)" : planningPeriodId,
                        PromptSafety.SYSTEM_GUARD);
    }
}
