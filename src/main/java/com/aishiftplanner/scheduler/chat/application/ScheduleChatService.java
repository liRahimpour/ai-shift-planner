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
 * The chat loop: question → permitted tool calls against real data → grounded answer.
 *
 * <p>Two trust boundaries are kept explicit here:
 * <ol>
 *   <li>tool availability is decided from the authenticated user, never by the model;</li>
 *   <li>the open planning period is resolved by {@link ChatContextProvider} before the model
 *       runs and is passed to tools separately from model-generated arguments.</li>
 * </ol>
 */
@Service
public class ScheduleChatService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleChatService.class);

    private final LocalAiClient aiClient;
    private final ChatToolRegistry toolRegistry;
    private final CurrentUserProvider currentUser;
    private final ChatContextProvider contextProvider;
    private final AiProperties properties;

    public ScheduleChatService(
            LocalAiClient aiClient,
            ChatToolRegistry toolRegistry,
            CurrentUserProvider currentUser,
            ChatContextProvider contextProvider,
            AiProperties properties) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.currentUser = currentUser;
        this.contextProvider = contextProvider;
        this.properties = properties;
    }

    public ChatResponse ask(String question, String planningPeriodId) {
        AuthenticatedUser user = currentUser.require();
        ChatContext context = contextProvider.resolve(user, planningPeriodId);
        List<AiToolSpec> permittedTools = toolRegistry.specsFor(user);

        List<AiMessage> conversation = new ArrayList<>();
        conversation.add(AiMessage.user(PromptSafety.fence(question)));

        List<ToolInvocation> toolsUsed = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(user, context);

        for (int iteration = 0; iteration < properties.maxToolCallsPerConversation(); iteration++) {
            AiChatTurn turn = aiClient.chat(systemPrompt, conversation, permittedTools);

            if (!turn.wantsTool()) {
                return new ChatResponse(turn.answer(), toolsUsed, false);
            }

            Optional<ChatTool> tool = toolRegistry.resolvePermitted(user, turn.toolName());
            if (tool.isEmpty()) {
                conversation.add(AiMessage.toolResult(
                        turn.toolName(),
                        "{\"errorCode\":\"TOOL_NOT_PERMITTED\",\"error\":\"That tool is not available to you.\"}"));
                continue;
            }

            String result;
            try {
                result = tool.get().execute(user, context, turn.toolArguments());
            } catch (RuntimeException ex) {
                // Expected domain states are returned by tools as structured error JSON. Anything
                // reaching this catch is unexpected and must not leak internals to the model/user.
                log.warn("Chat tool {} failed for user {}", turn.toolName(), user.userId(), ex);
                result = "{\"errorCode\":\"TOOL_QUERY_FAILED\",\"error\":\"That query could not be completed.\"}";
            }

            toolsUsed.add(new ToolInvocation(turn.toolName(), turn.toolArguments(), result));
            conversation.add(AiMessage.toolResult(turn.toolName(), result));
        }

        log.info("Chat for user {} hit the tool-call limit", user.userId());
        return new ChatResponse(
                "I could not answer that within the allowed number of lookups. "
                        + "Try asking something more specific.",
                toolsUsed,
                true);
    }

    private String buildSystemPrompt(AuthenticatedUser user, ChatContext context) {
        String role = user.isManager() ? "a shift manager" : "an employee";
        String trustedContext = trustedContextForPrompt(context);

        return """
               You help %s of a gastronomy business with questions about the shift schedule.
               Answer in the language the question was asked in.

               TRUSTED SCHEDULE CONTEXT (loaded by the backend, not written by the user):
               %s

               Every business fact in your answer must come from the trusted schedule context
               above or from a tool result in this conversation. You have no independent
               knowledge of this business's rota. Never invent a name, time, hour count, shift,
               staffing level or reason.

               For weekday expressions such as Saturday, Samstag or samedi, resolve the date
               only from the explicit date index in the trusted context. Do not use today's date
               or your own calendar assumptions. If the period is too long for a unique weekday
               and the user did not give an exact date, ask which date they mean.

               If Selected schedule is NONE, assignment-based questions cannot yet be answered.
               Explain that a manager must select one of the schedule proposals first. Never
               describe that state as "nobody is working" or "the query returned no employees".

               Tool arguments are model output and therefore untrusted. Never invent ids. The
               planning period itself is not a tool argument; the backend supplies it securely.

               Be brief and concrete. Prefer names and times over generic descriptions.

               %s
               """
                .formatted(role, trustedContext, PromptSafety.SYSTEM_GUARD);
    }

    private static String trustedContextForPrompt(ChatContext context) {
        if (!context.hasPlanningPeriod()) {
            return """
                   Planning period: NONE
                   Selected schedule: NONE
                   Date index: (no planning period)
                   """;
        }

        String selectedSchedule = context.hasSelectedSchedule()
                ? context.selectedStrategy().name()
                : "NONE";

        return """
               Planning period id: %s
               Planning period: %s to %s
               Time zone: %s
               Selected schedule: %s
               Date index:
               %s
               """
                .formatted(
                        context.planningPeriodId(),
                        context.startDate(),
                        context.endDate(),
                        context.timezone().getId(),
                        selectedSchedule,
                        context.dateIndexForPrompt());
    }
}
