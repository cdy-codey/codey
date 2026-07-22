package com.codey.tools;

import com.codey.infra.WorkspacePathSupport;
import com.codey.infra.WorkspaceGateway;
import com.codey.tool.ToolContext;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内核内部使用的工作区工具上下文。
 * 该类型承载路径解析与工作区边界能力，不属于对外 common。
 */
public class WorkspaceToolContext implements ToolContext {
    private static final String ATTR_TENANT_ID = "tenantId";

    private final Path workspaceRoot;
    private final String workingDirectory;
    private final WorkspaceGateway workspaceGateway;
    private final String requestId;
    private final String sessionId;
    private final Map<String, Object> attributes;

    public WorkspaceToolContext(Path workspaceRoot, WorkspaceGateway workspaceGateway) {
        this(workspaceRoot, null, workspaceGateway, null, null, Collections.<String, Object>emptyMap());
    }

    public WorkspaceToolContext(Path workspaceRoot,
                                String workingDirectory,
                                WorkspaceGateway workspaceGateway,
                                String requestId,
                                String sessionId,
                                Map<String, Object> attributes) {
        this.workspaceRoot = workspaceRoot;
        this.workingDirectory = workingDirectory;
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
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public WorkspaceToolContext withWorkingDirectory(String currentWorkingDirectory) {
        return new WorkspaceToolContext(
                workspaceRoot,
                currentWorkingDirectory,
                workspaceGateway,
                requestId,
                sessionId,
                attributes
        );
    }

    /**
     * 注入租户 ID 到工具上下文，供调用层工具按租户维度做业务操作。
     */
    public WorkspaceToolContext withTenantId(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<String, Object>(attributes);
        merged.put(ATTR_TENANT_ID, tenantId);
        return new WorkspaceToolContext(
                workspaceRoot,
                workingDirectory,
                workspaceGateway,
                requestId,
                sessionId,
                Collections.unmodifiableMap(merged)
        );
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

    @Override
    public String getTenantId() {
        Object value = getAttribute(ATTR_TENANT_ID);
        return value == null ? null : String.valueOf(value);
    }

    public Path resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (workspaceRoot == null) {
            throw new IllegalStateException("workspace root is required for path resolution");
        }
        return WorkspacePathSupport.resolveToolPath(workspaceRoot, workingDirectory, path);
    }

    /**
     * 对外回显路径时统一裁剪成工作区根目录下的相对路径。
     */
    public String relativize(Path path) {
        if (workspaceRoot == null) {
            return path == null ? "." : path.toString();
        }
        return WorkspacePathSupport.relativizeToolPath(workspaceRoot, workingDirectory, path);
    }
}
