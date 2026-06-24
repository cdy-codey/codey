package com.codey.loop;

import com.codey.tools.ToolInvocation;
import com.codey.config.AgentSession;

/**
 * 在需要人工复核时，于执行前确认高风险编辑操作。
 */
public interface HumanConfirmationService {
    HumanDecision confirmEdit(AgentSession session, FinalResult finalResult, ToolInvocation request);
}
