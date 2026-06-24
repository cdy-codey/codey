package com.codey.loop;

import com.codey.infra.ModelResponse;

/**
 * 校验模型响应结构以及最终结果字段是否符合约束。
 */
public class ResponseContractValidator {

    public ResponseValidationResult validateModelResponse(ModelResponse response) {
        if (response == null) {
            return ResponseValidationResult.failed("Response contract failed: model response is null");
        }
        if (response.hasToolCalls()) {
            return ResponseValidationResult.passed("Response contract passed");
        }
        if (response.getContent() == null || response.getContent().trim().isEmpty()) {
            return ResponseValidationResult.failed("Response contract failed: response content is empty");
        }
        return ResponseValidationResult.passed("Response contract passed");
    }

    public ResponseValidationResult validateFinalResult(FinalResult finalResult) {
        if (finalResult == null) {
            return ResponseValidationResult.failed("Response contract failed: final result is null");
        }
        if (finalResult.getStatus() == null || finalResult.getStatus().trim().isEmpty()) {
            return ResponseValidationResult.failed("Response contract failed: final status is empty");
        }
        // 最终完成态只接受 FINISH；继续循环应通过 tool_calls 或无效响应触发重试。
        if (!"FINISH".equalsIgnoreCase(finalResult.getStatus().trim())) {
            return ResponseValidationResult.failed("Response contract failed: unsupported final status " + finalResult.getStatus());
        }
        if (finalResult.getSummary() == null || finalResult.getSummary().trim().isEmpty()) {
            return ResponseValidationResult.failed("Response contract failed: final summary is empty");
        }
        if (Boolean.TRUE.equals(finalResult.getRequiresHumanConfirmation())
                && (finalResult.getUncertaintyReason() == null || finalResult.getUncertaintyReason().trim().isEmpty())) {
            return ResponseValidationResult.failed("Response contract failed: uncertaintyReason is required");
        }
        return ResponseValidationResult.passed("Parsed response contract passed");
    }
}
