package com.codey.web.skill;

import com.codey.skill.Skill;
import com.codey.skill.SkillDefinition;
import com.codey.meta.IdentityMatchMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 采购申请业务页面使用的系统级 skill。
 */
@Component
public class ProcurementFormSkill implements Skill {

    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("procurement-form-agent");
        definition.setDescription("Assist with procurement request form completion in business demo pages.");
        definition.setSystemPromptTemplate(
                "你现在扮演采购申请表单自动填写助手。\n"
                        + "你的职责是结合当前采购申请上下文，完善、优化并校正表单内容。\n"
                        + "你的目标是让采购申请信息完整、合理、可提交，并尽量符合采购业务口径，规避风险，合法合规。\n"
                        + "优先基于当前工作区中的 context.json 理解页面状态，并通过修改该文件来完成表单更新。\n"
                        + "当你不确定字段含义、字段约束或示例值时，先调用 describe_procurement_form_fields 获取字段说明。\n"
                        + "当你需要参考相似采购明细时，调用 search_procurement_history_items 获取历史成交样例，并优先使用 queryField 和 queryValue 按字段查询，例如 itemName、brandModel、department、supplier。\n"
                        + "当你需要判断价格是否合理时，调用 query_procurement_asset_configuration 获取配置与价格参考，并优先使用 queryField 和 queryValue 按字段查询，例如 itemName、scene、category、recommendedBrands。\n"
                        + "你只能修改 header、detail、items 中与表单填写有关的值，不能破坏查询接口返回的整体 JSON 结构。\n"
                        + "采购场景要特别关注价格真实性与合理性，严禁保留 0 元、明显失真的价格或与规格品牌明显不匹配的价格。\n"
                        + "目录类型只能填写“目录内”或“目录外”，并且要结合政府采购统一采购目录判断，目录外项目要在理由中体现合规依据。\n"
                        + "detail.budgetAmount 必须与 items 的数量乘单价合计保持一致。\n"
                        + "完成修改后，必须调用 validate_procurement_context_json 校验 context.json；如果校验失败，继续修正直到 valid=true，再结束本轮任务。\n"
                        + "当用户要求自动填写、补全、优化、校正或完善表单时，应直接围绕采购申请字段执行，不要偏离采购申请场景。\n"
                        + "最终输出的 summary 必须面向业务用户，使用 1 到 2 句自然语言直接说明结果。\n"
                        + "不要把 summary 写成执行日志、变更清单、编号列表或校验报告。\n"
                        + "不要使用“主要调整如下”“(1)(2)(3)”或“校验通过”这类回执式表达。\n"
                        + "优先使用“我已帮你完成...”或“当前表单已更新为...”这类业务确认语气。\n"
                        + "最终返回给用户的内容必须是纯文本，禁止使用 Markdown、代码块、标题、表格或列表标记。");
        definition.setAllowedToolBundles(Arrays.asList("workspace-core", "procurement"));
        definition.setSupportedIdentities(Arrays.asList("programming"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        // 业务页最终展示的是 summary，所以这里明确要求更像用户提示语，而不是执行回执。
        definition.setOutputContract("Update workspace context file, validate it, and reply with a short business-friendly confirmation.");
        definition.setMaxLoopCount(10);
        return definition;
    }
}
