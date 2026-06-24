package com.codey.task;

import com.codey.infra.LocalWorkspaceGateway;
import com.codey.infra.ModelGateway;
import com.codey.loop.FinalResultInterpreter;
import com.codey.loop.HumanConfirmationService;
import com.codey.loop.LoopOrchestrator;
import com.codey.loop.PromptAssembler;
import com.codey.loop.PromptContractValidator;
import com.codey.loop.ResponseContractValidator;
import com.codey.loop.ToolAccessController;
import com.codey.config.SessionFactory;
import com.codey.session.SessionStore;
import com.codey.skill.SkillRegistry;
import com.codey.skill.SkillSelector;
import com.codey.tools.WorkspaceToolContext;
import com.codey.tools.ToolExecutor;
import com.codey.tools.ToolRegistry;
import com.codey.verify.Verifier;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * 构建供命令行与 Spring Boot 启动器共用的 `TaskRunner` 实例。
 */
public class TaskRunnerFactory {

    public TaskRunner create(SkillRegistry skillRegistry,
                             ModelGateway modelGateway,
                             ToolRegistry toolRegistry,
                             SessionStore sessionStore,
                             Verifier verifier,
                             HumanConfirmationService humanConfirmationService,
                             ObjectMapper objectMapper,
                             Path workspaceRoot,
                             LocalWorkspaceGateway workspaceGateway) {
        return new TaskRunner(
                new SkillSelector(skillRegistry),
                new SessionFactory(),
                new LoopOrchestrator(
                        new PromptAssembler(),
                        new PromptContractValidator(toolRegistry),
                        new ResponseContractValidator(),
                        new FinalResultInterpreter(objectMapper),
                        new ToolAccessController(toolRegistry),
                        modelGateway,
                        toolRegistry,
                        new ToolExecutor(toolRegistry, new WorkspaceToolContext(workspaceRoot, workspaceGateway)),
                        verifier,
                        humanConfirmationService,
                        sessionStore
                )
        );
    }
}

