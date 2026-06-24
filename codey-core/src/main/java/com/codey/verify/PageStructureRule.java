package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 页面基础结构校验。
 */
public class PageStructureRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        if (pageContent == null || pageContent.trim().isEmpty()) {
            return VerifyResult.failed("Verification failed: target page content is empty");
        }
        if (!pageContent.contains("<template>") && !pageContent.contains("export default")) {
            return VerifyResult.failed("Verification failed: page structure looks broken");
        }
        if (hasDuplicateVueBlocks(pageContent)) {
            return VerifyResult.failed("Verification failed: page structure contains duplicated vue blocks");
        }
        return VerifyResult.passed("Page structure verification passed");
    }

    private boolean hasDuplicateVueBlocks(String pageContent) {
        int templateOpenCount = countOccurrences(pageContent, "<template>");
        int templateCloseCount = countOccurrences(pageContent, "</template>");
        if (templateOpenCount > 1 || templateCloseCount > 1) {
            return true;
        }
        int scriptOpenCount = countOccurrences(pageContent, "<script");
        int scriptCloseCount = countOccurrences(pageContent, "</script>");
        if (scriptOpenCount > 1 || scriptCloseCount > 1) {
            return true;
        }
        return templateOpenCount != templateCloseCount
                || scriptOpenCount != scriptCloseCount;
    }

    private int countOccurrences(String text, String segment) {
        int count = 0;
        int index = 0;
        while (true) {
            index = text.indexOf(segment, index);
            if (index < 0) {
                return count;
            }
            count++;
            index = index + segment.length();
        }
    }
}
