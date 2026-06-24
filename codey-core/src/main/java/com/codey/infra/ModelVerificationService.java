package com.codey.infra;

import com.codey.loop.FinalResult;
import com.codey.loop.FinalResultInterpreter;
import com.codey.loop.FinalResultParseResult;
import com.codey.loop.ResponseContractValidator;
import com.codey.loop.ResponseValidationResult;

import java.util.Collections;

/**
 * 通过协议契约校验模型是否能返回合规结果。
 */
public class ModelVerificationService {
    private static final String DEFAULT_VERIFY_PROMPT =
            "请只返回一个可解析的最终结果 JSON，至少包含 status 和 summary 字段。"
                    + "允许返回标准 tool_calls；禁止输出 markdown 包裹和额外解释。"
                    + "如果无法给出结果，请返回带原因的失败 summary。";
    private static final String DEFAULT_VERIFY_SYSTEM_PROMPT =
            "你是 codey 模型协议验证助手。"
                    + "只允许返回合法 JSON 最终结果或标准 tool_calls。"
                    + "不要输出 markdown，不要补充解释。";

    private final ModelGateway modelGateway;
    private final ResponseContractValidator responseContractValidator;
    private final FinalResultInterpreter finalResultInterpreter;

    public ModelVerificationService(ModelGateway modelGateway,
                                    ResponseContractValidator responseContractValidator,
                                    FinalResultInterpreter finalResultInterpreter) {
        this.modelGateway = modelGateway;
        this.responseContractValidator = responseContractValidator;
        this.finalResultInterpreter = finalResultInterpreter;
    }

    public ModelVerificationResult verify(String customPrompt) {
        String prompt = isBlank(customPrompt) ? DEFAULT_VERIFY_PROMPT : customPrompt;
        try {
            ModelRequest request = new ModelRequest();
            request.setMessages(java.util.Arrays.asList(
                    ModelMessage.system(DEFAULT_VERIFY_SYSTEM_PROMPT),
                    ModelMessage.user(prompt)
            ));

            ModelResponse response = modelGateway.chat(request);
            ResponseValidationResult rawValidation = responseContractValidator.validateModelResponse(response);
            if (!rawValidation.isPassed()) {
                return ModelVerificationResult.failure("模型响应不符合协议: " + rawValidation.getMessage(),
                        response == null ? null : response.getRawResponse());
            }

            if (response.hasToolCalls()) {
                return ModelVerificationResult.success("模型支持工具调用并通过基础协议校验", response.getRawResponse());
            }
            // 验证模式要求模型显式返回结构化最终结果，不能依赖运行时的纯文本兜底。
            if (!looksLikeJsonObject(response.getContent())) {
                return ModelVerificationResult.failure("模型输出无法解析为最终结果: expected JSON object", response.getRawResponse());
            }

            FinalResultParseResult parseResult = finalResultInterpreter.parse(response.getContent());
            if (!parseResult.isSuccess()) {
                return ModelVerificationResult.failure("模型输出无法解析为最终结果: " + parseResult.getErrorMessage(), response.getRawResponse());
            }

            FinalResult finalResult = parseResult.getFinalResult();
            ResponseValidationResult parsedValidation = responseContractValidator.validateFinalResult(finalResult);
            if (!parsedValidation.isPassed()) {
                return ModelVerificationResult.failure("最终结果字段校验失败: " + parsedValidation.getMessage(), response.getRawResponse());
            }

            return ModelVerificationResult.success("模型验证通过", response.getRawResponse());
        } catch (Exception exception) {
            return ModelVerificationResult.failure("模型验证异常: " + exception.getMessage(), null);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean looksLikeJsonObject(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return normalized.startsWith("{") && normalized.endsWith("}");
    }
}
