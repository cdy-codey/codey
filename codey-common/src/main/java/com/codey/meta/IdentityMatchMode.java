package com.codey.meta;

/**
 * 会话身份与 skill/tool 元数据的匹配模式。
 */
public enum IdentityMatchMode {
    /**
     * 会话身份命中任意一个即可视为匹配。
     */
    ANY,
    /**
     * 会话身份必须覆盖元数据声明的全部身份才视为匹配。
     */
    ALL
}
