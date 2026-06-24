package com.codey.verify;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 返回字段绑定校验。
 */
public class ResponseFieldsRule implements VerificationRule {
    @Override
    public VerifyResult verify(AgentSession session, SkillDefinition skill, String pageContent, JsonNode apiRoot) {
        JsonNode responseFields = apiRoot.path("responseFields");
        if (containsField(responseFields, "records") && !pageContent.contains("records")) {
            return VerifyResult.failed("Verification failed: page missing records binding");
        }
        if (containsField(responseFields, "total") && !pageContent.contains("total")) {
            return VerifyResult.failed("Verification failed: page missing total binding");
        }
        return VerifyResult.passed("Response fields verification passed");
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
