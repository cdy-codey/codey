package com.codey.starter;

import com.codey.infra.LocalWorkspaceGateway;
import com.codey.infra.ModelConfig;
import com.codey.infra.ModelGateway;
import com.codey.infra.ModelGatewayFactory;
import com.codey.loop.HumanConfirmationService;
import com.codey.mcp.AppendInstructionEditStrategy;
import com.codey.mcp.EditCodeTool;
import com.codey.mcp.ListWorkspaceTool;
import com.codey.mcp.ProjectMapTool;
import com.codey.mcp.QueryApiInfoTool;
import com.codey.mcp.ReadApiSpecTool;
import com.codey.mcp.ReadFileTool;
import com.codey.mcp.SearchCodeTool;
import com.codey.session.CompositeSessionStore;
import com.codey.session.JsonlSessionStore;
import com.codey.session.ModelInputLogStore;
import com.codey.session.ModelOutputLogStore;
import com.codey.session.SessionStore;
import com.codey.skill.Skill;
import com.codey.skill.SkillRegistry;
import com.codey.client.AgentClient;
import com.codey.client.SessionEventListener;
import com.codey.client.SessionEventPublisher;
import com.codey.config.ModelProperties;
import com.codey.task.TaskRunner;
import com.codey.task.TaskRunnerFactory;
import com.codey.tool.ToolSpec;
import com.codey.tools.ApplyPatchTool;
import com.codey.tools.DeleteFileTool;
import com.codey.tools.EditFileTool;
import com.codey.tools.ToolRegistry;
import com.codey.tools.WriteFileTool;
import com.codey.verify.Verifier;
import com.codey.verify.VerifierFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Spring 的自动装配入口。
 */
@Configuration
@EnableConfigurationProperties(SpringProperties.class)
public class AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper ObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelProperties ModelProperties(SpringProperties properties) {
        return properties.getModel();
    }

    @Bean
    @ConditionalOnMissingBean(name = "WorkspaceRoot")
    public Path WorkspaceRoot(SpringProperties properties) {
        return properties.resolveWorkingDirectoryRoot();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalWorkspaceGateway localWorkspaceGateway(Path WorkspaceRoot) {
        return new LocalWorkspaceGateway(WorkspaceRoot);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SpringProperties properties,
                                       ObjectProvider<Skill> skills) {
        // 收集 Spring 容器中实现了 Skill 接口的 Bean，再补充外部 YAML skill。
        List<Skill> discoveredSkills = new ArrayList<Skill>(skills.orderedStream().collect(Collectors.toList()));
        if (!isBlank(properties.getSkillsDirectory())) {
            discoveredSkills.addAll(SkillRegistry.fromDirectory(Paths.get(properties.getSkillsDirectory())).getAll());
        }
        return new SkillRegistry(discoveredSkills);
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanConfirmationService humanConfirmationService() {
        return new AutoApproveHumanConfirmationService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelGateway modelGateway(ModelProperties properties,
                                     SpringProperties springProperties,
                                     ObjectMapper objectMapper,
                                     Path WorkspaceRoot) {
        Path sessionDirectory = springProperties.resolveSessionDirectoryRoot(WorkspaceRoot);
        // 模型输入与模型输出统一挂到同一个会话根目录下，避免历史恢复读写分散在不同位置。
        return new ModelGatewayFactory().create(
                toModelConfig(properties),
                objectMapper,
                sessionDirectory.resolve("model-inputs")
        );
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public ListWorkspaceTool listWorkspaceTool(LocalWorkspaceGateway workspaceGateway) {
        return new ListWorkspaceTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public ProjectMapTool projectMapTool(LocalWorkspaceGateway workspaceGateway) {
        return new ProjectMapTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public ReadFileTool readFileTool(LocalWorkspaceGateway workspaceGateway) {
        return new ReadFileTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public QueryApiInfoTool queryApiInfoTool(LocalWorkspaceGateway workspaceGateway) {
        return new QueryApiInfoTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public ReadApiSpecTool readApiSpecTool(LocalWorkspaceGateway workspaceGateway) {
        return new ReadApiSpecTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public SearchCodeTool searchCodeTool(LocalWorkspaceGateway workspaceGateway) {
        return new SearchCodeTool(workspaceGateway);
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public WriteFileTool writeFileTool() {
        return new WriteFileTool();
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public EditFileTool editFileTool() {
        return new EditFileTool();
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public ApplyPatchTool applyPatchTool() {
        return new ApplyPatchTool();
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public DeleteFileTool deleteFileTool() {
        return new DeleteFileTool();
    }

    @Bean
    @Qualifier("BuiltinTool")
    @ConditionalOnMissingBean
    public EditCodeTool editCodeTool(LocalWorkspaceGateway workspaceGateway) {
        return new EditCodeTool(
                workspaceGateway,
                new AppendInstructionEditStrategy(),
                Paths.get("sessions", "backups").toString()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(@Qualifier("BuiltinTool")
                                     ObjectProvider<ToolSpec> builtinTools,
                                     ObjectProvider<ToolSpec> allTools) {
        ToolRegistry toolRegistry = new ToolRegistry();
        List<ToolSpec> builtinToolBeans = builtinTools.orderedStream().collect(Collectors.toList());
        Set<ToolSpec> builtinToolSet =
                Collections.newSetFromMap(new IdentityHashMap<ToolSpec, Boolean>());
        builtinToolSet.addAll(builtinToolBeans);

        // 内置 tool 与应用扩展都走 Bean 自动发现，只在注册阶段做一次分流。
        for (ToolSpec builtinTool : builtinToolBeans) {
            toolRegistry.registerBuiltin(builtinTool);
        }
        for (ToolSpec tool : allTools.orderedStream().collect(Collectors.toList())) {
            if (!builtinToolSet.contains(tool)) {
                toolRegistry.registerExtension(tool);
            }
        }
        return toolRegistry;
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemorySessionEventHub SessionEventHub(SpringProperties properties) {
        Integer configured = properties.getEventBufferSize();
        int bufferSize = configured == null ? 200 : configured.intValue();
        return new InMemorySessionEventHub(bufferSize);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionEventPublisher SessionEventPublisher(ObjectProvider<SessionEventListener> listeners) {
        return new DefaultSessionEventPublisher(listeners.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionStore sessionStore(SpringProperties properties,
                                     Path WorkspaceRoot,
                                     SessionEventPublisher sessionEventPublisher) {
        Path sessionDirectory = properties.resolveSessionDirectoryRoot(WorkspaceRoot);
        return new CompositeSessionStore(
                // Web Demo 也需要保留模型输入/输出归档，历史恢复才能补回最后一轮模型结果。
                new JsonlSessionStore(sessionDirectory),
                new ModelInputLogStore(sessionDirectory.resolve("model-inputs")),
                new ModelOutputLogStore(sessionDirectory.resolve("model-outputs")),
                new EventPublishingSessionStore(sessionEventPublisher)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public Verifier verifier() {
        return new VerifierFactory().createWorkspaceVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRunnerFactory taskRunnerFactory() {
        return new TaskRunnerFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRunner taskRunner(SkillRegistry skillRegistry,
                                 ModelGateway modelGateway,
                                 ToolRegistry toolRegistry,
                                 SessionStore sessionStore,
                                 Verifier verifier,
                                 HumanConfirmationService humanConfirmationService,
                                 ObjectMapper objectMapper,
                                 Path WorkspaceRoot,
                                 LocalWorkspaceGateway workspaceGateway,
                                 TaskRunnerFactory taskRunnerFactory) {
        return taskRunnerFactory.create(
                skillRegistry,
                modelGateway,
                toolRegistry,
                sessionStore,
                verifier,
                humanConfirmationService,
                objectMapper,
                WorkspaceRoot,
                workspaceGateway
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentClient AgentClient(TaskRunner taskRunner,
                                             SpringProperties properties,
                                             Path WorkspaceRoot) {
        return new DefaultAgentClient(taskRunner, properties, WorkspaceRoot);
    }

    private ModelConfig toModelConfig(ModelProperties properties) {
        // starter 只负责把 Spring 配置映射为运行时配置，不再兜底读取底层 model.yaml。
        ModelConfig config = new ModelConfig();
        if (properties == null) {
            return config;
        }
        if (!isBlank(properties.getProvider())) {
            config.setProvider(properties.getProvider());
        }
        if (!isBlank(properties.getEndpoint())) {
            config.setEndpoint(properties.getEndpoint());
        }
        if (!isBlank(properties.getModelName())) {
            config.setModelName(properties.getModelName());
        }
        String resolvedApiKey = resolveApiKey(properties);
        if (!isBlank(resolvedApiKey)) {
            config.setApiKey(resolvedApiKey);
        }
        if (!isBlank(properties.getApiKeyEnv())) {
            config.setApiKeyEnv(properties.getApiKeyEnv());
        }
        if (properties.getTemperature() != null) {
            config.setTemperature(properties.getTemperature());
        }
        if (properties.getConnectTimeoutMillis() != null) {
            config.setConnectTimeoutMillis(properties.getConnectTimeoutMillis().intValue());
        }
        if (properties.getReadTimeoutMillis() != null) {
            config.setReadTimeoutMillis(properties.getReadTimeoutMillis().intValue());
        }
        if (properties.getMaxRetries() != null) {
            config.setMaxRetries(properties.getMaxRetries().intValue());
        }
        return config;
    }

    private String resolveApiKey(ModelProperties properties) {
        if (properties == null) {
            return null;
        }
        if (!isBlank(properties.getApiKey())) {
            return properties.getApiKey();
        }
        if (!isBlank(properties.getApiKeyEnv())) {
            return System.getenv(properties.getApiKeyEnv());
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
