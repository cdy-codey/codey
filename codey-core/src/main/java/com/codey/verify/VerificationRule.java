package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单条校验规则。
 */
public interface VerificationRule {
    VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot);
}
