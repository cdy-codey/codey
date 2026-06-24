package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 分页字段绑定校验。
 */
public class PaginationRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        JsonNode queryParams = apiRoot.path("queryParams");
        if (containsField(queryParams, "pageNum") && !pageContent.contains("pageNum")) {
            return VerifyResult.failed("Verification failed: page missing pageNum binding");
        }
        if (containsField(queryParams, "pageSize") && !pageContent.contains("pageSize")) {
            return VerifyResult.failed("Verification failed: page missing pageSize binding");
        }
        return VerifyResult.passed("Pagination verification passed");
    }

    private boolean containsField(JsonNode fieldArray, String fieldName) {
        if (fieldArray == null || !fieldArray.isArray()) {
            return false;
        }
        for (JsonNode field : fieldArray) {
            if (fieldName.equals(field.path("name").asText())) {
                return true;
            }
        }
        return false;
    }
}
