package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.infra.WorkspaceGateway;
import com.codey.infra.WorkspaceEntry;
import com.codey.infra.WorkspaceListRequest;
import com.codey.infra.WorkspaceListResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 单表模式上下文提供者。
 * <p>
 * 每轮对话前读取工作目录内所有文件内容，
 * 并格式化为系统提示词注入片段，保证 AI 始终拿到最新文件内容。
 * </p>
 */
final class SingleFileContextProvider {
    /** 单表模式下单个文件最大读取字符数（避免巨型文件撑爆上下文） */
    private static final int MAX_FILE_CHARS = 500_000;
    /** 单表模式下最多读取文件数 */
    private static final int MAX_FILE_COUNT = 50;

    private final WorkspaceGateway workspaceGateway;

    SingleFileContextProvider(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    /**
     * 刷新工作目录内所有文件快照并存入会话。
     *
     * @param session 当前会话（需已设置 workingDirectory 和 singleFileMode）
     */
    void refreshFileSnapshot(AgentSession session) {
        if (session == null || !session.isSingleFileMode()) {
            return;
        }
        String workingDir = session.getWorkingDirectory();
        if (isBlank(workingDir)) {
            return;
        }
        String content = buildFileContentSnapshot(workingDir);
        session.setSingleFileContentSnapshot(content);
    }

    /**
     * 获取格式化后的文件内容快照（用于注入系统提示词）。
     */
    String getFormattedSnapshot(AgentSession session) {
        if (session == null || !session.isSingleFileMode()) {
            return "";
        }
        String snapshot = session.getSingleFileContentSnapshot();
        if (isBlank(snapshot)) {
            return "";
        }
        return "## 单表模式：工作目录文件内容（每轮自动刷新）\n\n"
                + "以下为当前工作目录内所有文件的最新内容，"
                + "你不需要调用 read_file 或 search_content 工具来获取文件内容，"
                + "直接引用即可。如果文件内容发生变化（如你写入了新内容），"
                + "下一轮对话会自动刷新为最新内容。\n\n"
                + snapshot;
    }

    private String buildFileContentSnapshot(String workingDir) {
        // 列出工作目录内所有文件
        WorkspaceListRequest listRequest = new WorkspaceListRequest();
        listRequest.setPathHint(workingDir);
        listRequest.setMaxDepth(5); // 最多5层目录
        listRequest.setLimit(MAX_FILE_COUNT + 20); // 多取一些用于过滤目录
        listRequest.setIncludeHidden(false);

        WorkspaceListResult listResult;
        try {
            listResult = workspaceGateway.listWorkspaceResult(listRequest);
        } catch (RuntimeException exception) {
            return "<!-- 读取工作目录失败: " + escapeXml(exception.getMessage()) + " -->";
        }

        if (listResult == null || listResult.getEntries() == null || listResult.getEntries().isEmpty()) {
            return "<!-- 工作目录为空 -->";
        }

        // 过滤出文件（非目录），限制数量
        List<WorkspaceEntry> files = new ArrayList<WorkspaceEntry>();
        for (WorkspaceEntry entry : listResult.getEntries()) {
            if (entry == null || entry.isDirectory()) {
                continue;
            }
            if (files.size() >= MAX_FILE_COUNT) {
                break;
            }
            files.add(entry);
        }

        // 读取每个文件内容并格式化
        StringBuilder builder = new StringBuilder();
        for (WorkspaceEntry file : files) {
            String relativePath = file.getPath();
            if (isBlank(relativePath)) {
                continue;
            }
            // listWorkspace 返回的路径是相对于 pathHint 的，
            // 需要拼上 workingDir 前缀才能从 workspaceRoot 正确解析
            String fullPath = resolveFullPath(workingDir, relativePath);
            String content;
            try {
                content = workspaceGateway.readFile(fullPath);
            } catch (RuntimeException exception) {
                builder.append("### ").append(relativePath).append("\n\n")
                        .append("```\n<!-- 读取失败: ").append(escapeXml(exception.getMessage())).append(" -->\n```\n\n");
                continue;
            }

            // 截断过长文件
            boolean truncated = false;
            if (content != null && content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS);
                truncated = true;
            }
            if (content == null) {
                content = "";
            }

            builder.append("### ").append(relativePath).append("\n\n");
            // 用代码块包裹文件内容，带语言标记
            String langHint = inferLanguage(relativePath);
            builder.append("```").append(langHint).append("\n");
            builder.append(content);
            if (truncated) {
                builder.append("\n<!-- 文件过长，已截断至 ").append(MAX_FILE_CHARS).append(" 字符 -->");
            }
            builder.append("\n```\n\n");
        }
        return builder.toString().trim();
    }

    /**
     * 根据文件扩展名推断代码语言标记。
     */
    private String inferLanguage(String path) {
        if (path == null) {
            return "";
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".vue")) return "html";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".tsx")) return "tsx";
        if (lower.endsWith(".jsx")) return "jsx";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".scss")) return "scss";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "bash";
        if (lower.endsWith(".properties") || lower.endsWith(".ini") || lower.endsWith(".cfg") || lower.endsWith(".conf") || lower.endsWith(".env")) return "properties";
        if (lower.endsWith(".kt")) return "kotlin";
        if (lower.endsWith(".gradle")) return "groovy";
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 拼接 workingDir 和 entry 相对路径，得到从 workspaceRoot 可解析的完整路径。
     * <p>
     * listWorkspace 以 pathHint 为根列出文件，返回的 entry.path 是相对于 pathHint 的。
     * 但 WorkspaceGateway.readFile() 从 workspaceRoot 解析路径，
     * 所以需要把 workingDir 和 entry.path 拼接起来。
     * </p>
     */
    private String resolveFullPath(String workingDir, String entryPath) {
        String dir = workingDir;
        if (dir.startsWith("./")) {
            dir = dir.substring(2);
        }
        if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        if (dir.isEmpty()) {
            return entryPath;
        }
        return dir + "/" + entryPath;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
