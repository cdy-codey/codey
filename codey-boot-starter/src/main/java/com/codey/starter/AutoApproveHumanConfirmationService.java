package com.codey.starter;

import com.codey.loop.FinalResult;
import com.codey.loop.HumanConfirmationService;
import com.codey.loop.HumanDecision;
import com.codey.config.AgentSession;
import com.codey.tools.ToolInvocation;

/**
 * 基于 Spring 的默认自动批准策略，避免依赖控制台交互。
 */
class AutoApproveHumanConfirmationService implements HumanConfirmationService {
    @Override
    public HumanDecision confirmEdit(AgentSession session, FinalResult finalResult, ToolInvocation request) {
        return HumanDecision.approve("Spring Boot starter default auto approval");
    }
}
