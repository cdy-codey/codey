package com.codey.loop;

import com.codey.infra.ModelResponse;
import com.codey.config.AgentSession;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.skill.SkillDefinition;
import com.codey.task.TaskResult;
import com.codey.verify.Verifier;
import com.codey.verify.VerifyResult;

/**
 * 统一处理完成态校验、assistant 回写和最终摘要输出。
 */
final class CompletionResultHandler {
    private final Verifier verifier;
    private final SessionStore sessionStore;
    private final ReplanService replanService;

    CompletionResultHandler(Verifier verifier,
                            SessionStore sessionStore,
                            ReplanService replanService) {
        this.verifier = verifier;
        this.sessionStore = sessionStore;
        this.replanService = replanService;
    }

    TaskResult handleCompletion(AgentSession session,
                                SkillDefinition skill,
                                ModelResponse modelResponse,
                                FinalResult finalResult) {
        VerifyResult verifyResult = verifier.verifyCompletion(session, skill);
        if (verifyResult.isApplicable()) {
            sessionStore.appendEvent(SessionEventFactory.verification(session.getSessionId(), verifyResult));
        }
        if (verifyResult.isFailed()) {
            replanService.appendFeedbackAndRequestReplan(session, verifyResult.getMessage());
            return null;
        }
        replanService.appendAssistantResponse(session, modelResponse.getContent(), modelResponse.getReasoningContent(), null);
        sessionStore.appendEvent(SessionEventFactory.finalSummary(session.getSessionId(), finalResult.getSummary()));
        return TaskResult.finished(session.getSessionId(), finalResult.getSummary());
    }
}
