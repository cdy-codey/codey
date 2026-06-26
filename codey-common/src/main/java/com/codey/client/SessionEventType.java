package com.codey.client;

/**
 * 统一会话事件类型。
 */
public enum SessionEventType {
    MODEL_INPUT("model_input"),
    MODEL_OUTPUT("model_output"),
    MODEL_TEXT_DELTA("model_text_delta"),
    MODEL_THINKING_DELTA("model_thinking_delta"),
    MODEL_TOOL_CALL_STARTED("model_tool_call_started"),
    TOOL_EXECUTION_STARTED("tool_execution_started"),
    TOOL_CALL("tool_call"),
    HUMAN_DECISION("human_decision"),
    VERIFICATION("verification"),
    TASK_STATUS("task_status"),
    SECURITY_EVENT("security_event"),
    FINAL_SUMMARY("final_summary"),
    DEBUG_TRACE("debug_trace");

    private final String code;

    SessionEventType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
