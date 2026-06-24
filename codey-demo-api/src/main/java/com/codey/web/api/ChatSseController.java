package com.codey.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.codey.client.SessionEvent;
import com.codey.client.SessionEventHub;
import com.codey.client.SessionEventListener;
import com.codey.client.SessionEventSubscription;
import com.codey.client.SessionEventType;
import com.codey.tools.ToolRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 SSE 事件接口。
 * 先回放当前快照，再持续转发该 session 的后续事件。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatSseController {
    private static final long SSE_TIMEOUT_MILLIS = 0L;

    private final SessionEventHub sessionEventHub;
    private final ChatSessionDisplayOptionsStore displayOptionsStore;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ChatSseController(SessionEventHub sessionEventHub,
                                    ChatSessionDisplayOptionsStore displayOptionsStore,
                                    ToolRegistry toolRegistry,
                                    ObjectMapper objectMapper) {
        this.sessionEventHub = sessionEventHub;
        this.displayOptionsStore = displayOptionsStore;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable("sessionId") String sessionId) {
        if (isBlank(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId 不能为空");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        try {
            sendConnected(emitter, sessionId);
            replaySnapshot(emitter, sessionId);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "初始化 SSE 连接失败", ex);
        }

        final SessionEventSubscription subscription = sessionEventHub.subscribe(sessionId, new SessionEventListener() {
            @Override
            public void onEvent(SessionEvent event) {
                try {
                    sendEvent(emitter, event);
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        emitter.onCompletion(subscription::close);
        emitter.onTimeout(() -> {
            subscription.close();
            emitter.complete();
        });
        emitter.onError(throwable -> subscription.close());
        return emitter;
    }

    void sendConnected(SseEmitter emitter, String sessionId) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Collections.singletonMap("sessionId", sessionId)));
        }
    }

    void replaySnapshot(SseEmitter emitter, String sessionId) throws IOException {
        List<SessionEvent> snapshot = sessionEventHub.snapshot(sessionId);
        for (SessionEvent event : snapshot) {
            sendEvent(emitter, event);
        }
    }

    void sendEvent(SseEmitter emitter, SessionEvent event) throws IOException {
        SessionEvent eventForClient = prepareEventForClient(event == null ? null : event.getSessionId(), event);
        if (eventForClient == null) {
            return;
        }
        String eventName = eventForClient.getType() == null ? "session_event" : eventForClient.getType().getCode();
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(eventForClient));
        }
    }

    SessionEvent prepareEventForClient(String sessionId, SessionEvent event) {
        if (event == null) {
            return null;
        }
        SessionEvent enrichedEvent = enrichToolDisplayName(event);
        if (displayOptionsStore.shouldIncludeThinking(sessionId)) {
            return enrichedEvent;
        }
        if (enrichedEvent.getType() == SessionEventType.MODEL_THINKING_DELTA) {
            return null;
        }
        if (enrichedEvent.getType() != SessionEventType.MODEL_OUTPUT) {
            return enrichedEvent;
        }
        String filteredRawOutput = stripThinkingFromRawOutput(enrichedEvent.getMessage());
        Object filteredPayload = stripThinkingFromPayload(enrichedEvent.getPayload());
        return new SessionEvent(
                enrichedEvent.getSessionId(),
                enrichedEvent.getType(),
                enrichedEvent.getStage(),
                filteredRawOutput,
                filteredPayload
        );
    }

    /**
     * 实时事件保留原始 toolName，同时补 displayName 供前端直接展示。
     */
    private SessionEvent enrichToolDisplayName(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return event;
        }
        if (event.getType() != SessionEventType.MODEL_TOOL_CALL_STARTED
                && event.getType() != SessionEventType.TOOL_EXECUTION_STARTED
                && event.getType() != SessionEventType.TOOL_CALL) {
            return event;
        }
        String toolName = resolveEventToolName(event);
        if (isBlank(toolName)) {
            return event;
        }
        String displayName = resolveToolDisplayName(toolName);
        Map<String, Object> payload = toMutableMap(event.getPayload());
        if (event.getType() == SessionEventType.TOOL_CALL) {
            Map<String, Object> request = toMutableMap(payload.get("request"));
            request.put("displayName", displayName);
            payload.put("request", request);
            payload.put("displayName", displayName);
        } else {
            payload.put("displayName", displayName);
        }
        return new SessionEvent(
                event.getSessionId(),
                event.getType(),
                event.getStage(),
                event.getMessage(),
                payload
        );
    }

    private String resolveEventToolName(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return "";
        }
        if (event.getType() == SessionEventType.TOOL_CALL) {
            Map<String, Object> payload = toMutableMap(event.getPayload());
            Map<String, Object> request = toMutableMap(payload.get("request"));
            Object toolName = request.get("toolName");
            if (toolName == null) {
                toolName = request.get("tool_name");
            }
            return toolName == null ? defaultString(event.getMessage()) : String.valueOf(toolName);
        }
        Map<String, Object> payload = toMutableMap(event.getPayload());
        Object toolName = payload.get("name");
        return toolName == null ? defaultString(event.getMessage()) : String.valueOf(toolName);
    }

    private String resolveToolDisplayName(String toolName) {
        String displayName = toolRegistry == null ? null : toolRegistry.resolveDisplayName(toolName);
        return isBlank(displayName) ? toolName : displayName;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMutableMap(Object source) {
        if (source instanceof Map<?, ?>) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }
        if (source == null) {
            return new LinkedHashMap<String, Object>();
        }
        return objectMapper.convertValue(source, LinkedHashMap.class);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private Object stripThinkingFromPayload(Object payload) {
        if (!(payload instanceof Map)) {
            return payload;
        }
        Map<?, ?> source = (Map<?, ?>) payload;
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        Object rawOutput = copy.get("rawOutput");
        if (rawOutput instanceof String) {
            copy.put("rawOutput", stripThinkingFromRawOutput((String) rawOutput));
        }
        return copy;
    }

    private String stripThinkingFromRawOutput(String rawOutput) {
        if (isBlank(rawOutput)) {
            return rawOutput;
        }
        try {
            JsonNode root = objectMapper.readTree(rawOutput);
            if (!(root instanceof ObjectNode)) {
                return rawOutput;
            }
            ObjectNode copy = ((ObjectNode) root).deepCopy();
            removeThinkingField(copy);
            JsonNode choices = copy.path("choices");
            if (choices.isArray()) {
                for (JsonNode choice : choices) {
                    if (!(choice instanceof ObjectNode)) {
                        continue;
                    }
                    ObjectNode choiceNode = (ObjectNode) choice;
                    removeThinkingField(choiceNode);
                    JsonNode message = choiceNode.path("message");
                    if (message instanceof ObjectNode) {
                        removeThinkingField((ObjectNode) message);
                    }
                    JsonNode delta = choiceNode.path("delta");
                    if (delta instanceof ObjectNode) {
                        removeThinkingField((ObjectNode) delta);
                    }
                }
            }
            return objectMapper.writeValueAsString(copy);
        } catch (Exception exception) {
            return rawOutput;
        }
    }

    private void removeThinkingField(ObjectNode node) {
        if (node == null) {
            return;
        }
        node.remove("reasoning_content");
        node.remove("reasoning");
    }

    boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
