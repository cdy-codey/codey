package com.codey.web.form;

import com.codey.client.FormContext;
import com.codey.web.tool.TargetFormFieldQueryTool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 独立构建采购申请表单的表单模式上下文。
 * 复用 {@link TargetFormFieldQueryTool#getFormMetadata()} 反射得到的表单元数据，
 * 将元数据转换为底层表单模式所需的结构化 {@link FormContext}，不侵入原工具类。
 */
@Component
public class ProcurementFormContextBuilder {

    @Resource
    private TargetFormFieldQueryTool targetFormFieldQueryTool;

    /**
     * 构建采购申请表单的表单模式上下文，供 {@link ProcurementFormProvider} 注入。
     */
    @SuppressWarnings("unchecked")
    public FormContext build() {
        Map<String, Object> metadata = targetFormFieldQueryTool.getFormMetadata();
        FormContext formContext = new FormContext();
        formContext.setRole(buildRole());
        formContext.setContext(buildContextText(metadata));
        formContext.setFields(toFormFields((List<Map<String, Object>>) metadata.get("fields")));
        return formContext;
    }

    /**
     * 表单模式下的角色扮演描述，由业务侧定义，随表单上下文注入。
     */
    private String buildRole() {
        return "你是政府采购申请表单填写助手。帮助用户填写采购申请表单的基础信息、采购明细信息、商务条款信息，"
                + "确保最终提交的申请完整、合理、符合政府采购法。";
    }

    /**
     * 把表单元数据中的历史参考工作流、推荐工具、特殊字段规则拼接为自然语言业务背景。
     */
    @SuppressWarnings("unchecked")
    private String buildContextText(Map<String, Object> metadata) {
        StringBuilder md = new StringBuilder();
       
        return md.toString();
    }



    /**
     * 把反射得到的字段元数据列表转换为结构化 FormField 列表，递归展开嵌套子结构。
     */
    @SuppressWarnings("unchecked")
    private List<FormContext.FormField> toFormFields(List<Map<String, Object>> fieldMetadatas) {
        List<FormContext.FormField> result = new ArrayList<FormContext.FormField>();
        if (fieldMetadatas == null) {
            return result;
        }
        for (Map<String, Object> fieldMetadata : fieldMetadatas) {
            result.add(toFormField(fieldMetadata));
        }
        return result;
    }

    /**
     * 转换单个字段元数据为 FormField。
     */
    @SuppressWarnings("unchecked")
    private FormContext.FormField toFormField(Map<String, Object> fieldMetadata) {
        FormContext.FormField formField = new FormContext.FormField();
        formField.setName((String) fieldMetadata.get("fieldName"));
        formField.setType(mapFormFieldType((String) fieldMetadata.get("fieldType")));
        // 透传反射阶段读取到的 @FormIgnore 标记，交由 core 底层统一过滤
        formField.setIgnored(Boolean.TRUE.equals(fieldMetadata.get("ignored")));
        Map<String, Object> apiProp = (Map<String, Object>) fieldMetadata.get("apiModelProperty");
        if (apiProp != null) {
            formField.setLabel((String) apiProp.get("value"));
            formField.setRequired(Boolean.TRUE.equals(apiProp.get("required")));
            formField.setDescription(trimToEmpty(apiProp.get("notes")));
        }
        formField.setOptions(toFormOptions(fieldMetadata));
        formField.setRules(toFormFieldRules(fieldMetadata));
        Map<String, Object> nestedInfo = (Map<String, Object>) fieldMetadata.get("nestedFieldInfo");
        if (nestedInfo != null && nestedInfo.get("itemFields") != null) {
            formField.setChildren(toFormFields((List<Map<String, Object>>) nestedInfo.get("itemFields")));
        }
        return formField;
    }

    /**
     * 把工具内部字段类型归一化为 JSON Schema 合法类型。
     */
    private String mapFormFieldType(String fieldType) {
        if (fieldType == null) {
            return "string";
        }
        switch (fieldType) {
            case "array":
                return "array";
            case "boolean":
                return "boolean";
            case "int":
            case "number":
                return "number";
            case "time":
                return "string";
            case "obj":
                return "object";
            case "string":
            default:
                return "string";
        }
    }

    /**
     * 转换字典项为枚举选项；当前元数据未加载字典值时返回空列表。
     */
    @SuppressWarnings("unchecked")
    private List<FormContext.FormOption> toFormOptions(Map<String, Object> fieldMetadata) {
        List<FormContext.FormOption> result = new ArrayList<FormContext.FormOption>();
        Map<String, Object> dict = (Map<String, Object>) fieldMetadata.get("dict");
        if (dict == null || !Boolean.TRUE.equals(dict.get("isDictionaryField"))) {
            return result;
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) dict.get("items");
        if (items == null) {
            return result;
        }
        for (Map<String, Object> item : items) {
            FormContext.FormOption option = new FormContext.FormOption();
            option.setValue((String) item.get("value"));
            option.setLabel((String) item.get("text"));
            result.add(option);
        }
        return result;
    }

    /**
     * 提取字段特殊规则：联动/同步维护规则 + 数组结构约束。
     */
    @SuppressWarnings("unchecked")
    private List<String> toFormFieldRules(Map<String, Object> fieldMetadata) {
        List<String> rules = new ArrayList<String>();
        Map<String, Object> specialRule = (Map<String, Object>) fieldMetadata.get("specialRule");
        if (specialRule != null) {
            String assignmentRule = trimToEmpty(specialRule.get("assignmentRule"));
            if (!assignmentRule.isEmpty()) {
                rules.add(assignmentRule);
            }
        }
        Map<String, Object> nestedInfo = (Map<String, Object>) fieldMetadata.get("nestedFieldInfo");
        if (nestedInfo != null) {
            String arrayRule = trimToEmpty(nestedInfo.get("arrayRule"));
            if (!arrayRule.isEmpty()) {
                rules.add(arrayRule);
            }
        }
        return rules;
    }

    /**
     * 将可能为 null 的对象安全转换为去首尾空白的字符串，null 时返回空字符串。
     */
    private String trimToEmpty(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
