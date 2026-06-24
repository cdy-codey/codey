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
 * 给采购 AI 提供计算机资产配置与价格参考，帮助估算合理区间。
 */
@Component
public class QueryProcurementAssetConfigurationTool extends AbstractTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryProcurementAssetConfigurationTool.class);

    private final ProcurementReferenceDataService referenceDataService;
    private final ObjectMapper objectMapper;

    public QueryProcurementAssetConfigurationTool(ProcurementReferenceDataService referenceDataService,
                                                  ObjectMapper objectMapper) {
        this.referenceDataService = referenceDataService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "query_procurement_asset_configuration",
                "查询资产配置参考",
                "按字段查询计算机采购配置与价格参考 demo 列表。优先使用 queryField 和 queryValue，例如按 itemName、scene、category、recommendedBrands 查询。",
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
            String queryField = readOptionalString(invocation, "queryField");
            String queryValue = readOptionalString(invocation, "queryValue");
            // 打印工具调用日志，便于直接在后端控制台确认 AI 是否实际触发了该工具。
            LOGGER.info(
                    "Procurement tool called: tool={}, requestId={}, sessionId={}, queryField={}, queryValue={}",
                    "query_procurement_asset_configuration",
                    context == null ? null : context.getRequestId(),
                    context == null ? null : context.getSessionId(),
                    queryField,
                    queryValue
            );
            String content = toPrettyJson(referenceDataService.queryAssetConfigurations(queryField, queryValue));
            String summary = queryField == null || queryValue == null
                    ? "已返回资产配置与价格参考列表。"
                    : "已返回资产配置与价格参考: " + queryField + "=" + queryValue;
            return ToolResult.ok(content, summary);
        } catch (Exception exception) {
            return ToolResult.fail("query_procurement_asset_configuration failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("queryField", stringProperty(
                "可选。指定查询字段名。支持 configCode、scene、category、itemName、specificationBaseline、recommendedBrands、fitFor。"
        ));
        properties.put("queryValue", stringProperty(
                "可选。指定查询字段值，例如 queryField=itemName 时可传 便携式计算机，queryField=category 时可传 目录外。"
        ));

        parameters.put("properties", properties);
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
