package com.codey.tools;

import com.codey.infra.LocalWorkspaceGateway;
import com.codey.mcp.*;
import com.codey.tool.ToolSpec;

import java.util.List;

/**
 * 统一创建基础工具容器，并合并外部扩展工具。
 */
public class BuiltinToolRegistryFactory {

    public ToolRegistry create(LocalWorkspaceGateway workspaceGateway,
                               List<ToolSpec> extensionTools) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerBuiltin(new ListWorkspaceTool(workspaceGateway));
        toolRegistry.registerBuiltin(new ProjectMapTool(workspaceGateway));
        toolRegistry.registerBuiltin(new ReadFileTool(workspaceGateway));
        toolRegistry.registerBuiltin(new SearchContentTool(workspaceGateway));
        toolRegistry.registerBuiltin(new WriteFileTool());
        toolRegistry.registerBuiltin(new EditFileTool());
        toolRegistry.registerBuiltin(new ApplyPatchTool());
        toolRegistry.registerBuiltin(new DeleteFileTool());

        if (extensionTools != null) {
            for (ToolSpec extensionTool : extensionTools) {
                toolRegistry.registerExtension(extensionTool);
            }
        }
        return toolRegistry;
    }
}
