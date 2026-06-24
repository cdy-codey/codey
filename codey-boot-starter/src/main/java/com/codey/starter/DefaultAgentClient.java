package com.codey.starter;

import com.codey.task.TaskRunner;
import com.codey.task.TaskRunnerAgentClient;

import java.nio.file.Path;

/**
 * Spring Boot 默认客户端，复用统一的 TaskRunner DTO 适配实现。
 */
class DefaultAgentClient extends TaskRunnerAgentClient {

    DefaultAgentClient(TaskRunner taskRunner, SpringProperties properties, Path workspaceRoot) {
        super(taskRunner, properties.getDefaultSkillName(), workspaceRoot == null ? null : workspaceRoot.toString());
    }
}
