package com.codey.console.cli;

import com.codey.client.AgentClient;
import com.codey.console.chat.ChatSessionState;
import com.codey.console.common.ConsoleIo;
import com.codey.console.common.InteractiveChatConsole;
import com.codey.console.common.StatefulChatTaskExecutor;
import com.codey.session.SessionStore;

import java.nio.file.Path;
import java.util.List;

/**
 * 根据已装配的运行时依赖创建交互式聊天控制台。
 */
public class CliInteractiveChatFactory {

    public  InteractiveChatConsole create(AgentClient agentClient,
                                  SessionStore sessionStore,
                                  Path workspaceRoot,
                                  String skillName,
                                  List<String> contextFiles,
                                  List<String> contextNotes,
                                  List<String> identities) {
        ChatSessionState state = new ChatSessionState(skillName, identities);
        // 交互式会话默认只暴露工作区内的相对根目录，不回显宿主机绝对路径。
        state.setWorkingDirectory(".");
        appendContextFiles(state, contextFiles);
        appendContextNotes(state, contextNotes);
        return new InteractiveChatConsole(
                ConsoleIo.reader(),
                ConsoleIo.out(),
                ConsoleIo.err(),
                new StatefulChatTaskExecutor(agentClient),
                state,
                sessionStore
        );
    }

    private void appendContextFiles(ChatSessionState state, List<String> contextFiles) {
        if (state == null || contextFiles == null) {
            return;
        }
        for (String contextFile : contextFiles) {
            if (contextFile != null && !contextFile.trim().isEmpty()) {
                state.addContextFile(contextFile);
            }
        }
    }

    private void appendContextNotes(ChatSessionState state, List<String> contextNotes) {
        if (state == null || contextNotes == null) {
            return;
        }
        for (String contextNote : contextNotes) {
            if (contextNote != null && !contextNote.trim().isEmpty()) {
                state.addContextNote(contextNote);
            }
        }
    }
}
