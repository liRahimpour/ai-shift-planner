package com.aishiftplanner.scheduler.ai;

import com.aishiftplanner.scheduler.ai.domain.AiChatTurn;
import com.aishiftplanner.scheduler.ai.domain.AiMessage;
import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.ai.domain.AiUnavailableException;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A scripted {@link LocalAiClient} for tests.
 *
 * <p>CI must never need a real model running: a test suite that depends on an LLM is slow,
 * flaky and untrustworthy as a gate. This fake makes the interesting cases — a model that
 * demands a forbidden tool, one that returns malformed JSON, one that loops forever, one that
 * is simply down — into ordinary, fast, deterministic tests.
 */
public class FakeLocalAiClient implements LocalAiClient {

    private final Deque<AiChatTurn> scriptedTurns = new ArrayDeque<>();
    private final List<List<AiToolSpec>> offeredToolsPerCall = new ArrayList<>();
    private final List<String> systemPrompts = new ArrayList<>();
    private final List<String> userContents = new ArrayList<>();

    private String scriptedCompletion = "ok";
    private String scriptedJson = "{}";
    private boolean available = true;
    private boolean throwUnavailable;

    // --- scripting -----------------------------------------------------------

    public FakeLocalAiClient thenAnswer(String answer) {
        scriptedTurns.add(AiChatTurn.answer(answer));
        return this;
    }

    public FakeLocalAiClient thenCallTool(String toolName, java.util.Map<String, String> arguments) {
        scriptedTurns.add(AiChatTurn.callTool(toolName, arguments));
        return this;
    }

    public FakeLocalAiClient withJson(String json) {
        this.scriptedJson = json;
        return this;
    }

    public FakeLocalAiClient withCompletion(String completion) {
        this.scriptedCompletion = completion;
        return this;
    }

    public FakeLocalAiClient unavailable() {
        this.available = false;
        this.throwUnavailable = true;
        return this;
    }

    // --- inspection ----------------------------------------------------------

    /** The tools offered on the nth chat call — used to assert what a caller was allowed to see. */
    public List<AiToolSpec> toolsOfferedOnCall(int index) {
        return offeredToolsPerCall.get(index);
    }

    public List<String> systemPrompts() {
        return systemPrompts;
    }

    public List<String> userContents() {
        return userContents;
    }

    public int chatCallCount() {
        return offeredToolsPerCall.size();
    }

    // --- LocalAiClient -------------------------------------------------------

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String complete(String systemPrompt, String userContent) {
        systemPrompts.add(systemPrompt);
        userContents.add(userContent);
        if (throwUnavailable) {
            throw AiUnavailableException.notReachable();
        }
        return scriptedCompletion;
    }

    @Override
    public String completeJson(String systemPrompt, String userContent, String jsonShapeDescription) {
        systemPrompts.add(systemPrompt);
        userContents.add(userContent);
        if (throwUnavailable) {
            throw AiUnavailableException.notReachable();
        }
        return scriptedJson;
    }

    @Override
    public AiChatTurn chat(String systemPrompt, List<AiMessage> conversation, List<AiToolSpec> tools) {
        systemPrompts.add(systemPrompt);
        offeredToolsPerCall.add(tools == null ? List.of() : List.copyOf(tools));
        if (throwUnavailable) {
            throw AiUnavailableException.notReachable();
        }
        if (scriptedTurns.isEmpty()) {
            return AiChatTurn.answer("No further scripted turns.");
        }
        return scriptedTurns.poll();
    }
}
