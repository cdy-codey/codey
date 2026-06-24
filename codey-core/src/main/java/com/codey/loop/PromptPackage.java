package com.codey.loop;

import com.codey.infra.ModelMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮模型输入的结构化封装。
 */
public class PromptPackage {
    private List<ModelMessage> messages = new ArrayList<ModelMessage>();

    public List<ModelMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ModelMessage> messages) {
        this.messages = messages == null ? new ArrayList<ModelMessage>() : new ArrayList<ModelMessage>(messages);
    }
}
