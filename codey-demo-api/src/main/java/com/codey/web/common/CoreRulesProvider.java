package com.codey.web.common;

import org.springframework.stereotype.Component;

/**
 * 提供全局铁律（core rules），在每次大模型会话中自动注入，确保 AI 不被长上下文稀释注意力。
 * 
 * <p>铁律通过 {@code RunRequest.coreRules} 传递到 codey-core，
 * 由 {@code CoreRulesMessageStage} 每轮以 user 消息注入到 LLM 输入中。
 */
@Component
public class CoreRulesProvider {

    /**
     * 返回完整的铁律文本，包含规则说明、推荐示例和禁止示例。
     */
    public String getCoreRules() {
        StringBuilder sb = new StringBuilder();

        // ========== 规则1：禁止输出英文技术标识符 ==========
        sb.append("1. 禁止输出英文技术标识符（最高优先级）\n");
        sb.append("   在生成任何面向用户的文本前，扫描全文。若存在形如 camelCase、snake_case、kebab-case");
        sb.append(" 的英文标识符（字段名、变量名、参数名、API路径片段），一律替换为对应的中文名称。");
        sb.append("不允许出现任何英文技术标识符。\n");
        sb.append("\n");

        // 推荐示例
        sb.append("   ✅ 推荐示例：\n");
        sb.append("   - \"请填写采购金额字段\" 而非 \"请填写 purchaseAmount 字段\"\n");
        sb.append("   - \"提交时间需要校验\" 而非 \"submit_time 需要校验\"\n");
        sb.append("   - \"供应商名称不能为空\" 而非 \"supplierName 不能为空\"\n");
        sb.append("   - \"合同编号\" 而非 \"contract_no\"\n");
        sb.append("   - \"审批状态\" 而非 \"approvalStatus\"\n");
        sb.append("   - \"调用获取用户列表接口\" 而非 \"调用 /api/getUserList 接口\"\n");
        sb.append("\n");

        // 禁止示例
        sb.append("   ❌ 禁止示例：\n");
        sb.append("   - \"purchaseAmount 字段需要填写\" → 必须写成 \"采购金额字段需要填写\"\n");
        sb.append("   - \"请检查 supplier_name 是否为空\" → 必须写成 \"请检查供应商名称是否为空\"\n");
        sb.append("   - \"approvalStatus 的值为 pending\" → 必须写成 \"审批状态的值为待审批\"\n");
        sb.append("   - \"调用 /api/submitOrder 提交\" → 必须写成 \"调用提交订单接口\"\n");
        sb.append("   - \"字段说明：contractNo 合同编号，totalAmount 总金额\"");
        sb.append(" → 必须写成 \"字段说明：合同编号，总金额\"\n");
        sb.append("   - \"请设置 enable_notification 为 true\" → 必须写成 \"请开启通知\"\n");

        return sb.toString();
    }
}
