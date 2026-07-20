package com.codey.web.api;

import com.codey.web.common.ApiResponse;
import com.codey.web.config.WebDemoProperties;
import com.codey.web.service.ChatSessionLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codey.tools.ToolRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为前端提供历史会话与请求日志读取能力。
 * 优先读取 demo 目录下的 sessions/model-inputs，同时兼容 codey.session-directory 配置。
 */
@RestController
@RequestMapping("/api/chat/history")
public class SessionArchiveController {
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final String configuredSessionDirectory;
    private final ChatSessionLifecycleService chatSessionLifecycleService;

    public SessionArchiveController(ObjectMapper objectMapper,
                                    ToolRegistry toolRegistry,
                                    WebDemoProperties properties,
                                    ChatSessionLifecycleService chatSessionLifecycleService) {
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        // 历史归档目录与 Web Demo 其他目录配置统一收口到 WebDemoProperties，避免默认值失效。
        this.configuredSessionDirectory = properties.getSessionDirectory();
        this.chatSessionLifecycleService = chatSessionLifecycleService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionArchiveSummary>> listSessions() {
        Path requestRoot = resolveRequestArchiveRoot();
        if (requestRoot == null) {
            return ApiResponse.success("历史会话列表读取成功", Collections.<SessionArchiveSummary>emptyList());
        }
        Path modelInputsRoot = requestRoot.resolve("model-inputs");
        if (!Files.isDirectory(modelInputsRoot)) {
            return ApiResponse.success("历史会话列表读取成功", Collections.<SessionArchiveSummary>emptyList());
        }

        List<SessionArchiveSummary> sessions = new ArrayList<SessionArchiveSummary>();
        Path eventRoot = resolveEventArchiveRoot(requestRoot);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelInputsRoot)) {
            for (Path sessionDir : stream) {
                if (!Files.isDirectory(sessionDir)) {
                    continue;
                }
                SessionArchiveSummary summary = buildSessionSummary(sessionDir, eventRoot);
                if (summary != null) {
                    sessions.add(summary);
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取历史会话失败", exception);
        }

        Collections.sort(sessions, new Comparator<SessionArchiveSummary>() {
            @Override
            public int compare(SessionArchiveSummary left, SessionArchiveSummary right) {
                return right.getUpdatedAt().compareTo(left.getUpdatedAt());
            }
        });
        return ApiResponse.success("历史会话列表读取成功", sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionArchiveDetail> getSessionDetail(@PathVariable("sessionId") String sessionId) {
        if (isBlank(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId 不能为空");
        }

        Path requestRoot = resolveRequestArchiveRoot();
        if (requestRoot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到历史会话目录");
        }
        Path sessionDir = requestRoot.resolve("model-inputs").resolve(sessionId);
        if (!Files.isDirectory(sessionDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定会话");
        }

        Path eventRoot = resolveEventArchiveRoot(requestRoot);
        Path modelOutputRoot = resolveModelOutputArchiveRoot(requestRoot);
        SessionArchiveSummary summary = buildSessionSummary(sessionDir, eventRoot);
        if (summary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定会话");
        }

        List<RequestLogEntry> requestLogs = readRequestLogs(sessionDir);
        List<ArchivedMessage> messages = buildArchivedMessages(summary.getSessionId(), requestLogs, modelOutputRoot);
        ResumeContext resumeContext = buildResumeContext(summary.getSessionId(), messages);
        return ApiResponse.success(
                "历史会话详情读取成功",
                new SessionArchiveDetail(summary, messages, requestLogs, resumeContext)
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable("sessionId") String sessionId) {
        if (isBlank(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId 不能为空");
        }
        Path requestRoot = resolveRequestArchiveRoot();
        if (requestRoot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到历史会话目录");
        }
        Path sessionDir = requestRoot.resolve("model-inputs").resolve(sessionId);
        if (!Files.isDirectory(sessionDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定会话");
        }
        try {
            chatSessionLifecycleService.deleteSessionContent(sessionId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    /**
     * 前端业务口径统一使用 POST；历史清理在保留 DELETE 的同时补一层 POST 入口。
     */
    @PostMapping("/sessions/{sessionId}/delete")
    public ApiResponse<Void> deleteSessionByPost(@PathVariable("sessionId") String sessionId) {
        deleteSession(sessionId);
        return ApiResponse.success("历史会话已删除", null);
    }

    @DeleteMapping("/sessions")
    public void clearSessions() {
        Path requestRoot = resolveRequestArchiveRoot();
        if (requestRoot == null) {
            return;
        }
        try {
            chatSessionLifecycleService.clearSessionContent();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    @PostMapping("/sessions/clear")
    public ApiResponse<Void> clearSessionsByPost() {
        clearSessions();
        return ApiResponse.success("历史会话已清空", null);
    }

    private SessionArchiveSummary buildSessionSummary(Path sessionDir, Path eventRoot) {
        List<Path> requestFiles = listRequestFiles(sessionDir);
        if (requestFiles.isEmpty()) {
            return null;
        }

        Path latestRequestFile = requestFiles.get(requestFiles.size() - 1);
        JsonNode latestRequestNode = readJsonFile(latestRequestFile);
        JsonNode requestNode = unwrapRequestNode(latestRequestNode);
        JsonNode messagesNode = requestNode.path("messages");
        String lastUserMessage = extractLastMessage(messagesNode, "user");
        String lastAssistantMessage = extractLastMessage(messagesNode, "assistant");
        Date updatedAt = readLastModified(latestRequestFile);
        int displayMessageCount = countDisplayMessages(messagesNode);
        ArchivedMessage latestModelOutput = readLatestModelOutputMessage(
                sessionDir.getFileName().toString(),
                resolveModelOutputArchiveRoot(resolveRequestArchiveRoot())
        );
        if (shouldAppendLatestModelOutput(Collections.<ArchivedMessage>emptyList(), latestModelOutput)) {
            lastAssistantMessage = defaultString(latestModelOutput.getContent());
            displayMessageCount += 1;
        }

        boolean hasEventLog = eventRoot != null && Files.exists(eventRoot.resolve(sessionDir.getFileName().toString() + ".jsonl"));
        String title = truncate(isBlank(lastUserMessage) ? sessionDir.getFileName().toString() : lastUserMessage, 32);
        String preview = truncate(isBlank(lastAssistantMessage) ? lastUserMessage : lastAssistantMessage, 96);
        return new SessionArchiveSummary(
                sessionDir.getFileName().toString(),
                title,
                defaultString(preview),
                updatedAt,
                requestFiles.size(),
                displayMessageCount,
                hasEventLog,
                sanitizeWorkingDirectory(extractWorkingDirectory(requestNode))
        );
    }

    private List<RequestLogEntry> readRequestLogs(Path sessionDir) {
        List<Path> requestFiles = listRequestFiles(sessionDir);
        List<RequestLogEntry> requestLogs = new ArrayList<RequestLogEntry>();
        for (Path requestFile : requestFiles) {
            JsonNode rootNode = readJsonFile(requestFile);
            JsonNode requestNode = unwrapRequestNode(rootNode);
            JsonNode messagesNode = requestNode.path("messages");
            requestLogs.add(new RequestLogEntry(
                    requestFile.getFileName().toString(),
                    parseSequence(requestFile.getFileName().toString()),
                    readLastModified(requestFile),
                    defaultString(extractLastMessage(messagesNode, "user")),
                    countDisplayMessages(messagesNode),
                    writePrettyJson(rootNode)
            ));
        }
        Collections.reverse(requestLogs);
        return requestLogs;
    }

    private List<ArchivedMessage> buildArchivedMessages(String sessionId, List<RequestLogEntry> requestLogs, Path modelOutputRoot) {
        if (requestLogs.isEmpty()) {
            return Collections.emptyList();
        }

        RequestLogEntry latestRequestLog = requestLogs.get(0);
        JsonNode latestRootNode = readJsonContent(latestRequestLog.getContent());
        JsonNode messagesNode = unwrapRequestNode(latestRootNode).path("messages");
        if (!messagesNode.isArray()) {
            return Collections.emptyList();
        }

        List<ArchivedMessage> messages = new ArrayList<ArchivedMessage>();
        for (JsonNode messageNode : messagesNode) {
            String role = textValue(messageNode, "role");
            if (isBlank(role) || "system".equals(role)) {
                continue;
            }
            String toolName = defaultString(textValue(messageNode, "name"));
            List<String> toolCalls = extractToolCalls(messageNode.path("tool_calls"));
            messages.add(new ArchivedMessage(
                    role,
                    defaultString(textValue(messageNode, "content")),
                    defaultString(textValue(messageNode, "reasoning_content")),
                    toolName,
                    resolveToolDisplayName(toolName),
                    toolCalls
            ));
        }
        // 最新一次模型输出通常只会落在 model-outputs 中，不一定会回写到最后一个 model-input 快照。
        ArchivedMessage latestModelOutput = readLatestModelOutputMessage(sessionId, modelOutputRoot);
        if (mergeLatestModelOutput(messages, latestModelOutput)) {
            return messages;
        }
        if (shouldAppendLatestModelOutput(messages, latestModelOutput)) {
            messages.add(latestModelOutput);
        }
        return messages;
    }

    private ResumeContext buildResumeContext(String sessionId, List<ArchivedMessage> messages) {
        List<String> chatHistory = new ArrayList<String>();
        if (messages != null) {
            for (ArchivedMessage message : messages) {
                if (message == null) {
                    continue;
                }
                String role = defaultString(message.getRole());
                String content = defaultString(message.getContent()).trim();
                if (isBlank(content)) {
                    continue;
                }
                if ("user".equals(role)) {
                    chatHistory.add("用户: " + content);
                    continue;
                }
                if ("assistant".equals(role)) {
                    chatHistory.add("助手: " + content);
                }
            }
        }
        return new ResumeContext(sessionId, chatHistory);
    }

    /**
     * 兼容两种历史格式：
     * 1. 结构化请求里直接带 workingDirectory
     * 2. 用户补充上下文里通过“AI工作目录: xxx”注入
     */
    private String extractWorkingDirectory(JsonNode requestNode) {
        String structuredValue = textValue(requestNode, "workingDirectory");
        if (!isBlank(structuredValue)) {
            return structuredValue;
        }
        JsonNode messagesNode = requestNode.path("messages");
        if (!messagesNode.isArray()) {
            return "";
        }
        for (int index = messagesNode.size() - 1; index >= 0; index--) {
            JsonNode messageNode = messagesNode.get(index);
            if (messageNode == null || !"user".equals(defaultString(textValue(messageNode, "role")))) {
                continue;
            }
            String content = defaultString(textValue(messageNode, "content"));
            String parsedValue = extractWorkingDirectoryFromContent(content);
            if (!isBlank(parsedValue)) {
                return parsedValue;
            }
        }
        return "";
    }

    private String extractWorkingDirectoryFromContent(String content) {
        if (isBlank(content)) {
            return "";
        }
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String candidate = line.trim();
            if (candidate.startsWith("- AI工作目录:")) {
                return candidate.substring("- AI工作目录:".length()).trim();
            }
            if (candidate.startsWith("AI工作目录:")) {
                return candidate.substring("AI工作目录:".length()).trim();
            }
            if (candidate.startsWith("- workingDirectory:")) {
                return candidate.substring("- workingDirectory:".length()).trim();
            }
            if (candidate.startsWith("workingDirectory:")) {
                return candidate.substring("workingDirectory:".length()).trim();
            }
        }
        return "";
    }

    private List<EventLogEntry> readEventLogEntries(String sessionId, Path eventRoot) {
        if (eventRoot == null) {
            return Collections.emptyList();
        }
        Path eventFile = eventRoot.resolve(sessionId + ".jsonl");
        if (!Files.exists(eventFile)) {
            return Collections.emptyList();
        }

        List<EventLogEntry> eventLogs = new ArrayList<EventLogEntry>();
        try {
            List<String> lines = Files.readAllLines(eventFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (isBlank(line)) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> payload = new LinkedHashMap<String, Object>();
                JsonNode payloadNode = node.path("payload");
                if (!payloadNode.isMissingNode() && !payloadNode.isNull()) {
                    payload = objectMapper.convertValue(payloadNode, Map.class);
                }
                eventLogs.add(new EventLogEntry(
                        defaultString(textValue(node, "eventType")),
                        defaultString(textValue(node, "stage")),
                        defaultString(textValue(node, "message")),
                        defaultString(textValue(node, "timestamp")),
                        payload
                ));
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取事件日志失败", exception);
        }
        return eventLogs;
    }

    private void deleteSessionArchives(String sessionId, Path requestRoot, Path eventRoot, Path modelOutputRoot) {
        deleteRecursively(requestRoot.resolve("model-inputs").resolve(sessionId));
        if (modelOutputRoot != null) {
            deleteRecursively(modelOutputRoot.resolve(sessionId));
        }
        if (eventRoot != null) {
            deleteIfExists(eventRoot.resolve(sessionId + ".jsonl"));
        }
    }

    private void clearArchiveDirectory(Path archiveRoot) {
        if (archiveRoot == null || !Files.isDirectory(archiveRoot)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(archiveRoot)) {
            for (Path entry : stream) {
                deleteRecursively(entry);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "清理历史会话失败", exception);
        }
    }

    private void clearEventLogs(Path eventRoot) {
        if (eventRoot == null || !Files.isDirectory(eventRoot)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(eventRoot, "*.jsonl")) {
            for (Path eventFile : stream) {
                deleteIfExists(eventFile);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "清理事件日志失败", exception);
        }
    }

    private Path resolveRequestArchiveRoot() {
        List<Path> candidates = new ArrayList<Path>();
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        addCandidate(candidates, workingDir.resolve("sessions"));
        addCandidate(candidates, workingDir.resolve("codey-web-demo").resolve("sessions"));
        Path parent = workingDir.getParent();
        if (parent != null) {
            addCandidate(candidates, parent.resolve("codey-web-demo").resolve("sessions"));
        }
        addCandidate(candidates, configuredPath(configuredSessionDirectory));
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate.resolve("model-inputs"))) {
                return candidate;
            }
        }
        return null;
    }

    private Path resolveEventArchiveRoot(Path requestRoot) {
        List<Path> candidates = new ArrayList<Path>();
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        addCandidate(candidates, configuredPath(configuredSessionDirectory));
        addCandidate(candidates, workingDir.resolve("codey").resolve("sessions"));
        Path parent = workingDir.getParent();
        if (parent != null) {
            addCandidate(candidates, parent.resolve("codey").resolve("sessions"));
        }
        addCandidate(candidates, requestRoot);
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Path resolveModelOutputArchiveRoot(Path requestRoot) {
        List<Path> candidates = new ArrayList<Path>();
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        addCandidate(candidates, requestRoot);
        addCandidate(candidates, configuredPath(configuredSessionDirectory));
        addCandidate(candidates, workingDir.resolve("sessions"));
        addCandidate(candidates, workingDir.resolve("codey-web-demo").resolve("sessions"));
        Path parent = workingDir.getParent();
        if (parent != null) {
            addCandidate(candidates, parent.resolve("codey-web-demo").resolve("sessions"));
            addCandidate(candidates, parent.resolve("codey").resolve("sessions"));
        }
        addCandidate(candidates, workingDir.resolve("codey").resolve("sessions"));
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate.resolve("model-outputs"))) {
                return candidate.resolve("model-outputs");
            }
        }
        return null;
    }

    private List<Path> listRequestFiles(Path sessionDir) {
        List<Path> requestFiles = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionDir, "*.json")) {
            for (Path requestFile : stream) {
                if (Files.isRegularFile(requestFile)) {
                    requestFiles.add(requestFile);
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取请求日志失败", exception);
        }

        Collections.sort(requestFiles, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareTo(right.getFileName().toString());
            }
        });
        return requestFiles;
    }

    private List<Path> listStructuredJsonFiles(Path sessionDir) {
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionDir, "*.json")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取结构化日志失败", exception);
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareTo(right.getFileName().toString());
            }
        });
        return files;
    }

    // 递归删除单个会话目录，确保请求快照和模型输出都能一起移除。
    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除历史会话失败", exception);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除历史会话失败", exception);
        }
    }

    private JsonNode readJsonFile(Path file) {
        try {
            return objectMapper.readTree(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析日志文件失败: " + file.getFileName(), exception);
        }
    }

    private JsonNode readJsonContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析日志内容失败", exception);
        }
    }

    private JsonNode unwrapRequestNode(JsonNode rootNode) {
        JsonNode payloadNode = rootNode.path("payload");
        if (!payloadNode.isMissingNode() && !payloadNode.isNull() && payloadNode.isObject()) {
            return payloadNode;
        }
        return rootNode;
    }

    private ArchivedMessage readLatestModelOutputMessage(String sessionId, Path modelOutputRoot) {
        if (isBlank(sessionId) || modelOutputRoot == null) {
            return null;
        }
        Path sessionDir = modelOutputRoot.resolve(sessionId);
        if (!Files.isDirectory(sessionDir)) {
            return null;
        }
        List<Path> outputFiles = listStructuredJsonFiles(sessionDir);
        for (int index = outputFiles.size() - 1; index >= 0; index--) {
            JsonNode rootNode = readJsonFile(outputFiles.get(index));
            String rawOutput = textValue(rootNode.path("payload"), "rawOutput");
            if (isBlank(rawOutput)) {
                rawOutput = defaultString(textValue(rootNode, "message"));
            }
            ArchivedMessage outputMessage = parseModelOutputMessage(rawOutput);
            if (outputMessage != null) {
                return outputMessage;
            }
        }
        return null;
    }

    private ArchivedMessage parseModelOutputMessage(String rawOutput) {
        if (isBlank(rawOutput)) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(rawOutput);
            JsonNode choiceNode = rootNode.path("choices").path(0);
            JsonNode messageNode = choiceNode.path("message");
            String finishReason = textValue(choiceNode, "finish_reason");
            String content = defaultString(textValue(messageNode, "content"));
            String reasoning = defaultString(textValue(messageNode, "reasoning_content"));
            List<String> toolCalls = extractToolCalls(messageNode.path("tool_calls"));
            if ("tool_calls".equals(finishReason) && isBlank(content) && isBlank(reasoning)) {
                return null;
            }
            if (isBlank(content) && isBlank(reasoning) && toolCalls.isEmpty()) {
                return null;
            }
            return new ArchivedMessage("assistant", content, reasoning, "", "", toolCalls);
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean shouldAppendLatestModelOutput(List<ArchivedMessage> messages, ArchivedMessage latestModelOutput) {
        if (latestModelOutput == null) {
            return false;
        }
        if (messages == null || messages.isEmpty()) {
            return true;
        }
        ArchivedMessage lastMessage = messages.get(messages.size() - 1);
        if (lastMessage == null || !"assistant".equals(lastMessage.getRole())) {
            return true;
        }
        if (!sameAssistantReply(lastMessage, latestModelOutput)) {
            return true;
        }
        return false;
    }

    /**
     * 如果最后一条助手消息与最新模型输出本质上是同一轮回复，就把缺失的思考和工具调用补齐，
     * 避免刷新历史时把同一条回复显示成两条。
     */
    private boolean mergeLatestModelOutput(List<ArchivedMessage> messages, ArchivedMessage latestModelOutput) {
        if (messages == null || messages.isEmpty() || latestModelOutput == null) {
            return false;
        }
        ArchivedMessage lastMessage = messages.get(messages.size() - 1);
        if (lastMessage == null || !"assistant".equals(lastMessage.getRole())) {
            return false;
        }
        if (!sameAssistantReply(lastMessage, latestModelOutput)) {
            return false;
        }
        String mergedReasoning = defaultString(lastMessage.getReasoning());
        if (isBlank(lastMessage.getReasoning()) && !isBlank(latestModelOutput.getReasoning())) {
            mergedReasoning = latestModelOutput.getReasoning();
        }
        List<String> mergedToolCalls = defaultToolCalls(lastMessage.getToolCalls());
        if (defaultToolCalls(lastMessage.getToolCalls()).isEmpty()
                && !defaultToolCalls(latestModelOutput.getToolCalls()).isEmpty()) {
            mergedToolCalls = new ArrayList<String>(latestModelOutput.getToolCalls());
        }
        messages.set(messages.size() - 1, new ArchivedMessage(
                lastMessage.getRole(),
                lastMessage.getContent(),
                mergedReasoning,
                lastMessage.getName(),
                lastMessage.getDisplayName(),
                mergedToolCalls
        ));
        return true;
    }

    /**
     * 同一轮助手回复以正文一致为主判定，思考和工具调用允许后补，避免仅因补齐字段而重复展示。
     */
    private boolean sameAssistantReply(ArchivedMessage left, ArchivedMessage right) {
        if (left == null || right == null) {
            return false;
        }
        if (!"assistant".equals(left.getRole()) || !"assistant".equals(right.getRole())) {
            return false;
        }
        String leftContent = defaultString(left.getContent());
        String rightContent = defaultString(right.getContent());
        if (!isBlank(leftContent) || !isBlank(rightContent)) {
            return leftContent.equals(rightContent);
        }
        String leftReasoning = defaultString(left.getReasoning());
        String rightReasoning = defaultString(right.getReasoning());
        if (!isBlank(leftReasoning) || !isBlank(rightReasoning)) {
            return leftReasoning.equals(rightReasoning);
        }
        return defaultToolCalls(left.getToolCalls()).equals(defaultToolCalls(right.getToolCalls()));
    }

    private List<String> defaultToolCalls(List<String> toolCalls) {
        return toolCalls == null ? Collections.<String>emptyList() : toolCalls;
    }

    /**
     * 历史日志保留原始工具名，同时补一个稳定 displayName 供前端展示。
     */
    private String resolveToolDisplayName(String toolName) {
        if (isBlank(toolName)) {
            return "";
        }
        String displayName = toolRegistry == null ? null : toolRegistry.resolveDisplayName(toolName);
        return isBlank(displayName) ? toolName : displayName;
    }

    private List<String> extractToolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> toolCalls = new ArrayList<String>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String name = textValue(toolCallNode.path("function"), "name");
            if (!isBlank(name)) {
                toolCalls.add(resolveToolDisplayName(name));
            }
        }
        return toolCalls;
    }

    private String extractLastMessage(JsonNode messagesNode, String role) {
        if (!messagesNode.isArray()) {
            return "";
        }
        for (int index = messagesNode.size() - 1; index >= 0; index--) {
            JsonNode messageNode = messagesNode.get(index);
            if (role.equals(textValue(messageNode, "role"))) {
                return defaultString(textValue(messageNode, "content"));
            }
        }
        return "";
    }

    private int countDisplayMessages(JsonNode messagesNode) {
        if (!messagesNode.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode messageNode : messagesNode) {
            String role = textValue(messageNode, "role");
            if (!isBlank(role) && !"system".equals(role)) {
                count++;
            }
        }
        return count;
    }

    private String writePrettyJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "格式化日志失败", exception);
        }
    }

    private Date readLastModified(Path file) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(file);
            return new Date(fileTime.toMillis());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取文件时间失败", exception);
        }
    }

    private int parseSequence(String fileName) {
        if (isBlank(fileName) || fileName.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(fileName.substring(0, 4));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Path configuredPath(String value) {
        if (isBlank(value)) {
            return null;
        }
        return Paths.get(value).toAbsolutePath().normalize();
    }

    /**
     * 历史记录里只回传相对目录，避免前端恢复会话时暴露服务端绝对路径。
     */
    private String sanitizeWorkingDirectory(String value) {
        if (isBlank(value)) {
            return "";
        }
        String candidate = value.trim();
        try {
            Path path = Paths.get(candidate);
            if (!path.isAbsolute()) {
                return normalizeSeparators(path.normalize().toString());
            }
            Path workingDir = Paths.get("").toAbsolutePath().normalize();
            Path relative = workingDir.relativize(path.toAbsolutePath().normalize());
            String displayPath = normalizeSeparators(relative.toString());
            return isBlank(displayPath) ? "." : displayPath;
        } catch (Exception exception) {
            return candidate.replace('\\', '/');
        }
    }

    private void addCandidate(List<Path> candidates, Path candidate) {
        if (candidate != null && !candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? "" : valueNode.asText("");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return defaultString(value);
        }
        return value.substring(0, maxLength) + "...";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalizeSeparators(String value) {
        return defaultString(value).replace('\\', '/');
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 历史会话列表项。
     */
    public static class SessionArchiveSummary {
        private final String sessionId;
        private final String title;
        private final String preview;
        private final Date updatedAt;
        private final int requestLogCount;
        private final int messageCount;
        private final boolean hasEventLog;
        private final String workingDirectory;

        public SessionArchiveSummary(String sessionId,
                                     String title,
                                     String preview,
                                     Date updatedAt,
                                     int requestLogCount,
                                     int messageCount,
                                     boolean hasEventLog,
                                     String workingDirectory) {
            this.sessionId = sessionId;
            this.title = title;
            this.preview = preview;
            this.updatedAt = updatedAt;
            this.requestLogCount = requestLogCount;
            this.messageCount = messageCount;
            this.hasEventLog = hasEventLog;
            this.workingDirectory = workingDirectory;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getTitle() {
            return title;
        }

        public String getPreview() {
            return preview;
        }

        public Date getUpdatedAt() {
            return updatedAt;
        }

        public int getRequestLogCount() {
            return requestLogCount;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public boolean isHasEventLog() {
            return hasEventLog;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }
    }

    /**
     * 历史会话详情。
     */
    public static class SessionArchiveDetail {
        private final SessionArchiveSummary session;
        private final List<ArchivedMessage> messages;
        private final List<RequestLogEntry> requestLogs;
        private final ResumeContext resumeContext;

        public SessionArchiveDetail(SessionArchiveSummary session,
                                    List<ArchivedMessage> messages,
                                    List<RequestLogEntry> requestLogs,
                                    ResumeContext resumeContext) {
            this.session = session;
            this.messages = messages;
            this.requestLogs = requestLogs;
            this.resumeContext = resumeContext;
        }

        public SessionArchiveSummary getSession() {
            return session;
        }

        public List<ArchivedMessage> getMessages() {
            return messages;
        }

        public List<RequestLogEntry> getRequestLogs() {
            return requestLogs;
        }

        public ResumeContext getResumeContext() {
            return resumeContext;
        }
    }

    /**
     * 历史会话恢复上下文。
     * 前端可以直接用该载荷重新打开同一 sessionId，并延续已有 chat history。
     */
    public static class ResumeContext {
        private final String sessionId;
        private final List<String> chatHistory;

        public ResumeContext(String sessionId, List<String> chatHistory) {
            this.sessionId = sessionId;
            this.chatHistory = chatHistory == null ? Collections.<String>emptyList() : chatHistory;
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<String> getChatHistory() {
            return chatHistory;
        }
    }

    /**
     * 前端消息视图项。
     */
    public static class ArchivedMessage {
        private final String role;
        private final String content;
        private final String reasoning;
        private final String name;
        private final String displayName;
        private final List<String> toolCalls;

        public ArchivedMessage(String role,
                               String content,
                               String reasoning,
                               String name,
                               String displayName,
                               List<String> toolCalls) {
            this.role = role;
            this.content = content;
            this.reasoning = reasoning;
            this.name = name;
            this.displayName = displayName;
            this.toolCalls = toolCalls;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getToolCalls() {
            return toolCalls;
        }
    }

    /**
     * 请求日志条目。
     */
    public static class RequestLogEntry {
        private final String fileName;
        private final int sequence;
        private final Date updatedAt;
        private final String userMessage;
        private final int messageCount;
        private final String content;

        public RequestLogEntry(String fileName,
                               int sequence,
                               Date updatedAt,
                               String userMessage,
                               int messageCount,
                               String content) {
            this.fileName = fileName;
            this.sequence = sequence;
            this.updatedAt = updatedAt;
            this.userMessage = userMessage;
            this.messageCount = messageCount;
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public int getSequence() {
            return sequence;
        }

        public Date getUpdatedAt() {
            return updatedAt;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * 历史事件日志条目。
     */
    public static class EventLogEntry {
        private final String type;
        private final String stage;
        private final String message;
        private final String timestamp;
        private final Map<String, Object> payload;

        public EventLogEntry(String type,
                             String stage,
                             String message,
                             String timestamp,
                             Map<String, Object> payload) {
            this.type = type;
            this.stage = stage;
            this.message = message;
            this.timestamp = timestamp;
            this.payload = payload;
        }

        public String getType() {
            return type;
        }

        public String getStage() {
            return stage;
        }

        public String getMessage() {
            return message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
