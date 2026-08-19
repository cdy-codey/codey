package com.codey.web.tool;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.codey.meta.IdentityMatchMode;
import com.codey.tool.*;
import com.codey.web.common.Dict;
import com.codey.web.entity.BizRequire;
import com.codey.web.entity.BizRequireTarget;
import com.codey.workspace.WorkspaceDirectoryService;
import com.codey.workspace.WorkspaceFile;
import com.codey.workspace.WorkspaceSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@Component
public class TargetValidateTool extends AbstractTool {

    @Resource
    private WorkspaceDirectoryService delegate;


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("TargetValidateTool", "校验表单格式", "当编辑完表单后必须调用", buildParameters()
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
        metadata.setGroup("programming");
        metadata.setBundle("procurement");
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            String path = requireString(invocation, "path");
            String working = context.getWorkingDirectory().replace("./", "");
            //如果path不包含 working，补上
            if (!path.contains(working)) {
                path = working + "/" + path;
            }
            WorkspaceSnapshot snapshot = delegate.query(path);
            WorkspaceFile currentFile = snapshot.getCurrentFile();
            if (currentFile == null) {
                return ToolResult.fail("校验表单格式失败", "表单内容不存在");
            }
            String content = currentFile.getContent();
            if (content == null || content.trim().isEmpty()) {
                return ToolResult.fail("校验表单格式失败", "表单内容为空");
            }
            OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String err = validateBizRequireDeserialization(content);
            if (err != null) {
                return ToolResult.fail(err, err);
            }
            return ToolResult.ok("校验成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ToolResult.fail(e.getMessage(), e.getMessage());
        }
    }

    private String validateBizRequireDeserialization(String content) throws JsonProcessingException {
        try {
            BizRequire bizRequire = OBJECT_MAPPER.readValue(content, BizRequire.class);
            if (bizRequire == null) {
                return "表单为空";
            }
            if (ObjectUtil.equal(bizRequire.getId(), "")) {
                return "$.id禁止空字符串，没有值则使用null替代";
            }
            Map<String, Set<String>> dictValueCache = new LinkedHashMap<String, Set<String>>();
            StringBuilder err = new StringBuilder();
            // AI修改：补充字典字段反射校验，确保 @Dict 字段的值必须能在字典接口返回结果中找到 - 2026-07-28
            appendValidationError(err, validateDictionaryFields(bizRequire, "$", dictValueCache));
            //如果标的不为空，需要检查标的列表信息
            if (bizRequire.getTargetList() != null && !bizRequire.getTargetList().isEmpty()) {
                for (int i = 0; i < bizRequire.getTargetList().size(); i++) {
                    BizRequireTarget target = bizRequire.getTargetList().get(i);
                    // AI修改：补充标的重要字段必填校验，避免标的名称、类型、采购目录等关键信息缺失 - 2026-07-28
                    appendValidationError(err, validateRequiredTargetFields(target, i));
                    if ((StrUtil.isNotBlank(target.getReferenceListStr()) && target.getReferenceList() == null) || (StrUtil.isBlank(target.getReferenceListStr()) && target.getReferenceList() != null)) {
                        //有字符串，也要有列表
                        appendValidationError(err, "第" + (i + 1) + "行的标的" + target.getTargetName() + "的参考品牌字段referenceListStr和referenceList没有配套");

                    }
                    if ((StrUtil.isNotBlank(target.getBizTargetParamListStr()) && target.getTargetParamList() == null) || (StrUtil.isBlank(target.getBizTargetParamListStr()) && target.getTargetParamList() != null)) {
                        //有字符串
                        appendValidationError(err, "第" + (i + 1) + "行的标的" + target.getTargetName() + "的标的参数字段targetParamList和bizTargetParamListStr没有配套");
                    }
                    if (ObjectUtil.equal(target.getId(), "")) {
                        return "第" + (i + 1) + "行的标的id禁止空字符串，没有值则使用null替代";
                    }
                    appendValidationError(err, validateDictionaryFields(target, "$.targetList[" + i + "]", dictValueCache));
                }
            }
            if (err.length() > 0) {
                err.append("\n请查询字段规则字段信息后修改");
                return err.toString();
            }

        } catch (JsonMappingException e) {
            return buildBizRequireDeserializeErrorMessage(e);
        } catch (JsonProcessingException e) {
            return e.getMessage();
        }

        return null;
    }

    private String buildBizRequireDeserializeErrorMessage(JsonMappingException e) {
        StringBuilder message = new StringBuilder("表单无法反序列化对象");
        String path = buildJsonPath(e);
        if (path != null && !path.isEmpty()) {
            message.append("，问题路径：").append(path);
        }
        String originalMessage = e.getOriginalMessage();
        if (originalMessage != null && !originalMessage.trim().isEmpty()) {
            message.append("，原因：").append(originalMessage.trim());
        }
        return message.toString();
    }

    private String buildJsonPath(JsonMappingException e) {
        if (e.getPath() == null || e.getPath().isEmpty()) {
            return "$";
        }
        StringBuilder pathBuilder = new StringBuilder("$");
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                pathBuilder.append(".").append(reference.getFieldName());
                continue;
            }
            if (reference.getIndex() >= 0) {
                pathBuilder.append("[").append(reference.getIndex()).append("]");
            }
        }
        return pathBuilder.toString();
    }

    private String validateRequiredTargetFields(BizRequireTarget target, int index) {
        StringBuilder err = new StringBuilder();
        String targetLabel = buildTargetLabel(target, index);
        if (target == null) {
            appendValidationError(err, "第" + (index + 1) + "行的标的不能为空");
            return err.toString();
        }
        appendRequiredTargetFieldError(err, target.getTargetName(), targetLabel, "标的名称", "targetName");
        appendRequiredTargetFieldError(err, target.getTargetTypeId(), targetLabel, "标的类型id", "targetTypeId");
        appendRequiredTargetFieldError(err, target.getTargetTypeName(), targetLabel, "标的类型名称", "targetTypeName");
        appendRequiredTargetFieldError(err, target.getRequireCatalog(), targetLabel, "采购目录", "requireCatalog");
        return err.length() == 0 ? null : err.toString();
    }

    private void appendRequiredTargetFieldError(StringBuilder err, String fieldValue, String targetLabel, String fieldLabel, String fieldName) {
        if (StrUtil.isBlank(fieldValue)) {
            appendValidationError(err, targetLabel + "的" + fieldLabel + "不能为空(" + fieldName + ")");
        }
    }

    private String buildTargetLabel(BizRequireTarget target, int index) {
        String targetName = target == null ? "" : target.getTargetName();
        if (StrUtil.isNotBlank(targetName)) {
            return "第" + (index + 1) + "行的标的" + targetName;
        }
        return "第" + (index + 1) + "行的标的";
    }

    private String validateDictionaryFields(Object data, String jsonPath, Map<String, Set<String>> dictValueCache) {
        if (data == null) {
            return null;
        }
        StringBuilder err = new StringBuilder();
        for (Class<?> currentClass = data.getClass(); currentClass != null && currentClass != Object.class; currentClass = currentClass.getSuperclass()) {
            Field[] declaredFields = currentClass.getDeclaredFields();
            for (Field field : declaredFields) {
                if (shouldIgnoreField(field)) {
                    continue;
                }
                Dict dict = field.getAnnotation(Dict.class);
                if (dict == null) {
                    continue;
                }
                Object fieldValue = readFieldValue(data, field);
                if (fieldValue == null || fieldValue.equals("")) continue;
                appendValidationError(err, validateDictionaryFieldValue(field, dict, fieldValue, jsonPath, dictValueCache));
            }
        }
        return err.length() == 0 ? null : err.toString();
    }

    private String validateDictionaryFieldValue(Field field, Dict dict, Object fieldValue, String jsonPath, Map<String, Set<String>> dictValueCache) {
        List<String> values = extractDictionaryValues(fieldValue);
        if (values.isEmpty()) {
            return null;
        }
        String dictExpression = buildDictExpression(dict);
        Set<String> dictValueSet = loadDictionaryValueSet(dict, dictValueCache);
        if (dictValueSet.isEmpty()) {
            return buildFieldPath(jsonPath, field.getName()) + " 对应的字典 " + dictExpression + " 未查询到可用值，无法完成校验";
        }
        List<String> invalidValues = new ArrayList<String>();
        for (String value : values) {
            if (!dictValueSet.contains(value)) {
                invalidValues.add(value);
            }
        }
        if (invalidValues.isEmpty()) {
            return null;
        }
        return buildFieldPath(jsonPath, field.getName()) + " 的值 " + invalidValues + " 不在字典 " + dictExpression + " 的可选值中";
    }

    private Set<String> loadDictionaryValueSet(Dict dict, Map<String, Set<String>> dictValueCache) {
        String dictExpression = buildDictExpression(dict);
        Set<String> cachedValueSet = dictValueCache.get(dictExpression);
        if (cachedValueSet != null) {
            return cachedValueSet;
        }
        Set<String> valueSet = new LinkedHashSet<String>();
        dictValueCache.put(dictExpression, valueSet);
        return valueSet;
    }

    private List<String> extractDictionaryValues(Object fieldValue) {
        List<String> values = new ArrayList<String>();
        if (fieldValue == null) {
            return values;
        }
        if (fieldValue instanceof Collection) {
            Collection<?> collection = (Collection<?>) fieldValue;
            for (Object item : collection) {
                addDictionaryValue(values, item);
            }
            return values;
        }
        if (fieldValue.getClass().isArray()) {
            int length = Array.getLength(fieldValue);
            for (int i = 0; i < length; i++) {
                addDictionaryValue(values, Array.get(fieldValue, i));
            }
            return values;
        }
        addDictionaryValue(values, fieldValue);
        return values;
    }

    private void addDictionaryValue(List<String> values, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.contains(",") || text.contains("，")) {
            String[] parts = text.split("[,，]");
            for (String part : parts) {
                String item = part == null ? "" : part.trim();
                if (!item.isEmpty()) {
                    values.add(item);
                }
            }
            return;
        }
        values.add(text);
    }

    private Object readFieldValue(Object data, Field field) {
        try {
            field.setAccessible(true);
            return field.get(data);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("读取字段失败：" + field.getName(), e);
        }
    }

    private boolean shouldIgnoreField(Field field) {
        int modifiers = field.getModifiers();
        return field.isSynthetic()
                || Modifier.isStatic(modifiers)
                || "serialVersionUID".equals(field.getName());
    }

    private String buildFieldPath(String jsonPath, String fieldName) {
        if (StrUtil.isBlank(jsonPath) || "$".equals(jsonPath)) {
            return "$." + fieldName;
        }
        return jsonPath + "." + fieldName;
    }

    private String buildDictExpression(Dict dict) {
        if (dict == null) {
            return "";
        }
        if (StrUtil.isNotBlank(dict.dicTable()) && StrUtil.isNotBlank(dict.dicCode()) && StrUtil.isNotBlank(dict.dicColumn())) {
            return dict.dicTable().trim() + "," + dict.dicCode().trim() + "," + dict.dicColumn().trim();
        }
        return dict.dicCode().trim();
    }

    private void appendValidationError(StringBuilder err, String message) {
        if (StrUtil.isBlank(message)) {
            return;
        }
        if (err.length() > 0) {
            err.append("\n");
        }
        err.append(message);
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty(
                "待校验的采购明细文件。"
        ));
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}

