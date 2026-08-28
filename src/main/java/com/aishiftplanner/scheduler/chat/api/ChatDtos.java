package com.aishiftplanner.scheduler.chat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String question,
            String planningPeriodId) {
    }

    /**
     * @param toolsUsed every backend lookup behind the answer, with its arguments and raw
     *     result — so a manager can check the answer against the facts rather than trust it
     * @param truncated true if the tool-call budget was exhausted before an answer was reached
     */
    public record ChatResponse(String answer, List<ToolInvocation> toolsUsed, boolean truncated) {
    }

    public record ToolInvocation(String tool, Map<String, String> arguments, String result) {
    }
}
