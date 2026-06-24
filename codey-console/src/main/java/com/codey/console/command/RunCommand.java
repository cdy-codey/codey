package com.codey.console.command;

import com.codey.client.AgentClient;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 运行工作目录级代码代理的 console 命令。
 */
@Command(name = "codey", mixinStandardHelpOptions = true, description = "codey AI code agent")
public class RunCommand implements Callable<Integer> {
    @Option(names = "--skill", description = "Skill name")
    private String skillName;

    @Option(names = "--goal", description = "User goal")
    private String goal;

    @Option(names = "--chat", description = "强制进入聊天交互模式")
    private boolean chatMode;

    @Option(names = "--workdir", description = "工作目录；未传时优先使用 app 配置，否则默认当前目录")
    private String workingDirectory;

    @Option(names = "--app-config", description = "console 应用配置 YAML 路径", defaultValue = "config/app.yaml")
    private String appConfigPath;

    @Option(names = "--context-file", description = "Optional context file paths")
    private List<String> contextFiles = new ArrayList<String>();

    @Option(names = "--context-note", description = "Optional context notes")
    private List<String> contextNotes = new ArrayList<String>();

    @Option(names = "--identity", description = "会话身份，可重复传入")
    private List<String> identities = new ArrayList<String>();

    @Option(names = "--skills-dir", description = "外部 Skill YAML 目录")
    private String skillsDir;

    @Option(names = "--model-config", description = "Model config YAML path", defaultValue = "config/model.yaml")
    private String modelConfigPath;

    @Option(names = "--model-provider", description = "Model provider: stub/http")
    private String modelProvider;

    @Option(names = "--model-endpoint", description = "HTTP model endpoint")
    private String modelEndpoint;

    @Option(names = "--model-name", description = "HTTP model name")
    private String modelName;

    @Option(names = "--model-api-key", description = "HTTP model api key")
    private String modelApiKey;

    @Option(names = "--model-debug", description = "Enable model HTTP debug log")
    private Boolean modelDebugEnabled;

    @Option(names = "--model-debug-dir", description = "Model HTTP debug log directory")
    private String modelDebugDir;

    @Option(names = "--verify-model", description = "仅验证模型连通性与标准 tool_calls/最终结果输出")
    private boolean verifyModel;

    @Option(names = "--verify-prompt", description = "Optional custom prompt for model verification")
    private String verifyPrompt;

    @Override
    public Integer call() {
        boolean interactiveMode = chatMode || isBlank(goal);
        RunCommandRuntime runtime = new RunCommandBootstrap().bootstrap(
                new RunCommandOptions(
                        skillName,
                        interactiveMode,
                        verifyModel,
                        workingDirectory,
                        appConfigPath,
                        mergeLegacyContextFiles(),
                        contextNotes,
                        identities,
                        skillsDir,
                        modelConfigPath,
                        modelProvider,
                        modelEndpoint,
                        modelName,
                        modelApiKey,
                        modelDebugEnabled,
                        modelDebugDir
                )
        );

        if (verifyModel) {
            return runtime.requireModelVerificationRunner().run(verifyPrompt);
        }

        Path workspaceRoot = runtime.getWorkspaceRoot();
        AgentClient agentClient = runtime.requireAgentClient();

        if (interactiveMode) {
            return runtime.requireInteractiveChatConsole().run();
        }

        RunRequest request = new RunRequest();
        request.setSkillName(skillName);
        request.setWorkingDirectory(workspaceRoot.toString());
        request.setGoal(goal);
        request.setContextFiles(mergeLegacyContextFiles());
        request.setContextNotes(contextNotes);
        request.setIdentities(identities);

        RunResult result = agentClient.run(request);
        if (result.isSuccess()) {
            System.out.println("Session: " + result.getSessionId());
            System.out.println("Summary: " + result.getSummary());
            return 0;
        }

        System.err.println("Session: " + result.getSessionId());
        System.err.println("Error: " + result.getErrorMessage());
        return 1;
    }

    private List<String> mergeLegacyContextFiles() {
        List<String> merged = new ArrayList<String>();
        if (contextFiles != null) {
            merged.addAll(contextFiles);
        }
        return merged;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
