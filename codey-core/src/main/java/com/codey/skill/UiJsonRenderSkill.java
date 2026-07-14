package com.codey.skill;

import com.codey.meta.IdentityMatchMode;

import java.util.Arrays;

/**
 * 内置的 UI JSON 展示规则 skill。
 * 只定义结构化展示协议，供上层按 skillName 显式启用，不承载业务领域语义。
 */
public class UiJsonRenderSkill implements Skill {

    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("ui-json-render-agent");
        definition.setDescription("Define UI JSON rendering contracts. Invoke when upper layers explicitly need structured JSON views such as form_data or diff_data.");
        definition.setSystemPromptTemplate(
                "你当前承担的是 UI JSON 展示规则，不负责额外扩展业务语义。\n"
                        + "只有在上层明确启用当前 skill，且回复内容适合结构化展示时，才输出结构化 view。\n"
                        + "如果不适合结构化展示，则使用 text view。\n"
                        + "\n"
                        + "支持三类 view 协议：\n"
                        + "0. text：用于普通文本展示。\n"
                        + "1. form_data：用于展示表单、单证、对象字段和列表明细。\n"
                        + "2. diff_data：用于展示修改前后对比内容。\n"
                        + "\n"
                        + "text 协议：\n"
                        + "{\n"
                        + "  \"_view_type\": \"text\",\n"
                        + "  \"content\": \"普通文本内容\"\n"
                        + "}\n"
                        + "\n"
                        + "form_data 协议：\n"
                        + "{\n"
                        + "  \"_view_type\": \"form_data\",\n"
                        + "  \"modules\": [\n"
                        + "    {\n"
                        + "      \"title\": \"基本信息\",\n"
                        + "      \"type\": \"object\",\n"
                        + "      \"data\": {\n"
                        + "        \"字段1\": \"值1\",\n"
                        + "        \"字段2\": \"值2\"\n"
                        + "      }\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"title\": \"明细列表\",\n"
                        + "      \"type\": \"list\",\n"
                        + "      \"headers\": [\"列1\", \"列2\"],\n"
                        + "      \"data\": [\n"
                        + "        {\n"
                        + "          \"列1\": \"值1\",\n"
                        + "          \"列2\": \"值2\"\n"
                        + "        }\n"
                        + "      ]\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n"
                        + "\n"
                        + "diff_data 协议：\n"
                        + "{\n"
                        + "  \"_view_type\": \"diff_data\",\n"
                        + "  \"title\": \"变更对比\",\n"
                        + "  \"changes\": [\n"
                        + "    {\n"
                        + "      \"field\": \"字段名称\",\n"
                        + "      \"old_value\": \"修改前\",\n"
                        + "      \"new_value\": \"修改后\"\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n"
                        + "\n"
                        + "输出规则：\n"
                        + "1. 最终完成时，必须把展示内容放进顶层 JSON 的 view 字段中，不要直接裸输出 _view_type JSON。\n"
                        + "2. 合法示例：{\"status\":\"FINISH\",\"view\":{\"_view_type\":\"form_data\",\"modules\":[...]}}\n"
                        + "3. form_data 中允许 object 与 list 混合分模块展示。\n"
                        + "4. list 的 headers 必须与 data 中字段语义一致，避免表头和数据错位。\n"
                        + "5. diff_data 只表达字段级差异，不要混入无关说明文字。\n"
                        + "6. JSON 必须完整闭合，不能输出半截 JSON、注释或省略号。");
        definition.setSupportedIdentities(Arrays.asList("workspace-core"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        definition.setOutputContract("Return the final answer through the top-level view field. Use text/form_data/diff_data views as appropriate.");
        definition.setMaxLoopCount(8);
        return definition;
    }
}
