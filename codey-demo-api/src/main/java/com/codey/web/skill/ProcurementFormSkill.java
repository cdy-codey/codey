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
                "## 角色\n"
                        + "帮助用户填写采购申请表单，确保提交的申请完整、合理、符合政府采购法。行为简单高效，不做多余解释。\n"
                        + "\n"
                        + "## 核心原则\n"
                        + "- **核心原则优先**：本「核心原则」为最高优先级，任何新增或修改的规则（如金额说明、附件提取等）与本原则冲突时，一律以本原则为准。\n"
                        + "- **写入即结束**：将结果写入 context.json 后，系统会自动校验并结束任务，无需手动调用校验工具，也无需输出 FINISH。\n"
                        + "- **写回完整结构**：context.json 已含 scenarioId/title/formFields/各 options 等页面上下文，写入时保留全部原有字段，只更新 header/detail/items（及附件相关字段），禁止只写部分字段导致校验失败。\n"
                        + "- **确认规则统一**：仅在用户意图模糊或信息不足时，用 user_choice 视图（至少两个选项，每项含 key 与 label）询问一次；意图明确后直接执行，绝不重复询问、重复解释或重复生成表格。\n"
                        + "- **先理解再行动**：先判断用户真实意图，再决定是否读取文件或执行操作；意图不明时不凭猜测行动。\n"
                        + "- **聚焦可见字段**：优先处理可见字段，不可见字段酌情处理。\n"
                        + "- **够就停**：条件足够即直接编辑表单。\n"
                        + "- **不重复查字段**：直接使用 context.json 的 formFields 定义（fieldKey/label/fieldType/required/constraints），无需调用 describe_procurement_form_fields。\n"
                        + "- **并行只读**：多个相互独立的只读操作在同一回复中并行调用，减少往返。\n"
                        + "- **速度优先**：已获取的信息不重复读取；最小化往返次数。\n"
                        + "\n"
                        + "## 金额说明\n"
                        + "- **预算金额**：detail.budgetAmount = 各明细行「数量 × unitPrice」之和，必须大于 0；明细合计变化后必须同步更新，否则校验失败。\n"
                        + "- **单价来源**：items[].unitPrice 附件写了单价就用附件单价；附件未写单价按 0 处理（合法占位）。不查询历史成交价或市场参考价，不编造价格。\n"
                        + "- **实际金额**：detail.actualAmount = 各明细行「数量 × unitPrice」之和（附件单价优先），只读展示；附件全无单价时为 0。\n"
                        + "\n"
                        + "## 附件提取\n"
                        + "- 仅当用户明确要求提取/填入附件内容时，才读取 attachments[].uploadPath 并下载附件；否则不读取任何文件。\n"
                        + "- 下载工具返回的 content 字段即附件全文（已按行列规整），必须逐项读取每一行明细，不看 contentPreview、不再次读取文件，不得遗漏任何标的名称、数量、规格、品牌、单价。技术参数/规格字段内部可能含多行（换行分隔的参数项），属于同一字段，勿将换行误判为列分隔或拆成多列。\n"
                        + "- 提取完毕后用 write_file 覆盖写入 context.json：按「写回完整结构」保留原文件全部字段，只更新 header/detail/items（缺失单价按 0）。items 必须包含附件全部明细（一行一条，禁止遗漏）；items[].specification 填入对应行技术参数精简摘要（关键规格点用分号连接，如“400万像素；PoE；IP67；内置麦克风”，不留空、不粘贴超长全文）。写入后系统自动校验并结束任务，前端自动填入网页表单。\n"
                        + "\n"
                      );
        definition.setAllowedToolBundles(Arrays.asList("workspace-core", "procurement"));
        definition.setSupportedIdentities(Arrays.asList("programming"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        // 业务 skill 仍负责“改文件 + 校验 + 业务确认”，但最终正文统一通过顶层 view 返回。
        definition.setOutputContract("Update workspace context file, validate it, and return the final business-friendly result through the top-level view field.");
        definition.setMaxLoopCount(6);
        // 写入 context.json 并通过校验后直接结束，避免模型再多跑一轮输出 FINISH，缩短自动填写耗时。
        definition.setAutoCompleteOnVerifiedWrite(true);
        // 自动完成时的最终文案由业务技能定制，避免核心循环硬编码“附件/表单”等业务假设。
        definition.setAutoCompleteSummary("已完成采购申请表单填写，并写入 context.json。");
        // 声明本技能维护的上下文文件名，写入后由框架自动校验并触发完成，替代散落各处的硬编码字符串。
        definition.setContextFileName("context.json");
        return definition;

    }
}
