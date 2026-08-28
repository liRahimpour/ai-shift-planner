package com.aishiftplanner.scheduler.ai.infrastructure;

import com.aishiftplanner.scheduler.ai.domain.AiChatTurn;
import com.aishiftplanner.scheduler.ai.domain.AiMessage;
import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.ai.domain.AiUnavailableException;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active when {@code app.ai.enabled=false}.
 *
 * <p>An explicit "off" implementation rather than no bean at all. If the bean were simply
 * absent, every class that injects {@link LocalAiClient} would fail to start, so turning AI
 * off would take the whole application down — the exact opposite of the property's purpose.
 * This way, disabling AI degrades precisely the AI endpoints and nothing else.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false")
public class DisabledLocalAiClient implements LocalAiClient {

    private static final String MESSAGE =
            "AI features are switched off in this deployment (app.ai.enabled=false). "
                    + "Scheduling, availability and publishing are unaffected.";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String complete(String systemPrompt, String userContent) {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public String completeJson(String systemPrompt, String userContent, String jsonShapeDescription) {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public AiChatTurn chat(String systemPrompt, List<AiMessage> conversation, List<AiToolSpec> tools) {
        throw new AiUnavailableException(MESSAGE);
    }
}
