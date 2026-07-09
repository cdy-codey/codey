package com.codey.session;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventType;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.verify.VerifyResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 交互式控制台会话存储。
 * 负责把模型流式文本、工具进度和校验结果转换为用户可读的中文摘要。
 */
public class ConsoleSessionStore implements SessionStore {
    private static final int MAX_OUTPUT_LENGTH = 1200;
    private static final String FINAL_JSON_PREFIX = "{\"status\":\"FINISH\"";
    private static final int STREAM_LOOKBEHIND = FINAL_JSON_PREFIX.length();
    private static final long WRITE_PROGRESS_TICK_MILLIS = 200L;
    private static final int WRITE_PROGRESS_MAX = 95;
    private static final String PROGRESS_PREFIX = "处理中> ";
    private static final String ASSISTANT_PREFIX = "助手> ";
    private static final String THINKING_PREFIX = "思考> ";
    private static final String SYSTEM_PREFIX = "系统> ";

    private final PrintStream out;
    private final PrintStream err;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object consoleLock = new Object();
    private final ScheduledExecutorService progressExecutor;
    private final Set<String> activeStreamingSessions = new HashSet<String>();
    private final Set<String> streamedSessions = new HashSet<String>();
    private final Set<String> displayedFinalSummarySessions = new HashSet<String>();
    private final Set<String> suppressStructuredTailSessions = new HashSet<String>();
    private final Map<String, StringBuilder> pendingStreamingText = new HashMap<String, StringBuilder>();
    private final Map<String, StringBuilder> currentVisibleStreamingText = new HashMap<String, StringBuilder>();
    private final Map<String, String> completedStreamingText = new HashMap<String, String>();
    private final Map<String, String> lastProgressMessage = new HashMap<String, String>();
    private final Map<String, LineMode> currentLineModes = new HashMap<String, LineMode>();
    private final Map<String, ToolProgressState> toolProgressStates = new HashMap<String, ToolProgressState>();

    public ConsoleSessionStore(PrintStream out, PrintStream err) {
        this(out, err, WRITE_PROGRESS_TICK_MILLIS);
    }

    ConsoleSessionStore(PrintStream out, PrintStream err, long writeProgressTickMillis) {
        this.out = out;
        this.err = err;
        this.progressExecutor = Executors.newSingleThreadScheduledExecutor(newProgressThreadFactory());
        this.progressTickMillis = Math.max(50L, writeProgressTickMillis);
    }

    private final long progressTickMillis;

    @Override
    public void completeModelText(String sessionId) {
        stopToolProgress(sessionId);
        flushPendingStreamingText(sessionId, true);
        finishCurrentLine(sessionId);
        activeStreamingSessions.remove(sessionId);
        lastProgressMessage.remove(sessionId);
        StringBuilder visibleText = currentVisibleStreamingText.remove(sessionId);
        if (visibleText != null) {
            completedStreamingText.put(sessionId, visibleText.toString());
        }
        pendingStreamingText.remove(sessionId);
        suppressStructuredTailSessions.remove(sessionId);
        currentLineModes.remove(sessionId);
    }

    @Override
    public void appendEvent(SessionEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        if (event.getType() == SessionEventType.MODEL_OUTPUT) {
            handleModelOutput(event);
            return;
        }
        if (event.getType() == SessionEventType.MODEL_TEXT_DELTA) {
            handleModelTextDelta(event);
            return;
        }
        if (event.getType() == SessionEventType.MODEL_THINKING_DELTA) {
            handleModelThinkingDelta(event);
            return;
        }
        if (event.getType() == SessionEventType.MODEL_TOOL_CALL_STARTED) {
            handleModelToolCallStarted(event);
            return;
        }
        if (event.getType() == SessionEventType.TOOL_EXECUTION_STARTED) {
            handleToolExecutionStarted(event);
            return;
        }
        if (event.getType() == SessionEventType.TOOL_CALL) {
            handleToolCall(event);
            return;
        }
        if (event.getType() == SessionEventType.HUMAN_DECISION) {
            if (!isBlank(event.getMessage())) {
                printSystemLine(event.getSessionId(), event.getMessage());
            }
            return;
        }
        if (event.getType() == SessionEventType.VERIFICATION) {
            handleVerification(event);
            return;
        }
        if (event.getType() == SessionEventType.SECURITY_EVENT) {
            handleSecurityEvent(event);
            return;
        }
        if (event.getType() == SessionEventType.FINAL_SUMMARY) {
            handleFinalSummary(event);
        }
    }

    private void handleModelOutput(SessionEvent event) {
        String rawOutput = event.getMessage();
        if (isBlank(rawOutput)) {
            rawOutput = stringPayload(event, "rawOutput");
        }
        if (isBlank(rawOutput)) {
            return;
        }
        if (streamedSessions.remove(event.getSessionId())) {
            return;
        }
        emitProgressMessage(event.getSessionId(), toUserFacingOutput(rawOutput));
    }

    private void handleModelTextDelta(SessionEvent event) {
        String delta = event.getMessage();
        if (isBlank(delta)) {
            delta = stringPayload(event, "delta");
        }
        if (isBlank(delta)) {
            return;
        }
        String sessionId = event.getSessionId();
        // 流式正文会先进入短缓冲，避免尾部 FINISH JSON 半截直接显示给用户。
        StringBuilder pending = pendingStreamingText.get(sessionId);
        if (pending == null) {
            pending = new StringBuilder();
            pendingStreamingText.put(sessionId, pending);
        }
        pending.append(delta);
        streamedSessions.add(sessionId);
        flushPendingStreamingText(sessionId, false);
    }

    private void handleModelThinkingDelta(SessionEvent event) {
        String delta = event.getMessage();
        if (isBlank(delta)) {
            delta = stringPayload(event, "delta");
        }
        if (isBlank(delta)) {
            return;
        }
        printDelta(event.getSessionId(), delta, LineMode.THINKING, THINKING_PREFIX);
    }

    private void handleModelToolCallStarted(SessionEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof com.codey.infra.ModelToolCall)) {
            return;
        }
        emitProgressMessage(event.getSessionId(),
                mapToolStartMessage(((com.codey.infra.ModelToolCall) payload).getName()));
    }

    private void handleToolExecutionStarted(SessionEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof ToolInvocation)) {
            return;
        }
        ToolInvocation invocation = (ToolInvocation) payload;
        String label = mapToolExecutingMessage(invocation.getToolName());
        if (isBlank(label)) {
            return;
        }
        startToolProgress(event.getSessionId(), label);
    }

    private void handleToolCall(SessionEvent event) {
        stopToolProgress(event.getSessionId());
        Object request = SessionEventFactory.payloadValue(event, "request");
        Object result = SessionEventFactory.payloadValue(event, "result");
        if (!(request instanceof ToolInvocation) || !(result instanceof ToolResult)) {
            return;
        }
        String display = summarizeToolResult((ToolInvocation) request, (ToolResult) result);
        if (!isBlank(display)) {
            printSystemLine(event.getSessionId(), display);
        }
    }

    private void handleVerification(SessionEvent event) {
        Object payload = event.getPayload();
        if (!(payload instanceof VerifyResult)) {
            return;
        }
        VerifyResult result = (VerifyResult) payload;
        // 普通聊天场景下，成功/跳过类校验属于内部执行细节，不应打断用户交互。
        if (!result.isFailed()) {
            return;
        }
        if (!isBlank(event.getMessage())) {
            printSystemLine(event.getSessionId(), event.getMessage());
        }
    }

    private void handleSecurityEvent(SessionEvent event) {
        if (isBlank(event.getMessage())) {
            return;
        }
        String display = toUserFacingSecurityMessage(event.getMessage());
        if (!isBlank(display)) {
            err.println(SYSTEM_PREFIX + display);
        }
    }

    private void handleFinalSummary(SessionEvent event) {
        lastProgressMessage.remove(event.getSessionId());
        String streamedText = completedStreamingText.remove(event.getSessionId());
        if (sameUserFacingText(streamedText, event.getMessage())) {
            displayedFinalSummarySessions.add(event.getSessionId());
        }
    }

    private String stringPayload(SessionEvent event, String key) {
        Object value = SessionEventFactory.payloadValue(event, key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public boolean consumeDisplayedFinalSummary(String sessionId) {
        return displayedFinalSummarySessions.remove(sessionId);
    }

    private void emitProgressMessage(String sessionId, String display) {
        if (isBlank(display) || sameUserFacingText(lastProgressMessage.get(sessionId), display)) {
            return;
        }
        lastProgressMessage.put(sessionId, display);
        synchronized (consoleLock) {
            flushPendingStreamingText(sessionId, true);
            finishCurrentLine(sessionId);
            out.println(PROGRESS_PREFIX + display);
        }
    }

    private void printSystemLine(String sessionId, String message) {
        if (isBlank(message)) {
            return;
        }
        synchronized (consoleLock) {
            flushPendingStreamingText(sessionId, true);
            finishCurrentLine(sessionId);
            out.println(SYSTEM_PREFIX + message);
            out.flush();
        }
    }

    private String compact(String value) {
        String normalized = value == null ? "" : value.replace("\r", "").replace("\n", "\\n").trim();
        if (normalized.length() <= MAX_OUTPUT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_OUTPUT_LENGTH) + "...(已截断)";
    }

    private String toUserFacingOutput(String rawOutput) {
        String normalized = compact(rawOutput);
        if (looksLikeHttpCompletionPayload(normalized)) {
            return "";
        }
        if (normalized.startsWith(FINAL_JSON_PREFIX)) {
            return "";
        }

        String mapped = mapToolStartMessage(normalized);
        return isBlank(mapped) ? normalized : mapped;
    }

    private boolean looksLikeHttpCompletionPayload(String value) {
        return value.startsWith("{") && value.contains("\"choices\"");
    }

    private void flushPendingStreamingText(String sessionId, boolean flushAll) {
        StringBuilder pending = pendingStreamingText.get(sessionId);
        if (pending == null || pending.length() == 0) {
            return;
        }
        if (!suppressStructuredTailSessions.contains(sessionId)) {
            int jsonIndex = pending.indexOf(FINAL_JSON_PREFIX);
            if (jsonIndex >= 0) {
                printStreamingText(sessionId, pending.substring(0, jsonIndex));
                pending.setLength(0);
                suppressStructuredTailSessions.add(sessionId);
                return;
            }
        }
        if (suppressStructuredTailSessions.contains(sessionId)) {
            pending.setLength(0);
            return;
        }
        int flushLength = flushAll ? pending.length() : Math.max(0, pending.length() - STREAM_LOOKBEHIND);
        if (flushLength <= 0) {
            return;
        }
        String visibleText = pending.substring(0, flushLength);
        printStreamingText(sessionId, visibleText);
        pending.delete(0, flushLength);
    }

    private void printStreamingText(String sessionId, String text) {
        if (isBlank(text)) {
            return;
        }
        StringBuilder visibleText = currentVisibleStreamingText.get(sessionId);
        if (visibleText == null) {
            visibleText = new StringBuilder();
            currentVisibleStreamingText.put(sessionId, visibleText);
        }
        visibleText.append(text);
        printDelta(sessionId, text, LineMode.ASSISTANT, ASSISTANT_PREFIX);
    }

    private void printDelta(String sessionId, String text, LineMode targetMode, String prefix) {
        if (isBlank(text)) {
            return;
        }
        synchronized (consoleLock) {
            LineMode currentMode = currentLineModes.get(sessionId);
            if (currentMode != null && currentMode != targetMode) {
                out.println();
                activeStreamingSessions.remove(sessionId);
            }
            if (!activeStreamingSessions.contains(sessionId)) {
                out.print(prefix);
                activeStreamingSessions.add(sessionId);
            }
            currentLineModes.put(sessionId, targetMode);
            out.print(text);
            out.flush();
        }
    }

    private void finishCurrentLine(String sessionId) {
        if (activeStreamingSessions.remove(sessionId)) {
            out.println();
        }
        currentLineModes.remove(sessionId);
    }

    private void startToolProgress(final String sessionId, final String label) {
        synchronized (consoleLock) {
            stopToolProgress(sessionId);
            flushPendingStreamingText(sessionId, true);
            finishCurrentLine(sessionId);
            ToolProgressState state = new ToolProgressState(label);
            toolProgressStates.put(sessionId, state);
            renderToolProgress(sessionId, state);
            state.future = progressExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    advanceToolProgress(sessionId, label);
                }
            }, progressTickMillis, progressTickMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void advanceToolProgress(String sessionId, String label) {
        synchronized (consoleLock) {
            ToolProgressState state = toolProgressStates.get(sessionId);
            if (state == null || !label.equals(state.label)) {
                return;
            }
            if (state.percent >= WRITE_PROGRESS_MAX) {
                return;
            }
            state.percent = nextToolProgressPercent(state.percent);
            renderToolProgress(sessionId, state);
        }
    }

    private void stopToolProgress(String sessionId) {
        synchronized (consoleLock) {
            ToolProgressState state = toolProgressStates.remove(sessionId);
            if (state == null) {
                return;
            }
            if (state.future != null) {
                state.future.cancel(true);
            }
            out.print("\r");
            out.print(repeat(' ', state.lastRenderedLength));
            out.print("\r");
            out.flush();
        }
    }

    private void renderToolProgress(String sessionId, ToolProgressState state) {
        String text = PROGRESS_PREFIX + state.label + "... " + state.percent + "%";
        out.print("\r");
        out.print(text);
        int trailingSpaces = Math.max(0, state.lastRenderedLength - text.length());
        if (trailingSpaces > 0) {
            out.print(repeat(' ', trailingSpaces));
        }
        out.flush();
        state.lastRenderedLength = text.length();
        lastProgressMessage.put(sessionId, state.label);
    }

    private int nextToolProgressPercent(int current) {
        if (current < 5) {
            return current + 1;
        }
        if (current < 25) {
            return Math.min(25, current + 4);
        }
        if (current < 60) {
            return Math.min(60, current + 3);
        }
        if (current < 85) {
            return Math.min(85, current + 2);
        }
        return Math.min(WRITE_PROGRESS_MAX, current + 1);
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private String mapToolStartMessage(String toolName) {
        if ("read_file".equals(toolName)) {
            return "正在查看文件内容";
        }
        if ("read_api_spec".equals(toolName)) {
            return "正在查看接口说明";
        }
        if ("query_api_info".equals(toolName)) {
            return "正在查询接口信息";
        }
        if ("list_workspace".equals(toolName)) {
            return "正在查看当前工作目录";
        }
        if ("project_map".equals(toolName)) {
            return "正在梳理项目结构和关键文件";
        }
        if ("search_content".equals(toolName)) {
            return "正在搜索文件内容";
        }
        if ("edit_code".equals(toolName)) {
            return "正在准备修改代码";
        }
        if ("write_file".equals(toolName)) {
            return "正在写入文件";
        }
        if ("edit_file".equals(toolName)) {
            return "正在精确替换文件内容";
        }
        if ("apply_structured_patch".equals(toolName)) {
            return "正在应用结构化补丁";
        }
        if ("delete_file".equals(toolName)) {
            return "正在删除文件";
        }
        return "";
    }

    private String mapToolExecutingMessage(String toolName) {
        if ("write_file".equals(toolName)) {
            return "正在写入文件";
        }
        if ("edit_file".equals(toolName)) {
            return "正在替换文件内容";
        }
        if ("apply_structured_patch".equals(toolName)) {
            return "正在应用结构化补丁";
        }
        if ("edit_code".equals(toolName)) {
            return "正在修改代码";
        }
        if ("delete_file".equals(toolName)) {
            return "正在删除文件";
        }
        return "";
    }

    private String summarizeToolResult(ToolInvocation request, ToolResult result) {
        String toolName = request.getToolName();
        if (!result.isSuccess()) {
            return summarizeToolFailure(toolName, result.getErrorMessage());
        }
        if (!isBlank(result.getSummary())) {
            return result.getSummary();
        }
        if ("project_map".equals(toolName)) {
            return summarizeProjectMap(result.getContent());
        }
        if ("edit_code".equals(toolName)) {
            return summarizeEditCode(result.getContent());
        }
        if ("read_file".equals(toolName)) {
            return summarizeReadFile(request, result.getContent());
        }
        if ("list_workspace".equals(toolName)) {
            return summarizeWorkspaceList(result.getContent());
        }
        if ("search_content".equals(toolName) || "query_api_info".equals(toolName)) {
            return summarizeSearch(toolName, result.getContent());
        }
        if ("write_file".equals(toolName)) {
            return "文件写入完成";
        }
        if ("edit_file".equals(toolName)) {
            return "文件精确替换完成";
        }
        if ("apply_structured_patch".equals(toolName)) {
            return "结构化补丁应用完成";
        }
        if ("delete_file".equals(toolName)) {
            return "文件删除完成";
        }
        return toolName + " 已执行完成";
    }

    private String summarizeToolFailure(String toolName, String errorMessage) {
        String baseName = isBlank(toolName) ? "工具" : toolName;
        if (isBlank(errorMessage)) {
            return baseName + " 执行失败";
        }
        return baseName + " 执行失败：" + compact(errorMessage);
    }

    private String summarizeProjectMap(String content) {
        JsonNode payload = extractJsonPayload(content);
        if (payload == null) {
            return "项目结构梳理完成";
        }
        int directories = payload.path("directories").asInt(-1);
        int files = payload.path("files").asInt(-1);
        int keyFiles = payload.path("keyFiles").isArray() ? payload.path("keyFiles").size() : 0;
        String root = payload.path("root").asText(".");
        return "项目结构梳理完成：范围 " + root
                + "，目录 " + nonNegative(directories)
                + " 个，文件 " + nonNegative(files)
                + " 个，关键文件 " + keyFiles + " 个";
    }

    private String summarizeEditCode(String content) {
        JsonNode payload = extractJsonPayload(content);
        if (payload == null) {
            return "代码修改完成";
        }
        String file = baseName(payload.path("file").asText(""));
        String mode = toEditModeLabel(payload.path("mode").asText(""));
        String summary = payload.path("summary").asText("");
        boolean changed = payload.path("changed").asBoolean(true);
        StringBuilder builder = new StringBuilder();
        builder.append(changed ? "代码修改完成" : "代码未发生变化");
        if (!isBlank(file)) {
            builder.append("：").append(file);
        }
        if (!isBlank(mode)) {
            builder.append("（").append(mode).append("）");
        }
        if (!isBlank(summary)) {
            builder.append("，").append(summary);
        }
        return builder.toString();
    }

    private String summarizeReadFile(ToolInvocation request, String content) {
        JsonNode payload = extractJsonPayload(content);
        String file = readArgument(request, "path");
        if (payload == null) {
            return isBlank(file) ? "文件读取完成" : "文件读取完成：" + baseName(file);
        }
        int startLine = payload.path("startLine").asInt(0);
        int endLine = payload.path("endLine").asInt(0);
        if (isBlank(file)) {
            file = payload.path("path").asText("");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("文件读取完成");
        if (!isBlank(file)) {
            builder.append("：").append(baseName(file));
        }
        if (startLine > 0 && endLine >= startLine) {
            builder.append("，第 ").append(startLine).append("-").append(endLine).append(" 行");
        }
        return builder.toString();
    }

    private String summarizeWorkspaceList(String content) {
        JsonNode payload = extractJsonPayload(content);
        if (payload == null) {
            return "工作目录查看完成";
        }
        int returned = payload.path("returnedCount").asInt(-1);
        int total = payload.path("totalCount").asInt(payload.path("totalDiscovered").asInt(-1));
        String root = payload.path("root").asText(payload.path("path").asText("."));
        return "工作目录查看完成：范围 " + root
                + "，返回 " + nonNegative(returned)
                + " 项，共 " + nonNegative(total) + " 项";
    }

    private String summarizeSearch(String toolName, String content) {
        JsonNode payload = extractJsonPayload(content);
        if (payload == null) {
            return "search_content".equals(toolName) ? "文件内容搜索完成" : "接口信息查询完成";
        }
        int totalMatches = payload.path("totalMatches").asInt(-1);
        String keyword = payload.path("keyword").asText("");
        StringBuilder builder = new StringBuilder();
        builder.append("query_api_info".equals(toolName) ? "接口信息查询完成" : "文件内容搜索完成");
        if (!isBlank(keyword)) {
            builder.append("：").append(keyword);
        }
        if (totalMatches >= 0) {
            builder.append("，命中 ").append(totalMatches).append(" 处");
        }
        return builder.toString();
    }

    private JsonNode extractJsonPayload(String content) {
        if (isBlank(content)) {
            return null;
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return null;
        }
        try {
            return objectMapper.readTree(content.substring(start));
        } catch (Exception exception) {
            return null;
        }
    }

    private String toEditModeLabel(String mode) {
        if ("APPEND".equals(mode)) {
            return "追加";
        }
        if ("INSERT_BEFORE".equals(mode)) {
            return "前插";
        }
        if ("INSERT_AFTER".equals(mode)) {
            return "后插";
        }
        if ("REPLACE_TEXT".equals(mode)) {
            return "精确替换";
        }
        if ("REPLACE_BETWEEN_MARKERS".equals(mode)) {
            return "区间替换";
        }
        if ("REPLACE_FILE".equals(mode)) {
            return "整文件替换";
        }
        return mode;
    }

    private String toUserFacingSecurityMessage(String message) {
        if (isBlank(message)) {
            return "";
        }
        if (message.startsWith("Parse error:")) {
            return "模型回复格式不稳定，系统正在自动重试。";
        }
        if (message.startsWith("Response contract failed:")) {
            return "模型回复没有满足输出约束，系统正在自动纠正。";
        }
        return message;
    }

    private int nonNegative(int value) {
        return value < 0 ? 0 : value;
    }

    private String readArgument(ToolInvocation request, String key) {
        if (request == null || request.getArguments() == null) {
            return "";
        }
        Object value = request.getArguments().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String baseName(String path) {
        if (isBlank(path)) {
            return "";
        }
        String normalized = path.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private boolean sameUserFacingText(String left, String right) {
        if (isBlank(left) || isBlank(right)) {
            return false;
        }
        return normalizeUserFacingText(left).equals(normalizeUserFacingText(right));
    }

    private String normalizeUserFacingText(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "").replace(" ", "").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum LineMode {
        THINKING,
        ASSISTANT
    }

    private ThreadFactory newProgressThreadFactory() {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "console-session-progress");
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    private static final class ToolProgressState {
        private final String label;
        private int percent = 1;
        private int lastRenderedLength;
        private ScheduledFuture<?> future;

        private ToolProgressState(String label) {
            this.label = label;
        }
    }
}
