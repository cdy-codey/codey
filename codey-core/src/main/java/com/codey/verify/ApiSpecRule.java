package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 校验接口说明文件是否满足基础约束。
 */
public class ApiSpecRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        if (apiRoot == null || apiRoot.isMissingNode()) {
            return VerifyResult.failed("Verification failed: api spec is missing");
        }
        if (apiRoot.path("path").isMissingNode() || apiRoot.path("method").isMissingNode()) {
            return VerifyResult.failed("Verification failed: api spec missing path or method");
        }
        return VerifyResult.passed("API spec verification passed");
    }
}
