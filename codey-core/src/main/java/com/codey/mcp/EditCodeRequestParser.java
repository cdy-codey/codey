package com.codey.mcp;

import com.codey.tools.*;

import java.util.Map;

/**
 * 将 MCP 工具调用参数解析为 `EditCodeRequest`。
 */
public class EditCodeRequestParser {

    public EditCodeRequest parse(ToolInvocation request) {
        Map<String, Object> arguments = request.getArguments();
        EditCodeRequest editRequest = new EditCodeRequest();
        editRequest.setFile(asString(arguments.get("file")));
        editRequest.setInstruction(asString(arguments.get("instruction")));
        editRequest.setTargetMarker(asString(arguments.get("targetMarker")));
        editRequest.setStartMarker(asString(arguments.get("startMarker")));
        editRequest.setEndMarker(asString(arguments.get("endMarker")));
        editRequest.setSearchText(asString(arguments.get("searchText")));
        editRequest.setReplaceText(asString(arguments.get("replaceText")));
        editRequest.setGeneratedContent(asString(arguments.get("generatedContent")));

        String mode = asString(arguments.get("mode"));
        if (mode != null && !mode.trim().isEmpty()) {
            editRequest.setMode(EditMode.valueOf(mode.trim().toUpperCase()));
        }
        return editRequest;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}


