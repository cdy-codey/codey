package com.codey.loop;

import com.codey.infra.ModelGateway;
import com.codey.infra.WorkspaceGateway;
import com.codey.session.SessionStore;
import com.codey.tools.ToolExecutor;
import com.codey.tools.ToolRegistry;
import com.codey.verify.Verifier;

/**
 * 负责装配 loop 运行时所需组件，减少编排入口的构造复杂度。
 */
final class LoopRuntimeFactory {
    TurnLoopRunner createRunner(PromptAssembler promptAssembler,
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
                                ToolExposurePlanner toolExposurePlanner,
                                int stagnationRoundsThreshold,
                                WorkspaceGateway workspaceGateway) {
        ReplanService replanService = new ReplanService();
        ContextSummaryService contextSummaryService = new ContextSummaryService(
                promptAssembler,
                modelGateway,
                sessionStore
        );
        // 单表模式文件上下文提供者（workspaceGateway 为 null 时单表模式不可用）
        SingleFileContextProvider singleFileContextProvider = workspaceGateway != null
                ? new SingleFileContextProvider(workspaceGateway)
                : null;
        LoopTurnPreparer loopTurnPreparer = new LoopTurnPreparer(
                toolExposurePlanner,
                promptAssembler,
                promptContractValidator,
                sessionStore,
                contextSummaryService,
                singleFileContextProvider
        );
        ToolCallProcessor toolCallProcessor = new ToolCallProcessor(
                toolExecutor,
                toolAccessController,
                loopGuard,
                humanConfirmationService,
                sessionStore,
                verifier,
                replanService
        );
        ModelTurnExecutor modelTurnExecutor = new ModelTurnExecutor(
                modelGateway,
                toolRegistry,
                responseContractValidator,
                finalResultInterpreter,
                sessionStore,
                replanService
        );
        CompletionResultHandler completionResultHandler = new CompletionResultHandler(
                verifier,
                sessionStore,
                replanService
        );
        LoopTurnEngine loopTurnEngine = new LoopTurnEngine(
                loopTurnPreparer,
                modelTurnExecutor,
                toolCallProcessor,
                completionResultHandler,
                contextSummaryService
        );
        return new TurnLoopRunner(loopTurnEngine, stagnationRoundsThreshold);
    }
}
