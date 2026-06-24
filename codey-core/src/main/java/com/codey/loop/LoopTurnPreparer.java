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
    private final ToolExposurePlanner toolExposurePlanner;
    private final PromptAssembler promptAssembler;
    private final PromptContractValidator promptContractValidator;
    private final SessionStore sessionStore;

    LoopTurnPreparer(ToolExposurePlanner toolExposurePlanner,
                     PromptAssembler promptAssembler,
                     PromptContractValidator promptContractValidator,
                     SessionStore sessionStore) {
        this.toolExposurePlanner = toolExposurePlanner;
        this.promptAssembler = promptAssembler;
        this.promptContractValidator = promptContractValidator;
        this.sessionStore = sessionStore;
    }

    PreparedTurn prepare(AgentSession session, SkillDefinition skill) {
        List<String> visibleTools = toolExposurePlanner.selectVisibleTools(session, skill);
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
