package com.codey.loop;

import com.codey.infra.ModelMessage;

import java.util.List;

/**
 * 单个消息构建阶段接口，采用过滤器/管道风格逐段追加消息。
 */
interface PromptMessageStage {
    void apply(List<ModelMessage> messages, PromptMessageBuildContext context);
}
