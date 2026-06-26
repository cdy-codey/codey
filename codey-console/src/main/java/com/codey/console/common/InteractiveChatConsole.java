package com.codey.console.common;

import com.codey.console.chat.ChatSessionState;
import com.codey.console.chat.ChatTaskExecutor;
import com.codey.session.SessionStore;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 控制台聊天壳：用户直接输入目标，按需维护工作目录和补充文件上下文。
 */
public class InteractiveChatConsole {
    private final BufferedReader reader;
    private final PrintStream out;
    private final PrintStream err;
    private final ChatTaskExecutor executor;
    private final ChatSessionState state;
    private final SessionStore sessionStore;

    public InteractiveChatConsole(BufferedReader reader,
                                  PrintStream out,
                                  PrintStream err,
                                  ChatTaskExecutor executor,
                                  ChatSessionState state,
                                  SessionStore sessionStore) {
        this.reader = reader;
        this.out = out;
        this.err = err;
        this.executor = executor;
        this.state = state;
        this.sessionStore = sessionStore;
    }

    public int run() {
        printWelcome();
        while (true) {
            try {
                out.print("你> ");
                out.flush();
                String line = reader.readLine();
                if (line == null) {
                    out.println();
                    out.println("聊天已结束。");
                    return 0;
                }
                String input = normalizeInput(line);
                if (input.isEmpty()) {
                    continue;
                }
                if (isExitCommand(input)) {
                    out.println("聊天已结束。");
                    return 0;
                }
                if (handleCommand(input)) {
                    continue;
                }
                runTask(input);
            } catch (IOException exception) {
                err.println("聊天输入失败: " + exception.getMessage());
                return 1;
            }
        }
    }

    private void printWelcome() {
        out.println("codey");
        out.println("面向较长代码会话的终端工作区。");
        out.println("直接输入任务即可；如果需要，可以先补充工作目录或相关文件。");
        out.println("命令: /help  /status  /workdir <路径>  /file <路径>  /clear  /exit");
        out.println(state.describeContext());
    }

    private boolean handleCommand(String input) {
        if ("/help".equalsIgnoreCase(input)) {
            printHelp();
            return true;
        }
        if ("/status".equalsIgnoreCase(input)) {
            out.println(state.describeContext());
            return true;
        }
        if ("/clear".equalsIgnoreCase(input)) {
            state.clearContext();
            executor.reset();
            out.println("已清空当前聊天上下文。");
            return true;
        }
        if (input.startsWith("/workdir ")) {
            String path = input.substring("/workdir ".length()).trim();
            if (isAbsolutePath(path)) {
                out.println("工作目录只允许使用相对工作区根目录的路径。");
                return true;
            }
            state.setWorkingDirectory(path);
            out.println("已设置工作目录: " + path);
            return true;
        }
        if (input.startsWith("/file ")) {
            String path = input.substring("/file ".length()).trim();
            state.addContextFile(path);
            out.println("已加入附加文件: " + path);
            return true;
        }
        return false;
    }

    private void runTask(String input) {
        state.absorbPathsFromMessage(input);
        RunRequest request = state.buildRequest(input);
        RunResult result = executor.run(request);
        if (result.isSuccess()) {
            if (sessionStore != null && sessionStore.consumeDisplayedFinalSummary(result.getSessionId())) {
                return;
            }
            out.println("助手> " + safe(result.getSummary()));
            return;
        }

        err.println("助手> 执行失败: " + safe(result.getErrorMessage()));
    }

    private void printHelp() {
        out.println("聊天命令说明:");
        out.println("- 直接输入你的需求或问题即可。");
        out.println("- 如果你知道工作目录或代码文件路径，可以直接发出来；不知道也没关系。");
        out.println("- /workdir <路径>：手动指定当前工作目录。");
        out.println("- /file <路径>：补充一个相关文件。");
        out.println("- /status：查看当前已识别到的上下文。");
        out.println("- /clear：清空当前上下文，并重新开始一段新会话。");
        out.println("- /exit：退出聊天模式。");
    }

    private boolean isExitCommand(String input) {
        return "/exit".equalsIgnoreCase(input) || "/quit".equalsIgnoreCase(input);
    }

    private String normalizeInput(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input;
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isAbsolutePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.trim();
        return normalized.matches("^[A-Za-z]:[\\\\/].*") || normalized.startsWith("/");
    }
}
