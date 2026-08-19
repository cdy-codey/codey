package com.codey.web.service;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventPublisher;
import com.codey.config.AgentSession;
import com.codey.loop.FinalResult;
import com.codey.loop.HumanConfirmationService;
import com.codey.loop.HumanDecision;
import com.codey.session.SessionEventFactory;
import com.codey.tool.ToolInvocation;
import org.springframework.stereotype.Component;

/**
 * Web 场景的人工确认实现：发布确认请求事件后阻塞等待前端回传决策，
 * 替代默认的自动批准策略，让使用者在写文件/改表单前有机会确认或取消。
 */
@Component
public class WebHumanConfirmationService implements HumanConfirmationService {
    private static final long CONFIRM_TIMEOUT_MILLIS = 120_000L;

    private final SessionEventPublisher eventPublisher;
    private final HumanConfirmationCoordinator coordinator;

    public WebHumanConfirmationService(SessionEventPublisher eventPublisher,
                                       HumanConfirmationCoordinator coordinator) {
        this.eventPublisher = eventPublisher;
        this.coordinator = coordinator;
    }

    @Override
    public HumanDecision confirmEdit(AgentSession session, FinalResult turn, ToolInvocation request) {
        String confirmationId = coordinator.register();
        SessionEvent event = SessionEventFactory.humanConfirmationRequired(
                session.getSessionId(), confirmationId, request, turn);
        eventPublisher.publish(event);

        HumanDecision decision = coordinator.await(confirmationId, CONFIRM_TIMEOUT_MILLIS);
        if (decision == null) {
            return HumanDecision.reject("等待人工确认超时，本次修改未执行。");
        }
        return decision;
    }
}
