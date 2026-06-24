package com.codey.verify;

import com.codey.infra.WorkspaceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统一管理 verifier 装配，避免把专用业务校验误接入通用工作区代理。
 */
public class VerifierFactory {

    /**
     * 通用工作区代理默认不装业务规则，后续只由专用 skill 显式启用。
     */
    public Verifier createWorkspaceVerifier() {
        return new NoOpVerifier();
    }

    /**
     * 页面/API 等专用场景需要时，再显式装配专用 verifier。
     */
    public Verifier createPageTaskVerifier(WorkspaceGateway workspaceGateway, ObjectMapper objectMapper) {
        return new BasicVerifier(workspaceGateway, objectMapper);
    }
}
