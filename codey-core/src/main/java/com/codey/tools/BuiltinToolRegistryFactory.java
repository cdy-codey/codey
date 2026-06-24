package com.codey.tools;

import com.codey.infra.LocalWorkspaceGateway;
import com.codey.mcp.AppendInstructionEditStrategy;
import com.codey.mcp.EditCodeTool;
import com.codey.mcp.ListWorkspaceTool;
import com.codey.mcp.ProjectMapTool;
import com.codey.mcp.QueryApiInfoTool;
import com.codey.mcp.ReadApiSpecTool;
import com.codey.mcp.ReadFileTool;
import com.codey.mcp.SearchCodeTool;
import com.codey.tool.ToolSpec;

import java.nio.file.Paths;
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
        toolRegistry.registerBuiltin(new QueryApiInfoTool(workspaceGateway));
        toolRegistry.registerBuiltin(new ReadApiSpecTool(workspaceGateway));
        toolRegistry.registerBuiltin(new SearchCodeTool(workspaceGateway));
        toolRegistry.registerBuiltin(new WriteFileTool());
        toolRegistry.registerBuiltin(new EditFileTool());
        toolRegistry.registerBuiltin(new ApplyPatchTool());
        toolRegistry.registerBuiltin(new DeleteFileTool());
        toolRegistry.registerBuiltin(new EditCodeTool(
                workspaceGateway,
                new AppendInstructionEditStrategy(),
                Paths.get("sessions", "backups").toString()
        ));
        if (extensionTools != null) {
            for (ToolSpec extensionTool : extensionTools) {
                toolRegistry.registerExtension(extensionTool);
            }
        }
        return toolRegistry;
    }
}
