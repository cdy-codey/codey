package com.codey.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 读取 classpath 下的提示词 md 资源。
 * 优先从源码目录定位资源，便于开发期直接改文件即生效；源码目录未命中时回退到 classpath 编译产物。
 */
public final class PromptResourceReader {
    private PromptResourceReader() {
    }

    /**
     * 读取提示词资源，资源缺失时抛异常，避免静默缺层导致提示词不完整。
     */
    public static String readResource(String resourcePath) {
        Path sourcePath = locateSourceResource(resourcePath);
        if (sourcePath != null) {
            try {
                return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read source prompt resource: " + sourcePath, exception);
            }
        }
        InputStream stream = PromptResourceReader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing prompt resource: " + resourcePath);
        }
        try {
            return readFully(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt resource: " + resourcePath, exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 忽略 classpath 资源关闭失败。
            }
        }
    }

    /**
     * 从当前工作目录逐级向上查找 src/main/resources 下的源文件。
     */
    private static Path locateSourceResource(String resourcePath) {
        Path relativePath = Paths.get("src", "main", "resources")
                .resolve(resourcePath.replace("/", File.separator));
        Path current = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (true) {
            int length = stream.read(buffer);
            if (length < 0) {
                break;
            }
            output.write(buffer, 0, length);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
