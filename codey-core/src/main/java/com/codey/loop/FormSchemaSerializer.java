package com.codey.loop;

import com.codey.client.FormContext;
import com.codey.client.FormVisibleField;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把表单字段定义序列化为 JSON Schema。
 * 表单模式下由本类确定性地生成目标 JSON 结构，模型只负责按 Schema 填值，
 * 不负责推导 key 命名、字段类型与枚举取值，从而消除结构层面的歧义。
 */
final class FormSchemaSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 FormContext 序列化为 JSON Schema 字符串，作为表单模式输出结构约束注入 prompt。
     */
    String serialize(FormContext formContext) {
        Set<String> visibleFields = collectVisibleFields(formContext);
        Map<String, String> visibleLabels = collectVisibleFieldLabels(formContext);
        Map<String, Object> schema = buildObjectSchema(formContext == null ? null : formContext.getFields(), visibleFields, visibleLabels, "");
        // 顶层禁止模型新增 Schema 之外的字段，保证输出 key 与字段定义严格一致
        schema.put("additionalProperties", Boolean.FALSE);

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception exception) {
            // 序列化异常时退回空对象 Schema，避免阻断 prompt 装配
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    /**
     * 构建对象类型 Schema：{type=object, properties, required?}。
     * visibleFields 非空时按点分路径（如 targetList.targetName）过滤各层级字段：
     * 顶层字段与递归子字段都遵循同一白名单，避免只过滤顶层导致嵌套结构塞满噪音字段。
     */
    private Map<String, Object> buildObjectSchema(List<FormContext.FormField> fields, Set<String> visibleFields, Map<String, String> visibleLabels, String pathPrefix) {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        if (fields != null) {
            for (FormContext.FormField field : fields) {
                if (field == null || isBlank(field.getName())) {
                    continue;
                }
                // 被 @FormIgnore 标记的字段直接排除，与下方可见字段过滤保持同一入口
                if (field.isIgnored()) {
                    continue;
                }
                String fullPath = buildFullPath(pathPrefix, field.getName().trim());
                // 提供可见字段列表时，仅保留列表内（或其子路径）的字段
                if (!isVisible(visibleFields, fullPath)) {
                    continue;
                }
                properties.put(field.getName(), buildProperty(field, visibleFields, visibleLabels, fullPath));
                if (field.isRequired()) {
                    required.add(field.getName());
                }
            }
        }
        Map<String, Object> objectSchema = new LinkedHashMap<String, Object>();
        objectSchema.put("type", "object");
        objectSchema.put("properties", properties);
        if (!required.isEmpty()) {
            objectSchema.put("required", required);
        }
        return objectSchema;
    }

    /**
     * 拼接字段完整路径：顶层字段直接返回字段名，嵌套字段用点号连接父路径与字段名。
     */
    private String buildFullPath(String pathPrefix, String fieldName) {
        return isBlank(pathPrefix) ? fieldName : pathPrefix + "." + fieldName;
    }

    /**
     * 判断字段是否可见：未配置白名单时全部可见；
     * 配置后，字段本身命中白名单、或作为某个可见子字段的祖先路径时可见（用于展开父级结构）。
     */
    private boolean isVisible(Set<String> visibleFields, String fullPath) {
        if (visibleFields == null) {
            return true;
        }
        if (visibleFields.contains(fullPath)) {
            return true;
        }
        String prefix = fullPath + ".";
        for (String visible : visibleFields) {
            if (visible.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断某字段是否存在更深层的白名单子路径（如白名单含 targetList.targetName 时，targetList 返回 true）。
     * 用于决定嵌套子字段是继续按白名单过滤，还是整对象全部保留。
     */
    private boolean hasChildVisibleFields(Set<String> visibleFields, String pathPrefix) {
        if (visibleFields == null) {
            return false;
        }
        String prefix = pathPrefix + ".";
        for (String visible : visibleFields) {
            if (visible.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集可见字段名称；未提供可见字段列表时返回 null，表示不过滤、保留全部字段。
     */
    private Set<String> collectVisibleFields(FormContext formContext) {
        if (formContext == null || formContext.getVisibleFields() == null || formContext.getVisibleFields().isEmpty()) {
            return null;
        }
        Set<String> visible = new LinkedHashSet<String>();
        for (FormVisibleField field : formContext.getVisibleFields()) {
            if (field != null && !isBlank(field.getField())) {
                visible.add(field.getField().trim());
            }
        }
        return visible;
    }

    /**
     * 收集可见字段的中文名映射（完整字段路径 -> 界面中文名）。
     * 前端对象化可见字段时提供 label，用于覆盖 Schema 中实体注解的字段中文描述。
     */
    private Map<String, String> collectVisibleFieldLabels(FormContext formContext) {
        if (formContext == null || formContext.getVisibleFields() == null || formContext.getVisibleFields().isEmpty()) {
            return null;
        }
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (FormVisibleField field : formContext.getVisibleFields()) {
            if (field != null && !isBlank(field.getField())) {
                labels.put(field.getField().trim(), field.getLabel());
            }
        }
        return labels;
    }

    /**
     * 返回表单级填写上下文的自然语言描述；为空时返回空串，由调用方决定是否渲染上下文段落。
     */
    String serializeContext(FormContext formContext) {
        if (formContext == null || isBlank(formContext.getContext())) {
            return "";
        }
        return formContext.getContext().trim();
    }

    /**
     * 返回角色扮演描述文本；为空时返回空串，由调用方决定是否渲染角色段落。
     */
    String serializeRole(FormContext formContext) {
        if (formContext == null || isBlank(formContext.getRole())) {
            return "";
        }
        return formContext.getRole().trim();
    }

    /**
     * 构建单个字段的 JSON Schema 属性定义：类型 + 嵌套子结构 + 枚举 + 描述（含中文名、说明、特殊规则）。
     */
    private Map<String, Object> buildProperty(FormContext.FormField field, Set<String> visibleFields, Map<String, String> visibleLabels, String pathPrefix) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        String type = mapType(field.getType());
        property.put("type", type);

        // 携带子字段时展开嵌套结构：array 用 items 描述元素对象，object 用 properties 描述子字段
        if (hasChildren(field)) {
            // 白名单只写到当前字段（无更深层子路径）时，其子字段全部保留，不再递归过滤
            Set<String> childVisibleFields = hasChildVisibleFields(visibleFields, pathPrefix) ? visibleFields : null;
            if ("array".equals(type)) {
                property.put("items", buildObjectSchema(field.getChildren(), childVisibleFields, visibleLabels, pathPrefix));
            } else if ("object".equals(type)) {
                Map<String, Object> childSchema = buildObjectSchema(field.getChildren(), childVisibleFields, visibleLabels, pathPrefix);
                property.put("properties", childSchema.get("properties"));
                if (childSchema.containsKey("required")) {
                    property.put("required", childSchema.get("required"));
                }
            }
        }

        // 只要提供了可选值，就视为枚举约束，避免依赖调用方是否把 type 写成 enum
        if (hasOptions(field)) {
            List<String> enumValues = new ArrayList<String>();
            for (FormContext.FormOption option : field.getOptions()) {
                if (option != null && !isBlank(option.getValue())) {
                    enumValues.add(option.getValue());
                }
            }
            if (!enumValues.isEmpty()) {
                property.put("enum", enumValues);
            }
        }

        // 前端对象化可见字段传入的中文名优先于实体注解 label，保证 AI 看到的中文名与界面一致
        String visibleLabel = visibleLabels == null ? null : visibleLabels.get(pathPrefix);
        String description = buildDescription(field, visibleLabel);
        if (!isBlank(description)) {
            property.put("description", description);
        }
        return property;
    }

    /**
     * 字段类型映射：enum 归一到 string 并用 enum 约束，日期类归一到 string。
     */
    private String mapType(String type) {
        if (isBlank(type)) {
            return "string";
        }
        String normalized = type.trim().toLowerCase();
        if ("enum".equals(normalized) || "date".equals(normalized) || "datetime".equals(normalized)) {
            return "string";
        }
        return normalized;
    }

    private boolean hasOptions(FormContext.FormField field) {
        return field.getOptions() != null && !field.getOptions().isEmpty();
    }

    private boolean hasChildren(FormContext.FormField field) {
        return field.getChildren() != null && !field.getChildren().isEmpty();
    }

    /**
     * 把中文名、说明、枚举项说明与特殊规则合并为 Schema 描述，保证模型理解字段语义。
     */
    private String buildDescription(FormContext.FormField field, String visibleLabel) {
        List<String> parts = new ArrayList<String>();
        // 前端对象化可见字段传入的 label 优先，未提供时回退到实体注解 label
        String label = isBlank(visibleLabel) ? field.getLabel() : visibleLabel;
        if (!isBlank(label)) {
            parts.add(label.trim());
        }
        if (!isBlank(field.getDescription())) {
            parts.add(field.getDescription().trim());
        }
        // 枚举项的中文说明并入描述，避免模型只看到编码枚举值而无法理解含义
        if (hasOptions(field)) {
            parts.add("可选值：" + formatEnumLabels(field.getOptions()));
        }
        if (field.getRules() != null) {
            for (String rule : field.getRules()) {
                if (!isBlank(rule)) {
                    parts.add("规则：" + rule.trim());
                }
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join("；", parts);
    }

    /**
     * 枚举项渲染为「value(中文说明)」列表，用顿号连接。
     */
    private String formatEnumLabels(List<FormContext.FormOption> options) {
        List<String> items = new ArrayList<String>();
        for (FormContext.FormOption option : options) {
            if (option == null || isBlank(option.getValue())) {
                continue;
            }
            String value = option.getValue().trim();
            if (!isBlank(option.getLabel())) {
                items.add(value + "(" + option.getLabel().trim() + ")");
            } else {
                items.add(value);
            }
        }
        return String.join("、", items);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
