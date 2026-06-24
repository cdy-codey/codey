package com.codey.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将分块返回的 HTTP 流响应组装成统一的 `ModelResponse`。
 */
public class HttpStreamingResponseAssembler {
    private final ObjectMapper objectMapper;
    private final String modelName;
    private final ModelStreamListener streamListener;
    private final FakeToolWrapperFilter fakeToolWrapperFilter = new FakeToolWrapperFilter();
    private final StringBuilder thinkingBuilder = new StringBuilder();
    private final StringBuilder contentBuilder = new StringBuilder();
    private final List<StreamedToolCallState> toolCalls = new ArrayList<StreamedToolCallState>();

    private String finishReason;

    public HttpStreamingResponseAssembler(ObjectMapper objectMapper, String modelName, ModelStreamListener streamListener) {
        this.objectMapper = objectMapper;
        this.modelName = modelName;
        this.streamListener = streamListener;
    }

    public void acceptPayload(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode choice = root.path("choices").path(0);
        String choiceFinishReason = text(choice, "finish_reason");
        if (!isBlank(choiceFinishReason)) {
            finishReason = choiceFinishReason;
        }

        JsonNode delta = choice.path("delta");
        String reasoningDelta = reasoningText(delta);
        if (!isBlank(reasoningDelta)) {
            thinkingBuilder.append(reasoningDelta);
            if (streamListener != null) {
                streamListener.onThinkingDelta(reasoningDelta);
            }
        }
        String contentDelta = text(delta, "content");
        if (!isBlank(contentDelta)) {
            String visibleDelta = fakeToolWrapperFilter.filter(contentDelta);
            if (!isBlank(visibleDelta)) {
                contentBuilder.append(visibleDelta);
                if (streamListener != null) {
                    streamListener.onTextDelta(visibleDelta);
                }
            }
        }
        mergeToolCallDelta(delta.path("tool_calls"));
    }

    public ModelResponse toModelResponse() throws Exception {
        ModelResponse modelResponse = new ModelResponse();
        String content = normalizeModelContent(contentBuilder.toString());
        modelResponse.setContent(content);
        modelResponse.setReasoningContent(normalizeModelContent(thinkingBuilder.toString()));
        modelResponse.setFinishReason(finishReason);
        List<ModelToolCall> builtToolCalls = buildToolCalls();
        modelResponse.setToolCalls(builtToolCalls);
        modelResponse.setRawResponse(buildRawResponse(content, finishReason, builtToolCalls));
        return modelResponse;
    }

    private void notifyToolCallStartedIfNeeded(StreamedToolCallState state) {
        if (streamListener == null || state == null || !state.shouldDispatchStartEvent()) {
            return;
        }
        streamListener.onToolCallStarted(state.toPreviewToolCall());
        state.markStartEventDispatched();
    }

    private void mergeToolCallDelta(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return;
        }
        for (JsonNode toolCallNode : toolCallsNode) {
            int index = toolCallNode.path("index").isInt() ? toolCallNode.path("index").asInt() : toolCalls.size();
            while (toolCalls.size() <= index) {
                toolCalls.add(new StreamedToolCallState());
            }
            StreamedToolCallState target = toolCalls.get(index);
            JsonNode functionNode = toolCallNode.path("function");
            target.mergeIdentity(text(toolCallNode, "id"), text(functionNode, "name"));
            notifyToolCallStartedIfNeeded(target);
            target.appendArguments(text(functionNode, "arguments"), objectMapper);
        }
    }

    private List<ModelToolCall> buildToolCalls() {
        List<ModelToolCall> builtToolCalls = new ArrayList<ModelToolCall>();
        for (StreamedToolCallState state : toolCalls) {
            if (state == null || !state.isReady()) {
                continue;
            }
            builtToolCalls.add(state.toToolCall(objectMapper));
        }
        return builtToolCalls;
    }

    private String buildRawResponse(String content, String finishReason, List<ModelToolCall> toolCalls) throws Exception {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("id", "streamed-response");
        response.put("object", "chat.completion");
        response.put("model", modelName);

        Map<String, Object> choice = new LinkedHashMap<String, Object>();
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", content);
        String thinking = normalizeModelContent(thinkingBuilder.toString());
        if (!isBlank(thinking)) {
            message.put("reasoning_content", thinking);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            message.put("tool_calls", buildRawToolCalls(toolCalls));
        }
        choice.put("index", Integer.valueOf(0));
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        response.put("choices", java.util.Collections.singletonList(choice));
        return objectMapper.writeValueAsString(response);
    }

    private List<Map<String, Object>> buildRawToolCalls(List<ModelToolCall> toolCalls) throws Exception {
        List<Map<String, Object>> rawToolCalls = new ArrayList<Map<String, Object>>();
        for (ModelToolCall toolCall : toolCalls) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", toolCall.getId());
            item.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", toolCall.getName());
            function.put("arguments", objectMapper.writeValueAsString(toolCall.getArguments()));
            item.put("function", function);
            rawToolCalls.add(item);
        }
        return rawToolCalls;
    }

    private String normalizeModelContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring("```json".length()).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring("```".length()).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        return child == null || child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private String reasoningText(JsonNode delta) {
        String reasoningContent = text(delta, "reasoning_content");
        if (!isBlank(reasoningContent)) {
            return reasoningContent;
        }
        return text(delta, "reasoning");
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
