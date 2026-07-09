package com.codey.mcp;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.FileMutationSupport;
import com.codey.tools.WorkspaceToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按结构化补丁 DSL 对文件执行新增或更新操作。
 */
public class ApplyPatchTool extends AbstractWorkspaceTool {
    private static final String TOOL_NAME = "apply_structured_patch";
    private static final String BEGIN_PATCH = "*** Begin Patch";
    private static final String END_PATCH = "*** End Patch";
    private static final String ADD_FILE = "*** Add File: ";
    private static final String UPDATE_FILE = "*** Update File: ";
    private static final String END_OF_FILE = "*** End of File";
    private static final int MAX_PREVIEW_LINES = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String displayName() {
        return "结构化更新文件";
    }

    @Override
    public String description() {
        return "Apply a structured patch DSL to add or update files. Git diff and unified diff are not supported.";
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
            String patch = readRequiredString(request, "patch");
            ParsedPatch parsedPatch = parsePatch(patch);
            List<Map<String, Object>> changes = new ArrayList<Map<String, Object>>();
            for (PatchOperation operation : parsedPatch.getOperations()) {
                changes.add(applyOperation(operation, context));
            }

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("operations", changes.size());
            payload.put("changes", changes);
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Apply patch success:\n" + result, "已完成补丁更新，处理 " + changes.size() + " 个文件");
        } catch (Exception exception) {
            return ToolResult.fail("应用结构化补丁失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> applyOperation(PatchOperation operation, WorkspaceToolContext context) throws Exception {
        if (operation instanceof AddFileOperation) {
            return applyAddFile((AddFileOperation) operation, context);
        }
        if (operation instanceof UpdateFileOperation) {
            return applyUpdateFile((UpdateFileOperation) operation, context);
        }
        throw new IllegalArgumentException("unsupported patch operation");
    }

    private Map<String, Object> applyAddFile(AddFileOperation operation, WorkspaceToolContext context) throws Exception {
        Path target = context.resolvePath(operation.getPath());
        if (Files.exists(target)) {
            throw new IllegalArgumentException("target already exists for add file: " + operation.getPath());
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        String updatedContent = joinLines(operation.getLines());
        Files.write(target, updatedContent.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("path", relativize(context, target));
        payload.put("action", "ADD_FILE");
        payload.put("summary", "已根据补丁新建文件");
        payload.put("diffSummary", FileMutationSupport.buildDiffSummary("", updatedContent));
        payload.put("preview", FileMutationSupport.buildPreview("", updatedContent, MAX_PREVIEW_LINES));
        return payload;
    }

    private Map<String, Object> applyUpdateFile(UpdateFileOperation operation, WorkspaceToolContext context) throws Exception {
        Path target = context.resolvePath(operation.getPath());
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("target does not exist for update file: " + operation.getPath());
        }
        String originalContent = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        List<String> workingLines = new ArrayList<String>(FileMutationSupport.toLines(originalContent));
        int cursor = 0;
        for (PatchHunk hunk : operation.getHunks()) {
            cursor = applyHunk(workingLines, hunk, cursor);
        }
        String updatedContent = joinLines(workingLines);
        Files.write(target, updatedContent.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("path", relativize(context, target));
        payload.put("action", "UPDATE_FILE");
        payload.put("summary", "已根据补丁更新文件");
        payload.put("diffSummary", FileMutationSupport.buildDiffSummary(originalContent, updatedContent));
        payload.put("preview", FileMutationSupport.buildPreview(originalContent, updatedContent, MAX_PREVIEW_LINES));
        return payload;
    }

    private int applyHunk(List<String> workingLines, PatchHunk hunk, int cursor) {
        List<String> expected = new ArrayList<String>();
        List<String> replacement = new ArrayList<String>();
        for (HunkLine hunkLine : hunk.getLines()) {
            if (hunkLine.getKind() == ' ' || hunkLine.getKind() == '-') {
                expected.add(hunkLine.getContent());
            }
            if (hunkLine.getKind() == ' ' || hunkLine.getKind() == '+') {
                replacement.add(hunkLine.getContent());
            }
        }

        int matchIndex = findSubsequence(workingLines, expected, cursor);
        if (matchIndex < 0 && cursor > 0) {
            matchIndex = findSubsequence(workingLines, expected, 0);
        }
        if (matchIndex < 0) {
            throw new IllegalArgumentException("patch hunk did not match target file");
        }

        int removeCount = expected.size();
        for (int index = 0; index < removeCount; index++) {
            workingLines.remove(matchIndex);
        }
        workingLines.addAll(matchIndex, replacement);
        return matchIndex + replacement.size();
    }

    private int findSubsequence(List<String> lines, List<String> expected, int startIndex) {
        if (expected.isEmpty()) {
            return Math.max(0, Math.min(startIndex, lines.size()));
        }
        for (int index = Math.max(0, startIndex); index <= lines.size() - expected.size(); index++) {
            boolean matched = true;
            for (int offset = 0; offset < expected.size(); offset++) {
                if (!safeEquals(lines.get(index + offset), expected.get(offset))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return index;
            }
        }
        return -1;
    }

    private ParsedPatch parsePatch(String patch) {
        String[] lines = patch.replace("\r", "").split("\n", -1);
        String firstLine = lines.length == 0 ? "" : lines[0].trim();
        if (looksLikeGitDiff(firstLine)) {
            throw new IllegalArgumentException(TOOL_NAME
                    + " only supports the structured patch DSL, not git diff/unified diff; patch must start with "
                    + BEGIN_PATCH + " and end with " + END_PATCH);
        }
        if (lines.length < 2 || !BEGIN_PATCH.equals(lines[0].trim())) {
            throw new IllegalArgumentException("patch must start with " + BEGIN_PATCH
                    + " exactly; do not use git diff or variants like '*** Begin Patch ***'");
        }
        if (!END_PATCH.equals(lines[lines.length - 1].trim())) {
            throw new IllegalArgumentException("patch must end with " + END_PATCH);
        }

        List<PatchOperation> operations = new ArrayList<PatchOperation>();
        int index = 1;
        while (index < lines.length - 1) {
            String line = lines[index];
            if (line == null || line.trim().isEmpty()) {
                index++;
                continue;
            }
            if (line.startsWith(ADD_FILE)) {
                String path = line.substring(ADD_FILE.length()).trim();
                index++;
                List<String> addedLines = new ArrayList<String>();
                while (index < lines.length - 1 && !isOperationHeader(lines[index])) {
                    String current = lines[index];
                    if (!current.startsWith("+")) {
                        throw new IllegalArgumentException("add file lines must start with +");
                    }
                    addedLines.add(current.substring(1));
                    index++;
                }
                operations.add(new AddFileOperation(path, addedLines));
                continue;
            }
            if (line.startsWith(UPDATE_FILE)) {
                String path = line.substring(UPDATE_FILE.length()).trim();
                index++;
                List<PatchHunk> hunks = new ArrayList<PatchHunk>();
                PatchHunk currentHunk = null;
                while (index < lines.length - 1 && !isOperationHeader(lines[index])) {
                    String current = lines[index];
                    if (current.startsWith("@@")) {
                        if (currentHunk != null) {
                            hunks.add(currentHunk);
                        }
                        currentHunk = new PatchHunk();
                        index++;
                        continue;
                    }
                    if (END_OF_FILE.equals(current.trim())) {
                        index++;
                        continue;
                    }
                    if (currentHunk == null) {
                        throw new IllegalArgumentException("update file must contain at least one @@ hunk");
                    }
                    if (current.isEmpty()) {
                        currentHunk.addLine(' ', "");
                        index++;
                        continue;
                    }
                    char kind = current.charAt(0);
                    if (kind != ' ' && kind != '-' && kind != '+') {
                        throw new IllegalArgumentException("invalid hunk line: " + current);
                    }
                    currentHunk.addLine(kind, current.substring(1));
                    index++;
                }
                if (currentHunk != null) {
                    hunks.add(currentHunk);
                }
                if (hunks.isEmpty()) {
                    throw new IllegalArgumentException("update file requires at least one hunk");
                }
                operations.add(new UpdateFileOperation(path, hunks));
                continue;
            }
            throw new IllegalArgumentException("unsupported patch header: " + line);
        }

        if (operations.isEmpty()) {
            throw new IllegalArgumentException("patch does not contain any file operations");
        }
        return new ParsedPatch(operations);
    }

    private boolean isOperationHeader(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        return END_PATCH.equals(trimmed)
                || line.startsWith(ADD_FILE)
                || line.startsWith(UPDATE_FILE);
    }

    private boolean looksLikeGitDiff(String firstLine) {
        if (firstLine == null) {
            return false;
        }
        return firstLine.startsWith("diff --git")
                || firstLine.startsWith("--- ")
                || firstLine.startsWith("+++ ")
                || firstLine.startsWith("Index: ");
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private String relativize(WorkspaceToolContext context, Path target) {
        if (context == null) {
            return target == null ? "." : target.toString();
        }
        return context.relativize(target);
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.standard();
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("patch", stringProperty("Structured patch DSL content. File paths inside the patch must be relative to the current working directory. It must start with *** Begin Patch and end with *** End Patch. Only *** Add File, *** Update File and @@ syntax are supported."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("patch"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readRequiredString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return String.valueOf(value);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static final class ParsedPatch {
        private final List<PatchOperation> operations;

        private ParsedPatch(List<PatchOperation> operations) {
            this.operations = operations;
        }

        private List<PatchOperation> getOperations() {
            return operations;
        }
    }

    private abstract static class PatchOperation {
        private final String path;

        private PatchOperation(String path) {
            this.path = path;
        }

        protected String getPath() {
            return path;
        }
    }

    private static final class AddFileOperation extends PatchOperation {
        private final List<String> lines;

        private AddFileOperation(String path, List<String> lines) {
            super(path);
            this.lines = lines;
        }

        private List<String> getLines() {
            return lines;
        }
    }

    private static final class UpdateFileOperation extends PatchOperation {
        private final List<PatchHunk> hunks;

        private UpdateFileOperation(String path, List<PatchHunk> hunks) {
            super(path);
            this.hunks = hunks;
        }

        private List<PatchHunk> getHunks() {
            return hunks;
        }
    }

    private static final class PatchHunk {
        private final List<HunkLine> lines = new ArrayList<HunkLine>();

        private void addLine(char kind, String content) {
            lines.add(new HunkLine(kind, content));
        }

        private List<HunkLine> getLines() {
            return lines;
        }
    }

    private static final class HunkLine {
        private final char kind;
        private final String content;

        private HunkLine(char kind, String content) {
            this.kind = kind;
            this.content = content;
        }

        private char getKind() {
            return kind;
        }

        private String getContent() {
            return content;
        }
    }
}
