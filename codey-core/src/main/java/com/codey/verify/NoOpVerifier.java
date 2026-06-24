package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;

/**
 * 通用工作区代理默认不做业务验收，避免把页面/API 规则误挂到主链路。
 */
public class NoOpVerifier implements Verifier {
    private static final String MESSAGE =
            "Verification not applicable: current skill does not enable business-specific verification by default";

    @Override
    public VerifyResult verifyEdit(AgentSession session, SkillDefinition skill) {
        return VerifyResult.notApplicable(MESSAGE);
    }

    @Override
    public VerifyResult verifyCompletion(AgentSession session, SkillDefinition skill) {
        return VerifyResult.notApplicable(MESSAGE);
    }
}
