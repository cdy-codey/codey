package com.codey.session;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventType;

import java.nio.file.Path;

/**
 * 单独记录模型原始输出内容，便于和输入日志分开分析。
 */
public class ModelOutputLogStore extends AbstractStructuredEventStore {
    public ModelOutputLogStore(Path rootDir) {
        super(rootDir);
    }

    @Override
    public void appendEvent(SessionEvent event) {
        if (event == null || event.getType() != SessionEventType.MODEL_OUTPUT) {
            return;
        }
        writeEvent(event.getSessionId(), event.getType().getCode(), event.getPayload());
    }
}
