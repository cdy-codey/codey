package com.codey.web.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验采购业务 context.json，避免模型把页面上下文改成非法 JSON 或不合理业务数据。
 */
@Component
public class ProcurementContextJsonValidator {
    private static final Set<String> ROOT_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "scenarioId",
            "title",
            "subtitle",
            "breadcrumbs",
            "goal",
            "header",
            "detail",
            "items",
            "formFields",
            "aiInsightCards",
            "aiSuggestions",
            "urgencyOptions",
            "orderOptions",
            "departmentOptions",
            "categoryOptions",
            "purchaseTypeOptions",
            "catalogScopeOptions",
            "aiInstruction"
    ));
    private static final Set<String> CATEGORY_VALUES = new LinkedHashSet<String>(Arrays.asList("目录内", "目录外"));
    private static final Set<String> PURCHASE_TYPE_VALUES = new LinkedHashSet<String>(Arrays.asList("非政府采购", "政府采购"));
    private static final BigDecimal DIRECTORY_IN_STANDARD_LIMIT = new BigDecimal("6000");
    private static final BigDecimal MIN_REASONABLE_COMPUTER_PRICE = new BigDecimal("2500");
    private static final BigDecimal MAX_REASONABLE_COMPUTER_PRICE = new BigDecimal("20000");

    private final ObjectMapper objectMapper;

    public ProcurementContextJsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationReport validate(String content) {
        ValidationReport report = new ValidationReport();
        if (isBlank(content)) {
            report.addError("$", "JSON 内容不能为空。");
            return report.finish();
        }
        if (content.contains("```")) {
            report.addError("$", "context.json 只能写纯 JSON，不能包含 Markdown 代码块。");
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

    public ValidationReport validateFile(Path filePath) {
        ValidationReport report = new ValidationReport();
        if (filePath == null) {
            report.addError("$", "采购上下文文件路径不能为空。");
            return report.finish();
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            report.addError("$", "采购上下文文件不存在: " + filePath);
            return report.finish();
        }
        try {
            return validate(new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            report.addError("$", "读取采购上下文文件失败: " + exception.getMessage());
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
        java.util.Iterator<String> fieldNames = root.fieldNames();
        List<String> unexpectedFields = new ArrayList<String>();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!ROOT_FIELDS.contains(fieldName)) {
                unexpectedFields.add(fieldName);
            }
        }
        if (!unexpectedFields.isEmpty()) {
            report.addWarning("$", "发现查询接口之外的额外字段: " + unexpectedFields);
        }
        for (String fieldName : ROOT_FIELDS) {
            if (!root.has(fieldName)) {
                report.addError("$." + fieldName, "缺少查询接口定义的字段 " + fieldName + "。");
            }
        }

        ObjectNode header = asObject(root.get("header"));
        if (header == null) {
            report.addError("$.header", "header 必须是对象。");
        } else {
            validateHeader(header, report);
        }

        ObjectNode detail = asObject(root.get("detail"));
        if (detail == null) {
            report.addError("$.detail", "detail 必须是对象。");
        } else {
            validateDetail(detail, report);
        }

        ArrayNode items = asArray(root.get("items"));
        if (items == null) {
            report.addError("$.items", "items 必须是数组。");
        } else {
            validateItems(items, report);
        }

        validateArrayField(root.get("breadcrumbs"), "$.breadcrumbs", report);
        validateArrayField(root.get("formFields"), "$.formFields", report);
        validateArrayField(root.get("aiInsightCards"), "$.aiInsightCards", report);
        validateArrayField(root.get("aiSuggestions"), "$.aiSuggestions", report);
        validateArrayField(root.get("urgencyOptions"), "$.urgencyOptions", report);
        validateArrayField(root.get("orderOptions"), "$.orderOptions", report);
        validateArrayField(root.get("departmentOptions"), "$.departmentOptions", report);
        validateArrayField(root.get("categoryOptions"), "$.categoryOptions", report);
        validateArrayField(root.get("purchaseTypeOptions"), "$.purchaseTypeOptions", report);
        validateArrayField(root.get("catalogScopeOptions"), "$.catalogScopeOptions", report);

        if (header != null && detail != null && items != null) {
            validateBudgetConsistency(header, detail, items, report);
            validateCatalogAndPriceRules(header, detail, items, report);
        }
    }

    private void validateHeader(ObjectNode header, ValidationReport report) {
        requireNonBlankText(header, "urgencyLevel", "$.header.urgencyLevel", report);
        requireNonBlankText(header, "relatedOrderNo", "$.header.relatedOrderNo", report);
        requireNonBlankText(header, "requestDepartment", "$.header.requestDepartment", report);
        requireNonBlankText(header, "assetType", "$.header.assetType", report);
        requireNonBlankText(header, "purchaseType", "$.header.purchaseType", report);
        requireNonBlankText(header, "requestDescription", "$.header.requestDescription", report);

        String requestDate = requireNonBlankText(header, "requestDate", "$.header.requestDate", report);
        if (!isBlank(requestDate)) {
            try {
                LocalDate.parse(requestDate);
            } catch (DateTimeParseException exception) {
                report.addError("$.header.requestDate", "requestDate 必须是 YYYY-MM-DD 格式。");
            }
        }

        String purchaseType = readText(header, "purchaseType");
        if (!isBlank(purchaseType) && !PURCHASE_TYPE_VALUES.contains(purchaseType)) {
            report.addError("$.header.purchaseType", "purchaseType 只允许填写“非政府采购”或“政府采购”。");
        }
    }

    private void validateDetail(ObjectNode detail, ValidationReport report) {
        BigDecimal budgetAmount = readDecimal(detail.get("budgetAmount"));
        if (budgetAmount == null) {
            report.addError("$.detail.budgetAmount", "budgetAmount 必须是数字。");
        } else if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            report.addError("$.detail.budgetAmount", "budgetAmount 必须大于 0。");
        }
        requireNonBlankText(detail, "requestReason", "$.detail.requestReason", report);
        if (detail.has("exceedReason") && !detail.get("exceedReason").isNull() && !detail.get("exceedReason").isTextual()) {
            report.addError("$.detail.exceedReason", "exceedReason 必须是字符串。");
        }
    }

    private void validateItems(ArrayNode items, ValidationReport report) {
        if (items.size() == 0) {
            report.addError("$.items", "items 至少需要保留一条采购明细。");
            return;
        }
        Set<Integer> rowNumbers = new LinkedHashSet<Integer>();
        for (int index = 0; index < items.size(); index++) {
            JsonNode itemNode = items.get(index);
            String path = "$.items[" + index + "]";
            if (itemNode == null || !itemNode.isObject()) {
                report.addError(path, "每一条采购明细都必须是对象。");
                continue;
            }
            ObjectNode item = (ObjectNode) itemNode;
            Integer rowNo = readInteger(item.get("rowNo"));
            if (rowNo == null || rowNo.intValue() <= 0) {
                report.addError(path + ".rowNo", "rowNo 必须是大于 0 的整数。");
            } else if (!rowNumbers.add(rowNo)) {
                report.addError(path + ".rowNo", "rowNo 不能重复。");
            }
            requireNonBlankText(item, "demandType", path + ".demandType", report);
            String category = requireNonBlankText(item, "category", path + ".category", report);
            if (!isBlank(category) && !CATEGORY_VALUES.contains(category)) {
                report.addError(path + ".category", "目录类型只允许填写“目录内”或“目录外”。");
            }
            requireNonBlankText(item, "itemName", path + ".itemName", report);
            Integer quantity = readInteger(item.get("quantity"));
            if (quantity == null || quantity.intValue() < 1) {
                report.addError(path + ".quantity", "quantity 必须是大于等于 1 的整数。");
            }
            BigDecimal unitPrice = readDecimal(item.get("unitPrice"));
            if (unitPrice == null) {
                report.addError(path + ".unitPrice", "unitPrice 必须是数字。");
            } else if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                report.addError(path + ".unitPrice", "unitPrice 必须大于 0，不能继续保留占位值 0。");
            }
            requireNonBlankText(item, "specification", path + ".specification", report);
            requireNonBlankText(item, "referenceBrand", path + ".referenceBrand", report);
            if (item.has("recommended") && !item.get("recommended").isBoolean()) {
                report.addError(path + ".recommended", "recommended 必须是布尔值。");
            }
            validatePriceReasonability(item, path, report);
        }
    }

    private void validateBudgetConsistency(ObjectNode header, ObjectNode detail, ArrayNode items, ValidationReport report) {
        BigDecimal budgetAmount = readDecimal(detail.get("budgetAmount"));
        if (budgetAmount == null) {
            return;
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            JsonNode itemNode = items.get(index);
            if (itemNode == null || !itemNode.isObject()) {
                continue;
            }
            Integer quantity = readInteger(itemNode.get("quantity"));
            BigDecimal unitPrice = readDecimal(itemNode.get("unitPrice"));
            if (quantity == null || unitPrice == null) {
                continue;
            }
            totalAmount = totalAmount.add(unitPrice.multiply(new BigDecimal(quantity.intValue())));
        }
        if (budgetAmount.compareTo(totalAmount) != 0) {
            report.addError(
                    "$.detail.budgetAmount",
                    "budgetAmount 必须等于明细行数量乘单价之和。当前预算 "
                            + budgetAmount.toPlainString()
                            + "，明细合计 "
                            + totalAmount.toPlainString()
                            + "。"
            );
        }

        String description = readText(header, "requestDescription");
        if (!isBlank(description) && budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
            String budgetText = budgetAmount.stripTrailingZeros().toPlainString();
            if (!description.contains(budgetText)) {
                report.addWarning("$.header.requestDescription", "requestDescription 建议体现当前预算金额 " + budgetText + "。");
            }
        }
    }

    private void validateCatalogAndPriceRules(ObjectNode header, ObjectNode detail, ArrayNode items, ValidationReport report) {
        String purchaseType = readText(header, "purchaseType");
        boolean hasDirectoryOutItem = false;
        boolean hasDirectoryInItem = false;
        for (int index = 0; index < items.size(); index++) {
            JsonNode itemNode = items.get(index);
            if (itemNode == null || !itemNode.isObject()) {
                continue;
            }
            ObjectNode item = (ObjectNode) itemNode;
            String path = "$.items[" + index + "]";
            String category = readText(item, "category");
            BigDecimal unitPrice = readDecimal(item.get("unitPrice"));
            if ("目录外".equals(category)) {
                hasDirectoryOutItem = true;
            }
            if ("目录内".equals(category)) {
                hasDirectoryInItem = true;
                if (unitPrice != null && unitPrice.compareTo(DIRECTORY_IN_STANDARD_LIMIT) > 0) {
                    report.addWarning(path + ".unitPrice", "目录内项目单价高于 6000 元，需再次确认是否仍属于目录内标准配置。");
                }
            }
        }

        String exceedReason = readText(detail, "exceedReason");
        if (hasDirectoryOutItem && isBlank(exceedReason)) {
            report.addWarning("$.detail.exceedReason", "存在目录外采购项目时，建议补充目录外采购原因与合规说明。");
        }
        if ("政府采购".equals(purchaseType) && !hasDirectoryInItem && hasDirectoryOutItem) {
            report.addWarning("$.items", "当前采购方式为政府采购，但明细全部为目录外项目，请确认目录识别是否准确。");
        }
    }

    private void validatePriceReasonability(ObjectNode item, String path, ValidationReport report) {
        String itemName = readText(item, "itemName");
        String specification = readText(item, "specification");
        BigDecimal unitPrice = readDecimal(item.get("unitPrice"));
        if (unitPrice == null) {
            return;
        }
        if (looksLikeComputer(itemName, specification)) {
            if (unitPrice.compareTo(MIN_REASONABLE_COMPUTER_PRICE) < 0) {
                report.addWarning(path + ".unitPrice", "电脑类设备单价明显偏低，请核对是否真实合理。");
            }
            if (unitPrice.compareTo(MAX_REASONABLE_COMPUTER_PRICE) > 0) {
                report.addWarning(path + ".unitPrice", "电脑类设备单价明显偏高，请核对市场价格与采购必要性。");
            }
        }
    }

    private void validateArrayField(JsonNode node, String path, ValidationReport report) {
        if (node == null) {
            return;
        }
        if (!node.isArray()) {
            report.addError(path, path.substring(2) + " 必须是数组。");
        }
    }

    private boolean looksLikeComputer(String itemName, String specification) {
        String text = (itemName == null ? "" : itemName) + " " + (specification == null ? "" : specification);
        return text.contains("电脑")
                || text.contains("计算机")
                || text.contains("笔记本")
                || text.contains("台式");
    }

    private String requireNonBlankText(ObjectNode node, String fieldName, String path, ValidationReport report) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isTextual() || isBlank(field.asText())) {
            report.addError(path, fieldName + " 不能为空。");
            return null;
        }
        return field.asText();
    }

    private ObjectNode asObject(JsonNode node) {
        return node != null && node.isObject() ? (ObjectNode) node : null;
    }

    private ArrayNode asArray(JsonNode node) {
        return node != null && node.isArray() ? (ArrayNode) node : null;
    }

    private String readText(ObjectNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal readDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer readInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return Integer.valueOf(node.asInt());
        }
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
                return "采购 context.json 校验通过，未发现结构问题。";
            }
            if (valid) {
                return "采购 context.json 校验通过，但仍有 " + warnings.size() + " 条风险提醒。";
            }
            return "采购 context.json 校验失败，发现 " + errors.size() + " 条错误，" + warnings.size() + " 条风险提醒。";
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
