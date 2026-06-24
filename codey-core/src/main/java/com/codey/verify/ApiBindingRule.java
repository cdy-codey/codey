package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 校验接口路径和方法是否已在页面中完成绑定。
 */
public class ApiBindingRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        String apiPath = apiRoot.path("path").asText();
        String apiMethod = apiRoot.path("method").asText();
        if (!containsIgnoreCase(pageContent, apiPath) || !containsIgnoreCase(pageContent, apiMethod)) {
            return VerifyResult.failed("Verification failed: page missing api path or method binding");
        }
        return VerifyResult.passed("API binding verification passed");
    }

    private boolean containsIgnoreCase(String content, String token) {
        return token != null
                && !token.trim().isEmpty()
                && content.toLowerCase().contains(token.toLowerCase());
    }
}
