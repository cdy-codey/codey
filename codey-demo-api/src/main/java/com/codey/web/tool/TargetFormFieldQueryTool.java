package com.hcjy.modules.business.commom.codey.tool;

import com.codey.meta.IdentityMatchMode;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hcjy.common.aspect.annotation.Dict;
import com.hcjy.common.system.api.ISysBaseCloudAPI;
import com.hcjy.common.system.vo.DictModel;
import com.hcjy.modules.business.entity.BizRequire;
import com.hcjy.modules.business.entity.BizTargetParam;
import com.hcjy.modules.business.commom.codey.CodeyEnum;
import com.hcjy.modules.business.entity.BizRequireTarget;
import com.hcjy.modules.business.entity.device.BizTargetReference;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.stereotype.Component;
import org.springframework.format.annotation.DateTimeFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

import javax.annotation.Resource;

/**
 * 采购明细表单字段查询工具。
 */
@Component
public class TargetFormFieldQueryTool extends AbstractTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Class<?> TARGET_FORM_CLASS = BizRequireTarget.class;
    private static final Class<?> BIZ_REQUIRE_CLASS = BizRequire.class;
    private static final String DEFAULT_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    // AI修改：缓存提示词结果，避免同一进程内重复构建相同元数据 - 2026-07-24
    private volatile String cachedPrompt;
    // AI修改：缓存规则结果，避免同一进程内重复构建相同规则内容 - 2026-07-24
    private volatile String cachedRules;
    

    @Resource
    private ISysBaseCloudAPI sysBaseCloudAPI;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "TargetFormFieldQueryTool",
                "查询表单字段",
                "返回采购明细表单字段信息；如果字段是字典字段，会直接返回可选值列表，AI 只能从给定 value 中选择填写",
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
        metadata.setSupportedIdentities(Arrays.asList(CodeyEnum.target.name()));
        metadata.setIdentityMatchMode(IdentityMatchMode.ANY);
        metadata.setGroup(CodeyEnum.target.name());
        metadata.setBundle(CodeyEnum.target.name());
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            // AI修改：优先复用类内缓存，避免重复构建提示词 - 2026-07-24
            return ToolResult.ok(getPrompt());
        } catch (Exception e) {
            return ToolResult.fail("查询采购明细表单字段失败", e.getMessage());
        }
    }

    // AI修改：提供与工具查询结果完全一致的提示词内容，避免外部重复拼装和内容漂移 - 2026-07-24
    public String getPrompt() {
        String prompt = cachedPrompt;
        if (prompt != null) {
            return prompt;
        }
        try {
            synchronized (this) {
                if (cachedPrompt != null) {
                    return cachedPrompt;
                }
                // AI修改：切换为 markdown 格式输出，替代原 JSON 格式 - 2026-07-29
                cachedPrompt = buildPromptMarkdown();
                return cachedPrompt;
            }
        } catch (Exception e) {
            throw new RuntimeException("构建采购明细表单提示词失败", e);
        }
    }

    // AI修改：提供仅包含规则信息的返回接口，便于外部直接读取规则内容 - 2026-07-24
    public String getRules() {
        String rules = cachedRules;
        if (rules != null) {
            return rules;
        }
        try {
            synchronized (this) {
                if (cachedRules != null) {
                    return cachedRules;
                }
                cachedRules = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(buildRuleMetadata());
                return cachedRules;
            }
        } catch (Exception e) {
            throw new RuntimeException("构建采购明细表单规则失败", e);
        }
    }

    // AI修改：还原真实层级关系，顶层只返回 BizRequire 字段，标的明细(targetList)的子结构通过 nestedFieldInfo 展开 - 2026-07-29
    private Map<String, Object> buildFormMetadata() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        ApiModel apiModel = BIZ_REQUIRE_CLASS.getAnnotation(ApiModel.class);
        // AI修改：字典码从 BizRequire 和 BizRequireTarget 两个实体收集，确保嵌套结构的字典值也能展示 - 2026-07-29
        Map<String, Map<String, Object>> dictResultMap = buildDictResultMap(BIZ_REQUIRE_CLASS, TARGET_FORM_CLASS);    
        result.put("formDescription", apiModel == null ? "" : apiModel.description());    
        result.put("historyReferenceWorkflow", buildHistoryReferenceWorkflow());
        result.put("recommendedTools", buildRecommendedTools());
        result.put("specialFieldRules", buildSpecialFieldRules());
        // AI修改：顶层只返回 BizRequire 字段，标的明细(targetList)的字段通过 nestedFieldInfo 层级展开 - 2026-07-29
        result.put("fields", collectFieldMetadata(BIZ_REQUIRE_CLASS, dictResultMap));
        result.put("oneTimeResponseNote", "本次返回为一次性完整返回，fields 为 BizRequire 的全部字段；targetList 字段的 nestedFieldInfo 中包含了标的明细(BizRequireTarget)的字段，标的明细下又有参考品牌(referenceList→BizTargetReference)和标的参数(targetParamList→BizTargetParam)的子字段。每个字段都已标注是否字典字段、字典类型，以及是否时间字段和时间格式。");
        return result;
    }

    // AI新增：将表单元数据转换为 markdown 格式，替代原 JSON 格式，保持字段层级关系 - 2026-07-29
    @SuppressWarnings("unchecked")
    private String buildPromptMarkdown() {
        Map<String, Object> formMetadata = buildFormMetadata();
        StringBuilder md = new StringBuilder();

        // 表单名称
        md.append("# ").append(formMetadata.get("formDescription")).append("\n\n");

        // 字段列表表格
        List<Map<String, Object>> fields = (List<Map<String, Object>>) formMetadata.get("fields");
        md.append("## 字段列表\n\n");
        md.append("| 字段名 | 类型 | 说明 | 字典 | 时间格式 | 规则提示 |\n");
        md.append("|--------|------|------|------|----------|----------|\n");
        for (Map<String, Object> field : fields) {
            md.append(buildFieldMarkdownRow(field));
        }

        // 收集并输出字典值
        Map<String, List<Map<String, Object>>> dictMap = collectAllDictValues(fields);
        if (!dictMap.isEmpty()) {
            md.append("\n## 字典值\n\n");
            for (Map.Entry<String, List<Map<String, Object>>> entry : dictMap.entrySet()) {
                md.append("### ").append(entry.getKey()).append("\n\n");
                md.append("| value | text | 说明 |\n");
                md.append("|-------|------|------|\n");
                for (Map<String, Object> item : entry.getValue()) {
                    md.append("| ").append(item.get("value"))
                      .append(" | ").append(item.get("text"))
                      .append(" | ").append(item.get("description"))
                      .append(" |\n");
                }
                md.append("\n");
            }
        }

        // 嵌套子结构：标的明细 (targetList → BizRequireTarget) → 参考品牌 / 标的参数
        md.append("## 标的明细 子结构 (targetList → BizRequireTarget)\n\n");
        md.append("targetList 是 BizRequire 中最重要的数组字段，每项为一个标的明细对象。\n\n");
        // 从 targetList 字段的 nestedFieldInfo 中提取标的明细字段
        Map<String, Object> targetListField = findFieldByName(fields, "targetList");
        if (targetListField != null) {
            Map<String, Object> nestedInfo = (Map<String, Object>) targetListField.get("nestedFieldInfo");
            if (nestedInfo != null && nestedInfo.get("itemFields") != null) {
                List<Map<String, Object>> targetFields = (List<Map<String, Object>>) nestedInfo.get("itemFields");
                md.append("### 标的明细字段\n\n");
                md.append("| 字段名 | 类型 | 说明 | 字典 |\n");
                md.append("|--------|------|------|------|\n");
                for (Map<String, Object> tf : targetFields) {
                    String fieldName = (String) tf.get("fieldName");
                    String fieldType = (String) tf.get("fieldType");
                    Map<String, Object> apiProp = (Map<String, Object>) tf.get("apiModelProperty");
                    String desc = apiProp != null ? (String) apiProp.get("value") : "";

                    // 检查是否有字典值
                    String dictInfo = "-";
                    if (dictMap.containsKey(fieldName)) {
                        List<Map<String, Object>> dictItems = dictMap.get(fieldName);
                        List<String> values = new ArrayList<>();
                        for (Map<String, Object> di : dictItems) {
                            values.add(di.get("value") + ":" + di.get("text"));
                        }
                        dictInfo = String.join(", ", values);
                    }
                    md.append("| ").append(fieldName)
                      .append(" | ").append(fieldType)
                      .append(" | ").append(desc)
                      .append(" | ").append(dictInfo)
                      .append(" |\n");
                }
            }
        }

        // 参考品牌子结构
        md.append("\n### 参考品牌 子结构 (referenceList → BizTargetReference)\n\n");
        Map<String, Object> referenceNestedInfo = buildNestedFieldInfoForClass(BizTargetReference.class);
        List<Map<String, Object>> refFields = (List<Map<String, Object>>) referenceNestedInfo.get("itemFields");
        md.append("| 字段名 | 类型 | 说明 |\n");
        md.append("|--------|------|------|\n");
        for (Map<String, Object> rf : refFields) {
            md.append(buildNestedFieldMarkdownRow(rf));
        }
        md.append("\n> referenceListStr 格式：品牌名称：规格型号：生产厂家（多行用换行分隔）\n\n");

        // 标的参数子结构
        md.append("### 标的参数 子结构 (targetParamList → BizTargetParam)\n\n");
        Map<String, Object> paramNestedInfo = buildNestedFieldInfoForClass(BizTargetParam.class);
        List<Map<String, Object>> paramFields = (List<Map<String, Object>>) paramNestedInfo.get("itemFields");
        md.append("| 字段名 | 类型 | 说明 |\n");
        md.append("|--------|------|------|\n");
        for (Map<String, Object> pf : paramFields) {
            md.append(buildNestedFieldMarkdownRow(pf));
        }
        md.append("\n> bizTargetParamListStr 格式：参数名称：参数要求（核心参数前加★）\n");
        md.append("> bizTargetResultParamsListStr 格式：参数名称：结论参数（核心参数前加★）\n\n");

        // 历史参考工作流
        md.append("## 历史参考工作流\n\n");
        appendWorkflowMarkdown(md, (Map<String, Object>) formMetadata.get("historyReferenceWorkflow"));

        // 推荐工具
        md.append("## 推荐工具\n\n");
        appendToolsMarkdown(md, (List<Map<String, Object>>) formMetadata.get("recommendedTools"));

        // 特殊字段规则
        md.append("## 特殊字段规则\n\n");
        appendRulesMarkdown(md, (List<Map<String, Object>>) formMetadata.get("specialFieldRules"));

        // 一次性返回提示
        md.append("---\n\n> ").append(formMetadata.get("oneTimeResponseNote")).append("\n");

        return md.toString();
    }

    // AI新增：构建单个字段的 markdown 表格行 - 2026-07-29
    @SuppressWarnings("unchecked")
    private String buildFieldMarkdownRow(Map<String, Object> field) {
        String fieldName = (String) field.get("fieldName");
        String fieldType = (String) field.get("fieldType");
        Map<String, Object> apiProp = (Map<String, Object>) field.get("apiModelProperty");
        String desc = apiProp != null ? (String) apiProp.get("value") : "";

        // 字典信息
        String dictInfo = "-";
        Map<String, Object> dict = (Map<String, Object>) field.get("dict");
        if (dict != null && Boolean.TRUE.equals(dict.get("isDictionaryField"))) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) dict.get("items");
            if (items != null && !items.isEmpty()) {
                List<String> valueTexts = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    valueTexts.add(item.get("value") + ":" + item.get("text"));
                }
                dictInfo = String.join(", ", valueTexts);
            } else {
                dictInfo = "（字典值未加载）";
            }
        }

        // 时间格式
        String timeFormat = "-";
        Map<String, Object> time = (Map<String, Object>) field.get("time");
        if (time != null && Boolean.TRUE.equals(time.get("isTimeField"))) {
            timeFormat = (String) time.get("format");
        }

        // AI 提示规则
        String hint = (String) field.get("aiHint");

        return "| " + fieldName
             + " | " + fieldType
             + " | " + desc
             + " | " + dictInfo
             + " | " + timeFormat
             + " | " + hint
             + " |\n";
    }

    // AI新增：构建嵌套子结构字段的 markdown 表格行 - 2026-07-29
    @SuppressWarnings("unchecked")
    private String buildNestedFieldMarkdownRow(Map<String, Object> field) {
        String fieldName = (String) field.get("fieldName");
        String fieldType = (String) field.get("fieldType");
        Map<String, Object> apiProp = (Map<String, Object>) field.get("apiModelProperty");
        String desc = apiProp != null ? (String) apiProp.get("value") : "";
        return "| " + fieldName + " | " + fieldType + " | " + desc + " |\n";
    }

    // AI新增：从字段列表中按名称查找字段 - 2026-07-29
    @SuppressWarnings("unchecked")
    private Map<String, Object> findFieldByName(List<Map<String, Object>> fields, String name) {
        for (Map<String, Object> field : fields) {
            if (name.equals(field.get("fieldName"))) {
                return field;
            }
        }
        return null;
    }

    // AI新增：为指定类构建嵌套字段信息（复用 collectNestedFieldMetadata 逻辑） - 2026-07-29
    private Map<String, Object> buildNestedFieldInfoForClass(Class<?> clazz) {
        Map<String, Object> nestedInfo = new LinkedHashMap<>();
        nestedInfo.put("itemClassName", clazz.getSimpleName());
        nestedInfo.put("itemFields", collectNestedFieldMetadata(clazz));
        return nestedInfo;
    }

    // AI新增：收集字段列表中所有字典字段的值，按字典码分组 - 2026-07-29
    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> collectAllDictValues(List<Map<String, Object>> fields) {
        Map<String, List<Map<String, Object>>> dictMap = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            Map<String, Object> dict = (Map<String, Object>) field.get("dict");
            if (dict != null && Boolean.TRUE.equals(dict.get("isDictionaryField"))) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) dict.get("items");
                if (items != null && !items.isEmpty()) {
                    String dictCode = "";
                    // 从 dict 或 specialRule 中提取字典码
                    Map<String, Object> specialRule = (Map<String, Object>) field.get("specialRule");
                    if (specialRule != null && specialRule.get("dictCode") != null) {
                        dictCode = (String) specialRule.get("dictCode");
                    }
                    if (dictCode.isEmpty()) {
                        dictCode = (String) field.get("fieldName");
                    }
                    dictMap.put(dictCode, items);
                }
            }
        }
        return dictMap;
    }

    // AI新增：将工作流信息输出为 markdown - 2026-07-29
    @SuppressWarnings("unchecked")
    private void appendWorkflowMarkdown(StringBuilder md, Map<String, Object> workflow) {
        md.append("- **规则名称**：").append(workflow.get("ruleName")).append("\n");
        md.append("- **触发时机**：").append(workflow.get("whenToTrigger")).append("\n");
        md.append("- **编辑前必须查询**：").append(workflow.get("mandatoryBeforeEdit")).append("\n");
        md.append("- **库查询规则**：").append(workflow.get("libraryQueryRule")).append("\n");
        md.append("- **资产配置判断规则**：").append(workflow.get("assetConfigJudgeRule")).append("\n");
        md.append("- **品牌查询规则**：").append(workflow.get("referenceQueryRule")).append("\n");
        md.append("- **参数查询规则**：").append(workflow.get("targetParamQueryRule")).append("\n");
        md.append("- **调用顺序**：\n");
        List<String> callSequence = (List<String>) workflow.get("callSequence");
        for (int i = 0; i < callSequence.size(); i++) {
            md.append("  ").append(i + 1).append(". ").append(callSequence.get(i)).append("\n");
        }
        md.append("- **禁止行为**：\n");
        List<String> forbidden = (List<String>) workflow.get("forbiddenBehavior");
        for (String fb : forbidden) {
            md.append("  - ").append(fb).append("\n");
        }
        md.append("\n");
    }

    // AI新增：将推荐工具列表输出为 markdown - 2026-07-29
    @SuppressWarnings("unchecked")
    private void appendToolsMarkdown(StringBuilder md, List<Map<String, Object>> tools) {
        for (Map<String, Object> tool : tools) {
            md.append("- **").append(tool.get("toolName")).append("**\n");
            md.append("  - 触发条件：").append(tool.get("trigger")).append("\n");
            md.append("  - 使用规则：").append(tool.get("usageRule")).append("\n");
        }
        md.append("\n");
    }

    // AI新增：将特殊字段规则输出为 markdown - 2026-07-29
    @SuppressWarnings("unchecked")
    private void appendRulesMarkdown(StringBuilder md, List<Map<String, Object>> rules) {
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
            if (rule.get("queryTip") != null) {
                md.append("- **查询提示**：").append(rule.get("queryTip")).append("\n");
            }
            if (rule.get("dictCode") != null) {
                md.append("- **字典码**：").append(rule.get("dictCode")).append("\n");
            }
            md.append("\n");
        }
    }

    // AI修改：抽出规则元数据构建，供 getRules 复用，避免与完整提示词内容耦合 - 2026-07-24
    private Map<String, Object> buildRuleMetadata() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("historyReferenceWorkflow", buildHistoryReferenceWorkflow());
        result.put("recommendedTools", buildRecommendedTools());
        result.put("specialFieldRules", buildSpecialFieldRules());
        result.put("oneTimeResponseNote", "本次返回仅包含规则信息，不包含 fields 字段明细。");
        return result;
    }

    private Map<String, Object> buildHistoryReferenceWorkflow() {
        Map<String, Object> workflow = new LinkedHashMap<String, Object>();
        workflow.put("ruleName", "targetEditHistoryReference");
        workflow.put("whenToTrigger", "当用户要新增采购明细（标的）、修改采购明细（标的）、补全采购明细信息、优化采购明细名称、补参考品牌、补标的参数时，如不确定查询历史参考，不能直接凭空生成。");
        workflow.put("mandatoryBeforeEdit", Boolean.TRUE);
        workflow.put("libraryQueryRule", "编辑采购明细基础信息前，如不确定可调用 TargetLibraryQueryTool 查询历史参考。conditions 传对象；如果当前采购明细已有 id，excludeIds 必须传当前采购明细 id 数组。");
        workflow.put("assetConfigJudgeRule", "当采购明细涉及资产配置标准判断时，填写完采购明细候选内容后，必须调用 AssetConfigQueryTool 查询资产配置标准；如果查询到结果，则需要判断是否超标；如果查询不到，则不需要判断是否超标。");
        workflow.put("referenceQueryRule", "编辑参考品牌前，如不确定可调用 TargetReferenceQueryTool 查询相近品牌历史。conditions 传对象；如果当前品牌行已有 id，excludeIds 必须传当前品牌 id 数组。");
        workflow.put("targetParamQueryRule", "编辑标的参数前，如不确定可以调用TargetParamQueryTool 查询相近参数历史。conditions 传对象；如果当前参数行已有 id，excludeIds 必须传当前参数 id 数组。");
        workflow.put("callSequence", Arrays.asList(
                "先调用 TargetFormFieldQueryTool 理解字段和联动规则",
                "再调用 TargetLibraryQueryTool 查询标的历史参考",
                "如需判断是否超标，再调用 AssetConfigQueryTool",
                "如需补品牌，再调用 TargetReferenceQueryTool",
                "如需补参数，再调用 TargetParamQueryTool",
                "最后再回填表单并调用 TargetValidateTool 校验"
        ));
        workflow.put("forbiddenBehavior", Arrays.asList(
                "禁止在新增或修改标的时跳过历史参考查询直接填写",
                "禁止在命中资产配置标准后跳过超标判断",
                "禁止调用 TargetDictQueryTool 或任何字典查询接口，所有字典值已在本返回的 dict.items 中",
                "禁止把自然语言整句直接当作查询参数",
                "禁止只填写 referenceListStr、bizTargetParamListStr、bizTargetResultParamsListStr 而不维护结构化字段"
        ));
        return workflow;
    }


    private List<Map<String, Object>> buildRecommendedTools() {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        tools.add(buildRecommendedTool(
                "TargetLibraryQueryTool",
                "新增或修改采购明细基础信息前先调用，优先参考调用结果",
                "conditions 使用对象，建议优先传 targetTypeName、targetName、targetContent；excludeIds 使用当前采购明细 id 数组。"
        ));
        tools.add(buildRecommendedTool(
                "AssetConfigQueryTool",
                "需要判断采购明细是否超标时必须调用",
                "conditions 使用对象，建议传 agencyId、departId、categoryName、assetName；如果查询到资产配置标准，则需要继续判断是否超标；如果查询不到，则不需要判断。"
        ));
        tools.add(buildRecommendedTool(
                "TargetReferenceQueryTool",
                "填写或修改 referenceList 前优先调用",
                "conditions 使用对象，建议优先传 brandName、model、manufacturer；excludeIds 使用当前品牌行 id 数组。"
        ));
        tools.add(buildRecommendedTool(
                "TargetParamQueryTool",
                "填写或修改 targetParamList 前优先调用",
                "conditions 使用对象，建议优先传 paramName、paramContent、resultParamContent；excludeIds 使用当前参数行 id 数组。"
        ));
        tools.add(buildRecommendedTool(
                "TargetTypeQueryTool",
                "填写 targetTypeId、targetTypeName、targetTypeCode 前调用",
                "按名称模糊查询标的类型，再按联动规则回填。"
        ));
        return tools;
    }

    private Map<String, Object> buildRecommendedTool(String toolName, String trigger, String usageRule) {
        Map<String, Object> tool = new LinkedHashMap<String, Object>();
        tool.put("toolName", toolName);
        tool.put("trigger", trigger);
        tool.put("usageRule", usageRule);
        return tool;
    }

    /**
     * 收集表单字段元数据
     * @param formClass 表单类
     */
    private List<Map<String, Object>> collectFieldMetadata(Class<?> formClass, Map<String, Map<String, Object>> dictResultMap) {
        List<Map<String, Object>> fieldMetadataList = new ArrayList<Map<String, Object>>();
        List<Class<?>> classChain = new ArrayList<Class<?>>();
        Class<?> currentClass = formClass;
        while (currentClass != null && currentClass != Object.class) {
            classChain.add(0, currentClass);
            currentClass = currentClass.getSuperclass();
        }
        for (Class<?> metadataClass : classChain) {
            Field[] declaredFields = metadataClass.getDeclaredFields();
            for (Field field : declaredFields) {
                if (shouldIgnoreField(field)) {
                    continue;
                }
                Map<String, Object> fieldMetadata = buildFieldMetadata(metadataClass, field, dictResultMap);
                if (shouldExcludeComplexField(fieldMetadata)) {
                    continue;
                }
                fieldMetadataList.add(fieldMetadata);
            }
        }
        return fieldMetadataList;
    }

    private boolean shouldIgnoreField(Field field) {
        int modifiers = field.getModifiers();
        return field.isSynthetic()
                || Modifier.isStatic(modifiers)
                || "serialVersionUID".equals(field.getName());
    }

    // AI修改：不再过滤字段，所有字段全部输出 - 2026-07-29
    private boolean shouldExcludeComplexField(Map<String, Object> fieldMetadata) {
        return fieldMetadata == null;
    }

    private Map<String, Object> buildFieldMetadata(Class<?> ownerClass, Field field, Map<String, Map<String, Object>> dictResultMap) {
        Map<String, Object> fieldMetadata = new LinkedHashMap<String, Object>();
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        Dict dict = field.getAnnotation(Dict.class);
        // 采购目录字段不走字典逻辑，单独由 TargetTypeQueryTool 处理
        if (isTargetTypeField(field.getName())) {
            dict = null;
        }
        fieldMetadata.put("ownerClass", ownerClass.getSimpleName());
        fieldMetadata.put("fieldName", field.getName());
        fieldMetadata.put("fieldType", resolveFieldType(field));
        fieldMetadata.put("apiModelProperty", buildApiModelPropertyMetadata(apiModelProperty));
        fieldMetadata.put("hasDict", dict != null);
        fieldMetadata.put("dict", buildDictMetadata(dict, dictResultMap));
        fieldMetadata.put("isTimeField", isTimeField(field));
        fieldMetadata.put("time", buildTimeMetadata(field));
        fieldMetadata.put("aiHint", buildAiHint(field.getName(), dict));
        fieldMetadata.put("specialRule", buildSpecialRule(field.getName()));
        fieldMetadata.put("nestedFieldInfo", buildNestedFieldInfo(field.getName()));
        return fieldMetadata;
    }

    private List<Map<String, Object>> buildSpecialFieldRules() {

        List<Map<String, Object>> specialFieldRules = new ArrayList<Map<String, Object>>();
        Map<String, Object> idRule = new LinkedHashMap<String, Object>();
        idRule.put("ruleName", "id规则");
        idRule.put("fieldNames", Arrays.asList("id"));
        idRule.put("description", "id字段禁止写空字符串''，没有则使用空值null");
        idRule.put("assignmentRule", "id字段禁止写空字符串''，没有则使用空值null");
        specialFieldRules.add(idRule);

        Map<String, Object> timeRule = new LinkedHashMap<String, Object>();
        idRule.put("ruleName", "时间规则");
        idRule.put("fieldNames", Arrays.asList("updateTime,createTime"));
        idRule.put("description", "不需要填更新时间和创建时间''");
        idRule.put("assignmentRule", "id字段禁止写空字符串''，没有则使用空值null");
        specialFieldRules.add(idRule);


        Map<String, Object> targetTypeRule = new LinkedHashMap<String, Object>();
        targetTypeRule.put("ruleName", "targetTypeFields");
        targetTypeRule.put("fieldNames", Arrays.asList("targetTypeId", "targetTypeName", "targetTypeCode"));
        targetTypeRule.put("dictCode", "target_information");
        targetTypeRule.put("description", "标的类型相关字段需要联动填写，不能按普通字符串字段处理。");
        targetTypeRule.put("assignmentRule", "查询字典 target_information 后，拿字典的 biz_code 赋值给 targetTypeId，拿字典的 name 赋值给 targetTypeName。");
        targetTypeRule.put("queryTip", "先调用标的类型专用查询工具按名称模糊查询，再按规则回填字段。");
        specialFieldRules.add(targetTypeRule);

        Map<String, Object> targetBaseInfoRule = new LinkedHashMap<String, Object>();
        targetBaseInfoRule.put("ruleName", "targetBaseInfoFields");
        targetBaseInfoRule.put("fieldNames", Arrays.asList("targetName", "targetContent", "unit", "requireConfig", "referencePrice", "referenceNum", "referenceTotalPrice"));
        targetBaseInfoRule.put("description", "这些是采购明细基础信息字段。新增或修改这些字段前，必须先查询历史采购明细信息作为参考。");
        targetBaseInfoRule.put("queryTool", "TargetLibraryQueryTool");
        targetBaseInfoRule.put("assignmentRule", "先查询历史相似采购明细，再结合当前业务上下文回填字段；不能跳过历史参考直接填写。");
        targetBaseInfoRule.put("queryTip", "建议优先使用 targetTypeName、targetName、targetContent 作为查询条件；如果当前采购明细已有 id，excludeIds 传当前采购明细 id 数组。");
        specialFieldRules.add(targetBaseInfoRule);

        Map<String, Object> assetConfigRule = new LinkedHashMap<String, Object>();
        assetConfigRule.put("ruleName", "assetConfigJudgeFields");
        assetConfigRule.put("fieldNames", Arrays.asList("agencyId", "departId", "targetTypeName", "targetName", "referencePrice", "unitPrice", "referenceNum", "num", "referenceTotalPrice", "targetPrice"));
        assetConfigRule.put("description", "这些字段会影响采购明细是否超标。填写候选值后，需查询资产配置标准判断是否超标。");
        assetConfigRule.put("queryTool", "AssetConfigQueryTool");
        assetConfigRule.put("assignmentRule", "先完成采购明细候选值填写，再调用 AssetConfigQueryTool 查询资产配置标准；查到结果则必须判断是否超标，查不到则无需判断。");
        assetConfigRule.put("queryTip", "建议使用 agencyId、departId、categoryName、assetName 查询资产配置标准。categoryName 可优先参考 targetTypeName，assetName 可优先参考 targetName。");
        specialFieldRules.add(assetConfigRule);

        Map<String, Object> referenceRule = new LinkedHashMap<String, Object>();
        referenceRule.put("ruleName", "referenceBrandFields");
        referenceRule.put("fieldNames", Arrays.asList("referenceList", "referenceListStr"));
        referenceRule.put("description", "referenceList，referenceListStr 必须同时维护，只写一个不合法。");
        referenceRule.put("assignmentRule", "填写参考品牌时，必须同时维护 referenceList 和 referenceListStr两个字段。referenceList 中每一项包含 brandName、model、manufacturer；referenceListStr 需要按 品牌名称：规格型号：生产厂家 的格式逐行拼接。");
        referenceRule.put("queryTool", "TargetReferenceQueryTool");
        referenceRule.put("queryTip", "优先调用 TargetReferenceQueryTool 查询历史品牌，再按结构化对象填写 referenceList，并同步生成 referenceListStr。");
        specialFieldRules.add(referenceRule);

        Map<String, Object> targetParamRule = new LinkedHashMap<String, Object>();
        targetParamRule.put("ruleName", "targetParamFields");
        targetParamRule.put("fieldNames", Arrays.asList("targetParamList", "bizTargetParamListStr", "bizTargetResultParamsListStr"));
        targetParamRule.put("description", "targetParamList，bizTargetParamListStr必须同时维护，只写一个不合法。。");
        targetParamRule.put("assignmentRule", "填写参考参数时，必须同时维护 targetParamList，并同步生成 bizTargetParamListStr 和 bizTargetResultParamsListStr。bizTargetParamListStr 使用 paramName:paramContent，bizTargetResultParamsListStr 使用 paramName:resultParamContent，核心参数前需加★。");
        targetParamRule.put("queryTool", "TargetParamQueryTool");
        targetParamRule.put("queryTip", "优先调用 TargetParamQueryTool 查询历史参数，再按结构化对象填写 targetParamList，并同步生成两个字符串字段。");
        specialFieldRules.add(targetParamRule);
        return specialFieldRules;
    }

    private Map<String, Object> buildApiModelPropertyMetadata(ApiModelProperty apiModelProperty) {
        Map<String, Object> apiMetadata = new LinkedHashMap<String, Object>();
        if (apiModelProperty == null) {
            apiMetadata.put("value", "");
            apiMetadata.put("notes", "");
            apiMetadata.put("required", Boolean.FALSE);
            apiMetadata.put("hidden", Boolean.FALSE);
            return apiMetadata;
        }
        apiMetadata.put("value", apiModelProperty.value());
        apiMetadata.put("notes", apiModelProperty.notes());
        apiMetadata.put("required", apiModelProperty.required());
        apiMetadata.put("hidden", apiModelProperty.hidden());
        return apiMetadata;
    }

    private Map<String, Object> buildDictMetadata(Dict dict, Map<String, Map<String, Object>> dictResultMap) {
        Map<String, Object> dictMetadata = new LinkedHashMap<String, Object>();
        if (dict == null) {
            dictMetadata.put("isDictionaryField", Boolean.FALSE);
            dictMetadata.put("selectionRule", "这不是字典字段，可按字段语义正常填写。");
            dictMetadata.put("items", new ArrayList<Map<String, Object>>());
            return dictMetadata;
        }
        String dictCode = resolveDictCode(dict);
        Map<String, Object> dictResult = dictResultMap == null ? null : dictResultMap.get(dictCode);
        dictMetadata.put("isDictionaryField", Boolean.TRUE);
        dictMetadata.put("selectionRule", "这是字典字段，只能从 items 列表中的 value 里选择填写。");
        dictMetadata.put("items", dictResult == null ? new ArrayList<Map<String, Object>>() : dictResult.get("items"));
        return dictMetadata;
    }

    private String buildDictExpressionExample(Dict dict) {
        if (dict == null) {
            return "";
        }
        String dicCode = dict.dicCode() == null ? "" : dict.dicCode().trim();
        String dicTable = dict.dicTable() == null ? "" : dict.dicTable().trim();
        String dicColumn = dict.dicColumn() == null ? "" : dict.dicColumn().trim();
        if (!dicTable.isEmpty() && !dicCode.isEmpty() && !dicColumn.isEmpty()) {
            // AI新增：为表字典直接生成可复用的查询表达式 - 2026-06-30
            return dicTable + "," + dicCode + "," + dicColumn;
        }
        return dicCode;
    }

    private String resolveDictType(Dict dict) {
        if (dict == null) {
            return "none";
        }
        String dicTable = dict.dicTable() == null ? "" : dict.dicTable().trim();
        return dicTable.isEmpty() ? "system" : "table";
    }

    private boolean isTimeField(Field field) {
        if (field == null) {
            return false;
        }
        return Date.class.isAssignableFrom(field.getType())
                || field.getAnnotation(JsonFormat.class) != null
                || field.getAnnotation(DateTimeFormat.class) != null;
    }

    private Map<String, Object> buildTimeMetadata(Field field) {
        Map<String, Object> timeMetadata = new LinkedHashMap<String, Object>();
        boolean timeField = isTimeField(field);
        timeMetadata.put("isTimeField", timeField);
        if (!timeField) {
            timeMetadata.put("format", "");
            timeMetadata.put("formatSource", "");
            timeMetadata.put("rule", "这不是时间字段。");
            return timeMetadata;
        }
        String format = resolveTimeFormat(field);
        if (format.isEmpty()) {
            // AI修改：时间字段未声明格式时，统一回退为默认时间格式 - 2026-07-22
            format = DEFAULT_TIME_FORMAT;
        }
        timeMetadata.put("format", format);
        timeMetadata.put("formatSource", resolveTimeFormatSource(field, format));
        timeMetadata.put("rule", "这是时间字段，填写时必须遵循格式：" + format);
        return timeMetadata;
    }

    private String resolveTimeFormat(Field field) {
        JsonFormat jsonFormat = field.getAnnotation(JsonFormat.class);
        if (jsonFormat != null && jsonFormat.pattern() != null && !jsonFormat.pattern().trim().isEmpty()) {
            return jsonFormat.pattern().trim();
        }
        DateTimeFormat dateTimeFormat = field.getAnnotation(DateTimeFormat.class);
        if (dateTimeFormat != null && dateTimeFormat.pattern() != null && !dateTimeFormat.pattern().trim().isEmpty()) {
            return dateTimeFormat.pattern().trim();
        }
        return "";
    }

    private String resolveTimeFormatSource(Field field, String format) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        JsonFormat jsonFormat = field.getAnnotation(JsonFormat.class);
        if (jsonFormat != null && format.equals(jsonFormat.pattern().trim())) {
            return "JsonFormat";
        }
        DateTimeFormat dateTimeFormat = field.getAnnotation(DateTimeFormat.class);
        if (dateTimeFormat != null && format.equals(dateTimeFormat.pattern().trim())) {
            return "DateTimeFormat";
        }
        return "";
    }

    private String buildAiHint(String fieldName, Dict dict) {
        // AI修改：明确 targetList 只能是一维对象数组，并提供最小合法结构约束，避免写成数组包数组 - 2026-07-24
        if ("targetList".equals(fieldName)) {
            return "这是采购明细列表字段。targetList 本身只能是 一维对象数组，合法结构是 [{...}, {...}]，每个元素都必须是采购明细对象，禁止写成 [[...]]、[{...}, [{...}]] 或出现 } ], [ { 这类数组包数组分段结构。编辑时应直接在同一个数组中追加或修改对象，相邻条目只能用 }, { 分隔。";
        }
        if ("targetTypeId".equals(fieldName)) {
            return "这是特殊联动字段，需要先调用 TargetTypeQueryTool 查询标的类型，并把返回结果中的 bizCode 赋值给 targetTypeId。";
        }
        if ("targetTypeName".equals(fieldName)) {
            return "这是特殊联动字段，需要先调用 TargetTypeQueryTool 查询标的类型，并把返回结果中的 name 赋值给 targetTypeName。";
        }
        if ("targetTypeCode".equals(fieldName)) {
            return "这是标的类型联动字段，跟随 targetTypeId / targetTypeName 联动填写。";
        }
        if ("targetName".equals(fieldName) || "targetContent".equals(fieldName) || "unit".equals(fieldName)
                || "requireConfig".equals(fieldName) || "referencePrice".equals(fieldName)
                || "referenceNum".equals(fieldName) || "referenceTotalPrice".equals(fieldName)) {
            return "这是采购明细基础信息字段。新增或修改前，必须先调用 TargetLibraryQueryTool 查询历史参考；如果需要判断是否超标，还要继续调用 AssetConfigQueryTool 查询资产配置标准。";
        }
        if ("agencyId".equals(fieldName) || "departId".equals(fieldName)) {
            return "这是超标判断的重要范围字段。调用 AssetConfigQueryTool 时，建议把当前字段作为条件传入，用于缩小资产配置标准范围。";
        }
        if ("unitPrice".equals(fieldName) || "num".equals(fieldName) || "targetPrice".equals(fieldName)) {
            return "这是超标判断字段。填写候选值后，优先调用 AssetConfigQueryTool 查询资产配置标准；若查到标准，需要结合单价、数量、金额判断是否超标。";
        }
        if ("targetTypeName".equals(fieldName)) {
            return "这是特殊联动字段，需要先调用 TargetTypeQueryTool 查询标的类型；如需判断是否超标，还可把该字段作为 categoryName 的参考值传给 AssetConfigQueryTool。";
        }
        if ("referenceList".equals(fieldName)) {
            return "这是参考品牌结构化字段，填写前优先调用 TargetReferenceQueryTool 查询历史参考，再填写品牌名称、规格型号、生产厂家，并同步生成 referenceListStr。";
        }
        if ("referenceListStr".equals(fieldName)) {
            return "这是参考品牌展示字符串字段，不能单独填写，需要根据 referenceList 按 品牌名称：规格型号：生产厂家 的格式逐行生成。";
        }
        if ("targetParamList".equals(fieldName)) {
            return "这是参考参数结构化字段，填写前优先调用 TargetParamQueryTool 查询历史参考，再填写参数名称、参数要求、结论参数、是否核心参数，并同步生成 bizTargetParamListStr 和 bizTargetResultParamsListStr。";
        }
        if ("bizTargetParamListStr".equals(fieldName)) {
            return "这是参考参数字符串字段，不能单独填写，需要根据 targetParamList 按 参数名称：参数要求 的格式逐行生成，核心参数前加★。";
        }
        if ("bizTargetResultParamsListStr".equals(fieldName)) {
            return "这是结论参数字符串字段，不能单独填写，需要根据 targetParamList 按 参数名称：结论参数 的格式逐行生成，核心参数前加★。";
        }
        if (dict == null) {
            return "这是普通字段，可结合字段说明和字段类型填写。";
        }
        return "字段 " + fieldName + " 是字典字段，只能从 dict.items 给出的 value 列表中选择填写。";
    }

    private boolean isTargetTypeField(String fieldName) {
        return "targetTypeId".equals(fieldName)
                || "targetTypeName".equals(fieldName)
                || "targetTypeCode".equals(fieldName);
    }

    private Map<String, Map<String, Object>> buildDictResultMap(Class<?>... classes) {
        Map<String, Map<String, Object>> dictResultMap = new LinkedHashMap<String, Map<String, Object>>();
        Set<String> dictCodes = collectDictCodes(classes);
        for (String dictCode : dictCodes) {
            dictResultMap.put(dictCode, loadDictResult(dictCode));
        }
        return dictResultMap;
    }

    private Set<String> collectDictCodes(Class<?>... classes) {
        Set<String> dictCodes = new LinkedHashSet<String>();
        if (classes == null) {
            return dictCodes;
        }
        for (Class<?> clazz : classes) {
            if (clazz == null) {
                continue;
            }
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (shouldIgnoreField(field)) {
                        continue;
                    }
                    Dict dict = field.getAnnotation(Dict.class);
                    // 采购目录字段不走字典逻辑
                    if (isTargetTypeField(field.getName())) {
                        continue;
                    }
                    String dictCode = resolveDictCode(dict);
                    if (dictCode != null && !dictCode.isEmpty()) {
                        dictCodes.add(dictCode);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        }
        return dictCodes;
    }

    private String resolveDictCode(Dict dict) {
        if (dict == null || dict.dicCode() == null) {
            return "";
        }
        return dict.dicCode().trim();
    }

    private Map<String, Object> loadDictResult(String dictCode) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String normalizedDictCode = dictCode == null ? "" : dictCode.trim();
        result.put("dictCode", normalizedDictCode);
        result.put("items", new ArrayList<Map<String, Object>>());
        if (normalizedDictCode.isEmpty()) {
            return result;
        }
        try {
            // AI修改：系统字典回填只按 Dict.dicCode 查询，避免拼接表达式导致回填失败 - 2026-07-22
            List<DictModel> dictItems = sysBaseCloudAPI.queryDictItemsByCode(normalizedDictCode);
            result.put("items", convertDictItems(dictItems));
            return result;
        } catch (Exception e) {
            return result;
        }
    }

    private List<Map<String, Object>> convertDictItems(List<DictModel> dictItems) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (dictItems == null) {
            return items;
        }
        for (DictModel dictItem : dictItems) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("value", dictItem.getValue());
            item.put("text", dictItem.getText());
            item.put("description", dictItem.getDescription());
            item.put("sort", dictItem.getSort());
            item.put("id", dictItem.getId());
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> buildSpecialRule(String fieldName) {
        Map<String, Object> specialRule = new LinkedHashMap<String, Object>();
        if ("targetTypeId".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("dictCode", "target_information");
            specialRule.put("queryTool", "TargetTypeQueryTool");
            specialRule.put("assignmentRule", "把标的类型查询结果中的 bizCode 赋值给 targetTypeId。");
            return specialRule;
        }
        if ("targetTypeName".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("dictCode", "target_information");
            specialRule.put("queryTool", "TargetTypeQueryTool");
            specialRule.put("assignmentRule", "把标的类型查询结果中的 name 赋值给 targetTypeName。");
            return specialRule;
        }
        if ("targetTypeCode".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("dictCode", "target_information");
            specialRule.put("queryTool", "TargetTypeQueryTool");
            specialRule.put("assignmentRule", "这是标的类型联动字段，填写时需要同步参考标的类型查询结果以及 targetTypeId、targetTypeName 的映射规则。");
            return specialRule;
        }
        if ("targetName".equals(fieldName) || "targetContent".equals(fieldName) || "unit".equals(fieldName)
                || "requireConfig".equals(fieldName) || "referencePrice".equals(fieldName)
                || "referenceNum".equals(fieldName) || "referenceTotalPrice".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetLibraryQueryTool");
            specialRule.put("additionalQueryTool", "AssetConfigQueryTool");
            specialRule.put("linkedFields", Arrays.asList("targetName", "targetContent", "unit", "requireConfig", "referencePrice", "referenceNum", "referenceTotalPrice"));
            specialRule.put("assignmentRule", "新增或修改这些采购明细基础信息字段前，必须先调用 TargetLibraryQueryTool 查询历史参考；如需判断超标，还要继续调用 AssetConfigQueryTool 查询资产配置标准。");
            return specialRule;
        }
        if ("agencyId".equals(fieldName) || "departId".equals(fieldName) || "unitPrice".equals(fieldName)
                || "num".equals(fieldName) || "targetPrice".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "AssetConfigQueryTool");
            specialRule.put("linkedFields", Arrays.asList("agencyId", "departId", "targetTypeName", "targetName", "referencePrice", "unitPrice", "referenceNum", "num", "referenceTotalPrice", "targetPrice"));
            specialRule.put("assignmentRule", "这些字段会影响是否超标。填写候选值后，需要调用 AssetConfigQueryTool 查询资产配置标准；查到标准则必须继续判断是否超标。");
            return specialRule;
        }
        if ("referenceList".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetReferenceQueryTool");
            specialRule.put("linkedFields", Arrays.asList("referenceList", "referenceListStr"));
            specialRule.put("assignmentRule", "填写 referenceList 前，优先调用 TargetReferenceQueryTool 查询历史品牌；填写后，需要同步生成 referenceListStr。referenceList 每项包含 brandName、model、manufacturer。");
            return specialRule;
        }
        if ("referenceListStr".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetReferenceQueryTool");
            specialRule.put("linkedFields", Arrays.asList("referenceList", "referenceListStr"));
            specialRule.put("assignmentRule", "referenceListStr 需要由 referenceList 反向生成，格式为 品牌名称：规格型号：生产厂家，多条数据按换行分隔。");
            return specialRule;
        }
        if ("targetParamList".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetParamQueryTool");
            specialRule.put("linkedFields", Arrays.asList("targetParamList", "bizTargetParamListStr", "bizTargetResultParamsListStr"));
            specialRule.put("assignmentRule", "填写 targetParamList 前，优先调用 TargetParamQueryTool 查询历史参数；填写后，需要同步生成 bizTargetParamListStr 和 bizTargetResultParamsListStr。");
            return specialRule;
        }
        if ("bizTargetParamListStr".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetParamQueryTool");
            specialRule.put("linkedFields", Arrays.asList("targetParamList", "bizTargetParamListStr", "bizTargetResultParamsListStr"));
            specialRule.put("assignmentRule", "bizTargetParamListStr 需要由 targetParamList 反向生成，格式为 参数名称：参数要求，核心参数前加★。");
            return specialRule;
        }
        if ("bizTargetResultParamsListStr".equals(fieldName)) {
            specialRule.put("isSpecialField", Boolean.TRUE);
            specialRule.put("queryTool", "TargetParamQueryTool");
            specialRule.put("linkedFields", Arrays.asList("targetParamList", "bizTargetParamListStr", "bizTargetResultParamsListStr"));
            specialRule.put("assignmentRule", "bizTargetResultParamsListStr 需要由 targetParamList 反向生成，格式为 参数名称：结论参数，核心参数前加★。");
            return specialRule;
        }
        specialRule.put("isSpecialField", Boolean.FALSE);
        return specialRule;
    }

    private Map<String, Object> buildNestedFieldInfo(String fieldName) {
        Map<String, Object> nestedFieldInfo = new LinkedHashMap<String, Object>();
        if ("targetList".equals(fieldName)) {
            // AI修改：为 targetList 增加一维数组说明和最小合法示例，降低模型把列表写成二维数组的概率 - 2026-07-24
            nestedFieldInfo.put("itemClassName", BizRequireTarget.class.getSimpleName());
            nestedFieldInfo.put("itemDescription", "采购明细对象列表。targetList 只能是一维对象数组，数组元素必须是采购明细对象，不能再包一层数组。");
            nestedFieldInfo.put("arrayRule", "合法结构是 [{...}, {...}]；禁止写成 [[...]]、[{...}, [{...}]] 或 } ], [ { 这类分段数组结构。");
            nestedFieldInfo.put("itemFields", collectNestedFieldMetadata(BizRequireTarget.class));
            nestedFieldInfo.put("jsonExample", "[{\"targetName\":\"示例标的\",\"targetTypeId\":\"A02091102\",\"targetTypeName\":\"通用摄像机\",\"requireCatalog\":\"outCatalog\",\"num\":1,\"unit\":\"台\",\"referenceList\":[],\"referenceListStr\":\"\",\"targetParamList\":[],\"bizTargetParamListStr\":\"\",\"bizTargetResultParamsListStr\":\"\",\"targetContent\":\"示例参数说明\"}]");
            return nestedFieldInfo;
        }
        if ("referenceList".equals(fieldName)) {
            // AI修改：只返回简单类名，避免暴露完整包路径给AI - 2026-07-23
            nestedFieldInfo.put("itemClassName", BizTargetReference.class.getSimpleName());
            nestedFieldInfo.put("itemDescription", "参考品牌对象列表。");
            nestedFieldInfo.put("itemFields", collectNestedFieldMetadata(BizTargetReference.class));
            nestedFieldInfo.put("textFormatExample", "品牌名称：规格型号：生产厂家");
            return nestedFieldInfo;
        }
        if ("targetParamList".equals(fieldName)) {
            // AI修改：只返回简单类名，避免暴露完整包路径给AI - 2026-07-23
            nestedFieldInfo.put("itemClassName", BizTargetParam.class.getSimpleName());
            nestedFieldInfo.put("itemDescription", "标的参数对象列表。");
            nestedFieldInfo.put("itemFields", collectNestedFieldMetadata(BizTargetParam.class));
            nestedFieldInfo.put("textFormatExample", "参数名称：参数要求");
            nestedFieldInfo.put("resultTextFormatExample", "参数名称：结论参数");
            nestedFieldInfo.put("coreParamTip", "如果 isCoreParam=1，则字符串行前需要加★。");
            return nestedFieldInfo;
        }
        return nestedFieldInfo;
    }

    private List<Map<String, Object>> collectNestedFieldMetadata(Class<?> nestedClass) {
        List<Map<String, Object>> nestedFields = new ArrayList<Map<String, Object>>();
        Field[] declaredFields = nestedClass.getDeclaredFields();
        for (Field field : declaredFields) {
            if (shouldIgnoreField(field)) {
                continue;
            }
            nestedFields.add(buildNestedFieldMetadata(field));
        }
        return nestedFields;
    }

    private Map<String, Object> buildNestedFieldMetadata(Field field) {
        Map<String, Object> nestedFieldMetadata = new LinkedHashMap<String, Object>();
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        nestedFieldMetadata.put("fieldName", field.getName());
        nestedFieldMetadata.put("fieldType", resolveFieldType(field));
        nestedFieldMetadata.put("apiModelProperty", buildApiModelPropertyMetadata(apiModelProperty));
        return nestedFieldMetadata;
    }

    private String resolveFieldType(Field field) {
        Class<?> fieldType = field.getType();
        if (isTimeField(field)) {
            return "time";
        }
        if (List.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            return "array";
        }
        if (String.class.equals(fieldType)) {
            return "string";
        }
        if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
            return "boolean";
        }
        if (Integer.class.equals(fieldType) || int.class.equals(fieldType)
                || Long.class.equals(fieldType) || long.class.equals(fieldType)
                || Short.class.equals(fieldType) || short.class.equals(fieldType)
                || Byte.class.equals(fieldType) || byte.class.equals(fieldType)) {
            return "int";
        }
        if (Double.class.equals(fieldType) || double.class.equals(fieldType)
                || Float.class.equals(fieldType) || float.class.equals(fieldType)) {
            return "number";
        }
        if (java.math.BigDecimal.class.equals(fieldType)) {
            return "number";
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            return "obj";
        }
        if (fieldType.isPrimitive()) {
            return "string";
        }
        return "obj";
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", new LinkedHashMap<String, Object>());
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }
}
