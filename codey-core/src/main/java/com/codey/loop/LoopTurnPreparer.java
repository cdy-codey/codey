package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.skill.SkillDefinition;

import java.util.List;

/**
 * 负责单轮 loop 的前置准备，包括可见工具选择、prompt 构建、预检与调试输出。
 */
final class LoopTurnPreparer {
    /** 默认模式：上下文硬上限 800K 字符 */
    private static final int CONTEXT_SESSION_HARD_LIMIT_CHARS = 800_000;
    /** 单表模式：上下文硬上限 100M 字符 */
    private static final int SINGLE_FILE_HARD_LIMIT_CHARS = 100_000_000;

    private final ToolExposurePlanner toolExposurePlanner;
    private final PromptAssembler promptAssembler;
    private final PromptBudgetEstimator promptBudgetEstimator = new PromptBudgetEstimator();
    private final PromptContractDefinition promptContractDefinition = new PromptContractDefinition();
    private final PromptContractValidator promptContractValidator;
    private final SessionStore sessionStore;
    private final ContextSummaryService contextSummaryService;

    LoopTurnPreparer(ToolExposurePlanner toolExposurePlanner,
                     PromptAssembler promptAssembler,
                     PromptContractValidator promptContractValidator,
                     SessionStore sessionStore,
                     ContextSummaryService contextSummaryService) {
        this.toolExposurePlanner = toolExposurePlanner;
        this.promptAssembler = promptAssembler;
        this.promptContractValidator = promptContractValidator;
        this.sessionStore = sessionStore;
        this.contextSummaryService = contextSummaryService;
    }

    PreparedTurn prepare(AgentSession session, SkillDefinition skill, int currentLoop) {
        List<String> visibleTools = toolExposurePlanner.selectVisibleTools(session, skill);
        PromptPackage initialPromptPackage = promptAssembler.buildPackage(session, skill, visibleTools);
        PromptBudgetReport initialBudgetReport = promptBudgetEstimator.estimate(initialPromptPackage, promptContractDefinition);
        // 单表模式使用更高的上下文上限
        int hardLimit = (session != null && session.isSingleFileMode())
                ? SINGLE_FILE_HARD_LIMIT_CHARS
                : CONTEXT_SESSION_HARD_LIMIT_CHARS;
        if (initialBudgetReport.getEstimatedTotalChars() >= hardLimit) {
            String message = "当前会话上下文已达到 " + hardLimit
                    + " 字符上限，请重新开启一个新会话后继续。";
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), message));
            return PreparedTurn.rejected(message);
        }
        if (contextSummaryService != null) {
            contextSummaryService.maybeSummarize(session, skill, visibleTools, currentLoop);
        }
        PromptPackage promptPackage = promptAssembler.buildPackage(session, skill, visibleTools);
        String debugView = promptAssembler.renderDebugView(promptPackage);
        PromptValidationResult promptValidation = promptContractValidator.validate(promptPackage, session, skill);
        if (!isBlank(promptValidation.getDiagnostic())) {
            sessionStore.appendEvent(SessionEventFactory.debugTrace(session.getSessionId(), "context_budget", promptValidation.getDiagnostic()));
        }
        if (!promptValidation.isPassed()) {
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), promptValidation.getMessage()));
            return PreparedTurn.rejected(promptValidation.getMessage());
        }
        sessionStore.appendEvent(SessionEventFactory.debugTrace(session.getSessionId(), "message_view_built", debugView));
        return PreparedTurn.ready(promptPackage, visibleTools);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class PreparedTurn {
        private final boolean ready;
        private final PromptPackage promptPackage;
        private final List<String> visibleTools;
        private final String failureMessage;

        private PreparedTurn(boolean ready,
                             PromptPackage promptPackage,
                             List<String> visibleTools,
                             String failureMessage) {
            this.ready = ready;
            this.promptPackage = promptPackage;
            this.visibleTools = visibleTools;
            this.failureMessage = failureMessage;
        }

        static PreparedTurn ready(PromptPackage promptPackage, List<String> visibleTools) {
            return new PreparedTurn(true, promptPackage, visibleTools, null);
        }

        static PreparedTurn rejected(String failureMessage) {
            return new PreparedTurn(false, null, null, failureMessage);
        }

        boolean isReady() {
            return ready;
        }

        PromptPackage getPromptPackage() {
            return promptPackage;
        }

        List<String> getVisibleTools() {
            return visibleTools;
        }

        String getFailureMessage() {
            return failureMessage;
        }
    }
}
