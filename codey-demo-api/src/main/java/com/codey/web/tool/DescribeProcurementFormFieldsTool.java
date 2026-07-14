package com.codey.web.tool;

import com.codey.web.service.ProcurementReferenceDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codey.meta.IdentityMatchMode;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给采购 AI 提供表单字段说明，帮助模型理解字段语义和填写约束。
 */
@Component
public class DescribeProcurementFormFieldsTool extends AbstractTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DescribeProcurementFormFieldsTool.class);

    private final ProcurementReferenceDataService referenceDataService;
    private final ObjectMapper objectMapper;

    public DescribeProcurementFormFieldsTool(ProcurementReferenceDataService referenceDataService,
                                             ObjectMapper objectMapper) {
        this.referenceDataService = referenceDataService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "describe_procurement_form_fields",
                "查询采购申请单字段说明",
                "获取采购表单各字段的详细说明、枚举值定义和填充规则。不需要入参，已全部返回。",
                buildParameters()
        );
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.readOnlyParallel();
    }

    @Override
    public ToolMetadata metadata() {
        ToolMetadata metadata = ToolMetadata.standard();
        metadata.setSupportedIdentities(Arrays.asList("programming"));
        metadata.setIdentityMatchMode(IdentityMatchMode.ANY);
        metadata.setGroup("procurement");
        metadata.setBundle("procurement");
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            // 打印工具调用日志，便于直接在后端控制台确认 AI 是否实际触发了该工具。
            LOGGER.info(
                    "Procurement tool called: tool={}, requestId={}, sessionId={}",
                    "describe_procurement_form_fields",
                    context == null ? null : context.getRequestId(),
                    context == null ? null : context.getSessionId()
            );
            String content = toPrettyJson(referenceDataService.getFormFieldGuide(null));
            return ToolResult.ok(content, "已返回采购表单字段说明。");
        } catch (Exception exception) {
            return ToolResult.fail("describe_procurement_form_fields failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", new LinkedHashMap<String, Object>());
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String toPrettyJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private String readOptionalString(ToolInvocation invocation, String key) {
        if (invocation == null || invocation.getArguments() == null) {
            return null;
        }
        Object value = invocation.getArguments().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
