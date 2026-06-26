package com.codey.console.command;

import com.codey.console.cli.CliInteractiveChatFactory;
import com.codey.console.cli.CliModelVerificationRunner;
import com.codey.console.cli.CliSessionStoreFactory;
import com.codey.console.cli.CliToolRegistryFactory;
import com.codey.console.common.ConsoleHumanConfirmationService;
import com.codey.console.common.InteractiveChatConsole;
import com.codey.console.common.WorkspaceRootResolver;
import com.codey.infra.AppConfig;
import com.codey.infra.AppConfigLoader;
import com.codey.infra.LocalWorkspaceGateway;
import com.codey.infra.ModelConfig;
import com.codey.infra.ModelConfigLoader;
import com.codey.infra.ModelGateway;
import com.codey.infra.ModelGatewayFactory;
import com.codey.infra.ModelVerificationService;
import com.codey.loop.FinalResultInterpreter;
import com.codey.loop.ResponseContractValidator;
import com.codey.session.SessionStore;
import com.codey.client.AgentClient;
import com.codey.skill.SkillRegistry;
import com.codey.task.TaskRunner;
import com.codey.task.TaskRunnerAgentClient;
import com.codey.task.TaskRunnerFactory;
import com.codey.tools.ToolRegistry;
import com.codey.verify.Verifier;
import com.codey.verify.VerifierFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 为 `RunCommand` 装配运行时所需的各类依赖对象。
 */
final class RunCommandBootstrap {
    private final WorkspaceRootResolver workspaceRootResolver;
    private final CliToolRegistryFactory toolRegistryFactory;
    private final CliSessionStoreFactory sessionStoreFactory;
    private final CliInteractiveChatFactory interactiveChatFactory;
    private final VerifierFactory verifierFactory;
    private final TaskRunnerFactory taskRunnerFactory;

    RunCommandBootstrap() {
        this(
                new WorkspaceRootResolver(),
                new CliToolRegistryFactory(),
                new CliSessionStoreFactory(),
                new CliInteractiveChatFactory(),
                new VerifierFactory(),
                new TaskRunnerFactory()
        );
    }

    RunCommandBootstrap(WorkspaceRootResolver workspaceRootResolver,
                        CliToolRegistryFactory toolRegistryFactory,
                        CliSessionStoreFactory sessionStoreFactory,
                        CliInteractiveChatFactory interactiveChatFactory,
                        VerifierFactory verifierFactory,
                        TaskRunnerFactory taskRunnerFactory) {
        this.workspaceRootResolver = workspaceRootResolver;
        this.toolRegistryFactory = toolRegistryFactory;
        this.sessionStoreFactory = sessionStoreFactory;
        this.interactiveChatFactory = interactiveChatFactory;
        this.verifierFactory = verifierFactory;
        this.taskRunnerFactory = taskRunnerFactory;
    }

    RunCommandRuntime bootstrap(RunCommandOptions options) {
        AppConfig appConfig = new AppConfigLoader().load(resolveConfigPath(options.getAppConfigPath()));
        Path workspaceRoot = resolveWorkspaceRoot(options, appConfig);
        LocalWorkspaceGateway workspaceGateway = new LocalWorkspaceGateway(workspaceRoot);
        ObjectMapper objectMapper = new ObjectMapper();
        ModelGateway modelGateway = createModelGateway(options, objectMapper);
        CliModelVerificationRunner modelVerificationRunner = createModelVerificationRunner(modelGateway, objectMapper);

        if (options.isVerifyModelOnly()) {
            return new RunCommandRuntime(
                    workspaceRoot,
                    workspaceGateway,
                    objectMapper,
                    null,
                    null,
                    null,
                    null,
                    modelVerificationRunner
            );
        }

        ToolRegistry toolRegistry = toolRegistryFactory.create(workspaceGateway);
        SessionStore sessionStore = sessionStoreFactory.create(options.isInteractiveMode(), workspaceRoot.resolve(".codey"));
        TaskRunner taskRunner = createTaskRunner(
                options,
                workspaceRoot,
                workspaceGateway,
                objectMapper,
                modelGateway,
                sessionStore,
                toolRegistry
        );
        AgentClient agentClient = new TaskRunnerAgentClient(
                taskRunner,
                options.getSkillName(),
                ".",
                null,
                null,
                workspaceRoot
        );
        InteractiveChatConsole interactiveChatConsole = options.isInteractiveMode()
                ? interactiveChatFactory.create(
                agentClient,
                sessionStore,
                workspaceRoot,
                options.getSkillName(),
                options.getContextFiles(),
                options.getContextNotes(),
                options.getIdentities()
        )
                : null;
        return new RunCommandRuntime(
                workspaceRoot,
                workspaceGateway,
                objectMapper,
                sessionStore,
                taskRunner,
                agentClient,
                interactiveChatConsole,
                modelVerificationRunner
        );
    }

    private Path resolveWorkspaceRoot(RunCommandOptions options, AppConfig appConfig) {
        return workspaceRootResolver.resolve(
                options.getWorkingDirectory(),
                appConfig == null ? null : appConfig.getDefaultWorkingDirectory()
        );
    }

    private ModelGateway createModelGateway(RunCommandOptions options, ObjectMapper objectMapper) {
        ModelConfigLoader modelConfigLoader = new ModelConfigLoader();
        ModelConfig modelConfig = modelConfigLoader.load(resolveConfigPath(options.getModelConfigPath()));
        modelConfig = modelConfigLoader.applyOverrides(
                modelConfig,
                options.getModelProvider(),
                options.getModelEndpoint(),
                options.getModelName(),
                options.getModelApiKey()
        );
        if (options.getModelDebugEnabled() != null) {
            modelConfig.setDebugEnabled(options.getModelDebugEnabled().booleanValue());
        }
        if (!isBlank(options.getModelDebugDir())) {
            modelConfig.setDebugDir(options.getModelDebugDir());
        }
        modelConfig.setApiKey(resolveApiKey(modelConfig));
        return new ModelGatewayFactory().create(modelConfig, objectMapper);
    }

    private TaskRunner createTaskRunner(RunCommandOptions options,
                                        Path workspaceRoot,
                                        LocalWorkspaceGateway workspaceGateway,
                                        ObjectMapper objectMapper,
                                        ModelGateway modelGateway,
                                        SessionStore sessionStore,
                                        ToolRegistry toolRegistry) {
        return taskRunnerFactory.create(
                loadSkillRegistry(options),
                modelGateway,
                toolRegistry,
                sessionStore,
                createWorkspaceVerifier(workspaceGateway, objectMapper),
                new ConsoleHumanConfirmationService(),
                objectMapper,
                workspaceRoot,
                workspaceGateway
        );
    }

    private SkillRegistry loadSkillRegistry(RunCommandOptions options) {
        if (options == null || isBlank(options.getSkillsDir())) {
            return new SkillRegistry(java.util.Collections.<com.codey.skill.Skill>emptyList());
        }
        return SkillRegistry.fromDirectory(Paths.get(options.getSkillsDir()));
    }

    private Verifier createWorkspaceVerifier(LocalWorkspaceGateway workspaceGateway, ObjectMapper objectMapper) {
        // 通用代码代理默认不挂页面/API 业务规则，避免误拦截普通工作区任务。
        return verifierFactory.createWorkspaceVerifier();
    }

    private CliModelVerificationRunner createModelVerificationRunner(ModelGateway modelGateway, ObjectMapper objectMapper) {
        return new CliModelVerificationRunner(
                new ModelVerificationService(
                        modelGateway,
                        new ResponseContractValidator(),
                        new FinalResultInterpreter(objectMapper)
                )
        );
    }

    /**
     * 兼容从仓库根目录启动时，自动定位到 codey-console 专属配置目录。
     */
    private String resolveConfigPath(String configuredPath) {
        if (isBlank(configuredPath)) {
            return configuredPath;
        }
        Path directPath = Paths.get(configuredPath);
        if (directPath.isAbsolute() || directPath.toFile().exists()) {
            return configuredPath;
        }
        Path consoleRelativePath = Paths.get("codey-console").resolve(configuredPath).normalize();
        if (consoleRelativePath.toFile().exists()) {
            return consoleRelativePath.toString();
        }
        return configuredPath;
    }

    private String resolveApiKey(ModelConfig modelConfig) {
        if (modelConfig != null && !isBlank(modelConfig.getApiKey())) {
            return modelConfig.getApiKey();
        }
        if (modelConfig != null && !isBlank(modelConfig.getApiKeyEnv())) {
            String envValue = System.getenv(modelConfig.getApiKeyEnv());
            if (!isBlank(envValue)) {
                return envValue;
            }
        }
        return System.getenv("_MODEL_API_KEY");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
