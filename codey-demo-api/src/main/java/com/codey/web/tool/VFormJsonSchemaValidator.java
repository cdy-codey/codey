package com.codey.web.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 v-form-designer JSON 的顶层结构与常见组件字段。
 */
@Component
public class VFormJsonSchemaValidator {
    private static final Set<String> ROOT_FIELDS = new LinkedHashSet<String>(Arrays.asList("widgetList", "formConfig"));
    private static final List<String> RECOMMENDED_FORM_CONFIG_FIELDS = Arrays.asList(
            "modelName",
            "refName",
            "rulesName",
            "labelWidth",
            "labelPosition",
            "size",
            "labelAlign",
            "cssCode",
            "customClass",
            "functions",
            "layoutType",
            "jsonVersion",
            "onFormCreated",
            "onFormMounted",
            "onFormDataChange"
    );

    private final ObjectMapper objectMapper;

    public VFormJsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationReport validate(String content) {
        ValidationReport report = new ValidationReport();
        if (isBlank(content)) {
            report.addError("$", "JSON 内容不能为空。");
            return report.finish();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            report.addError("$", "JSON 解析失败: " + exception.getOriginalMessage());
            return report.finish();
        }

        if (root == null || !root.isObject()) {
            report.addError("$", "最外层必须是 JSON 对象。");
            return report.finish();
        }

        validateRoot((ObjectNode) root, report);
        return report.finish();
    }

    /**
     * 校验磁盘中的表单 JSON 文件，避免把整段 JSON 文本再传一遍给工具。
     */
    public ValidationReport validateFile(Path filePath) {
        ValidationReport report = new ValidationReport();
        if (filePath == null) {
            report.addError("$", "表单文件路径不能为空。");
            return report.finish();
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            report.addError("$", "表单文件不存在: " + filePath);
            return report.finish();
        }
        try {
            return validate(new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            report.addError("$", "读取表单文件失败: " + exception.getMessage());
            return report.finish();
        }
    }

    public String toPrettyJson(ValidationReport report) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report.toView());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize validation report", exception);
        }
    }

    private void validateRoot(ObjectNode root, ValidationReport report) {
        if (looksLikeWrappedResult(root, "result")) {
            report.addError("$", "最外层不要包装 result，必须直接输出 widgetList 和 formConfig。");
        }
        if (looksLikeWrappedResult(root, "data")) {
            report.addError("$", "最外层不要包装 data，必须直接输出 widgetList 和 formConfig。");
        }
        if (looksLikeWrappedResult(root, "schema")) {
            report.addError("$", "最外层不要包装 schema，必须直接输出 widgetList 和 formConfig。");
        }
        if (looksLikeWrappedResult(root, "formJson")) {
            report.addError("$", "最外层不要包装 formJson，必须直接输出 widgetList 和 formConfig。");
        }

        List<String> unexpectedFields = new ArrayList<String>();
        java.util.Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!ROOT_FIELDS.contains(fieldName)) {
                unexpectedFields.add(fieldName);
            }
        }
        if (!unexpectedFields.isEmpty()) {
            report.addError("$", "最外层只允许 widgetList 和 formConfig，发现额外字段: " + unexpectedFields);
        }

        JsonNode widgetList = root.get("widgetList");
        if (widgetList == null || !widgetList.isArray()) {
            report.addError("$.widgetList", "widgetList 必须是数组。");
        } else {
            validateWidgetArray((ArrayNode) widgetList, "$.widgetList", report);
        }

        JsonNode formConfig = root.get("formConfig");
        if (formConfig == null || !formConfig.isObject()) {
            report.addError("$.formConfig", "formConfig 必须是对象。");
        } else {
            validateFormConfig((ObjectNode) formConfig, "$.formConfig", report);
        }
    }

    private void validateFormConfig(ObjectNode formConfig, String path, ValidationReport report) {
        for (String fieldName : RECOMMENDED_FORM_CONFIG_FIELDS) {
            if (!formConfig.has(fieldName)) {
                report.addWarning(path + "." + fieldName, "推荐补齐 formConfig." + fieldName + " 字段。");
            }
        }

        JsonNode layoutType = formConfig.get("layoutType");
        if (layoutType != null && !layoutType.isTextual()) {
            report.addError(path + ".layoutType", "layoutType 必须是字符串。");
        }

        JsonNode jsonVersion = formConfig.get("jsonVersion");
        if (jsonVersion != null && !jsonVersion.isNumber()) {
            report.addError(path + ".jsonVersion", "jsonVersion 必须是数字。");
        }
    }

    private void validateWidgetArray(ArrayNode widgets, String path, ValidationReport report) {
        for (int index = 0; index < widgets.size(); index++) {
            JsonNode widgetNode = widgets.get(index);
            String widgetPath = path + "[" + index + "]";
            if (widgetNode == null || !widgetNode.isObject()) {
                report.addError(widgetPath, "组件必须是对象。");
                continue;
            }
            validateWidget((ObjectNode) widgetNode, widgetPath, report);
        }
    }

    private void validateWidget(ObjectNode widget, String path, ValidationReport report) {
        String type = readText(widget, "type");
        if (isBlank(type)) {
            report.addError(path + ".type", "组件必须包含非空 type。");
        }

        if (isBlank(readText(widget, "id"))) {
            report.addError(path + ".id", "组件必须包含非空 id。");
        }

        JsonNode options = widget.get("options");
        if (options == null || !options.isObject()) {
            report.addError(path + ".options", "组件必须包含对象类型的 options。");
            return;
        }
        validateOptions((ObjectNode) options, path + ".options", report);

        boolean container = "container".equals(readText(widget, "category"));
        if (container) {
            validateContainerWidget(widget, type, path, report);
            return;
        }

        if (!widget.has("formItemFlag")) {
            report.addWarning(path + ".formItemFlag", "普通字段组件推荐显式声明 formItemFlag。");
        }
        if (isBlank(readText(widget, "icon"))) {
            report.addWarning(path + ".icon", "普通字段组件推荐显式声明 icon。");
        }
        if (asBoolean(widget.get("formItemFlag")) && isBlank(readText((ObjectNode) options, "label"))) {
            report.addWarning(path + ".options.label", "表单项组件推荐显式声明 options.label。");
        }

        validateChoiceWidgetOptions(type, (ObjectNode) options, path + ".options", report);
    }

    private void validateOptions(ObjectNode options, String path, ValidationReport report) {
        if (isBlank(readText(options, "name"))) {
            report.addError(path + ".name", "options.name 不能为空。");
        }
    }

    private void validateChoiceWidgetOptions(String type, ObjectNode options, String path, ValidationReport report) {
        if ("select".equals(type) || "radio".equals(type) || "checkbox".equals(type)) {
            JsonNode optionItems = options.get("optionItems");
            if (optionItems == null || !optionItems.isArray()) {
                report.addWarning(path + ".optionItems", type + " 组件推荐提供 optionItems 数组。");
            }
        }
    }

    private void validateContainerWidget(ObjectNode widget, String type, String path, ValidationReport report) {
        if (isBlank(readText(widget, "icon"))) {
            report.addWarning(path + ".icon", "容器组件推荐显式声明 icon。");
        }

        if ("grid".equals(type)) {
            JsonNode cols = widget.get("cols");
            if (cols == null || !cols.isArray()) {
                report.addError(path + ".cols", "grid 容器必须包含 cols 数组。");
                return;
            }
            validateGridColumns((ArrayNode) cols, path + ".cols", report);
            if (!widget.path("options").has("gutter")) {
                report.addWarning(path + ".options.gutter", "grid 容器推荐显式声明 options.gutter。");
            }
            return;
        }

        if ("grid-col".equals(type)) {
            validateGridColumn(widget, path, report);
            return;
        }

        if ("card".equals(type)) {
            validateRequiredWidgetList(widget, path, report);
            return;
        }

        if ("tabs".equals(type)) {
            JsonNode tabs = widget.get("tabs");
            if (tabs == null || !tabs.isArray()) {
                report.addError(path + ".tabs", "tabs 容器必须包含 tabs 数组。");
                return;
            }
            validateWidgetArray((ArrayNode) tabs, path + ".tabs", report);
            return;
        }

        if ("tab-pane".equals(type)) {
            validateRequiredWidgetList(widget, path, report);
            return;
        }

        // 未知容器类型先校验常见子结构，避免误判但仍给出提示。
        boolean hasKnownChildren = false;
        if (widget.has("widgetList") && widget.get("widgetList").isArray()) {
            hasKnownChildren = true;
            validateWidgetArray((ArrayNode) widget.get("widgetList"), path + ".widgetList", report);
        }
        if (widget.has("cols") && widget.get("cols").isArray()) {
            hasKnownChildren = true;
            validateWidgetArray((ArrayNode) widget.get("cols"), path + ".cols", report);
        }
        if (widget.has("tabs") && widget.get("tabs").isArray()) {
            hasKnownChildren = true;
            validateWidgetArray((ArrayNode) widget.get("tabs"), path + ".tabs", report);
        }
        if (!hasKnownChildren) {
            report.addWarning(path, "未识别的容器组件，推荐补齐常见子结构字段 widgetList、cols 或 tabs。");
        }
    }

    private void validateGridColumns(ArrayNode cols, String path, ValidationReport report) {
        for (int index = 0; index < cols.size(); index++) {
            JsonNode colNode = cols.get(index);
            String colPath = path + "[" + index + "]";
            if (colNode == null || !colNode.isObject()) {
                report.addError(colPath, "grid-cols 中的每一项都必须是对象。");
                continue;
            }
            ObjectNode column = (ObjectNode) colNode;
            if (!"grid-col".equals(readText(column, "type"))) {
                report.addError(colPath + ".type", "grid 的 cols 只允许 grid-col。");
            }
            validateGridColumn(column, colPath, report);
        }
    }

    private void validateGridColumn(ObjectNode column, String path, ValidationReport report) {
        JsonNode widgetList = column.get("widgetList");
        if (widgetList == null || !widgetList.isArray()) {
            report.addError(path + ".widgetList", "grid-col 必须包含 widgetList 数组。");
        } else {
            validateWidgetArray((ArrayNode) widgetList, path + ".widgetList", report);
        }

        ObjectNode options = asObject(column.get("options"));
        if (options != null) {
            if (!options.has("span")) {
                report.addWarning(path + ".options.span", "grid-col 推荐显式声明 options.span。");
            }
            if (!options.has("responsive")) {
                report.addWarning(path + ".options.responsive", "grid-col 推荐显式声明 options.responsive。");
            }
        }

        if (!column.has("internal")) {
            report.addWarning(path + ".internal", "grid-col 推荐显式声明 internal: true。");
        }
    }

    private void validateRequiredWidgetList(ObjectNode widget, String path, ValidationReport report) {
        JsonNode widgetList = widget.get("widgetList");
        if (widgetList == null || !widgetList.isArray()) {
            report.addError(path + ".widgetList", "容器组件必须包含 widgetList 数组。");
            return;
        }
        validateWidgetArray((ArrayNode) widgetList, path + ".widgetList", report);
    }

    private boolean looksLikeWrappedResult(ObjectNode root, String fieldName) {
        JsonNode child = root.get(fieldName);
        return child != null
                && child.isObject()
                && child.has("widgetList")
                && child.has("formConfig");
    }

    private ObjectNode asObject(JsonNode node) {
        return node != null && node.isObject() ? (ObjectNode) node : null;
    }

    private boolean asBoolean(JsonNode node) {
        return node != null && node.isBoolean() && node.booleanValue();
    }

    private String readText(ObjectNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ValidationReport {
        private boolean valid;
        private final List<ValidationIssue> errors = new ArrayList<ValidationIssue>();
        private final List<ValidationIssue> warnings = new ArrayList<ValidationIssue>();

        public boolean isValid() {
            return valid;
        }

        public List<ValidationIssue> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<ValidationIssue> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public ValidationReport addError(String path, String message) {
            errors.add(new ValidationIssue("error", path, message));
            return this;
        }

        public ValidationReport addWarning(String path, String message) {
            warnings.add(new ValidationIssue("warning", path, message));
            return this;
        }

        public ValidationReport finish() {
            this.valid = errors.isEmpty();
            return this;
        }

        public Map<String, Object> toView() {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("valid", valid);
            view.put("errorCount", errors.size());
            view.put("warningCount", warnings.size());
            view.put("errors", issuesToView(errors));
            view.put("warnings", issuesToView(warnings));
            view.put("summary", buildSummary());
            return view;
        }

        private List<Map<String, Object>> issuesToView(List<ValidationIssue> issues) {
            List<Map<String, Object>> view = new ArrayList<Map<String, Object>>();
            for (ValidationIssue issue : issues) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("severity", issue.getSeverity());
                item.put("path", issue.getPath());
                item.put("message", issue.getMessage());
                view.add(item);
            }
            return view;
        }

        private String buildSummary() {
            if (valid && warnings.isEmpty()) {
                return "校验通过，未发现结构问题。";
            }
            if (valid) {
                return "校验通过，但仍有 " + warnings.size() + " 条推荐修正项。";
            }
            return "校验失败，发现 " + errors.size() + " 条错误，" + warnings.size() + " 条警告。";
        }
    }

    public static class ValidationIssue {
        private final String severity;
        private final String path;
        private final String message;

        public ValidationIssue(String severity, String path, String message) {
            this.severity = severity;
            this.path = path;
            this.message = message;
        }

        public String getSeverity() {
            return severity;
        }

        public String getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }
    }
}
