package com.codey.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codey.infra.ModelToolDefinition;
import com.codey.infra.WorkspaceGateway;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.FileMutationSupport;
import com.codey.tools.WorkspaceToolContext;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在工作区内执行结构化代码编辑，并返回备份、摘要和预览信息。
 */
public class EditCodeTool extends AbstractWorkspaceTool {
    private static final int MAX_PREVIEW_LINES = 12;

    private final WorkspaceGateway workspaceGateway;
    private final EditStrategy editStrategy;
    private final String backupDirectory;
    private final EditCodeRequestParser requestParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EditCodeTool(WorkspaceGateway workspaceGateway, EditStrategy editStrategy, String backupDirectory) {
        this.workspaceGateway = workspaceGateway;
        this.editStrategy = editStrategy;
        this.backupDirectory = backupDirectory;
        this.requestParser = new EditCodeRequestParser();
    }

    @Override
    public String name() {
        return "edit_code";
    }

    @Override
    public String displayName() {
        return "编辑代码";
    }

    @Override
    public String description() {
        return "Edit code files in the workspace, supporting precise replacement, marker insertion and full file replacement.";
    }

    @Override
    public ModelToolDefinition toModelToolDefinition() {
        ModelToolDefinition definition = new ModelToolDefinition();
        definition.setName(name());
        definition.setDescription(description());
        definition.setParameters(buildParameters());
        return definition;
    }

    @Override
    public ToolResult execute(ToolInvocation request, WorkspaceToolContext context) {
        try {
            EditCodeRequest editRequest = requestParser.parse(request);
            validate(editRequest);

            String original = loadOriginalContent(editRequest);
            String backupPath = backupOriginalIfPresent(editRequest);

            EditStrategyResult strategyResult = editStrategy.apply(original, editRequest);
            String target = workspaceGateway.writeFile(editRequest.getFile(), strategyResult.getUpdatedContent());
            return ToolResult.ok("Edit code success:\n" + buildStructuredResult(
                    editRequest,
                    target,
                    backupPath,
                    original,
                    strategyResult
            ), "已完成代码更新");
        } catch (Exception exception) {
            return ToolResult.fail("编辑代码失败：" + exception.getMessage());
        }
    }

    private void validate(EditCodeRequest request) {
        if (!FileMutationSupport.containsText(request.getFile())) {
            throw new IllegalArgumentException("edit_code file is required");
        }
        if (request.getMode() == EditMode.REPLACE_TEXT && !FileMutationSupport.containsText(request.getSearchText())) {
            throw new IllegalArgumentException("edit_code REPLACE_TEXT requires searchText");
        }
        if (!FileMutationSupport.containsText(request.getGeneratedContent())
                && !FileMutationSupport.containsText(request.getInstruction())
                && !FileMutationSupport.containsText(request.getReplaceText())) {
            throw new IllegalArgumentException("edit_code requires generatedContent or instruction");
        }
    }

    private String loadOriginalContent(EditCodeRequest request) {
        try {
            return workspaceGateway.readFile(request.getFile());
        } catch (RuntimeException exception) {
            // `REPLACE_FILE` 允许直接创建原本不存在的目标文件。
            if (request.getMode() == EditMode.REPLACE_FILE) {
                return "";
            }
            throw exception;
        }
    }

    private String backupOriginalIfPresent(EditCodeRequest request) {
        try {
            String backupPath = buildBackupPath(request.getFile());
            workspaceGateway.copyFile(request.getFile(), backupPath);
            return backupPath;
        } catch (RuntimeException exception) {
            if (request.getMode() == EditMode.REPLACE_FILE) {
                return null;
            }
            throw exception;
        }
    }

    private String buildBackupPath(String file) {
        String normalized = file.replace("\\", "/");
        String name = normalized.replace(":", "_").replace("/", "__");
        return backupDirectory + File.separator + name + ".bak";
    }

    private String buildStructuredResult(EditCodeRequest request,
                                         String target,
                                         String backupPath,
                                         String originalContent,
                                         EditStrategyResult strategyResult) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("file", target);
        payload.put("backupFile", backupPath == null ? "(new file)" : backupPath);
        payload.put("mode", request.getMode() == null ? EditMode.APPEND.name() : request.getMode().name());
        payload.put("changed", strategyResult.isChanged());
        payload.put("summary", strategyResult.getSummary());
        payload.put("diffSummary", strategyResult.getDiffSummary());
        payload.put("originalLength", originalContent == null ? 0 : originalContent.length());
        payload.put("updatedLength", strategyResult.getUpdatedContent() == null ? 0 : strategyResult.getUpdatedContent().length());
        payload.put("preview", FileMutationSupport.buildPreview(originalContent, strategyResult.getUpdatedContent(), MAX_PREVIEW_LINES));
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("file", stringProperty("Target file path to edit."));
        properties.put("mode", enumProperty("Edit mode.", Arrays.asList(
                "APPEND", "INSERT_BEFORE", "INSERT_AFTER", "REPLACE_TEXT", "REPLACE_BETWEEN_MARKERS", "REPLACE_FILE"
        )));
        properties.put("instruction", stringProperty("Description of the intended edit."));
        properties.put("targetMarker", stringProperty("Marker used by INSERT_BEFORE or INSERT_AFTER."));
        properties.put("startMarker", stringProperty("Start marker for REPLACE_BETWEEN_MARKERS."));
        properties.put("endMarker", stringProperty("End marker for REPLACE_BETWEEN_MARKERS."));
        properties.put("searchText", stringProperty("Original text to find for REPLACE_TEXT."));
        properties.put("replaceText", stringProperty("Replacement text for REPLACE_TEXT."));
        properties.put("generatedContent", stringProperty("Full content or code snippet to write."));
        properties.put("requiresHumanConfirmation", booleanProperty("Set true when the edit target is uncertain."));
        properties.put("uncertaintyReason", stringProperty("Explain why human confirmation is required."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("file"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> enumProperty(String description, List<String> values) {
        Map<String, Object> property = stringProperty(description);
        property.put("enum", values);
        return property;
    }
}


