package com.codey.console.common;

import com.codey.loop.HumanConfirmationService;
import com.codey.loop.HumanDecision;
import com.codey.loop.FinalResult;
import com.codey.tools.ToolInvocation;
import com.codey.config.AgentSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

/**
 * 在控制台里展示编辑方案，并等待人工确认。
 */
public class ConsoleHumanConfirmationService implements HumanConfirmationService {
    private final BufferedReader reader;
    private final PrintStream out;

    public ConsoleHumanConfirmationService() {
        this(ConsoleIo.reader(), ConsoleIo.out());
    }

    ConsoleHumanConfirmationService(BufferedReader reader, PrintStream out) {
        this.reader = reader;
        this.out = out;
    }

    @Override
    public HumanDecision confirmEdit(AgentSession session, FinalResult turn, ToolInvocation request) {
        try {
            out.println();
            out.println("=== 需要人工确认 ===");
            out.println("会话: " + session.getSessionId());
            out.println("摘要: " + safe(turn.getSummary()));
            out.println("工具: " + request.getToolName());
            if (!safe(turn.getUncertaintyReason()).isEmpty()) {
                out.println("不确定原因: " + safe(turn.getUncertaintyReason()));
            }
            printArguments(request.getArguments());
            out.println("[y] 同意并执行");
            out.println("[n] 拒绝并让模型重试");
            out.println("[f] 拒绝并补充反馈");
            out.print("请输入决策 (y/n/f): ");

            String decision = safe(reader.readLine()).trim().toLowerCase();
            if ("y".equals(decision)) {
                // 同意后直接执行，避免每次批准都再追问一次补充说明。
                return HumanDecision.approve("");
            }
            if ("f".equals(decision)) {
                String feedback = readRequiredLine("请输入给模型的反馈: ");
                return HumanDecision.reject("人工反馈: " + feedback);
            }
            return HumanDecision.reject("人工拒绝了本次编辑提案，请重新规划更合适的修改方案。");
        } catch (IOException exception) {
            throw new IllegalStateException("读取人工确认输入失败", exception);
        }
    }

    private void printArguments(Map<String, Object> arguments) {
        out.println("参数:");
        if (arguments == null || arguments.isEmpty()) {
            out.println("  (空)");
            return;
        }
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            out.println("  - " + entry.getKey() + ": " + String.valueOf(entry.getValue()));
        }
    }

    private String readRequiredLine(String label) throws IOException {
        while (true) {
            out.print(label);
            String value = safe(reader.readLine()).trim();
            if (!value.isEmpty()) {
                return value;
            }
            out.println("反馈不能为空。");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
