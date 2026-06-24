package com.codey.mcp;

import com.codey.tools.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认编辑策略，按模式执行替换、插入或追加，找不到精确位置时回退为文件尾追加。
 */
public class AppendInstructionEditStrategy implements EditStrategy {

    @Override
    public EditStrategyResult apply(String originalContent, EditCodeRequest request) {
        String generatedContent = resolveGeneratedContent(request);
        EditMode mode = request.getMode() == null ? EditMode.APPEND : request.getMode();

        if (FileMutationSupport.containsText(generatedContent) && originalContent.contains(generatedContent)) {
            return new EditStrategyResult(
                    originalContent,
                    "Skipped because generated content already exists",
                    false,
                    "No diff because generated content already exists"
            );
        }

        if (mode == EditMode.REPLACE_FILE) {
            return new EditStrategyResult(
                    generatedContent,
                    "Replaced full file content",
                    true,
                    FileMutationSupport.buildDiffSummary(originalContent, generatedContent)
            );
        }

        if (mode == EditMode.REPLACE_TEXT && FileMutationSupport.containsText(request.getSearchText())) {
            String replacement = FileMutationSupport.containsText(request.getReplaceText())
                    ? request.getReplaceText()
                    : generatedContent;
            FileMutationSupport.ReplaceResult replaceAttempt = FileMutationSupport.replaceWithFallbacks(
                    originalContent,
                    request.getSearchText(),
                    replacement
            );
            if (!replaceAttempt.isMatched()) {
                throw new IllegalArgumentException("searchText was not found in target file");
            }
            return new EditStrategyResult(
                    replaceAttempt.getUpdatedContent(),
                    replaceAttempt.getSummary(),
                    true,
                    FileMutationSupport.buildDiffSummary(originalContent, replaceAttempt.getUpdatedContent())
            );
        }

        if (mode == EditMode.INSERT_BEFORE && FileMutationSupport.containsText(request.getTargetMarker())
                && originalContent.contains(request.getTargetMarker())) {
            String updated = originalContent.replace(request.getTargetMarker(),
                    generatedContent + System.lineSeparator() + request.getTargetMarker());
            return new EditStrategyResult(
                    updated,
                    "Inserted generated content before target marker",
                    true,
                    FileMutationSupport.buildDiffSummary(originalContent, updated)
            );
        }

        if (mode == EditMode.INSERT_AFTER && FileMutationSupport.containsText(request.getTargetMarker())
                && originalContent.contains(request.getTargetMarker())) {
            String updated = originalContent.replace(request.getTargetMarker(),
                    request.getTargetMarker() + System.lineSeparator() + generatedContent);
            return new EditStrategyResult(
                    updated,
                    "Inserted generated content after target marker",
                    true,
                    FileMutationSupport.buildDiffSummary(originalContent, updated)
            );
        }

        if (mode == EditMode.REPLACE_BETWEEN_MARKERS
                && FileMutationSupport.containsText(request.getStartMarker())
                && FileMutationSupport.containsText(request.getEndMarker())) {
            int start = originalContent.indexOf(request.getStartMarker());
            int end = originalContent.indexOf(request.getEndMarker());
            if (start >= 0 && end > start) {
                int replaceStart = start + request.getStartMarker().length();
                String updated = originalContent.substring(0, replaceStart)
                        + System.lineSeparator()
                        + generatedContent
                        + System.lineSeparator()
                        + originalContent.substring(end);
                return new EditStrategyResult(
                        updated,
                        "Replaced content between markers",
                        true,
                        FileMutationSupport.buildDiffSummary(originalContent, updated)
                );
            }
        }

        String updated = originalContent
                + System.lineSeparator()
                + System.lineSeparator()
                + generatedContent;
        return new EditStrategyResult(
                updated,
                "Appended generated content at file end",
                true,
                FileMutationSupport.buildDiffSummary(originalContent, updated)
        );
    }

    private String resolveGeneratedContent(EditCodeRequest request) {
        if (FileMutationSupport.containsText(request.getGeneratedContent())) {
            return request.getGeneratedContent();
        }
        return "// codey edit placeholder" + System.lineSeparator()
                + "// instruction: " + request.getInstruction();
    }
}

