package com.codey.loop;

import com.codey.infra.ModelGateway;
import com.codey.tools.ToolExecutor;
import com.codey.tools.ToolRegistry;
import com.codey.config.AgentSession;
import com.codey.session.SessionStore;
import com.codey.skill.SkillDefinition;
import com.codey.task.TaskResult;
import com.codey.verify.Verifier;

/**
 * 执行模型、工具和校验器之间的循环。
 */
public class LoopOrchestrator {
    private static final int STAGNATION_ROUNDS_THRESHOLD = 4;
    private final TurnLoopRunner turnLoopRunner;

    LoopOrchestrator(TurnLoopRunner turnLoopRunner) {
        this.turnLoopRunner = turnLoopRunner;
    }

    public LoopOrchestrator(PromptAssembler promptAssembler,
                            PromptContractValidator promptContractValidator,
                            ResponseContractValidator responseContractValidator,
                            FinalResultInterpreter finalResultInterpreter,
                            ToolAccessController toolAccessController,
                            ModelGateway modelGateway,
                            ToolRegistry toolRegistry,
                            ToolExecutor toolExecutor,
                            Verifier verifier,
                            HumanConfirmationService humanConfirmationService,
                            SessionStore sessionStore) {
        this(promptAssembler,
                promptContractValidator,
                responseContractValidator,
                finalResultInterpreter,
                toolAccessController,
                modelGateway,
                toolRegistry,
                toolExecutor,
                verifier,
                humanConfirmationService,
                sessionStore,
                new LoopGuard(),
                new ToolExposurePlanner(toolRegistry));
    }

    public LoopOrchestrator(PromptAssembler promptAssembler,
                            PromptContractValidator promptContractValidator,
                            ResponseContractValidator responseContractValidator,
                            FinalResultInterpreter finalResultInterpreter,
                            ToolAccessController toolAccessController,
                            ModelGateway modelGateway,
                            ToolRegistry toolRegistry,
                            ToolExecutor toolExecutor,
                            Verifier verifier,
                            HumanConfirmationService humanConfirmationService,
                            SessionStore sessionStore,
                            LoopGuard loopGuard) {
        this(promptAssembler,
                promptContractValidator,
                responseContractValidator,
                finalResultInterpreter,
                toolAccessController,
                modelGateway,
                toolRegistry,
                toolExecutor,
                verifier,
                humanConfirmationService,
                sessionStore,
                loopGuard,
                new ToolExposurePlanner(toolRegistry));
    }

    public LoopOrchestrator(PromptAssembler promptAssembler,
                            PromptContractValidator promptContractValidator,
                            ResponseContractValidator responseContractValidator,
                            FinalResultInterpreter finalResultInterpreter,
                            ToolAccessController toolAccessController,
                            ModelGateway modelGateway,
                            ToolRegistry toolRegistry,
                            ToolExecutor toolExecutor,
                            Verifier verifier,
                            HumanConfirmationService humanConfirmationService,
                            SessionStore sessionStore,
                            LoopGuard loopGuard,
                            ToolExposurePlanner toolExposurePlanner) {
        this(new LoopRuntimeFactory().createRunner(
                promptAssembler,
                promptContractValidator,
                responseContractValidator,
                finalResultInterpreter,
                toolAccessController,
                modelGateway,
                toolRegistry,
                toolExecutor,
                verifier,
                humanConfirmationService,
                sessionStore,
                loopGuard,
                toolExposurePlanner,
                STAGNATION_ROUNDS_THRESHOLD
        ));
    }

    public TaskResult run(AgentSession session, SkillDefinition skill) {
        return turnLoopRunner.run(session, skill);
    }
}
