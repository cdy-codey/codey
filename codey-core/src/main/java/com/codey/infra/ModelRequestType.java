package com.codey.infra;

/**
 * 标记模型请求的运行时用途，便于日志隔离与排障。
 */
public enum ModelRequestType {
    BUSINESS,
    CONTEXT_SUMMARY
}
