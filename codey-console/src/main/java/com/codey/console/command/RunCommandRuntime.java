package com.codey.console.command;
import com.codey.console.cli.CliModelVerificationRunner;
import com.codey.console.common.InteractiveChatConsole;
import com.codey.infra.LocalWorkspaceGateway;
import com.codey.session.SessionStore;
import com.codey.client.AgentClient;
import com.codey.task.TaskRunner;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * 保存 console 运行阶段会复用的依赖对象。
 */
final class RunCommandRuntime {
    private final Path workspaceRoot;
    private final LocalWorkspaceGateway workspaceGateway;
    private final ObjectMapper objectMapper;
    private final SessionStore sessionStore;
    private final TaskRunner taskRunner;
    private final AgentClient agentClient;
    private final InteractiveChatConsole interactiveChatConsole;
    private final CliModelVerificationRunner modelVerificationRunner;

    RunCommandRuntime(Path workspaceRoot,
                      LocalWorkspaceGateway workspaceGateway,
                      ObjectMapper objectMapper,
                      SessionStore sessionStore,
                      TaskRunner taskRunner,
                      AgentClient agentClient,
                      InteractiveChatConsole interactiveChatConsole,
                      CliModelVerificationRunner modelVerificationRunner) {
        this.workspaceRoot = workspaceRoot;
        this.workspaceGateway = workspaceGateway;
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
        this.taskRunner = taskRunner;
        this.agentClient = agentClient;
        this.interactiveChatConsole = interactiveChatConsole;
        this.modelVerificationRunner = modelVerificationRunner;
    }

    Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    LocalWorkspaceGateway getWorkspaceGateway() {
        return workspaceGateway;
    }

    ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    SessionStore requireSessionStore() {
        if (sessionStore == null) {
            throw new IllegalStateException("SessionStore is not available for the current runtime mode");
        }
        return sessionStore;
    }

    TaskRunner requireTaskRunner() {
        if (taskRunner == null) {
            throw new IllegalStateException("TaskRunner is not available for the current runtime mode");
        }
        return taskRunner;
    }

    AgentClient requireAgentClient() {
        if (agentClient == null) {
            throw new IllegalStateException("AgentClient is not available for the current runtime mode");
        }
        return agentClient;
    }

    InteractiveChatConsole requireInteractiveChatConsole() {
        if (interactiveChatConsole == null) {
            throw new IllegalStateException("InteractiveChatConsole is not available for the current runtime mode");
        }
        return interactiveChatConsole;
    }

    CliModelVerificationRunner requireModelVerificationRunner() {
        if (modelVerificationRunner == null) {
            throw new IllegalStateException("Model verification runner is not available for the current runtime mode");
        }
        return modelVerificationRunner;
    }
}
