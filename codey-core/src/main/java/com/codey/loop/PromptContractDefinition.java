package com.codey.loop;

import java.util.Arrays;
import java.util.List;

/**
 * 提示词契约定义。
 * 第一版先约束最关键的段落，后续可按技能扩展。
 */
public class PromptContractDefinition {
    private final List<String> requiredSections = Arrays.asList(
            "## Language",
            "## Preamble Rhythm",
            "## Toolbox",
            "## Runtime Guardrails"
    );

    private final int contextWindowChars = 32000;
    private final int reservedOutputChars = 4000;
    private final int headroomChars = 2000;

    public List<String> getRequiredSections() {
        return requiredSections;
    }

    public int getMaxPromptLength() {
        return contextWindowChars;
    }

    public int getContextWindowChars() {
        return contextWindowChars;
    }

    public int getReservedOutputChars() {
        return reservedOutputChars;
    }

    public int getHeadroomChars() {
        return headroomChars;
    }

    public int getInputBudgetChars() {
        return contextWindowChars - reservedOutputChars - headroomChars;
    }
}
