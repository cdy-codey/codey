package com.codey.task;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import com.codey.config.ModelProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 TaskRunner 的统一客户端实现。
 * console 和 Spring Boot 都应复用这条 DTO 调用链。
 */
public class TaskRunnerAgentClient implements AgentClient {
    private final TaskRunner taskRunner;
    private final String defaultSkillName;
    private final String defaultWorkingDirectory;
    private final ModelProperties defaultModelConfig;
    private final ConcurrentMap<String, TaskRunner.ChatSessionHandle> sessions =
            new ConcurrentHashMap<String, TaskRunner.ChatSessionHandle>();

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory) {
        this(taskRunner, defaultSkillName, defaultWorkingDirectory, null);
    }

    public TaskRunnerAgentClient(TaskRunner taskRunner,
                                 String defaultSkillName,
                                 String defaultWorkingDirectory,
                                 ModelProperties defaultModelConfig) {
        this.taskRunner = taskRunner;
        this.defaultSkillName = defaultSkillName;
        this.defaultWorkingDirectory = defaultWorkingDirectory;
        this.defaultModelConfig = copyModelConfig(defaultModelConfig);
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
    public void closeSession(String sessionId) {
        if (!isBlank(sessionId)) {
            sessions.remove(sessionId);
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
}
