package com.codey.web.skill;

import com.codey.skill.Skill;
import com.codey.skill.SkillDefinition;
import com.codey.meta.IdentityMatchMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 面向 v-form-designer JSON 的应用层 skill。
 */
@Component
public class VFormJsonSkill implements Skill {
    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("vform-json-agent");
        definition.setDescription("Generate and edit v-form-designer JSON with strict schema guidance.");
        definition.setSystemPromptTemplate(
                "你正在生成或修改 v-form-designer 的表单 JSON 配置。\n"
                        + "如果系统反馈指出失败或约束冲突，要调整方案，不要原样重复上一轮调用。\n"
                        + "默认直接面向用户交付自然中文结果，不要做自我复述。\n"
                        + "\n"
                        + "生成规范：\n"
                        + "1. 最外层只能输出一个 JSON 对象，且只包含两个顶层字段：widgetList、formConfig。\n"
                        + "2. 不要额外包装 result、data、schema、formJson 等字段。\n"
                        + "3. 不要输出注释、markdown 代码围栏、半截 JSON 或省略号。\n"
                        + "4. 写入 forms/*.form.json 后，调用 validate_vform_json 并传入文件路径，让工具自己读取文件做最终校验。\n"
                        + "5. 如果校验失败，继续修正文件并重新校验，不要把未通过校验的结果当作完成态返回。\n"
                        + "\n"
                        + "推荐最小顶层模板：\n"
                        + "{\n"
                        + "  \"widgetList\": [],\n"
                        + "  \"formConfig\": {\n"
                        + "    \"modelName\": \"formData\",\n"
                        + "    \"refName\": \"vForm\",\n"
                        + "    \"rulesName\": \"rules\",\n"
                        + "    \"labelWidth\": 80,\n"
                        + "    \"labelPosition\": \"left\",\n"
                        + "    \"size\": \"\",\n"
                        + "    \"labelAlign\": \"label-left-align\",\n"
                        + "    \"cssCode\": \"\",\n"
                        + "    \"customClass\": \"\",\n"
                        + "    \"functions\": \"\",\n"
                        + "    \"layoutType\": \"PC\",\n"
                        + "    \"jsonVersion\": 3,\n"
                        + "    \"onFormCreated\": \"\",\n"
                        + "    \"onFormMounted\": \"\",\n"
                        + "    \"onFormDataChange\": \"\"\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "widgetList 中每个组件的推荐规范：\n"
                        + "1. 普通字段组件至少包含：type、icon、formItemFlag、options、id。\n"
                        + "2. options 至少包含：name。表单项通常还应包含 label。\n"
                        + "3. 容器组件至少包含：type、category: \"container\"、options、id，以及自身子结构字段。\n"
                        + "\n"
                        + "推荐 grid 模板：\n"
                        + "{\n"
                        + "  \"type\": \"grid\",\n"
                        + "  \"category\": \"container\",\n"
                        + "  \"icon\": \"grid\",\n"
                        + "  \"cols\": [\n"
                        + "    {\n"
                        + "      \"type\": \"grid-col\",\n"
                        + "      \"category\": \"container\",\n"
                        + "      \"icon\": \"grid-col\",\n"
                        + "      \"internal\": true,\n"
                        + "      \"widgetList\": [],\n"
                        + "      \"options\": {\n"
                        + "        \"name\": \"gridCol001\",\n"
                        + "        \"hidden\": false,\n"
                        + "        \"span\": 12,\n"
                        + "        \"offset\": 0,\n"
                        + "        \"push\": 0,\n"
                        + "        \"pull\": 0,\n"
                        + "        \"responsive\": false,\n"
                        + "        \"md\": 12,\n"
                        + "        \"sm\": 12,\n"
                        + "        \"xs\": 12,\n"
                        + "        \"customClass\": \"\"\n"
                        + "      },\n"
                        + "      \"id\": \"grid-col-001\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"type\": \"grid-col\",\n"
                        + "      \"category\": \"container\",\n"
                        + "      \"icon\": \"grid-col\",\n"
                        + "      \"internal\": true,\n"
                        + "      \"widgetList\": [],\n"
                        + "      \"options\": {\n"
                        + "        \"name\": \"gridCol002\",\n"
                        + "        \"hidden\": false,\n"
                        + "        \"span\": 12,\n"
                        + "        \"offset\": 0,\n"
                        + "        \"push\": 0,\n"
                        + "        \"pull\": 0,\n"
                        + "        \"responsive\": false,\n"
                        + "        \"md\": 12,\n"
                        + "        \"sm\": 12,\n"
                        + "        \"xs\": 12,\n"
                        + "        \"customClass\": \"\"\n"
                        + "      },\n"
                        + "      \"id\": \"grid-col-002\"\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"options\": {\n"
                        + "    \"name\": \"grid001\",\n"
                        + "    \"hidden\": false,\n"
                        + "    \"gutter\": 12,\n"
                        + "    \"customClass\": \"\"\n"
                        + "  },\n"
                        + "  \"id\": \"grid001\"\n"
                        + "}\n"
                        + "\n"
                        + "当任务完成且不再需要工具时，必须输出顶层 JSON 最终结果，不要额外包装 result 等字段。\n");
        // vform 会话统一按 bundle 暴露工具，避免继续维护逐个工具名白名单。
        definition.setAllowedToolBundles(Arrays.asList("workspace-core", "vform"));
        // vform 会话进入后，只暴露同域能力，避免普通代码会话误用该 skill。
        definition.setSupportedIdentities(Arrays.asList("vform", "programming"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        definition.setOutputContract("OpenAI tool_calls + final result JSON");
        definition.setMaxLoopCount(12);
        return definition;
    }
}
