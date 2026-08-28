package com.aishiftplanner.scheduler.chat.application;

import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.auth.application.AuthenticatedUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the chat tools and decides which ones a given caller may use.
 *
 * <p>Every {@link ChatTool} bean on the classpath is registered automatically, so adding a
 * capability is one class — but it only reaches a user if its own
 * {@link ChatTool#isPermittedFor} says so.
 */
@Component
public class ChatToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatToolRegistry.class);

    private final Map<String, ChatTool> toolsByName = new LinkedHashMap<>();

    public ChatToolRegistry(List<ChatTool> tools) {
        for (ChatTool tool : tools) {
            ChatTool previous = toolsByName.put(tool.name(), tool);
            if (previous != null) {
                // Two tools answering to one name would make which code runs depend on bean
                // ordering - unacceptable for something on the authorization path.
                throw new IllegalStateException("Duplicate chat tool name: " + tool.name());
            }
        }
        log.info("Registered {} chat tools: {}", toolsByName.size(), toolsByName.keySet());
    }

    /** The tools this user may use — the only ones the model is ever told about. */
    public List<AiToolSpec> specsFor(AuthenticatedUser user) {
        return toolsByName.values().stream()
                .filter(tool -> tool.isPermittedFor(user))
                .map(ChatTool::spec)
                .toList();
    }

    /**
     * Resolves a tool the model asked for, <em>if</em> this caller may use it.
     *
     * <p>Returns empty both for an unknown name and for a known-but-forbidden one. The two
     * cases are deliberately indistinguishable to the caller: a distinct "you are not allowed
     * to use getSalaries" answer would confirm that such a tool exists, which is itself
     * information an employee should not have.
     */
    public Optional<ChatTool> resolvePermitted(AuthenticatedUser user, String toolName) {
        ChatTool tool = toolsByName.get(toolName);
        if (tool == null) {
            log.debug("Model requested unknown tool '{}'", toolName);
            return Optional.empty();
        }
        if (!tool.isPermittedFor(user)) {
            // Worth a warning: either a model hallucinated a tool it was never offered, or
            // something is trying to reach past the filter. Both are worth seeing in a log.
            log.warn(
                    "User {} requested tool '{}' they are not permitted to use",
                    user.userId(),
                    toolName);
            return Optional.empty();
        }
        return Optional.of(tool);
    }
}
