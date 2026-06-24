package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 检查页面是否出现了真实的编辑痕迹。
 */
public class EditMarkerRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        if (pageContent == null || pageContent.trim().isEmpty()) {
            return VerifyResult.failed("Verification failed: target page content is empty");
        }

        boolean hasFrameworkStructure = pageContent.contains("<template>")
                || pageContent.contains("export default")
                || pageContent.contains("function");
        boolean hasInteractiveState = pageContent.contains("pageNum")
                || pageContent.contains("pageSize")
                || pageContent.contains("records")
                || pageContent.contains("total");
        boolean hasRequestIntent = pageContent.contains("/api/")
                || pageContent.contains("fetch(")
                || pageContent.contains("axios")
                || pageContent.contains("URLSearchParams");

        if (!hasFrameworkStructure) {
            return VerifyResult.failed("Verification failed: target file lost expected page structure");
        }
        if (!hasInteractiveState && !hasRequestIntent) {
            return VerifyResult.failed("Verification failed: no obvious edit evidence was found in target page");
        }

        return VerifyResult.passed("Edit evidence verification passed");
    }
}
