package com.codey.infra;

import com.codey.config.ModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 兼容 OpenAI 风格 chat completions 的最小 HTTP 网关。
 */
public class HttpModelGateway implements ModelGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpModelGateway.class);

    private final ModelConfig config;
    private final ObjectMapper objectMapper;
    private final Path modelInputLogRoot;
    private final Map<String, Integer> modelInputSequenceBySession = new HashMap<String, Integer>();

    public HttpModelGateway(ModelConfig config, ObjectMapper objectMapper) {
        this(config, objectMapper, Paths.get("sessions", "model-inputs"));
    }

    HttpModelGateway(ModelConfig config, ObjectMapper objectMapper, Path modelInputLogRoot) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.modelInputLogRoot = modelInputLogRoot;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        ModelConfig effectiveConfig = resolveConfig(request);
        Exception lastException = null;
        int attempts = Math.max(1, effectiveConfig.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return doChat(request, effectiveConfig);
            } catch (Exception exception) {
                lastException = exception;
                LOGGER.warn("HTTP model call failed on attempt {}/{}: {}", Integer.valueOf(attempt),
                        Integer.valueOf(attempts), exception.getMessage());
            }
        }
        throw new IllegalStateException("Failed to call HTTP model gateway after retries", lastException);
    }

    private ModelResponse doChat(ModelRequest request, ModelConfig effectiveConfig) throws Exception {
        HttpURLConnection connection = null;
        String requestBody = null;
        try {
            URL url = new URL(effectiveConfig.getEndpoint());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(effectiveConfig.getConnectTimeoutMillis());
            connection.setReadTimeout(effectiveConfig.getReadTimeoutMillis());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (effectiveConfig.getApiKey() != null && !effectiveConfig.getApiKey().trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + effectiveConfig.getApiKey());
            }

            requestBody = buildRequestBody(request, effectiveConfig);
            writeWireRequestLog(request, requestBody);
            byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body);
            outputStream.flush();
            outputStream.close();

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (statusCode >= 400) {
                String response = readAll(inputStream);
                writeDebugLog("error", requestBody, response, effectiveConfig);
                throw new IllegalStateException("Model HTTP error " + statusCode + ": " + response);
            }

            String contentType = connection.getHeaderField("Content-Type");
            if (contentType != null && contentType.toLowerCase().contains("text/event-stream")) {
                ModelResponse streamedResponse = readStreamingResponse(inputStream, request.getStreamListener(), effectiveConfig);
                writeDebugLog("success", requestBody, streamedResponse.getRawResponse(), effectiveConfig);
                return streamedResponse;
            }

            String response = readAll(inputStream);
            writeDebugLog("success", requestBody, response, effectiveConfig);
            return parseModelResponse(response);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ModelResponse parseModelResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        ModelResponse modelResponse = new ModelResponse();
        modelResponse.setRawResponse(response);

        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        modelResponse.setFinishReason(text(choice, "finish_reason"));

        String content = normalizeModelContent(text(message, "content"));
        if (content != null && !content.trim().isEmpty()) {
            modelResponse.setContent(content);
        } else {
            modelResponse.setContent(normalizeModelContent(text(root, "content")));
        }
        String reasoning = normalizeModelContent(reasoningText(message));
        if (isBlank(reasoning)) {
            reasoning = normalizeModelContent(reasoningText(root));
        }
        if (reasoning != null && !reasoning.trim().isEmpty()) {
            modelResponse.setReasoningContent(reasoning);
        }

        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            modelResponse.setToolCalls(parseToolCalls(toolCallsNode));
        }
        return modelResponse;
    }

    private ModelResponse readStreamingResponse(InputStream inputStream,
                                               ModelStreamListener streamListener,
                                               ModelConfig effectiveConfig) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        HttpStreamingResponseAssembler assembler =
                new HttpStreamingResponseAssembler(objectMapper, effectiveConfig.getModelName(), streamListener);
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data: ")) {
                continue;
            }
            String payload = line.substring("data: ".length()).trim();
            if (payload.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(payload)) {
                break;
            }
            assembler.acceptPayload(payload);
        }
        reader.close();
        return assembler.toModelResponse();
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

    private String buildRequestBody(ModelRequest request, ModelConfig effectiveConfig) throws Exception {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", effectiveConfig.getModelName());
        List<Map<String, Object>> messages = buildWireMessages(request);
        body.put("messages", messages);
        body.put("temperature",
                effectiveConfig.getTemperature() == null ? Double.valueOf(0.2d) : effectiveConfig.getTemperature());
        body.put("stream", Boolean.TRUE);
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildOpenAiTools(request.getTools()));
            body.put("tool_choice", "auto");
        }
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 这里落盘的是最终 HTTP 请求正文，而不是内部 ModelRequest 中间态。
     */
    private void writeWireRequestLog(ModelRequest request, String requestBody) {
        if (request == null || isBlank(request.getSessionId()) || isBlank(requestBody)) {
            return;
        }
        try {
            Path sessionDir = modelInputLogRoot.resolve(request.getSessionId());
            Files.createDirectories(sessionDir);
            int sequence = nextModelInputSequence(request.getSessionId());
            String fileName = String.format("%04d-model_input.json", Integer.valueOf(sequence));
            Path file = sessionDir.resolve(fileName);
            Object jsonPayload = objectMapper.readValue(requestBody, Object.class);
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonPayload)
                    + System.lineSeparator();
            Files.write(file, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception exception) {
            LOGGER.warn("Failed to write wire model input log: {}", exception.getMessage());
        }
    }

    private synchronized int nextModelInputSequence(String sessionId) {
        Integer current = modelInputSequenceBySession.get(sessionId);
        int next = current == null ? 1 : current.intValue() + 1;
        modelInputSequenceBySession.put(sessionId, Integer.valueOf(next));
        return next;
    }

    /**
     * 发送前做一次协议级清洗，避免裁剪后的历史把 tool_calls/tool 配对打坏。
     */
    private List<Map<String, Object>> buildWireMessages(ModelRequest request) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        List<ModelMessage> sanitizedMessages = sanitizeMessages(request.getMessages());
        validateSystemMessageUsage(sanitizedMessages);
        validateUserMessageUsage(sanitizedMessages);
        validateAssistantMessageUsage(sanitizedMessages);
        validateToolMessageUsage(sanitizedMessages);
        for (ModelMessage message : sanitizedMessages) {
            appendMessage(messages, message);
        }
        return messages;
    }

    /**
     * 允许多个连续的前置 system 消息组成系统会话块，但不允许 system 出现在首个非 system 消息之后。
     */
    private void validateSystemMessageUsage(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        boolean sawLeadingSystem = false;
        boolean leftLeadingSystemBlock = false;
        for (ModelMessage message : messages) {
            if (message == null || isBlank(message.getRole())) {
                continue;
            }
            if (!leftLeadingSystemBlock) {
                if (message.isSystem()) {
                    sawLeadingSystem = true;
                    continue;
                }
                leftLeadingSystemBlock = true;
                continue;
            }
            if (message.isSystem()) {
                throw new IllegalStateException("System message must stay inside the leading system block");
            }
        }
        if (!sawLeadingSystem) {
            throw new IllegalStateException("System message is missing");
        }
    }

    private void validateUserMessageUsage(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ModelMessage message : messages) {
            if (message == null || !message.isUser() || isBlank(message.getContent())) {
                continue;
            }
            String content = message.getContent();
            if (content.contains("<conversation_memory>")
                    || content.contains("<context_snapshot>")
                    || content.contains("<replan>")
                    || content.contains("<current_turn>")
                    || content.contains("<recent_tool_results>")
                    || content.contains("<recent_edit_results>")
                    || content.contains("<recent_system_feedback>")
                    || content.contains("<recent_interactions>")
                    || containsLineStartingWith(content, "当前技能:")
                    || containsLineStartingWith(content, "当前暴露工具:")
                    || containsLineStartingWith(content, "当前轮重点:")
                    || containsLineStartingWith(content, "标准输出:")) {
                throw new IllegalStateException("User message must only contain real user input");
            }
        }
    }

    private void validateToolMessageUsage(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ModelMessage message : messages) {
            if (message == null || !message.isToolResult()) {
                continue;
            }
            if (isBlank(message.getToolCallId())
                    || isBlank(message.getToolName())
                    || isBlank(message.getContent())
                    || containsLineStartingWith(message.getContent(), "Tool call not executed:")
                    || containsLineStartingWith(message.getContent(), "Tool request not allowed by skill constraints:")) {
                throw new IllegalStateException("Tool message must only contain actual tool result content");
            }
        }
    }

    private void validateAssistantMessageUsage(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ModelMessage message : messages) {
            if (message == null || !message.isAssistant()) {
                continue;
            }
            if (containsAssistantRolePollution(message.getContent())
                    || containsAssistantRolePollution(message.getReasoningContent())) {
                throw new IllegalStateException("Assistant message must only contain historical assistant output");
            }
        }
    }

    private boolean containsAssistantRolePollution(String content) {
        if (isBlank(content)) {
            return false;
        }
        return content.contains("<conversation_memory>")
                || content.contains("<context_snapshot>")
                || content.contains("<replan>")
                || content.contains("<current_turn>")
                || content.contains("<recent_tool_results>")
                || content.contains("<recent_edit_results>")
                || content.contains("<recent_system_feedback>")
                || content.contains("<recent_interactions>")
                || containsLineStartingWith(content, "用户目标:")
                || containsLineStartingWith(content, "工作目录:")
                || containsLineStartingWith(content, "补充文件:")
                || containsLineStartingWith(content, "补充说明:")
                || containsLineStartingWith(content, "当前技能:")
                || containsLineStartingWith(content, "当前暴露工具:")
                || containsLineStartingWith(content, "当前轮重点:")
                || containsLineStartingWith(content, "标准输出:")
                || containsLineStartingWith(content, "## Language")
                || containsLineStartingWith(content, "## Preamble Rhythm")
                || containsLineStartingWith(content, "## Toolbox")
                || containsLineStartingWith(content, "## Environment")
                || containsLineStartingWith(content, "## Runtime Guardrails");
    }

    private boolean containsLineStartingWith(String content, String prefix) {
        if (isBlank(content) || isBlank(prefix)) {
            return false;
        }
        for (String line : content.replace("\r", "").split("\n")) {
            if (line.trim().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void appendMessage(List<Map<String, Object>> messages, ModelMessage modelMessage) throws Exception {
        if (modelMessage == null || isBlank(modelMessage.getRole())) {
            return;
        }
        if (modelMessage.hasToolCalls() && !modelMessage.isAssistant()) {
            throw new IllegalStateException("Only assistant messages can carry tool_calls");
        }
        if (modelMessage.isToolResult() && !isBlank(modelMessage.getToolName()) && isBlank(modelMessage.getToolCallId())) {
            throw new IllegalStateException("Tool result message requires tool_call_id");
        }
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("role", modelMessage.getRole());
        if (modelMessage.hasToolCalls()) {
            message.put("content", isBlank(modelMessage.getContent()) ? "" : modelMessage.getContent());
            if (!isBlank(modelMessage.getReasoningContent())) {
                message.put("reasoning_content", modelMessage.getReasoningContent());
            }
            message.put("tool_calls", buildWireToolCalls(modelMessage.getToolCalls()));
            messages.add(message);
            return;
        }
        if (modelMessage.isToolResult()) {
            message.put("tool_call_id", modelMessage.getToolCallId());
            if (!isBlank(modelMessage.getToolName())) {
                message.put("name", modelMessage.getToolName());
            }
            message.put("content", isBlank(modelMessage.getContent()) ? "" : modelMessage.getContent());
            messages.add(message);
            return;
        }
        if (isBlank(modelMessage.getContent())) {
            if (modelMessage.isAssistant() && !isBlank(modelMessage.getReasoningContent())) {
                message.put("content", "");
                message.put("reasoning_content", modelMessage.getReasoningContent());
                messages.add(message);
            }
            return;
        }
        message.put("content", modelMessage.getContent());
        if (modelMessage.isAssistant() && !isBlank(modelMessage.getReasoningContent())) {
            message.put("reasoning_content", modelMessage.getReasoningContent());
        }
        messages.add(message);
    }

    private List<Map<String, Object>> buildWireToolCalls(List<ModelToolCall> toolCalls) throws Exception {
        List<Map<String, Object>> wireToolCalls = new ArrayList<Map<String, Object>>();
        if (toolCalls == null) {
            return wireToolCalls;
        }
        for (ModelToolCall toolCall : toolCalls) {
            if (toolCall == null || isBlank(toolCall.getName())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", toolCall.getId());
            item.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", toolCall.getName());
            function.put("arguments", objectMapper.writeValueAsString(toolCall.getArguments()));
            item.put("function", function);
            wireToolCalls.add(item);
        }
        return wireToolCalls;
    }

    private List<ModelMessage> sanitizeMessages(List<ModelMessage> inputMessages) {
        List<ModelMessage> sanitized = new ArrayList<ModelMessage>();
        if (inputMessages == null || inputMessages.isEmpty()) {
            return sanitized;
        }

        ModelMessage pendingAssistantToolCallMessage = null;
        Set<String> pendingToolCallIds = new HashSet<String>();
        Map<String, ModelMessage> pendingToolResults = new LinkedHashMap<String, ModelMessage>();

        for (ModelMessage message : inputMessages) {
            if (message == null || isBlank(message.getRole())) {
                continue;
            }
            if (message.hasToolCalls()) {
                flushPendingToolBundle(sanitized, pendingAssistantToolCallMessage, pendingToolCallIds, pendingToolResults);
                pendingAssistantToolCallMessage = copyMessage(message);
                pendingToolCallIds = extractToolCallIds(pendingAssistantToolCallMessage);
                pendingToolResults.clear();
                if (pendingToolCallIds.isEmpty()) {
                    pendingAssistantToolCallMessage = null;
                }
                continue;
            }
            if (message.isToolResult()) {
                if (pendingAssistantToolCallMessage == null) {
                    continue;
                }
                if (isBlank(message.getToolCallId()) || !pendingToolCallIds.contains(message.getToolCallId())) {
                    continue;
                }
                pendingToolResults.put(message.getToolCallId(), copyMessage(message));
                continue;
            }

            flushPendingToolBundle(sanitized, pendingAssistantToolCallMessage, pendingToolCallIds, pendingToolResults);
            pendingAssistantToolCallMessage = null;
            pendingToolCallIds.clear();
            pendingToolResults.clear();
            sanitized.add(copyMessage(message));
        }
        flushPendingToolBundle(sanitized, pendingAssistantToolCallMessage, pendingToolCallIds, pendingToolResults);
        return sanitized;
    }

    private void flushPendingToolBundle(List<ModelMessage> sanitized,
                                        ModelMessage pendingAssistantToolCallMessage,
                                        Set<String> pendingToolCallIds,
                                        Map<String, ModelMessage> pendingToolResults) {
        if (pendingAssistantToolCallMessage == null || pendingToolCallIds == null || pendingToolCallIds.isEmpty()) {
            return;
        }
        if (pendingToolResults == null || pendingToolResults.size() < pendingToolCallIds.size()) {
            // 如果 tool_calls 没有被完整回应，直接丢弃整个 bundle，避免触发模型接口协议错误。
            return;
        }
        sanitized.add(pendingAssistantToolCallMessage);
        if (pendingAssistantToolCallMessage.getToolCalls() == null) {
            return;
        }
        for (ModelToolCall toolCall : pendingAssistantToolCallMessage.getToolCalls()) {
            if (toolCall == null || isBlank(toolCall.getId())) {
                continue;
            }
            ModelMessage toolResult = pendingToolResults.get(toolCall.getId());
            if (toolResult != null) {
                sanitized.add(toolResult);
            }
        }
    }

    private Set<String> extractToolCallIds(ModelMessage message) {
        Set<String> ids = new HashSet<String>();
        if (message == null || message.getToolCalls() == null) {
            return ids;
        }
        for (ModelToolCall toolCall : message.getToolCalls()) {
            if (toolCall == null || isBlank(toolCall.getId()) || isBlank(toolCall.getName())) {
                continue;
            }
            ids.add(toolCall.getId());
        }
        return ids;
    }

    private ModelMessage copyMessage(ModelMessage source) {
        ModelMessage copy = new ModelMessage();
        copy.setRoleEnum(source.getRoleEnum());
        copy.setContent(source.getContent());
        copy.setReasoningContent(source.getReasoningContent());
        copy.setToolCallId(source.getToolCallId());
        copy.setToolName(source.getToolName());
        copy.setToolCalls(source.getToolCalls());
        return copy;
    }

    private String reasoningText(JsonNode message) {
        String reasoningContent = text(message, "reasoning_content");
        if (!isBlank(reasoningContent)) {
            return reasoningContent;
        }
        return text(message, "reasoning");
    }

    private String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        byte[] buffer = new byte[4096];
        StringBuilder builder = new StringBuilder();
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, length, StandardCharsets.UTF_8));
        }
        inputStream.close();
        return builder.toString();
    }

    private void writeDebugLog(String status, String requestBody, String responseBody, ModelConfig effectiveConfig) {
        if (effectiveConfig == null || !effectiveConfig.isDebugEnabled()) {
            return;
        }
        try {
            Path debugDir = Paths.get(effectiveConfig.getDebugDir());
            Files.createDirectories(debugDir);
            String fileName = System.currentTimeMillis() + "-" + status + "-" + UUID.randomUUID().toString() + ".log";
            Path file = debugDir.resolve(fileName);
            StringBuilder builder = new StringBuilder();
            builder.append("endpoint: ").append(effectiveConfig.getEndpoint()).append(System.lineSeparator());
            builder.append("model: ").append(effectiveConfig.getModelName()).append(System.lineSeparator());
            builder.append("request: ").append(maskApiKey(requestBody, effectiveConfig)).append(System.lineSeparator());
            builder.append("response: ").append(maskApiKey(responseBody, effectiveConfig)).append(System.lineSeparator());
            Files.write(file, builder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            LOGGER.warn("Failed to write model debug log: {}", exception.getMessage());
        }
    }

    private String maskApiKey(String content, ModelConfig effectiveConfig) {
        if (content == null) {
            return "";
        }
        String masked = content;
        if (effectiveConfig != null
                && effectiveConfig.getApiKey() != null
                && !effectiveConfig.getApiKey().trim().isEmpty()) {
            masked = masked.replace(effectiveConfig.getApiKey(), "***");
        }
        return masked;
    }

    /**
     * 启动期配置提供默认值，会话内模型配置快照只覆盖当前会话显式指定的字段。
     */
    private ModelConfig resolveConfig(ModelRequest request) {
        // 启动期默认配置与会话级覆盖统一走同一套底层归一化逻辑，
        // 避免不同入口各自 merge 导致最终运行态配置不一致。
        ModelConfig resolved = ModelConfigResolver.merge(config, request == null ? null : request.getModelConfig());
        // #region debug-point D:effective-model-config
        debugReport(
                "pre-fix",
                "D",
                "HttpModelGateway.resolveConfig",
                "[DEBUG] 模型网关已解析本次请求的最终模型配置",
                "{"
                        + "\"sessionId\":\"" + escapeDebug(request == null ? null : request.getSessionId()) + "\","
                        + "\"messageCount\":\"" + String.valueOf(request == null || request.getMessages() == null ? 0 : request.getMessages().size()) + "\","
                        + "\"toolCount\":\"" + String.valueOf(request == null || request.getTools() == null ? 0 : request.getTools().size()) + "\","
                        + "\"provider\":\"" + escapeDebug(resolved == null ? null : resolved.getProvider()) + "\","
                        + "\"endpoint\":\"" + escapeDebug(resolved == null ? null : resolved.getEndpoint()) + "\","
                        + "\"modelName\":\"" + escapeDebug(resolved == null ? null : resolved.getModelName()) + "\","
                        + "\"temperature\":\"" + escapeDebug(String.valueOf(resolved == null ? null : resolved.getTemperature())) + "\","
                        + "\"connectTimeoutMillis\":\"" + escapeDebug(String.valueOf(resolved == null ? null : resolved.getConnectTimeoutMillis())) + "\","
                        + "\"readTimeoutMillis\":\"" + escapeDebug(String.valueOf(resolved == null ? null : resolved.getReadTimeoutMillis())) + "\","
                        + "\"maxRetries\":\"" + escapeDebug(String.valueOf(resolved == null ? null : resolved.getMaxRetries())) + "\","
                        + "\"apiKeyTail\":\"" + escapeDebug(maskApiKey(resolved == null ? null : resolved.getApiKey())) + "\""
                        + "}",
                request == null ? null : request.getSessionId()
        );
        // #endregion
        return resolved;
    }

    // #region debug-point D:effective-model-config
    private void debugReport(String runId, String hypothesisId, String location, String msg, String dataJson, String traceId) {
        try {
            String serverUrl = "http://127.0.0.1:7777/event";
            String sessionId = "db-model-stagnation";
            Path envPath = Paths.get(".dbg", "db-model-stagnation.env");
            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("DEBUG_SERVER_URL=")) {
                        serverUrl = line.substring("DEBUG_SERVER_URL=".length()).trim();
                    } else if (line.startsWith("DEBUG_SESSION_ID=")) {
                        sessionId = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
            }
            String payload = "{"
                    + "\"sessionId\":\"" + escapeDebug(sessionId) + "\","
                    + "\"runId\":\"" + escapeDebug(runId) + "\","
                    + "\"hypothesisId\":\"" + escapeDebug(hypothesisId) + "\","
                    + "\"location\":\"" + escapeDebug(location) + "\","
                    + "\"msg\":\"" + escapeDebug(msg) + "\","
                    + "\"traceId\":\"" + escapeDebug(traceId) + "\","
                    + "\"data\":" + (dataJson == null ? "{}" : dataJson)
                    + "}";
            HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(body);
                outputStream.flush();
            } finally {
                outputStream.close();
            }
            connection.getInputStream().close();
        } catch (Exception ignored) {
        }
    }

    private String escapeDebug(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return trimmed;
        }
        return "***" + trimmed.substring(trimmed.length() - 4);
    }
    // #endregion

    private List<Map<String, Object>> buildOpenAiTools(List<ModelToolDefinition> definitions) {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        for (ModelToolDefinition definition : definitions) {
            Map<String, Object> tool = new LinkedHashMap<String, Object>();
            tool.put("type", "function");

            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", definition.getName());
            // 工具参数结构会常驻在每轮模型输入中，过长的描述性字段会显著占用上下文预算。
            // 这里保留名称和精简后的参数结构，描述由系统提示词或技能文档承担即可。
            function.put("parameters", ToolSchemaCompactor.compactParameters(definition.getParameters()));

            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private List<ModelToolCall> parseToolCalls(JsonNode toolCallsNode) throws Exception {
        List<ModelToolCall> toolCalls = new ArrayList<ModelToolCall>();
        for (JsonNode toolCallNode : toolCallsNode) {
            ModelToolCall toolCall = new ModelToolCall();
            toolCall.setId(text(toolCallNode, "id"));
            JsonNode functionNode = toolCallNode.path("function");
            toolCall.setName(text(functionNode, "name"));
            toolCall.setArguments(parseArguments(functionNode.get("arguments")));
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private Map<String, Object> parseArguments(JsonNode argumentsNode) throws Exception {
        if (argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()) {
            return new LinkedHashMap<String, Object>();
        }
        if (argumentsNode.isTextual()) {
            String text = argumentsNode.asText();
            if (text == null || text.trim().isEmpty()) {
                return new LinkedHashMap<String, Object>();
            }
            return objectMapper.readValue(text,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        }
        return objectMapper.convertValue(argumentsNode,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        return child == null || child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
