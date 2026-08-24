package com.codey.mcp;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.FileMutationSupport;
import com.codey.tools.WorkspaceToolContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 面向 AI 的 JSON 精确编辑工具。
 * 通过 JSON Pointer 和结构化操作修改局部节点，避免模型反复拼接整段文本。
 */
public class EditJsonTool extends AbstractWorkspaceTool {
    private static final int MAX_PREVIEW_LINES = 20;

    /**
     * 支持的日期时间格式（从粗到细），set 时若字符串匹配日期前缀则校验。
     */
    private static final Pattern[] DATE_TIME_PATTERNS = {
            Pattern.compile("\\d{4}-\\d{2}"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
    };

    /** 触发日期校验的前缀：年份-月份 格式 */
    private static final Pattern DATE_LIKE_PREFIX = Pattern.compile("\\d{4}-\\d{2}");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "edit_json";
    }

    @Override
    public String displayName() {
        return "格式化编辑文件";
    }

    @Override
    public String description() {
        return "Primary editing tool for .json files. Prefer this over edit_file for JSON. Supports set/remove/merge/append/add by pointer, replace (standard or recursive key match).";
    }

    @Override
    public ModelToolDefinition toModelToolDefinition() {
        ModelToolDefinition definition = new ModelToolDefinition();
        definition.setName(name());
        definition.setDescription(description());
        definition.setParameters(buildParameters());
        return definition;
    }

    @Override
    public ToolResult execute(ToolInvocation request, WorkspaceToolContext context) {
        try {
            String pathValue = readRequiredString(request, "path");
            List<Map<String, Object>> operations = readRequiredOperations(request, "operations");
            Path target = context.resolvePath(pathValue);
            String originalContent = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            JsonNode currentRoot = objectMapper.readTree(originalContent);
            if (currentRoot == null) {
                throw new IllegalArgumentException("target json is empty");
            }

            List<Map<String, Object>> appliedOperations = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> rawOperation : operations) {
                OperationResult operationResult = applyOperation(currentRoot, rawOperation);
                currentRoot = operationResult.getRoot();
                appliedOperations.add(operationResult.getPayload());
            }

            String updatedContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentRoot);
            // 写入前 re-parse 校验 JSON 合法性，不合法则报告具体位置
            validateJson(updatedContent);
            Files.write(target, updatedContent.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("operations", appliedOperations);
            // diff 摘要与预览一次行扫描产出，避免对原/新内容重复 split 与遍历。
            FileMutationSupport.DiffResult diff =
                    FileMutationSupport.buildDiff(originalContent, updatedContent, MAX_PREVIEW_LINES);
            payload.put("diffSummary", diff.getSummary());
            payload.put("preview", diff.getPreview());
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Edit json success:\n" + result, "已完成 JSON 更新");
        } catch (Exception exception) {
            return ToolResult.fail("编辑 JSON 失败：" + exception.getMessage());
        }
    }

    private OperationResult applyOperation(JsonNode root, Map<String, Object> rawOperation) {
        String op = requiredText(rawOperation, "op").toLowerCase();
        String pointer = normalizePointer(readPointer(rawOperation, "pointer"));
        boolean createMissing = readBoolean(rawOperation, "createMissing", true);
        JsonNode valueNode = rawOperation.containsKey("value")
                ? objectMapper.valueToTree(rawOperation.get("value"))
                : null;

        // add 等价于 append
        if ("set".equals(op)) {
            JsonNode updatedRoot = applySet(root, pointer, valueNode, createMissing);
            return OperationResult.of(updatedRoot, buildOperationPayload(op, pointer, "已设置 JSON 节点"));
        }
        // replace 有 key 时递归替换同名键值，无 key 时等价于 set（兼容标准 JSON Patch）
        if ("replace".equals(op)) {
            String key = readKey(rawOperation, "key");
            if (key != null && !key.trim().isEmpty()) {
                int replacedCount = applyReplace(root, pointer, key, valueNode);
                return OperationResult.of(root, buildReplacePayload(pointer, key, replacedCount));
            }
            // 无 key 时按标准 JSON Patch replace 语义：等价于 set
            JsonNode updatedRoot = applySet(root, pointer, valueNode, createMissing);
            return OperationResult.of(updatedRoot, buildOperationPayload(op, pointer, "已替换 JSON 节点"));
        }
        if ("remove".equals(op)) {
            JsonNode updatedRoot = applyRemove(root, pointer);
            return OperationResult.of(updatedRoot, buildOperationPayload(op, pointer, "已删除 JSON 节点"));
        }
        if ("merge".equals(op)) {
            JsonNode updatedRoot = applyMerge(root, pointer, valueNode, createMissing);
            return OperationResult.of(updatedRoot, buildOperationPayload(op, pointer, "已合并 JSON 对象"));
        }
        if ("append".equals(op) || "add".equals(op)) {
            JsonNode updatedRoot = applyAppend(root, pointer, valueNode, createMissing);
            return OperationResult.of(updatedRoot, buildOperationPayload(op, pointer, "已向 JSON 数组追加元素"));
        }
        throw new IllegalArgumentException("unsupported op: " + op);
    }

    private JsonNode applySet(JsonNode root, String pointer, JsonNode valueNode, boolean createMissing) {
        if (valueNode == null) {
            throw new IllegalArgumentException("set operation requires value");
        }
        // 文本值若疑似日期则校验格式
        if (valueNode.isTextual()) {
            validateDateTimeFormat(valueNode.asText(), pointer);
        }
        if (isRootPointer(pointer)) {
            return valueNode.deepCopy();
        }
        PointerTarget parentTarget = resolveParent(root, pointer, createMissing);
        JsonNode parent = parentTarget.getNode();
        String token = parentTarget.getLastToken();
        JsonNode replacement = valueNode.deepCopy();
        if (parent.isObject()) {
            ((ObjectNode) parent).set(token, replacement);
            return root;
        }
        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            if ("-".equals(token)) {
                arrayNode.add(replacement);
                return root;
            }
            int index = parseArrayIndex(token);
            if (index < arrayNode.size()) {
                arrayNode.set(index, replacement);
                return root;
            }
            if (index == arrayNode.size()) {
                arrayNode.add(replacement);
                return root;
            }
            throw new IllegalArgumentException("array index out of bounds: " + index);
        }
        throw new IllegalArgumentException("parent node is neither object nor array for pointer: " + pointer);
    }

    private JsonNode applyRemove(JsonNode root, String pointer) {
        if (isRootPointer(pointer)) {
            throw new IllegalArgumentException("remove root is not supported");
        }
        PointerTarget parentTarget = resolveParent(root, pointer, false);
        JsonNode parent = parentTarget.getNode();
        String token = parentTarget.getLastToken();
        if (parent.isObject()) {
            JsonNode removed = ((ObjectNode) parent).remove(token);
            if (removed == null) {
                throw new IllegalArgumentException("target pointer does not exist: " + pointer);
            }
            return root;
        }
        if (parent.isArray()) {
            ArrayNode arrayNode = (ArrayNode) parent;
            int index = parseArrayIndex(token);
            if (index < 0 || index >= arrayNode.size()) {
                throw new IllegalArgumentException("array index out of bounds: " + index);
            }
            arrayNode.remove(index);
            return root;
        }
        throw new IllegalArgumentException("parent node is neither object nor array for pointer: " + pointer);
    }

    private JsonNode applyMerge(JsonNode root, String pointer, JsonNode valueNode, boolean createMissing) {
        if (valueNode == null || !valueNode.isObject()) {
            throw new IllegalArgumentException("merge operation requires object value");
        }
        if (isRootPointer(pointer)) {
            if (!root.isObject()) {
                throw new IllegalArgumentException("root merge requires target root object");
            }
            deepMerge((ObjectNode) root, (ObjectNode) valueNode);
            return root;
        }
        JsonNode target = resolveNode(root, pointer);
        if (target == null || target.isMissingNode() || target.isNull()) {
            if (!createMissing) {
                throw new IllegalArgumentException("target pointer does not exist: " + pointer);
            }
            root = applySet(root, pointer, JsonNodeFactory.instance.objectNode(), true);
            target = resolveNode(root, pointer);
        }
        if (!target.isObject()) {
            throw new IllegalArgumentException("merge target must be object: " + pointer);
        }
        deepMerge((ObjectNode) target, (ObjectNode) valueNode);
        return root;
    }

    private JsonNode applyAppend(JsonNode root, String pointer, JsonNode valueNode, boolean createMissing) {
        if (valueNode == null) {
            throw new IllegalArgumentException("append operation requires value");
        }
        JsonNode target = resolveNode(root, pointer);
        if (target == null || target.isMissingNode() || target.isNull()) {
            if (!createMissing) {
                throw new IllegalArgumentException("target pointer does not exist: " + pointer);
            }
            root = applySet(root, pointer, JsonNodeFactory.instance.arrayNode(), true);
            target = resolveNode(root, pointer);
        }
        if (!target.isArray()) {
            throw new IllegalArgumentException("append target must be array: " + pointer);
        }
        ((ArrayNode) target).add(valueNode.deepCopy());
        return root;
    }

    /**
     * 递归替换 JSON 树中所有匹配 key 的节点值。
     *
     * @param root   根节点
     * @param pointer 搜索范围，空字符串表示整棵树
     * @param key    要匹配的字段名
     * @param valueNode 新的值
     * @return 实际替换的数量
     */
    private int applyReplace(JsonNode root, String pointer, String key, JsonNode valueNode) {
        if (valueNode == null) {
            throw new IllegalArgumentException("replace operation requires value");
        }
        JsonNode scope = resolveNode(root, pointer);
        if (scope == null || scope.isMissingNode()) {
            throw new IllegalArgumentException("replace scope does not exist: " + pointer);
        }
        return replaceMatchingKeys(scope, key, valueNode);
    }

    /**
     * 在指定节点内递归查找匹配 key，替换其值。
     */
    private int replaceMatchingKeys(JsonNode node, String key, JsonNode newValue) {
        int count = 0;
        if (node == null || node.isMissingNode()) {
            return count;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode child = field.getValue();
                if (fieldName.equals(key)) {
                    // 文本值若疑似日期则校验格式
                    if (newValue.isTextual()) {
                        validateDateTimeFormat(newValue.asText(), key);
                    }
                    objectNode.set(fieldName, newValue.deepCopy());
                    count++;
                } else if (child.isObject() || child.isArray()) {
                    count += replaceMatchingKeys(child, key, newValue);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                count += replaceMatchingKeys(item, key, newValue);
            }
        }
        return count;
    }

    /**
     * 按 pointer 定位父节点，并在需要时自动补齐中间对象或数组。
     */
    private PointerTarget resolveParent(JsonNode root, String pointer, boolean createMissing) {
        List<String> tokens = parsePointer(pointer);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("pointer must not point to root for this operation");
        }
        JsonNode current = root;
        for (int index = 0; index < tokens.size() - 1; index++) {
            String token = tokens.get(index);
            String nextToken = tokens.get(index + 1);
            if (current.isObject()) {
                ObjectNode objectNode = (ObjectNode) current;
                JsonNode child = objectNode.get(token);
                if (child == null || child.isNull() || child.isMissingNode()) {
                    if (!createMissing) {
                        throw new IllegalArgumentException("target pointer does not exist: " + pointer);
                    }
                    child = createContainerForNextToken(nextToken);
                    objectNode.set(token, child);
                }
                current = child;
                continue;
            }
            if (current.isArray()) {
                ArrayNode arrayNode = (ArrayNode) current;
                int arrayIndex = parseArrayIndex(token);
                if (arrayIndex < arrayNode.size()) {
                    JsonNode child = arrayNode.get(arrayIndex);
                    if ((child == null || child.isNull() || child.isMissingNode()) && createMissing) {
                        child = createContainerForNextToken(nextToken);
                        arrayNode.set(arrayIndex, child);
                    }
                    if (child == null || child.isNull() || child.isMissingNode()) {
                        throw new IllegalArgumentException("target pointer does not exist: " + pointer);
                    }
                    current = child;
                    continue;
                }
                if (arrayIndex == arrayNode.size() && createMissing) {
                    JsonNode child = createContainerForNextToken(nextToken);
                    arrayNode.add(child);
                    current = child;
                    continue;
                }
                throw new IllegalArgumentException("array index out of bounds: " + arrayIndex);
            }
            throw new IllegalArgumentException("encountered scalar node before pointer ended: " + pointer);
        }
        return new PointerTarget(current, tokens.get(tokens.size() - 1));
    }

    private JsonNode resolveNode(JsonNode root, String pointer) {
        if (isRootPointer(pointer)) {
            return root;
        }
        return root.at(pointer);
    }

    private JsonNode createContainerForNextToken(String nextToken) {
        if (isArrayToken(nextToken)) {
            return JsonNodeFactory.instance.arrayNode();
        }
        return JsonNodeFactory.instance.objectNode();
    }

    /**
     * 递归合并对象，保留目标对象中未覆盖的字段。
     */
    private void deepMerge(ObjectNode target, ObjectNode update) {
        Iterator<Map.Entry<String, JsonNode>> fields = update.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode updateValue = field.getValue();
            JsonNode currentValue = target.get(fieldName);
            if (currentValue != null && currentValue.isObject() && updateValue.isObject()) {
                deepMerge((ObjectNode) currentValue, (ObjectNode) updateValue);
                continue;
            }
            target.set(fieldName, updateValue.deepCopy());
        }
    }

    private List<Map<String, Object>> readRequiredOperations(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        JsonNode node = objectMapper.valueToTree(value);
        if (!node.isArray() || node.size() == 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-empty array");
        }
        List<Map<String, Object>> operations = new ArrayList<Map<String, Object>>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("each operation must be an object");
            }
            operations.add(objectMapper.convertValue(item, Map.class));
        }
        return operations;
    }

    private String normalizePointer(String pointer) {
        String trimmed = pointer == null ? "" : pointer.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException("json pointer must start with '/' or be empty for root");
        }
        return trimmed;
    }

    private boolean isRootPointer(String pointer) {
        return pointer == null || pointer.isEmpty();
    }

    private List<String> parsePointer(String pointer) {
        if (isRootPointer(pointer)) {
            return new ArrayList<String>();
        }
        String[] rawTokens = pointer.substring(1).split("/", -1);
        List<String> tokens = new ArrayList<String>();
        for (String rawToken : rawTokens) {
            tokens.add(rawToken.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }

    private boolean isArrayToken(String token) {
        if ("-".equals(token)) {
            return true;
        }
        try {
            parseArrayIndex(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int parseArrayIndex(String token) {
        try {
            int index = Integer.parseInt(token);
            if (index < 0) {
                throw new IllegalArgumentException("array index must be non-negative");
            }
            return index;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid array index: " + token);
        }
    }

    private String requiredText(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return String.valueOf(value);
    }

    /**
     * 读取可选的 key 字段，不存在或为空时返回 null。
     */
    private String readKey(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String readPointer(Map<String, Object> source, String fieldName) {
        if (!source.containsKey(fieldName)) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        Object value = source.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean readBoolean(Map<String, Object> source, String fieldName, boolean defaultValue) {
        Object value = source.get(fieldName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Object> buildOperationPayload(String op, String pointer, String summary) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("op", op);
        payload.put("pointer", pointer);
        payload.put("summary", summary);
        return payload;
    }

    private Map<String, Object> buildReplacePayload(String pointer, String key, int replacedCount) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("op", "replace");
        payload.put("pointer", pointer);
        payload.put("key", key);
        payload.put("replacedCount", replacedCount);
        payload.put("summary", "已替换 " + replacedCount + " 处匹配的键值");
        return payload;
    }

    /**
     * 写入前校验 JSON 合法性，re-parse 失败时报告具体行列位置。
     */
    private void validateJson(String jsonContent) throws JsonProcessingException {
        try {
            objectMapper.readTree(jsonContent);
        } catch (JsonProcessingException exception) {
            com.fasterxml.jackson.core.JsonLocation location = exception.getLocation();
            String locationInfo = "";
            if (location != null) {
                locationInfo = " (line " + location.getLineNr()
                        + ", column " + location.getColumnNr() + ")";
            }
            throw new IllegalArgumentException(
                    "JSON 格式不合法" + locationInfo + "：" + exception.getOriginalMessage());
        }
    }

    /**
     * 校验字符串是否匹配支持的日期时间格式。
     * 仅当字符串前缀疑似日期（yyyy-MM）时触发。
     */
    private void validateDateTimeFormat(String value, String pointer) {
        if (value == null || !DATE_LIKE_PREFIX.matcher(value).find()) {
            return;
        }
        for (Pattern pattern : DATE_TIME_PATTERNS) {
            if (pattern.matcher(value).matches()) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "日期时间格式不合法 at " + pointer + "：" + value
                        + "，支持的格式：yyyy-MM、yyyy-MM-dd、yyyy-MM-dd HH、yyyy-MM-dd HH:mm、yyyy-MM-dd HH:mm:ss");
    }

    private String relativize(WorkspaceToolContext context, Path target) {
        if (context == null) {
            return target == null ? "." : target.toString();
        }
        return context.relativize(target);
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.standard();
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty("Target JSON file path relative to the current working directory."));
        properties.put("operations", operationsProperty());

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "operations"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Map<String, Object> operationsProperty() {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "array");
        property.put("description", "JSON edit operations. Supported op: set, replace, remove, merge, append, add. add is alias for append. replace without key = standard JSON Patch replace; with key = recursive key match replace.");

        Map<String, Object> itemSchema = new LinkedHashMap<String, Object>();
        itemSchema.put("type", "object");

        Map<String, Object> itemProperties = new LinkedHashMap<String, Object>();
        itemProperties.put("op", stringProperty("Operation: set, replace, remove, merge, append, add. add is alias for append. replace without key works like set; with key recursively replaces matching field names."));
        itemProperties.put("pointer", stringProperty("JSON Pointer path. For replace with key, this is the search scope (empty for root)."));
        itemProperties.put("key", stringProperty("Optional for replace: field name to recursively match and replace all occurrences."));

        Map<String, Object> valueProperty = new LinkedHashMap<String, Object>();
        valueProperty.put("description", "JSON value used by set, replace, merge, append and add operations.");
        itemProperties.put("value", valueProperty);

        Map<String, Object> createMissingProperty = new LinkedHashMap<String, Object>();
        createMissingProperty.put("type", "boolean");
        createMissingProperty.put("description", "Whether missing parent nodes can be created automatically. Defaults to true.");
        itemProperties.put("createMissing", createMissingProperty);

        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", Arrays.asList("op", "pointer"));
        itemSchema.put("additionalProperties", Boolean.FALSE);
        property.put("items", itemSchema);
        return property;
    }

    private String readRequiredString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return String.valueOf(value);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static final class PointerTarget {
        private final JsonNode node;
        private final String lastToken;

        private PointerTarget(JsonNode node, String lastToken) {
            this.node = node;
            this.lastToken = lastToken;
        }

        private JsonNode getNode() {
            return node;
        }

        private String getLastToken() {
            return lastToken;
        }
    }

    private static final class OperationResult {
        private final JsonNode root;
        private final Map<String, Object> payload;

        private OperationResult(JsonNode root, Map<String, Object> payload) {
            this.root = root;
            this.payload = payload;
        }

        private static OperationResult of(JsonNode root, Map<String, Object> payload) {
            return new OperationResult(root, payload);
        }

        private JsonNode getRoot() {
            return root;
        }

        private Map<String, Object> getPayload() {
            return payload;
        }
    }
}
