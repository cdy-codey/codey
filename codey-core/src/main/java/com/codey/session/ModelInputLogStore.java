package com.codey.session;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventType;

import java.nio.file.Path;

/**
 * 单独记录发给模型的输入内容，避免和原会话日志混在一起。
 */
public class ModelInputLogStore extends AbstractStructuredEventStore {
    public ModelInputLogStore(Path rootDir) {
        super(rootDir);
    }

    @Override
    public void appendEvent(SessionEvent event) {
        if (event == null || event.getType() != SessionEventType.MODEL_INPUT) {
            return;
        }
        writeEvent(event.getSessionId(), event.getType().getCode(), event.getPayload());
    }
}
