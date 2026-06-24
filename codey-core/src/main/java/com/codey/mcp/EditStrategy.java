package com.codey.mcp;

import com.codey.tools.*;

/**
 * 定义如何把一次编辑请求应用到原始文件内容上。
 */
public interface EditStrategy {
    EditStrategyResult apply(String originalContent, EditCodeRequest request);
}

