package com.codey.loop;

/**
 * 循环守卫对当前工具请求的决策。
 */
public class LoopGuardDecision {
    public enum Action {
        ALLOW,
        SKIP,
        REPLAN
    }

    private final Action action;
    private final String message;
    private final String requestSignature;

    private LoopGuardDecision(Action action, String message, String requestSignature) {
        this.action = action;
        this.message = message;
        this.requestSignature = requestSignature;
    }

    public static LoopGuardDecision allow(String requestSignature) {
        return new LoopGuardDecision(Action.ALLOW, "", requestSignature);
    }

    public static LoopGuardDecision skip(String message, String requestSignature) {
        return new LoopGuardDecision(Action.SKIP, message, requestSignature);
    }

    public static LoopGuardDecision replan(String message, String requestSignature) {
        return new LoopGuardDecision(Action.REPLAN, message, requestSignature);
    }

    public Action getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestSignature() {
        return requestSignature;
    }
}
