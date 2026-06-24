package com.codey.console.cli;

import com.codey.console.common.ConsoleIo;
import com.codey.session.CompositeSessionStore;
import com.codey.session.ConsoleSessionStore;
import com.codey.session.JsonlSessionStore;
import com.codey.session.ModelInputLogStore;
import com.codey.session.ModelOutputLogStore;
import com.codey.session.SessionStore;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 为不同运行模式组装会话存储实现。
 */
public class CliSessionStoreFactory {

    public SessionStore create(boolean interactiveMode, Path runtimeRoot) {
        Path baseRoot = runtimeRoot == null ? Paths.get(".codey").toAbsolutePath().normalize() : runtimeRoot.toAbsolutePath().normalize();
        Path sessionsRoot = baseRoot.resolve("sessions");
        JsonlSessionStore jsonlStore = new JsonlSessionStore(sessionsRoot);
        ModelInputLogStore modelInputLogStore = new ModelInputLogStore(sessionsRoot.resolve("model-inputs"));
        ModelOutputLogStore modelOutputLogStore = new ModelOutputLogStore(sessionsRoot.resolve("model-outputs"));
        if (!interactiveMode) {
            return new CompositeSessionStore(
                    jsonlStore,
                    modelInputLogStore,
                    modelOutputLogStore
            );
        }
        return new CompositeSessionStore(
                jsonlStore,
                modelInputLogStore,
                modelOutputLogStore,
                new ConsoleSessionStore(ConsoleIo.out(), ConsoleIo.err())
        );
    }
}
