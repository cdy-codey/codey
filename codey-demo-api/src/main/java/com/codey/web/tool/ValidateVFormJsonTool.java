package com.codey.web.tool;

import com.codey.infra.WorkspacePathSupport;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.codey.tools.WorkspaceToolContext;
import com.codey.meta.IdentityMatchMode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给模型提供 v-form-designer JSON 结构校验能力。
 */
@Component
public class ValidateVFormJsonTool extends AbstractTool {
    private final VFormJsonSchemaValidator validator;

    public ValidateVFormJsonTool(VFormJsonSchemaValidator validator) {
        this.validator = validator;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "validate_vform_json",
                "校验表单格式",
                "读取指定的 v-form-designer JSON 文件，并校验其顶层结构、widgetList 组件字段和常见容器字段。",
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
        // 该工具只在 vform 会话中参与编排，避免普通代码会话拿到领域校验工具。
        metadata.setSupportedIdentities(Arrays.asList("vform"));
        metadata.setIdentityMatchMode(IdentityMatchMode.ANY);
        metadata.setGroup("validation");
        metadata.setBundle("vform");
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            String path = requireString(invocation, "path");
            Path resolvedPath = resolveFilePath(path, context);
            VFormJsonSchemaValidator.ValidationReport report = validator.validateFile(resolvedPath);
            String result = validator.toPrettyJson(report);
            String summary = report.isValid()
                    ? "表单格式校验通过"
                    : "表单格式校验未通过";
            return ToolResult.ok(result, summary);
        } catch (IllegalArgumentException exception) {
            return ToolResult.fail("校验表单格式失败：" + exception.getMessage());
        } catch (Exception exception) {
            return ToolResult.fail("校验表单格式失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty(
                "待校验的 v-form-designer JSON 文件路径，相对于当前工作目录。工具会读取文件内容并校验，最外层必须直接是 widgetList 和 formConfig。"
        ));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    /**
     * 该工具必须运行在工作区上下文中，并显式按当前 workingDirectory 解析文件路径。
     */
    private Path resolveFilePath(String path, ToolContext context) {
        if (!(context instanceof WorkspaceToolContext)) {
            throw new IllegalStateException("workspace tool context is required");
        }
        WorkspaceToolContext workspaceContext = (WorkspaceToolContext) context;
        // ToolContext 只暴露工作目录字段，实际解析仍由 core 的工作区边界规则统一处理。
        return WorkspacePathSupport.resolveToolPath(
                workspaceContext.getWorkspaceRoot(),
                context.getWorkingDirectory(),
                path
        );
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
