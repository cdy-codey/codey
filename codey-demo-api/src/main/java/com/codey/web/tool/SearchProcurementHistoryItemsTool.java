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
 * 给采购 AI 提供历史明细 demo 数据，便于参考同类计算机采购记录。
 */
@Component
public class SearchProcurementHistoryItemsTool extends AbstractTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchProcurementHistoryItemsTool.class);

    private final ProcurementReferenceDataService referenceDataService;
    private final ObjectMapper objectMapper;

    public SearchProcurementHistoryItemsTool(ProcurementReferenceDataService referenceDataService,
                                             ObjectMapper objectMapper) {
        this.referenceDataService = referenceDataService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "search_procurement_history_items",
                "查询历史采购明细",
                "按字段查询历史计算机采购明细 demo 列表。优先使用 queryField 和 queryValue，例如按 itemName、brandModel、department、supplier 查询。",
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
                    "search_procurement_history_items",
                    context == null ? null : context.getRequestId(),
                    context == null ? null : context.getSessionId(),
                    queryField,
                    queryValue
            );
            String content = toPrettyJson(referenceDataService.searchHistoryItems(queryField, queryValue));
            String summary = queryField == null || queryValue == null
                    ? "已返回历史采购明细参考列表。"
                    : "已返回历史采购明细参考: " + queryField + "=" + queryValue;
            return ToolResult.ok(content, summary);
        } catch (Exception exception) {
            return ToolResult.fail("search_procurement_history_items failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("queryField", stringProperty(
                "可选。指定查询字段名。支持 historyId、department、itemName、category、brandModel、specification、supplier、usageScene。"
        ));
        properties.put("queryValue", stringProperty(
                "可选。指定查询字段值，例如 queryField=itemName 时可传 台式计算机，queryField=brandModel 时可传 ThinkPad。"
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
