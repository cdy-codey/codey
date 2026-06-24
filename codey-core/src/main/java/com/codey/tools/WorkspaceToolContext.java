package com.codey.tools;

import com.codey.infra.WorkspaceGateway;
import com.codey.tool.ToolContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * 内核内部使用的工作区工具上下文。
 * 该类型承载路径解析与工作区边界能力，不属于对外 common。
 */
public class WorkspaceToolContext implements ToolContext {
    private final Path workspaceRoot;
    private final WorkspaceGateway workspaceGateway;
    private final String requestId;
    private final String sessionId;
    private final Map<String, Object> attributes;

    public WorkspaceToolContext(Path workspaceRoot, WorkspaceGateway workspaceGateway) {
        this(workspaceRoot, workspaceGateway, null, null, Collections.<String, Object>emptyMap());
    }

    public WorkspaceToolContext(Path workspaceRoot,
                                WorkspaceGateway workspaceGateway,
                                String requestId,
                                String sessionId,
                                Map<String, Object> attributes) {
        this.workspaceRoot = workspaceRoot;
        this.workspaceGateway = workspaceGateway;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.attributes = attributes == null ? Collections.<String, Object>emptyMap() : attributes;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public WorkspaceGateway getWorkspaceGateway() {
        return workspaceGateway;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Path resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (workspaceRoot == null) {
            return Paths.get(path.trim()).toAbsolutePath().normalize();
        }
        Path candidate = Paths.get(path.trim());
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : workspaceRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace root: " + path);
        }
        return resolved;
    }
}
