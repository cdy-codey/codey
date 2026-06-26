package com.codey.web.tool;

import com.codey.meta.IdentityMatchMode;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.codey.tools.WorkspaceToolContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给采购业务场景提供 context.json 结构与价格合理性校验能力。
 */
@Component
public class ValidateProcurementContextJsonTool extends AbstractTool {
    private final ProcurementContextJsonValidator validator;

    public ValidateProcurementContextJsonTool(ProcurementContextJsonValidator validator) {
        this.validator = validator;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "validate_procurement_context_json",
                "校验采购上下文",
                "读取指定的采购 context.json 文件，校验 JSON 合法性、查询接口结构、价格与目录类型规则。",
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
        metadata.setGroup("validation");
        metadata.setBundle("procurement");
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            String path = requireString(invocation, "path");
            Path resolvedPath = resolveFilePath(path, context);
            ProcurementContextJsonValidator.ValidationReport report = validator.validateFile(resolvedPath);
            String result = validator.toPrettyJson(report);
            String summary = report.isValid()
                    ? "采购上下文校验通过"
                    : "采购上下文校验未通过";
            return ToolResult.ok(result, summary);
        } catch (IllegalArgumentException exception) {
            return ToolResult.fail("校验采购上下文失败：" + exception.getMessage());
        } catch (Exception exception) {
            return ToolResult.fail("校验采购上下文失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty(
                "待校验的采购 context.json 文件路径，相对于当前工作目录。工具会读取文件内容并校验查询接口结构、价格、目录内外和预算一致性。"
        ));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Path resolveFilePath(String path, ToolContext context) {
        if (!(context instanceof WorkspaceToolContext)) {
            throw new IllegalStateException("workspace tool context is required");
        }
        return ((WorkspaceToolContext) context).resolvePath(path);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
