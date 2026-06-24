package com.codey.infra;

/**
 * 模型网关。
 */
public interface ModelGateway {
    ModelResponse chat(ModelRequest request);
}
