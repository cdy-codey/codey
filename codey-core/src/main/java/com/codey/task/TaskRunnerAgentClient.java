package com.codey.task;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import com.codey.client.SessionEventPublisher;
import com.codey.config.ModelProperties;
import com.codey.session.SessionEventFactory;

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
    private final TaskRunner taskRunner;
    private final String defaultSkillName;
    private final String defaultWorkingDirectory;
    private final ModelProperties defaultModelConfig;
    private final SessionEventPublisher sessionEventPublisher;
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
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, null, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, defaultModelConfig, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig,
                                 SessionEventPublisher sessionEventPublisher) {
        this.taskRunner = taskRunner;
        this.defaultSkillName = defaultSkillName;
        this.defaultWorkingDirectory = defaultWorkingDirectory;
        this.defaultModelConfig = copyModelConfig(defaultModelConfig);
        this.sessionEventPublisher = sessionEventPublisher;
    }

    @Override
    public RunResult run(RunRequest request) {
        return toResult(taskRunner.run(toTask(request)));
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
        TaskRunner.ChatSessionHandle handle = taskRunner.openChatSession(toTask(request));
        String sessionId = handle == null ? null : handle.getSessionId();
        if (isBlank(sessionId)) {
            throw new IllegalStateException("打开会话失败，未返回 sessionId");
        }
        sessions.put(sessionId, handle);
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
        return toResult(taskRunner.runChatTurn(handle, toTask(request)));
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
        final GenerateTask task = toTask(request);
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

    private GenerateTask toTask(RunRequest request) {
        RunRequest source = request == null ? new RunRequest() : request;
        GenerateTask task = new GenerateTask();
        task.setSessionId(source.getSessionId());
        task.setGoal(source.getGoal());
        task.setSkillName(resolveSkillName(source));
        task.setWorkingDirectory(resolveWorkingDirectory(source));
        task.setPagePath(source.getPagePath());
        task.setApiSpecPath(source.getApiSpecPath());
        task.setContextFiles(source.getContextFiles());
        task.setContextNotes(source.getContextNotes());
        task.setChatHistory(source.getChatHistory());
        task.setIdentities(source.getIdentities());
        task.setModelConfig(resolveModelConfig(source));
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

    private String resolveSkillName(RunRequest request) {
        if (request != null && !isBlank(request.getSkillName())) {
            return request.getSkillName();
        }
        return defaultSkillName;
    }

    private String resolveWorkingDirectory(RunRequest request) {
        if (request != null && !isBlank(request.getWorkingDirectory())) {
            return request.getWorkingDirectory();
        }
        return defaultWorkingDirectory;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ModelProperties resolveModelConfig(RunRequest request) {
        if (request != null && request.getModelConfig() != null) {
            return request.getModelConfig();
        }
        return copyModelConfig(defaultModelConfig);
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
