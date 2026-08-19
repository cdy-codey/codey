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

    /**
     * Demo 字典数据源：仅内置少量常用字典的可选值，用于演示“命中则校验、未命中则跳过”。
     * 真实场景应替换为字典接口 / 数据库查询结果。
     */
    private static final Map<String, Set<String>> DEMO_DICT_VALUES = buildDemoDictValues();

    private static Map<String, Set<String>> buildDemoDictValues() {
        Map<String, Set<String>> values = new LinkedHashMap<String, Set<String>>();
        values.put("yn", new LinkedHashSet<String>(Arrays.asList("1", "0")));
        values.put("project_attribute", new LinkedHashSet<String>(Arrays.asList("goods", "build", "service")));
        values.put("require_confirm_status", new LinkedHashSet<String>(Arrays.asList("confirm", "notConfirm")));
        values.put("business_doc_type", new LinkedHashSet<String>(Arrays.asList("year", "day")));
        return values;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("TargetValidateTool", "校验表单格式", "当编辑完表单后必须调用", buildParameters()
        );
    }

    @Override
    public ToolCapability capability() {
        // 校验是“写文件前”的关卡，需要顺序执行，便于未通过时暂停等待用户选择。
        return ToolCapability.readOnly();
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
            List<String> problems = validateBizRequireDeserialization(content);
            if (problems == null || problems.isEmpty()) {
                return ToolResult.ok("校验成功");
            }
            return buildUserChoiceResult(problems);
        } catch (Exception e) {
            e.printStackTrace();
            return ToolResult.fail(e.getMessage(), e.getMessage());
        }
    }

    private List<String> validateBizRequireDeserialization(String content) {
        try {
            BizRequire bizRequire = OBJECT_MAPPER.readValue(content, BizRequire.class);
            if (bizRequire == null) {
                return singleProblem("表单为空");
            }
            if (ObjectUtil.equal(bizRequire.getId(), "")) {
                return singleProblem("$.id禁止空字符串，没有值则使用null替代");
            }
            List<String> problems = new ArrayList<String>();
            Map<String, Set<String>> dictValueCache = new LinkedHashMap<String, Set<String>>();
            addProblems(problems, validateDictionaryFields(bizRequire, "$", dictValueCache));
            //如果标的不为空，需要检查标的列表信息
            if (bizRequire.getTargetList() != null && !bizRequire.getTargetList().isEmpty()) {
                for (int i = 0; i < bizRequire.getTargetList().size(); i++) {
                    BizRequireTarget target = bizRequire.getTargetList().get(i);
                    addProblems(problems, validateRequiredTargetFields(target, i));
                    if ((StrUtil.isNotBlank(target.getReferenceListStr()) && target.getReferenceList() == null) || (StrUtil.isBlank(target.getReferenceListStr()) && target.getReferenceList() != null)) {
                        addProblem(problems, "第" + (i + 1) + "行的标的" + target.getTargetName() + "的参考品牌字段referenceListStr和referenceList没有配套");
                    }
                    if ((StrUtil.isNotBlank(target.getBizTargetParamListStr()) && target.getTargetParamList() == null) || (StrUtil.isBlank(target.getBizTargetParamListStr()) && target.getTargetParamList() != null)) {
                        addProblem(problems, "第" + (i + 1) + "行的标的" + target.getTargetName() + "的标的参数字段targetParamList和bizTargetParamListStr没有配套");
                    }
                    if (ObjectUtil.equal(target.getId(), "")) {
                        addProblem(problems, "第" + (i + 1) + "行的标的id禁止空字符串，没有值则使用null替代");
                    }
                    addProblems(problems, validateDictionaryFields(target, "$.targetList[" + i + "]", dictValueCache));
                }
            }
            return problems;
        } catch (JsonMappingException e) {
            return singleProblem(buildBizRequireDeserializeErrorMessage(e));
        } catch (JsonProcessingException e) {
            return singleProblem(e.getMessage());
        }
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

    private List<String> validateRequiredTargetFields(BizRequireTarget target, int index) {
        List<String> problems = new ArrayList<String>();
        if (target == null) {
            addProblem(problems, "第" + (index + 1) + "行的标的不能为空");
            return problems;
        }
        String targetLabel = buildTargetLabel(target, index);
        addRequiredTargetFieldProblem(problems, target.getTargetName(), targetLabel, "标的名称", "targetName");
        addRequiredTargetFieldProblem(problems, target.getTargetTypeId(), targetLabel, "标的类型id", "targetTypeId");
        addRequiredTargetFieldProblem(problems, target.getTargetTypeName(), targetLabel, "标的类型名称", "targetTypeName");
        addRequiredTargetFieldProblem(problems, target.getRequireCatalog(), targetLabel, "采购目录", "requireCatalog");
        return problems;
    }

    private void addRequiredTargetFieldProblem(List<String> problems, String fieldValue, String targetLabel, String fieldLabel, String fieldName) {
        if (StrUtil.isBlank(fieldValue)) {
            addProblem(problems, targetLabel + "的" + fieldLabel + "不能为空(" + fieldName + ")");
        }
    }

    private String buildTargetLabel(BizRequireTarget target, int index) {
        String targetName = target == null ? "" : target.getTargetName();
        if (StrUtil.isNotBlank(targetName)) {
            return "第" + (index + 1) + "行的标的" + targetName;
        }
        return "第" + (index + 1) + "行的标的";
    }

    private List<String> validateDictionaryFields(Object data, String jsonPath, Map<String, Set<String>> dictValueCache) {
        List<String> problems = new ArrayList<String>();
        if (data == null) {
            return problems;
        }
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
                addProblem(problems, validateDictionaryFieldValue(field, dict, fieldValue, jsonPath, dictValueCache));
            }
        }
        return problems;
    }

    private String validateDictionaryFieldValue(Field field, Dict dict, Object fieldValue, String jsonPath, Map<String, Set<String>> dictValueCache) {
        List<String> values = extractDictionaryValues(fieldValue);
        if (values.isEmpty()) {
            return null;
        }
        String dictExpression = buildDictExpression(dict);
        Set<String> dictValueSet = loadDictionaryValueSet(dict, dictValueCache);
        // 字典数据未返回时不再静默跳过，而是作为“需人工确认”的问题暴露给用户选择处理方式。
        if (dictValueSet.isEmpty()) {
            return buildFieldPath(jsonPath, field.getName()) + " 依赖字典 " + dictExpression + "，但字典数据未返回，需人工确认该字段取值";
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
        // 从 demo 字典数据源查询可用值；未配置的字典返回空集合，表示本次不参与校验。
        Set<String> valueSet = lookupDemoDictionaryValues(dict);
        dictValueCache.put(dictExpression, valueSet);
        return valueSet;
    }

    private Set<String> lookupDemoDictionaryValues(Dict dict) {
        if (dict == null) {
            return Collections.emptySet();
        }
        String dicCode = dict.dicCode() == null ? "" : dict.dicCode().trim();
        Set<String> values = DEMO_DICT_VALUES.get(dicCode);
        return values == null ? Collections.emptySet() : values;
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

    private void addProblem(List<String> problems, String message) {
        if (StrUtil.isNotBlank(message)) {
            problems.add(message);
        }
    }

    private void addProblems(List<String> problems, List<String> more) {
        if (more != null) {
            problems.addAll(more);
        }
    }

    private List<String> singleProblem(String message) {
        List<String> problems = new ArrayList<String>();
        addProblem(problems, message);
        return problems;
    }

    /**
     * 把校验问题转换为 user_choice 视图，等待用户选择后再继续，而不是让模型直接改写文件。
     */
    private ToolResult buildUserChoiceResult(List<String> problems) {
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < problems.size(); i++) {
            Map<String, Object> option = new LinkedHashMap<String, Object>();
            option.put("key", optionKey(i));
            option.put("label", problems.get(i));
            options.add(option);
        }
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("_view_type", "user_choice");
        view.put("title", "表单校验未通过");
        view.put("description", "以下问题需要处理，请选择一项以继续：");
        view.put("options", options);

        String contentForModel = buildProblemsText(problems) + "\n请等待用户选择后再继续，不要直接修改文件。";
        return ToolResult.userChoice(view, contentForModel);
    }

    private String optionKey(int index) {
        if (index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        return "OPT" + (index + 1);
    }

    private String buildProblemsText(List<String> problems) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < problems.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(optionKey(i)).append(". ").append(problems.get(i));
        }
        return sb.toString();
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

