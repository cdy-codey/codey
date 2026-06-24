package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;

/**
 * 编辑后和结束前的系统校验器。
 */
public interface Verifier {
    VerifyResult verifyEdit(AgentSession session, SkillDefinition skill);

    VerifyResult verifyCompletion(AgentSession session, SkillDefinition skill);
}
