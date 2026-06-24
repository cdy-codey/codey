package com.codey.loop;

import com.codey.config.AgentSession;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 汇总当前会话的稳定事实，帮助模型在重规划时优先复用已有上下文。
 */
public class CanonicalStateBuilder {
    private static final int CONCRETE_EVIDENCE_THRESHOLD = 2;
    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"");

    public List<String> build(AgentSession session) {
        List<String> lines = new ArrayList<String>();
        if (session == null) {
            return lines;
        }

        // 这里聚合的是“稳定且高价值”的状态：避免与 prompt 里其他区块（tool results / system feedback 等）重复。
        // 否则会出现同一信息在 context_snapshot 与 recent_* 里重复回灌，增加 token 压力。
        if (!session.getContextFiles().isEmpty()) {
            lines.add("相关文件: " + join(session.getContextFiles()));
        }

        if (session.getConsecutiveToolFailureCount() > 0) {
            lines.add("连续工具失败次数: " + session.getConsecutiveToolFailureCount());
        }
        String concreteFile = detectConcreteTargetFile(session);
        if (!isBlank(concreteFile)) {
            lines.add("已定位文件: " + concreteFile);
        }
        if (hasConcreteCodeEvidence(session)) {
            lines.add("收敛信号: 已拿到具体代码片段，可直接修改或只补一个关键问题");
        }

        String nextAction = suggestNextAction(session);
        if (!isBlank(nextAction)) {
            lines.add("建议动作: " + nextAction);
        }
        return lines;
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private String suggestNextAction(AgentSession session) {
        if (session.isReplanMode() || session.getConsecutiveToolFailureCount() > 0) {
            return "先基于上面的失败原因重新规划，优先复用已确认事实，不要重复相同工具请求。";
        }
        if (isStructureOverviewGoal(session) && hasStructureOverviewResults(session)) {
            return "用户当前主要要项目/目录概览；如果 project_map 或 list_workspace 已经给出整体结构，就直接总结并 FINISH，除非用户明确要求继续查看具体文件。";
        }
        if (hasConcreteCodeEvidence(session)) {
            return "已经定位到具体文件和代码片段；下一步直接用 edit_file、apply_structured_patch、write_file 或 edit_code 做最小修复，若仍缺唯一关键判断，只问用户一个精准问题。";
        }
        if (!session.getEditResults().isEmpty()) {
            return "优先基于最近编辑结果判断是否已经满足目标并可以 FINISH，不要为了确认结果重复读取同一文件。";
        }
        if (!session.getToolResults().isEmpty()) {
            return "优先基于已确认上下文判断是调用 edit_code 还是直接输出 FINISH。";
        }
        return "";
    }

    private boolean isStructureOverviewGoal(AgentSession session) {
        if (session == null || isBlank(session.getUserGoal())) {
            return false;
        }
        String normalized = session.getUserGoal().replace(" ", "").toLowerCase();
        return containsAny(normalized,
                "项目结构", "目录结构", "梳理结构", "整体结构", "项目概览", "目录概览", "项目骨架", "workspace", "projectmap");
    }

    private boolean hasStructureOverviewResults(AgentSession session) {
        if (session == null || session.getToolResults().isEmpty()) {
            return false;
        }
        for (String toolResult : session.getToolResults()) {
            if (isBlank(toolResult)) {
                continue;
            }
            if (toolResult.startsWith("Project map result:")
                    || toolResult.startsWith("Workspace list result:")
                    || toolResult.startsWith("List workspace success:")
                    || toolResult.contains("\"summary\"")
                    || toolResult.contains("\"entries\"")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcreteCodeEvidence(AgentSession session) {
        if (session == null || session.getToolResults().isEmpty()) {
            return false;
        }
        int evidenceCount = 0;
        for (String toolResult : session.getToolResults()) {
            if (isBlank(toolResult)) {
                continue;
            }
            if (toolResult.startsWith("Read file success:")
                    || toolResult.startsWith("Search code result:")
                    || toolResult.startsWith("Search code success:")) {
                evidenceCount++;
            }
            if (toolResult.contains("\"matchedLine\"")
                    || toolResult.contains("\"startLine\"")
                    || toolResult.contains("\"content\"")) {
                evidenceCount++;
            }
            if (evidenceCount >= CONCRETE_EVIDENCE_THRESHOLD) {
                return true;
            }
        }
        return !isBlank(detectConcreteTargetFile(session)) && isFixOrEditGoal(session);
    }

    private String detectConcreteTargetFile(AgentSession session) {
        if (session == null) {
            return "";
        }
        if (!isBlank(session.getTargetPagePath())) {
            return session.getTargetPagePath();
        }
        if (!session.getContextFiles().isEmpty()) {
            return session.getContextFiles().get(session.getContextFiles().size() - 1);
        }
        for (int index = session.getToolResults().size() - 1; index >= 0; index--) {
            String path = extractPath(session.getToolResults().get(index));
            if (!isBlank(path)) {
                return path;
            }
        }
        return "";
    }

    private String extractPath(String toolResult) {
        if (isBlank(toolResult)) {
            return "";
        }
        Matcher matcher = JSON_PATH_PATTERN.matcher(toolResult);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String[] lines = toolResult.replace("\r", "").split("\n");
        for (String line : lines) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.startsWith("Read file success:")) {
                return normalized.substring("Read file success:".length()).trim();
            }
        }
        return "";
    }

    private boolean isFixOrEditGoal(AgentSession session) {
        if (session == null || isBlank(session.getUserGoal())) {
            return false;
        }
        String normalized = session.getUserGoal().replace(" ", "");
        return containsAny(normalized, "修复", "修改", "调整", "优化", "bug", "问题");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
