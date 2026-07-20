package com.codey.web.service;

import com.codey.client.AgentClient;
import com.codey.client.SessionEventHub;
import com.codey.starter.SpringProperties;
import com.codey.web.api.ChatSessionDisplayOptionsStore;
import com.codey.web.config.WebDemoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一管理会话生命周期，补齐临时/永久会话的落盘元数据与清理逻辑。
 */
@Service
public class ChatSessionLifecycleService {
    private static final String SESSION_TYPE_TEMPORARY = "temporary";
    private static final String SESSION_TYPE_PERSISTENT = "persistent";

    private final AgentClient agentClient;
    private final SessionEventHub sessionEventHub;
    private final ChatSessionDisplayOptionsStore displayOptionsStore;
    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;
    private final Path preferredSessionRoot;
    private final Path demoSessionRoot;
    private final long temporarySessionIdleExpireMillis;

    public ChatSessionLifecycleService(AgentClient agentClient,
                                       SessionEventHub sessionEventHub,
                                       ChatSessionDisplayOptionsStore displayOptionsStore,
                                       ObjectMapper objectMapper,
                                       WebDemoProperties webDemoProperties,
                                       SpringProperties springProperties,
                                       @Value("${codey.temporary-session.idle-expire-seconds:3600}") long temporarySessionIdleExpireSeconds) {
        this.agentClient = agentClient;
        this.sessionEventHub = sessionEventHub;
        this.displayOptionsStore = displayOptionsStore;
        this.objectMapper = objectMapper;
        this.workspaceRoot = webDemoProperties.resolveWorkingDirectoryRoot();
        this.preferredSessionRoot = springProperties.resolveSessionDirectoryRoot(this.workspaceRoot);
        this.demoSessionRoot = webDemoProperties.resolveSessionDirectoryRoot(this.workspaceRoot);
        this.temporarySessionIdleExpireMillis = Math.max(temporarySessionIdleExpireSeconds, 1L) * 1000L;
    }

    /**
     * 只在建会话时判定一次类型，后续轮次即使 workingDirectory 变成 sessionId 也不改类型。
     */
    public void registerSession(String sessionId, String requestedWorkingDirectory) {
        if (isBlank(sessionId)) {
            return;
        }
        long now = System.currentTimeMillis();
        SessionMetadata metadata = readSessionMetadata(sessionId);
        if (metadata == null) {
            metadata = new SessionMetadata();
            metadata.setSessionId(sessionId);
            metadata.setCreatedAt(now);
        }
        if (isBlank(metadata.getSessionType())) {
            metadata.setSessionType(isBlank(requestedWorkingDirectory) ? SESSION_TYPE_TEMPORARY : SESSION_TYPE_PERSISTENT);
        }
        if (isBlank(metadata.getWorkingDirectory())) {
            metadata.setWorkingDirectory(resolveStoredWorkingDirectory(sessionId, requestedWorkingDirectory, metadata.getSessionType()));
        }
        metadata.setLastActiveAt(now);
        writeSessionMetadata(metadata);
    }

    public void touchSession(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        long now = System.currentTimeMillis();
        SessionMetadata metadata = readSessionMetadata(sessionId);
        if (metadata == null) {
            metadata = buildFallbackMetadata(sessionId);
            metadata.setCreatedAt(now);
        }
        metadata.setLastActiveAt(now);
        writeSessionMetadata(metadata);
    }

    public void deleteSessionContent(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        boolean temporarySession = isTemporarySession(sessionId);
        clearRuntimeState(sessionId);
        deleteArchives(sessionId);
        if (temporarySession) {
            deleteTemporaryWorkspace(sessionId);
        }
    }

    public void clearSessionContent() {
        for (String sessionId : collectKnownSessionIds()) {
            deleteSessionContent(sessionId);
        }
    }

    /**
     * 定时回收会话内容。
     * 只有 session-meta 中明确存在的永久会话，或未过期的临时会话会被保留。
     */
    @Scheduled(fixedDelayString = "#{${codey.temporary-session.cleanup-interval-seconds:60} * 1000}")
    public void cleanupExpiredTemporarySessions() {
        long now = System.currentTimeMillis();
        for (String sessionId : collectKnownSessionIds()) {
            if (shouldRetainSession(sessionId, now)) {
                continue;
            }
            deleteSessionContent(sessionId);
        }
    }

    private boolean shouldRetainSession(String sessionId, long now) {
        SessionMetadata metadata = readSessionMetadata(sessionId);
        if (metadata == null) {
            return false;
        }
        if (SESSION_TYPE_PERSISTENT.equals(metadata.getSessionType())) {
            return true;
        }
        if (!SESSION_TYPE_TEMPORARY.equals(metadata.getSessionType())) {
            return false;
        }
        Long lastActiveAt = metadata.getLastActiveAt();
        if (lastActiveAt == null) {
            return false;
        }
        return now - lastActiveAt.longValue() <= temporarySessionIdleExpireMillis;
    }

    private void clearRuntimeState(String sessionId) {
        agentClient.closeSession(sessionId);
        sessionEventHub.clear(sessionId);
        displayOptionsStore.clear(sessionId);
    }

    private void deleteArchives(String sessionId) {
        for (Path sessionRoot : resolveSessionRoots()) {
            deleteRecursively(sessionRoot.resolve("model-inputs").resolve(sessionId));
            deleteRecursively(sessionRoot.resolve("model-outputs").resolve(sessionId));
            deleteIfExists(sessionRoot.resolve(sessionId + ".jsonl"));
            deleteIfExists(sessionRoot.resolve("session-meta").resolve(sessionId + ".json"));
        }
    }

    private void deleteTemporaryWorkspace(String sessionId) {
        Path temporaryWorkspace = workspaceRoot.resolve(sessionId).normalize();
        if (!temporaryWorkspace.startsWith(workspaceRoot)) {
            return;
        }
        deleteRecursively(temporaryWorkspace);
    }

    private boolean isTemporarySession(String sessionId) {
        SessionMetadata metadata = readSessionMetadata(sessionId);
        if (metadata != null && !isBlank(metadata.getSessionType())) {
            return SESSION_TYPE_TEMPORARY.equals(metadata.getSessionType());
        }
        return SESSION_TYPE_TEMPORARY.equals(resolveSessionTypeFromArchives(sessionId));
    }

    private Long resolveLastActiveAt(String sessionId) {
        SessionMetadata metadata = readSessionMetadata(sessionId);
        if (metadata != null && metadata.getLastActiveAt() != null) {
            return metadata.getLastActiveAt();
        }
        Long archiveLastModified = resolveArchiveLastModified(sessionId);
        if (archiveLastModified != null) {
            return archiveLastModified;
        }
        Path temporaryWorkspace = workspaceRoot.resolve(sessionId).normalize();
        if (Files.exists(temporaryWorkspace)) {
            return lastModifiedTime(temporaryWorkspace);
        }
        return null;
    }

    private Long resolveArchiveLastModified(String sessionId) {
        Long latest = null;
        for (Path sessionRoot : resolveSessionRoots()) {
            latest = maxTime(latest, latestFileTime(sessionRoot.resolve("model-inputs").resolve(sessionId)));
            latest = maxTime(latest, latestFileTime(sessionRoot.resolve("model-outputs").resolve(sessionId)));
            latest = maxTime(latest, lastModifiedTimeIfExists(sessionRoot.resolve(sessionId + ".jsonl")));
        }
        return latest;
    }

    private SessionMetadata buildFallbackMetadata(String sessionId) {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setSessionId(sessionId);
        String sessionType = resolveSessionTypeFromArchives(sessionId);
        metadata.setSessionType(sessionType);
        metadata.setWorkingDirectory(resolveStoredWorkingDirectory(sessionId, resolveWorkingDirectoryFromArchives(sessionId), sessionType));
        return metadata;
    }

    private String resolveSessionTypeFromArchives(String sessionId) {
        String archivedWorkingDirectory = resolveWorkingDirectoryFromArchives(sessionId);
        if (isBlank(archivedWorkingDirectory)) {
            return SESSION_TYPE_TEMPORARY;
        }
        String normalized = normalizeDirectory(archivedWorkingDirectory);
        return sessionId.equals(normalized) ? SESSION_TYPE_TEMPORARY : SESSION_TYPE_PERSISTENT;
    }

    private String resolveWorkingDirectoryFromArchives(String sessionId) {
        for (Path sessionRoot : resolveSessionRoots()) {
            Path requestDir = sessionRoot.resolve("model-inputs").resolve(sessionId);
            String workingDirectory = extractWorkingDirectoryFromRequestLogs(requestDir);
            if (!isBlank(workingDirectory)) {
                return workingDirectory;
            }
        }
        return sessionId;
    }

    private String extractWorkingDirectoryFromRequestLogs(Path requestDir) {
        if (requestDir == null || !Files.isDirectory(requestDir)) {
            return "";
        }
        List<Path> requestFiles = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(requestDir, "*.json")) {
            for (Path requestFile : stream) {
                if (Files.isRegularFile(requestFile)) {
                    requestFiles.add(requestFile);
                }
            }
        } catch (IOException exception) {
            return "";
        }
        requestFiles.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
        for (int index = requestFiles.size() - 1; index >= 0; index--) {
            JsonNode requestNode = unwrapRequestNode(readJsonFile(requestFiles.get(index)));
            String structuredValue = textValue(requestNode, "workingDirectory");
            if (!isBlank(structuredValue)) {
                return structuredValue;
            }
        }
        return "";
    }

    private JsonNode readJsonFile(Path file) {
        try {
            return objectMapper.readTree(Files.readAllBytes(file));
        } catch (IOException exception) {
            return null;
        }
    }

    private JsonNode unwrapRequestNode(JsonNode rootNode) {
        if (rootNode == null) {
            return null;
        }
        JsonNode payloadNode = rootNode.path("payload");
        if (!payloadNode.isMissingNode() && !payloadNode.isNull() && payloadNode.isObject()) {
            return payloadNode;
        }
        return rootNode;
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? "" : valueNode.asText("");
    }

    private SessionMetadata readSessionMetadata(String sessionId) {
        for (Path sessionRoot : resolveSessionRoots()) {
            Path metadataFile = sessionRoot.resolve("session-meta").resolve(sessionId + ".json");
            if (!Files.isRegularFile(metadataFile)) {
                continue;
            }
            try {
                return objectMapper.readValue(Files.readAllBytes(metadataFile), SessionMetadata.class);
            } catch (IOException exception) {
                return null;
            }
        }
        return null;
    }

    private void writeSessionMetadata(SessionMetadata metadata) {
        if (metadata == null || isBlank(metadata.getSessionId())) {
            return;
        }
        try {
            Path metadataDir = preferredSessionRoot.resolve("session-meta");
            Files.createDirectories(metadataDir);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(metadataDir.resolve(metadata.getSessionId() + ".json").toFile(), metadata);
        } catch (IOException exception) {
            throw new IllegalStateException("写入会话元数据失败", exception);
        }
    }

    private Set<String> collectKnownSessionIds() {
        Set<String> sessionIds = new LinkedHashSet<String>();
        for (Path sessionRoot : resolveSessionRoots()) {
            collectSessionIdsFromDirectory(sessionRoot.resolve("model-inputs"), sessionIds);
            collectSessionIdsFromDirectory(sessionRoot.resolve("model-outputs"), sessionIds);
            collectSessionIdsFromMetadata(sessionRoot.resolve("session-meta"), sessionIds);
            collectSessionIdsFromEventLogs(sessionRoot, sessionIds);
        }
        return sessionIds;
    }

    private void collectSessionIdsFromDirectory(Path directory, Set<String> sessionIds) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && entry.getFileName() != null) {
                    sessionIds.add(entry.getFileName().toString());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectSessionIdsFromMetadata(Path metadataDir, Set<String> sessionIds) {
        if (metadataDir == null || !Files.isDirectory(metadataDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(metadataDir, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                if (fileName.endsWith(".json")) {
                    sessionIds.add(fileName.substring(0, fileName.length() - 5));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void collectSessionIdsFromEventLogs(Path sessionRoot, Set<String> sessionIds) {
        if (sessionRoot == null || !Files.isDirectory(sessionRoot)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionRoot, "*.jsonl")) {
            for (Path file : stream) {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                if (fileName.endsWith(".jsonl")) {
                    sessionIds.add(fileName.substring(0, fileName.length() - 6));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private List<Path> resolveSessionRoots() {
        List<Path> roots = new ArrayList<Path>();
        addUnique(roots, preferredSessionRoot);
        addUnique(roots, demoSessionRoot);
        addUnique(roots, Paths.get("").toAbsolutePath().normalize().resolve("sessions"));
        addUnique(roots, Paths.get("").toAbsolutePath().normalize().resolve("codey").resolve("sessions"));
        return roots;
    }

    private void addUnique(List<Path> roots, Path candidate) {
        if (candidate != null && !roots.contains(candidate)) {
            roots.add(candidate.normalize());
        }
    }

    private String resolveStoredWorkingDirectory(String sessionId, String requestedWorkingDirectory, String sessionType) {
        if (SESSION_TYPE_TEMPORARY.equals(sessionType)) {
            return sessionId;
        }
        String normalized = normalizeDirectory(requestedWorkingDirectory);
        return isBlank(normalized) ? sessionId : normalized;
    }

    private String normalizeDirectory(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Long latestFileTime(Path root) {
        if (root == null || !Files.exists(root)) {
            return null;
        }
        final long[] latest = new long[]{0L};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    latest[0] = Math.max(latest[0], attrs.lastModifiedTime().toMillis());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    latest[0] = Math.max(latest[0], attrs.lastModifiedTime().toMillis());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            return null;
        }
        return latest[0] <= 0L ? null : Long.valueOf(latest[0]);
    }

    private Long lastModifiedTimeIfExists(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        return lastModifiedTime(path);
    }

    private Long lastModifiedTime(Path path) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return Long.valueOf(fileTime.toMillis());
        } catch (IOException exception) {
            return null;
        }
    }

    private Long maxTime(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Long.valueOf(Math.max(left.longValue(), right.longValue()));
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("删除会话文件失败", exception);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("删除会话文件失败", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 元数据单独落盘，避免仅靠 workingDirectory 文本推断“临时/永久”产生歧义。
     */
    public static class SessionMetadata {
        private String sessionId;
        private String sessionType;
        private String workingDirectory;
        private Long createdAt;
        private Long lastActiveAt;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionType() {
            return sessionType;
        }

        public void setSessionType(String sessionType) {
            this.sessionType = sessionType;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public void setWorkingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }

        public Long getLastActiveAt() {
            return lastActiveAt;
        }

        public void setLastActiveAt(Long lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
        }
    }
}
