package com.codey.infra;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保存单次流式工具调用在组装过程中的增量状态。
 */
public class StreamedToolCallState {
    private static final int SMALL_BUFFER_PARSE_LIMIT = 2048;
    private static final int LARGE_BUFFER_PARSE_STEP = 2048;

    private String id;
    private final StringBuilder name = new StringBuilder();
    private final StringBuilder argumentsBuffer = new StringBuilder();
    private Map<String, Object> latestParsedArguments = new LinkedHashMap<String, Object>();
    private int lastParseAttemptLength;
    private int incrementalParseAttempts;
    private boolean startEventDispatched;

    public boolean mergeIdentity(String idChunk, String nameChunk) {
        boolean wasReady = isReady();
        if (!isBlank(idChunk)) {
            this.id = idChunk;
        }
        if (!isBlank(nameChunk)) {
            this.name.append(nameChunk);
        }
        return !wasReady && isReady();
    }

    public void appendArguments(String argumentsChunk, ObjectMapper objectMapper) {
        if (isBlank(argumentsChunk)) {
            return;
        }
        argumentsBuffer.append(argumentsChunk);
        if (!shouldAttemptIncrementalParse(argumentsChunk)) {
            return;
        }
        rememberParseAttempt();
        Map<String, Object> parsedArguments = ToolArgumentParser.tryParse(objectMapper, argumentsBuffer.toString());
        if (parsedArguments != null) {
            latestParsedArguments = parsedArguments;
        }
    }

    /**
     * 从已累计的参数片段中尽力解析出最终参数映射。
     */
    public Map<String, Object> finalArguments(ObjectMapper objectMapper) {
        if (argumentsBuffer.length() > 0) {
            Map<String, Object> finalized = ToolArgumentParser.tryParse(objectMapper, argumentsBuffer.toString());
            if (finalized != null) {
                return finalized;
            }
        }
        return new LinkedHashMap<String, Object>(latestParsedArguments);
    }

    public boolean isReady() {
        return name.length() > 0;
    }

    public boolean shouldDispatchStartEvent() {
        return isReady() && !startEventDispatched;
    }

    public void markStartEventDispatched() {
        startEventDispatched = true;
    }

    public ModelToolCall toToolCall(ObjectMapper objectMapper) {
        ModelToolCall toolCall = new ModelToolCall();
        toolCall.setId(id);
        toolCall.setName(name.toString());
        toolCall.setArguments(finalArguments(objectMapper));
        return toolCall;
    }

    public ModelToolCall toPreviewToolCall() {
        ModelToolCall toolCall = new ModelToolCall();
        toolCall.setId(id);
        toolCall.setName(name.toString());
        toolCall.setArguments(new LinkedHashMap<String, Object>());
        return toolCall;
    }

    int getIncrementalParseAttempts() {
        return incrementalParseAttempts;
    }

    private boolean shouldAttemptIncrementalParse(String argumentsChunk) {
        int bufferLength = argumentsBuffer.length();
        if (bufferLength <= SMALL_BUFFER_PARSE_LIMIT) {
            return true;
        }
        if (containsLikelyJsonBoundary(argumentsChunk)) {
            return true;
        }
        return bufferLength - lastParseAttemptLength >= LARGE_BUFFER_PARSE_STEP;
    }

    private void rememberParseAttempt() {
        lastParseAttemptLength = argumentsBuffer.length();
        incrementalParseAttempts++;
    }

    // 长代码流式写入时，generatedContent 会把 arguments 拉得很长；这里只在可能闭合或跨过固定步长时再尝试解析。
    private boolean containsLikelyJsonBoundary(String text) {
        if (isBlank(text)) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '}' || current == ']') {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
