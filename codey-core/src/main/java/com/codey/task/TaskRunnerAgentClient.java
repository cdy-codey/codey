package com.codey.task;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import com.codey.client.SessionEventPublisher;
import com.codey.config.ModelProperties;
import com.codey.infra.WorkspacePathSupport;
import com.codey.session.CoreSessionLifecycleManager;
import com.codey.session.SessionStore;
import com.codey.session.SessionEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 TaskRunner 的统一客户端实现。
 * console 和 Spring Boot 都应复用这条 DTO 调用链。
 */
public class TaskRunnerAgentClient implements AgentClient {
    private static final long DEFAULT_TEMPORARY_SESSION_IDLE_EXPIRE_SECONDS = 3600L;
    private static final long DEFAULT_SESSION_CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final TaskRunner taskRunner;
    private final String defaultSkillName;
    private final String defaultWorkingDirectory;
    private final Path workspaceRoot;
    private final ModelProperties defaultModelConfig;
    private final SessionEventPublisher sessionEventPublisher;
    private final CoreSessionLifecycleManager sessionLifecycleManager;
    private final ConcurrentMap<String, TaskRunner.ChatSessionHandle> sessions =
            new ConcurrentHashMap<String, TaskRunner.ChatSessionHandle>();
    /**
     * 同一会话的 turn 需要串行执行，避免共享 session 状态被并发改写。
     */
    private final ConcurrentMap<String, CompletableFuture<Void>> sessionTurnChains =
            new ConcurrentHashMap<String, CompletableFuture<Void>>();
    private final ExecutorService turnExecutor = Executors.newCachedThreadPool(new TurnExecutorThreadFactory());

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, null, null, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, defaultModelConfig, null, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig,
                                 SessionEventPublisher sessionEventPublisher) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, defaultModelConfig, sessionEventPublisher, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig,
                                 SessionEventPublisher sessionEventPublisher,
                                 Path workspaceRoot) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, defaultModelConfig, sessionEventPublisher, workspaceRoot, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig,
                                 SessionEventPublisher sessionEventPublisher,
                                 Path workspaceRoot,
                                 SessionStore sessionStore) {
        this.taskRunner = taskRunner;
        this.defaultSkillName = defaultSkillName;
        this.defaultWorkingDirectory = defaultWorkingDirectory;
        this.workspaceRoot = workspaceRoot;
        this.defaultModelConfig = copyModelConfig(defaultModelConfig);
        this.sessionEventPublisher = sessionEventPublisher;
        this.sessionLifecycleManager = createSessionLifecycleManager(sessionStore, workspaceRoot);
    }

    @Override
    public RunResult run(RunRequest request) {
        return toResult(taskRunner.run(toStandaloneTask(request)));
    }

    @Override
    public ChatSession openSession(RunRequest request) {
        String requestedSessionId = request == null ? null : request.getSessionId();
        if (!isBlank(requestedSessionId)) {
            TaskRunner.ChatSessionHandle existing = sessions.get(requestedSessionId);
            if (existing != null) {
                return new ChatSession(requestedSessionId);
            }
        }
        TaskRunner.ChatSessionHandle handle = taskRunner.openChatSession(toSessionOpenTask(request));
        String sessionId = handle == null ? null : handle.getSessionId();
        if (isBlank(sessionId)) {
            throw new IllegalStateException("打开会话失败，未返回 sessionId");
        }
        sessions.put(sessionId, handle);
        if (sessionLifecycleManager != null) {
            sessionLifecycleManager.registerSession(sessionId, request);
        }
        return new ChatSession(sessionId);
    }

    @Override
    public RunResult runTurn(String sessionId, RunRequest request) {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        TaskRunner.ChatSessionHandle handle = sessions.get(sessionId);
        if (handle == null) {
            throw new IllegalStateException("未找到会话: " + sessionId);
        }
        if (sessionLifecycleManager != null) {
            sessionLifecycleManager.touchSession(sessionId);
        }
        return toResult(taskRunner.runChatTurn(handle, toSessionTurnTask(request)));
    }

    @Override
    public void submitTurn(String sessionId, RunRequest request) {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        final TaskRunner.ChatSessionHandle handle = sessions.get(sessionId);
        if (handle == null) {
            throw new IllegalStateException("未找到会话: " + sessionId);
        }
        final GenerateTask task = toSessionTurnTask(request);
        final CompletableFuture<Void> queuedTurn = sessionTurnChains.compute(sessionId, (key, previous) -> {
            CompletableFuture<Void> head = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.exceptionally(error -> null);
            // HTTP 层只确认消息已进入执行队列，真正结果通过 SSE 事件继续推送。
            return head.thenRunAsync(() -> executeSubmittedTurn(handle, task), turnExecutor);
        });
        publishTaskStatus(sessionId, "accepted", false, true, "消息已进入处理队列");
        queuedTurn.whenComplete((ignored, error) -> sessionTurnChains.remove(sessionId, queuedTurn));
    }

    @Override
    public void closeSession(String sessionId) {
        if (!isBlank(sessionId)) {
            sessions.remove(sessionId);
            sessionTurnChains.remove(sessionId);
        }
    }

    @Override
    public void deleteSessionContent(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        if (sessionLifecycleManager != null) {
            sessionLifecycleManager.deleteSessionContent(sessionId);
            return;
        }
        closeSession(sessionId);
    }

    @Override
    public void clearSessionContent() {
        if (sessionLifecycleManager != null) {
            sessionLifecycleManager.clearSessionContent();
            return;
        }
        for (String sessionId : new ArrayList<String>(sessions.keySet())) {
            closeSession(sessionId);
        }
    }

    private GenerateTask toStandaloneTask(RunRequest request) {
        return toTask(request, true);
    }

    private GenerateTask toSessionOpenTask(RunRequest request) {
        return toTask(request, true);
    }

    private GenerateTask toSessionTurnTask(RunRequest request) {
        return toTask(request, false);
    }

    private GenerateTask toTask(RunRequest request, boolean resolveModelConfig) {
        RunRequest source = request == null ? new RunRequest() : request;
        GenerateTask task = new GenerateTask();
        task.setSessionId(source.getSessionId());
        task.setGoal(source.getGoal());
        List<String> resolvedSkillNames = resolveSkillNames(source);
        task.setSkillNames(resolvedSkillNames);
        task.setSkillName(resolvedSkillNames.isEmpty() ? null : String.join(",", resolvedSkillNames));
        String sanitizedWorkingDirectory = resolveWorkingDirectory(source);
        task.setWorkingDirectory(sanitizedWorkingDirectory);
        task.setPagePath(source.getPagePath());
        task.setApiSpecPath(source.getApiSpecPath());
        task.setContextFiles(sanitizeContextFiles(source.getContextFiles()));
        task.setContextNotes(sanitizeContextNotes(source.getContextNotes()));
        task.setChatHistory(source.getChatHistory());
        task.setIdentities(source.getIdentities());
        task.setCoreRules(source.getCoreRules());
        task.setSingleFileMode(source.isSingleFileMode());
        if (resolveModelConfig) {
            task.setModelConfig(resolveInitialModelConfig(source));
        }
        return task;
    }

    private RunResult toResult(TaskResult result) {
        if (result == null) {
            return RunResult.failed(null, "任务执行结果为空");
        }
        if (result.isSuccess()) {
            return RunResult.success(result.getSessionId(), result.getSummary());
        }
        return RunResult.failed(result.getSessionId(), result.getErrorMessage());
    }

    private List<String> resolveSkillNames(RunRequest request) {
        Set<String> values = new LinkedHashSet<String>();
        if (request != null && request.getSkillNames() != null) {
            for (String item : request.getSkillNames()) {
                addSkillNames(values, item);
            }
        }
        if (values.isEmpty()) {
            addSkillNames(values, request == null ? null : request.getSkillName());
        }
        if (values.isEmpty()) {
            addSkillNames(values, defaultSkillName);
        }
        return new ArrayList<String>(values);
    }

    private void addSkillNames(Set<String> values, String rawValue) {
        if (isBlank(rawValue)) {
            return;
        }
        String[] parts = rawValue.split(",");
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
    }

    private String resolveWorkingDirectory(RunRequest request) {
        if (request != null && !isBlank(request.getWorkingDirectory())) {
            return WorkspacePathSupport.sanitizeWorkingDirectory(request.getWorkingDirectory(), workspaceRoot);
        }
        return null;
    }

    private List<String> sanitizeContextFiles(List<String> contextFiles) {
        List<String> sanitized = new ArrayList<String>();
        if (contextFiles == null) {
            return sanitized;
        }
        for (String item : contextFiles) {
            if (isBlank(item)) {
                continue;
            }
            sanitized.add(WorkspacePathSupport.sanitizeContextText(item, workspaceRoot));
        }
        return sanitized;
    }

    private List<String> sanitizeContextNotes(List<String> contextNotes) {
        List<String> sanitized = new ArrayList<String>();
        if (contextNotes == null) {
            return sanitized;
        }
        for (String item : contextNotes) {
            if (isBlank(item)) {
                continue;
            }
            sanitized.add(WorkspacePathSupport.sanitizeContextText(item, workspaceRoot));
        }
        return sanitized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ModelProperties resolveInitialModelConfig(RunRequest request) {
        if (request != null && request.getModelConfig() != null) {
            return copyModelConfig(request.getModelConfig());
        }
        ModelProperties fallback = copyModelConfig(defaultModelConfig);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("模型配置缺失：openSession 时请传入 modelConfig；如果未传入，则必须在 application.yml 中配置 codey.model");
    }

    private ModelProperties copyModelConfig(ModelProperties source) {
        if (source == null) {
            return null;
        }
        ModelProperties copy = new ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setEndpoint(source.getEndpoint());
        copy.setModelName(source.getModelName());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEnv(source.getApiKeyEnv());
        copy.setTemperature(source.getTemperature());
        copy.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        copy.setReadTimeoutMillis(source.getReadTimeoutMillis());
        copy.setMaxRetries(source.getMaxRetries());
        return copy;
    }

    private void executeSubmittedTurn(TaskRunner.ChatSessionHandle handle, GenerateTask task) {
        String sessionId = handle == null ? null : handle.getSessionId();
        try {
            if (sessionLifecycleManager != null && !isBlank(sessionId)) {
                sessionLifecycleManager.touchSession(sessionId);
            }
            TaskResult result = taskRunner.runChatTurn(handle, task);
            if (result != null && result.isSuccess()) {
                publishTaskStatus(sessionId, "completed", true, true, result.getSummary());
                return;
            }
            String errorMessage = result == null ? "任务执行结果为空" : result.getErrorMessage();
            publishTaskStatus(sessionId, "failed", true, false, errorMessage);
        } catch (RuntimeException exception) {
            publishTaskStatus(sessionId, "failed", true, false, exception.getMessage());
        }
    }

    private void publishTaskStatus(String sessionId, String status, boolean terminal, boolean success, String message) {
        if (sessionEventPublisher == null || isBlank(sessionId)) {
            return;
        }
        sessionEventPublisher.publish(SessionEventFactory.taskStatus(sessionId, status, terminal, success, message));
    }

    private CoreSessionLifecycleManager createSessionLifecycleManager(SessionStore sessionStore, Path workspaceRoot) {
        if (sessionStore == null) {
            return null;
        }
        return new CoreSessionLifecycleManager(
                this,
                null,
                sessionStore,
                new ObjectMapper().findAndRegisterModules(),
                workspaceRoot,
                DEFAULT_TEMPORARY_SESSION_IDLE_EXPIRE_SECONDS,
                DEFAULT_SESSION_CLEANUP_INTERVAL_MILLIS
        );
    }

    private static final class TurnExecutorThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "codey-chat-turn-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
