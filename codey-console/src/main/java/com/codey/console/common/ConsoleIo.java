package com.codey.console.common;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 统一控制台输入输出编码，尽量避免 Windows 终端出现中文乱码。
 */
public final class ConsoleIo {
    private static final PrintStream UTF8_OUT = createPrintStream(FileDescriptor.out);
    private static final PrintStream UTF8_ERR = createPrintStream(FileDescriptor.err);

    private ConsoleIo() {
    }

    public static void installUtf8Console() {
        System.setOut(UTF8_OUT);
        System.setErr(UTF8_ERR);
    }

    public static PrintStream out() {
        return UTF8_OUT;
    }

    public static PrintStream err() {
        return UTF8_ERR;
    }

    public static BufferedReader reader() {
        return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    private static PrintStream createPrintStream(FileDescriptor descriptor) {
        try {
            return new PrintStream(new FileOutputStream(descriptor), true, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 UTF-8 控制台输出失败", exception);
        }
    }
}
