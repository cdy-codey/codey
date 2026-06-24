package com.codey.console.cli;

import com.codey.infra.LocalWorkspaceGateway;
import com.codey.tool.ToolSpec;
import com.codey.tools.BuiltinToolRegistryFactory;
import com.codey.tools.ToolRegistry;
import java.util.Collections;

/**
 * 统一创建 console 使用的工具注册表，避免 `RunCommand` 直接持有装配细节。
 */
public class CliToolRegistryFactory {
    private final BuiltinToolRegistryFactory builtinToolRegistryFactory;

    public CliToolRegistryFactory() {
        this(new BuiltinToolRegistryFactory());
    }

    CliToolRegistryFactory(BuiltinToolRegistryFactory builtinToolRegistryFactory) {
        this.builtinToolRegistryFactory = builtinToolRegistryFactory;
    }

    public ToolRegistry create(LocalWorkspaceGateway workspaceGateway) {
        return builtinToolRegistryFactory.create(workspaceGateway, Collections.<ToolSpec>emptyList());
    }
}
