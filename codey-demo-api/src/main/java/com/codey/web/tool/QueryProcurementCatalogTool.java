package com.codey.web.tool;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.codey.meta.IdentityMatchMode;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采购目录（标的类型）查询工具。
 * <p>
 * 调用远程语义检索接口 {@code POST {target-information.service-url}/api/target_information/query}，
 * 请求体为 {@code {"query": "查询内容", "top_k": 10}}，通过 {@code X-API-Key} 请求头鉴权。
 * 返回与查询内容最相似的采购目录条目，供 AI 在回填 targetTypeId / targetTypeName / targetTypeCode 时参考。
 * </p>
 */
@Component
public class QueryProcurementCatalogTool extends AbstractTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryProcurementCatalogTool.class);

    /** 远程查询接口路径（拼接在基础地址之后） */
    private static final String QUERY_PATH = "/api/target_information/query";

    /** 远程服务基础地址，通过 application.yml 中 target-information.service-url 配置 */
    @Value("${target-information.service-url:}")
    private String serviceUrl;

    /** 远程接口鉴权 Key，通过 application.yml 中 target-information.api-key 配置 */
    @Value("${target-information.api-key:}")
    private String apiKey;

    /** 单个请求连接与读取超时（毫秒），防止远程接口异常导致工具长时间阻塞 */
    private static final int TIMEOUT_MILLIS = 15000;

    /** top_k 允许的最大值，避免一次拉取过多数据放大模型输入体积 */
    private static final int MAX_TOP_K = 50;

    private final ObjectMapper objectMapper;

    public QueryProcurementCatalogTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "query_procurement_catalog",
                "查询采购目录",
                "按语义查询采购目录（标的类型）条目。基于给定的查询内容返回最相似的结果，"
                        + "供填写 targetTypeId / targetTypeName / targetTypeCode 时参考。",
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
            // 校验并读取入参：query 必填，top_k 可选（默认 10）
            String query = readRequiredString(invocation, "query");
            int topK = readOptionalInt(invocation, "top_k", 10);

            // 打印工具调用日志，便于在后端控制台确认 AI 是否实际触发了该工具
            LOGGER.info(
                    "Procurement tool called: tool={}, requestId={}, sessionId={}, query={}, top_k={}",
                    "query_procurement_catalog",
                    context == null ? null : context.getRequestId(),
                    context == null ? null : context.getSessionId(),
                    query,
                    topK
            );

            // 调用远程接口获取采购目录检索结果
            String responseBody = callRemoteQuery(query, topK);

            // 将响应 JSON 美化后返回，便于 AI 直接读取条目
            String content = toPrettyJson(responseBody);
            return ToolResult.ok(content, "已返回采购目录查询结果，可从中选择最匹配的条目。");
        } catch (Exception exception) {
            return ToolResult.fail("query_procurement_catalog failed: " + exception.getMessage());
        }
    }

    /**
     * 调用远程采购目录查询接口。
     *
     * @param query 查询内容
     * @param topK  返回的最多结果条数
     * @return 接口响应体（原始 JSON 字符串）
     */
    private String callRemoteQuery(String query, int topK) {
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            throw new IllegalStateException("未配置远程采购目录服务地址，请在 application.yml 中设置 target-information.service-url");
        }

        // 拼接完整请求地址，基础地址末尾带 / 时避免出现双斜杠
        String baseUrl = serviceUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl + QUERY_PATH;

        // 构造请求体：{"query": "查询内容", "top_k": 10}
        Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("query", query);
        requestBody.put("top_k", topK);

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new IllegalStateException("构造请求体失败: " + e.getMessage(), e);
        }

        try (HttpResponse response = HttpRequest.post(url)
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .body(bodyJson)
                .timeout(TIMEOUT_MILLIS)
                .execute()) {

            int status = response.getStatus();
            String responseBody = response.body();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("远程采购目录服务返回异常状态码: HTTP " + status
                        + ", 响应内容: " + truncate(responseBody, 500));
            }
            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new IllegalStateException("远程采购目录服务返回空内容");
            }
            return responseBody;
        }
    }

    /**
     * 将响应 JSON 字符串美化（缩进）后返回；若解析失败则原样返回。
     */
    private String toPrettyJson(String json) {
        try {
            Object value = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 截断过长的字符串，避免异常信息或响应内容刷屏。
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("query", stringProperty(
                "必填。查询内容，例如 通用摄像机、便携式计算机 等标的名称或关键词。"
        ));
        properties.put("top_k", integerProperty(
                "可选。返回的最多结果条数，默认 10，最大 " + MAX_TOP_K + "。"
        ));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("query"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    /**
     * 读取必填字符串参数，缺失或为空白时抛出异常。
     */
    private String readRequiredString(ToolInvocation invocation, String key) {
        if (invocation == null || invocation.getArguments() == null
                || invocation.getArguments().get(key) == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        String text = String.valueOf(invocation.getArguments().get(key)).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("参数不能为空: " + key);
        }
        return text;
    }

    /**
     * 读取可选整数参数，缺失或非法时使用默认值，并限制在 1 ~ MAX_TOP_K 之间。
     */
    private int readOptionalInt(ToolInvocation invocation, String key, int defaultValue) {
        if (invocation == null || invocation.getArguments() == null
                || invocation.getArguments().get(key) == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(String.valueOf(invocation.getArguments().get(key)).trim());
            return Math.max(1, Math.min(value, MAX_TOP_K));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }
}
