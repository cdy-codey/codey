package com.codey.console;

import com.codey.console.command.RunCommand;
import com.codey.console.common.ConsoleIo;
import picocli.CommandLine;

/**
 * 命令行应用主入口。
 */
public final class ConsoleApplication {
    private ConsoleApplication() {
    }

    public static void main(String[] args) {
        ConsoleIo.installUtf8Console();
        int exitCode = new CommandLine(new RunCommand()).execute(args);
        System.exit(exitCode);
    }
}
