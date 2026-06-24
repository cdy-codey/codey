package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 按顺序执行校验规则，并在首次失败时立即返回。
 */
final class VerificationPipeline {
    private final VerificationTextSanitizer textSanitizer = new VerificationTextSanitizer();

    VerifyResult verify(List<VerificationRule> rules,
                        AgentSession session,
                        SkillDefinition skill,
                        String pageContent,
                        JsonNode apiRoot) {
        String sanitizedPageContent = textSanitizer.stripComments(pageContent);
        for (VerificationRule rule : rules) {
            VerifyResult result = rule.verify(session, skill, sanitizedPageContent, apiRoot);
            if (!result.isPassed()) {
                return result;
            }
        }
        return VerifyResult.passed("All verification rules passed");
    }
}
