package com.aishiftplanner.scheduler.chat.api;

import com.aishiftplanner.scheduler.chat.api.ChatDtos.ChatRequest;
import com.aishiftplanner.scheduler.chat.api.ChatDtos.ChatResponse;
import com.aishiftplanner.scheduler.chat.application.ScheduleChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Questions about the schedule, answered from real system data")
public class ChatController {

    private final ScheduleChatService chatService;

    public ChatController(ScheduleChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Note the absence of a {@code @PreAuthorize} beyond authentication: the chat's
     * permissions are not a single role gate but a per-tool decision made inside the loop, so
     * an employee and a manager can use the same endpoint and reach entirely different data.
     */
    @PostMapping
    @Operation(
            summary = "Ask a question about the schedule",
            description = "Answers are grounded in backend tool calls against the database. The "
                    + "caller's own permissions decide which tools are available, so an employee "
                    + "and a manager asking the same question get answers from different data. "
                    + "Returns 503 AI_TEMPORARILY_UNAVAILABLE if the local model is unreachable; "
                    + "scheduling is unaffected by that.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Answered"),
        @ApiResponse(responseCode = "503", description = "The local AI service is unavailable")
    })
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.question(), request.planningPeriodId());
    }
}
