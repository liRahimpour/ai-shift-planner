package com.aishiftplanner.scheduler.ai.infrastructure;

import com.aishiftplanner.scheduler.ai.domain.AiChatTurn;
import com.aishiftplanner.scheduler.ai.domain.AiMessage;
import com.aishiftplanner.scheduler.ai.domain.AiToolSpec;
import com.aishiftplanner.scheduler.ai.domain.AiUnavailableException;
import com.aishiftplanner.scheduler.ai.domain.LocalAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link LocalAiClient} backed by a local Ollama instance.
 *
 * <p>Talks to Ollama's native {@code /api/chat} endpoint. See
 * {@code docs/adr/010-ollama-native-api-instead-of-spring-ai.md} for why this is direct HTTP
 * rather than a general-purpose AI abstraction, and how to swap one in.
 *
 * <p>Every path out of this class either returns a usable value or throws
 * {@link AiUnavailableException}. Callers therefore have exactly one failure mode to handle,
 * and no connection error, timeout or malformed payload can escape into a stack trace shown
 * to a user.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OllamaLocalAiClient implements LocalAiClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLocalAiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public OllamaLocalAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.requestTimeoutSeconds());
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.debug("Ollama not reachable at {}: {}", properties.baseUrl(), ex.getMessage());
            return false;
        }
    }

    @Override
    public String complete(String systemPrompt, String userContent) {
        Map<String, Object> request = baseRequest(List.of(
                message("system", systemPrompt),
                message("user", userContent)));
        JsonNode response = post(request);
        return textOf(response);
    }

    @Override
    public String completeJson(String systemPrompt, String userContent, String jsonShapeDescription) {
        String prompt = systemPrompt
                + "\n\nRespond with a single JSON object and nothing else. No prose, no code "
                + "fences, no explanation. The object must have this shape:\n"
                + jsonShapeDescription;

        Map<String, Object> request = baseRequest(List.of(
                message("system", prompt),
                message("user", userContent)));
        // Ollama's structured-output mode. Asking for JSON in the prompt alone is a
        // suggestion; this makes it a constraint on decoding, which removes most of the
        // "model wrote a friendly sentence before the JSON" failures.
        request.put("format", "json");

        JsonNode response = post(request);
        return textOf(response);
    }

    @Override
    public AiChatTurn chat(String systemPrompt, List<AiMessage> conversation, List<AiToolSpec> tools) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        for (AiMessage entry : conversation) {
            messages.add(switch (entry.role()) {
                case USER -> message("user", entry.content());
                case ASSISTANT -> message("assistant", entry.content());
                case TOOL -> toolMessage(entry);
            });
        }

        Map<String, Object> request = baseRequest(messages);
        if (tools != null && !tools.isEmpty()) {
            request.put("tools", tools.stream().map(OllamaLocalAiClient::toToolSchema).toList());
        }

        JsonNode response = post(request);
        JsonNode messageNode = response.path("message");
        JsonNode toolCalls = messageNode.path("tool_calls");

        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            JsonNode first = toolCalls.get(0).path("function");
            String name = first.path("name").asText(null);
            if (name == null || name.isBlank()) {
                throw AiUnavailableException.unusableResponse();
            }
            Map<String, String> arguments = new LinkedHashMap<>();
            JsonNode argumentsNode = first.path("arguments");
            argumentsNode.fields().forEachRemaining(field ->
                    arguments.put(field.getKey(), field.getValue().asText()));
            // These arguments are model output and therefore untrusted; the tool registry
            // validates them before anything is executed.
            return AiChatTurn.callTool(name, arguments);
        }

        return AiChatTurn.answer(messageNode.path("content").asText(""));
    }

    // --- internals -----------------------------------------------------------

    private Map<String, Object> baseRequest(List<Map<String, Object>> messages) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.model());
        request.put("messages", messages);
        request.put("stream", false);
        request.put("options", Map.of("temperature", properties.temperature()));
        return request;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private static Map<String, Object> toolMessage(AiMessage entry) {
        Map<String, Object> message = message("tool", entry.content());
        if (entry.toolName() != null) {
            message.put("name", entry.toolName());
        }
        return message;
    }

    private JsonNode post(Map<String, Object> request) {
        try {
            String body = restClient
                    .post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw AiUnavailableException.unusableResponse();
            }
            return objectMapper.readTree(body);
        } catch (RestClientException ex) {
            // Logged at warn, not error: an unreachable optional component is a degraded
            // feature, not an incident, and treating it as one trains people to ignore
            // error logs.
            log.warn("Ollama request failed ({}): {}", properties.baseUrl(), ex.getMessage());
            throw AiUnavailableException.notReachable();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Could not parse Ollama response: {}", ex.getOriginalMessage());
            throw AiUnavailableException.unusableResponse();
        }
    }

    private static String textOf(JsonNode response) {
        String content = response.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw AiUnavailableException.unusableResponse();
        }
        return content;
    }

    /** Builds the JSON-schema-shaped tool description Ollama expects. */
    private static Map<String, Object> toToolSchema(AiToolSpec tool) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (AiToolSpec.Parameter parameter : tool.parameters()) {
            properties.put(
                    parameter.name(),
                    Map.of("type", parameter.type(), "description", parameter.description()));
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", required)));
    }
}
