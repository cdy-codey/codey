package com.codey.console.command;

import com.codey.console.cli.CliInteractiveChatFactory;
import com.codey.console.cli.CliModelVerificationRunner;
import com.codey.console.cli.CliSessionStoreFactory;
import com.codey.console.cli.CliToolRegistryFactory;
import com.codey.console.common.ConsoleHumanConfirmationService;
import com.codey.console.common.InteractiveChatConsole;
import com.codey.console.common.WorkspaceRootResolver;
import com.codey.config.ModelProperties;
import com.codey.infra.AppConfig;
import com.codey.infra.LocalWorkspaceGateway;
import com.codey.infra.ModelConfig;
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
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
        AppConfig appConfig = loadConsoleAppConfig(resolveConfigPath(options.getAppConfigPath()));
        Path workspaceRoot = resolveWorkspaceRoot(options, appConfig);
        LocalWorkspaceGateway workspaceGateway = new LocalWorkspaceGateway(workspaceRoot);
        ObjectMapper objectMapper = new ObjectMapper();
        ModelConfig modelConfig = resolveModelConfig(options);
        ModelGateway modelGateway = new ModelGatewayFactory().create(modelConfig, objectMapper);
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
                toModelProperties(modelConfig),
                null,
                workspaceRoot,
                sessionStore
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

    private ModelConfig resolveModelConfig(RunCommandOptions options) {
        ModelConfig modelConfig = loadConsoleModelConfig(resolveModelConfigPath(options));
        applyModelOverrides(
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
        return modelConfig;
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

    /**
     * console 只在本模块内兼容聚合 YAML，避免把 CLI 兼容逻辑扩散到 core。
     */
    private AppConfig loadConsoleAppConfig(String configPath) {
        AppConfig config = new AppConfig();
        File file = toExistingFile(configPath);
        if (file == null) {
            return config;
        }
        try {
            JsonNode rootNode = createYamlMapper().readTree(file);
            JsonNode appNode = rootNode == null ? null : rootNode.path("codey");
            if (appNode == null || appNode.isMissingNode() || appNode.isNull() || !appNode.isObject()) {
                appNode = rootNode;
            }
            if (appNode == null || appNode.isMissingNode() || appNode.isNull()) {
                return config;
            }
            AppConfig loaded = createYamlMapper().treeToValue(appNode, AppConfig.class);
            return loaded == null ? config : loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load app config: " + configPath, exception);
        }
    }

    /**
     * 当独立 model.yaml 缺失时，回退到 app.yaml 里的 codey.model，
     * 这样可以兼容“单文件聚合配置”的启动方式。
     */
    private String resolveModelConfigPath(RunCommandOptions options) {
        String resolvedModelPath = resolveConfigPath(options.getModelConfigPath());
        if (!isBlank(resolvedModelPath) && Paths.get(resolvedModelPath).toFile().exists()) {
            return resolvedModelPath;
        }
        return resolveConfigPath(options.getAppConfigPath());
    }

    /**
     * console 私有兼容层：同时支持独立 model.yaml 与 app.yaml 里的 codey.model。
     */
    private ModelConfig loadConsoleModelConfig(String configPath) {
        ModelConfig config = new ModelConfig();
        File file = toExistingFile(configPath);
        if (file == null) {
            return config;
        }
        try {
            JsonNode rootNode = createYamlMapper().readTree(file);
            JsonNode modelNode = rootNode == null ? null : rootNode.path("codey").path("model");
            if (modelNode == null || modelNode.isMissingNode() || modelNode.isNull() || !modelNode.isObject()) {
                modelNode = rootNode;
            }
            if (modelNode == null || modelNode.isMissingNode() || modelNode.isNull()) {
                return config;
            }
            applyModelNode(config, modelNode);
            return config;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load model config: " + configPath, exception);
        }
    }

    private void applyModelOverrides(ModelConfig config,
                                     String provider,
                                     String endpoint,
                                     String modelName,
                                     String apiKey) {
        if (!isBlank(provider)) {
            config.setProvider(provider);
        }
        if (!isBlank(endpoint)) {
            config.setEndpoint(endpoint);
        }
        if (!isBlank(modelName)) {
            config.setModelName(modelName);
        }
        if (!isBlank(apiKey)) {
            config.setApiKey(apiKey);
        }
    }

    private void applyModelNode(ModelConfig config, JsonNode modelNode) {
        if (config == null || modelNode == null || modelNode.isNull()) {
            return;
        }
        setIfPresent(modelNode, "provider", config::setProvider);
        setIfPresent(modelNode, "endpoint", config::setEndpoint);
        setIfPresent(modelNode, "api-key", config::setApiKey);
        setIfPresent(modelNode, "apiKey", config::setApiKey);
        setIfPresent(modelNode, "api-key-env", config::setApiKeyEnv);
        setIfPresent(modelNode, "apiKeyEnv", config::setApiKeyEnv);
        setIfPresent(modelNode, "model-name", config::setModelName);
        setIfPresent(modelNode, "modelName", config::setModelName);
        if (modelNode.hasNonNull("temperature")) {
            config.setTemperature(modelNode.path("temperature").asDouble());
        }
        if (modelNode.hasNonNull("debug-enabled")) {
            config.setDebugEnabled(modelNode.path("debug-enabled").asBoolean());
        }
        if (modelNode.hasNonNull("debugEnabled")) {
            config.setDebugEnabled(modelNode.path("debugEnabled").asBoolean());
        }
        setIfPresent(modelNode, "debug-dir", config::setDebugDir);
        setIfPresent(modelNode, "debugDir", config::setDebugDir);
        if (modelNode.hasNonNull("connect-timeout-millis")) {
            config.setConnectTimeoutMillis(modelNode.path("connect-timeout-millis").asInt());
        }
        if (modelNode.hasNonNull("connectTimeoutMillis")) {
            config.setConnectTimeoutMillis(modelNode.path("connectTimeoutMillis").asInt());
        }
        if (modelNode.hasNonNull("read-timeout-millis")) {
            config.setReadTimeoutMillis(modelNode.path("read-timeout-millis").asInt());
        }
        if (modelNode.hasNonNull("readTimeoutMillis")) {
            config.setReadTimeoutMillis(modelNode.path("readTimeoutMillis").asInt());
        }
        if (modelNode.hasNonNull("max-retries")) {
            config.setMaxRetries(modelNode.path("max-retries").asInt());
        }
        if (modelNode.hasNonNull("maxRetries")) {
            config.setMaxRetries(modelNode.path("maxRetries").asInt());
        }
    }

    private void setIfPresent(JsonNode node, String fieldName, StringValueSetter setter) {
        if (node == null || setter == null || isBlank(fieldName) || !node.hasNonNull(fieldName)) {
            return;
        }
        String value = node.path(fieldName).asText();
        if (!isBlank(value)) {
            setter.set(value);
        }
    }

    private ObjectMapper createYamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private File toExistingFile(String configPath) {
        if (isBlank(configPath)) {
            return null;
        }
        File file = new File(configPath);
        if (!file.exists()) {
            return null;
        }
        return file;
    }

    /**
     * console 启动阶段读取到的默认模型配置，需要继续透传给 AgentClient，
     * 这样非交互式 `--goal` 流程也能复用同一份配置。
     */
    private ModelProperties toModelProperties(ModelConfig modelConfig) {
        if (modelConfig == null) {
            return null;
        }
        ModelProperties properties = new ModelProperties();
        properties.setProvider(modelConfig.getProvider());
        properties.setEndpoint(modelConfig.getEndpoint());
        properties.setModelName(modelConfig.getModelName());
        properties.setApiKey(modelConfig.getApiKey());
        properties.setApiKeyEnv(modelConfig.getApiKeyEnv());
        properties.setTemperature(modelConfig.getTemperature());
        properties.setConnectTimeoutMillis(Integer.valueOf(modelConfig.getConnectTimeoutMillis()));
        properties.setReadTimeoutMillis(Integer.valueOf(modelConfig.getReadTimeoutMillis()));
        properties.setMaxRetries(Integer.valueOf(modelConfig.getMaxRetries()));
        return properties;
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

    private interface StringValueSetter {
        void set(String value);
    }
}
