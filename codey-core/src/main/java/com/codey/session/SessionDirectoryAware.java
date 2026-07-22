package com.codey.session;

import java.nio.file.Path;

/**
 * 暴露会话根目录，供 core 生命周期管理器统一定位元数据与归档目录。
 */
public interface SessionDirectoryAware {
    Path getSessionDirectory();
}
