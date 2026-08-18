package com.codey.web.form;

import com.codey.client.FormContext;
import com.codey.web.tool.TargetFormFieldQueryTool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 独立构建采购申请表单的表单模式上下文。
 * 复用 {@link TargetFormFieldQueryTool#getFormMetadata()} 反射得到的表单元数据，
 * 将元数据转换为底层表单模式所需的结构化 {@link FormContext}，不侵入原工具类。
 */
@Component
public class ProcurementFormContextBuilder {

    /**
     * 表单模式下的可见字段白名单：仅序列化这些字段到 JSON Schema，过滤实体中的查询条件、系统字段、字典冗余 text 等噪音字段。
     * 支持点分路径约束嵌套子字段（如 targetList.targetName），与业务场景展示字段定义（BusinessScenarioService）保持一致，按需增删。
     */
    private static final List<String> VISIBLE_FIELDS = Arrays.asList(
            "requireTitle",                // 需求标题
            "requireAttribute",            // 需求属性
            "purchaseCategory",            // 采购类别
            "emergency",                   // 需求紧急度
            "purchaseContent",             // 采购内容描述
            "busService",                  // 商务服务要求
            "purchaseAmount",              // 申购金额（元）
            "targetList",                  // 采购标的明细
            "targetList.targetName",       // 标的名称
            "targetList.num",              // 数量
            "targetList.unitPrice",        // 单价
            "targetList.targetParamList",    // 标的参数规则
            "targetList.bizTargetParamListStr",       // 商务参数规则
            "targetList.referenceListStr", // 参考品牌
            "businessEntryList"           // 商务条目
    );

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
        formContext.setVisibleFields(VISIBLE_FIELDS);
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
        Map<String, Object> workflow = (Map<String, Object>) metadata.get("historyReferenceWorkflow");
        if (workflow != null) {
            md.append("## 历史参考工作流\n\n");
            appendWorkflow(md, workflow);
        }
        List<Map<String, Object>> tools = (List<Map<String, Object>>) metadata.get("recommendedTools");
        if (tools != null && !tools.isEmpty()) {
            md.append("## 推荐工具\n\n");
            appendTools(md, tools);
        }
        List<Map<String, Object>> rules = (List<Map<String, Object>>) metadata.get("specialFieldRules");
        if (rules != null && !rules.isEmpty()) {
            md.append("## 特殊字段规则\n\n");
            appendRules(md, rules);
        }
        return md.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendWorkflow(StringBuilder md, Map<String, Object> workflow) {
        md.append("- **规则名称**：").append(workflow.get("ruleName")).append("\n");
        md.append("- **触发时机**：").append(workflow.get("whenToTrigger")).append("\n");
        md.append("- **库查询规则**：").append(workflow.get("libraryQueryRule")).append("\n");
        md.append("- **资产配置判断规则**：").append(workflow.get("assetConfigJudgeRule")).append("\n");
        md.append("- **品牌查询规则**：").append(workflow.get("referenceQueryRule")).append("\n");
        md.append("- **参数查询规则**：").append(workflow.get("targetParamQueryRule")).append("\n");
        md.append("- **调用顺序**：\n");
        List<String> callSequence = (List<String>) workflow.get("callSequence");
        if (callSequence != null) {
            for (int i = 0; i < callSequence.size(); i++) {
                md.append("  ").append(i + 1).append(". ").append(callSequence.get(i)).append("\n");
            }
        }
        md.append("- **禁止行为**：\n");
        List<String> forbidden = (List<String>) workflow.get("forbiddenBehavior");
        if (forbidden != null) {
            for (String fb : forbidden) {
                md.append("  - ").append(fb).append("\n");
            }
        }
        md.append("\n");
    }

    private void appendTools(StringBuilder md, List<Map<String, Object>> tools) {
        for (Map<String, Object> tool : tools) {
            md.append("- **").append(tool.get("toolName")).append("**\n");
            md.append("  - 触发条件：").append(tool.get("trigger")).append("\n");
            md.append("  - 使用规则：").append(tool.get("usageRule")).append("\n");
        }
        md.append("\n");
    }

    private void appendRules(StringBuilder md, List<Map<String, Object>> rules) {
        for (Map<String, Object> rule : rules) {
            md.append("### ").append(rule.get("ruleName")).append("\n\n");
            md.append("- **涉及字段**：").append(rule.get("fieldNames")).append("\n");
            md.append("- **描述**：").append(rule.get("description")).append("\n");
            if (rule.get("assignmentRule") != null) {
                md.append("- **填写规则**：").append(rule.get("assignmentRule")).append("\n");
            }
            if (rule.get("queryTool") != null) {
                md.append("- **查询工具**：").append(rule.get("queryTool")).append("\n");
            }
            md.append("\n");
        }
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
