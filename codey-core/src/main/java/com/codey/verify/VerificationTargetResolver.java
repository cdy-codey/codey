package com.codey.verify;

import com.codey.config.AgentSession;

import java.util.List;

/**
 * 从会话状态中解析待校验的页面文件和可选的 API 规格文件。
 */
final class VerificationTargetResolver {

    VerificationTarget resolve(AgentSession session) {
        return new VerificationTarget(resolveTargetFile(session), resolveApiSpecFile(session));
    }

    boolean isPageLikeFile(String path) {
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.toLowerCase();
        return normalized.endsWith(".vue")
                || normalized.endsWith(".jsx")
                || normalized.endsWith(".tsx")
                || normalized.endsWith(".html")
                || isPageScriptUnderUiDirectory(normalized);
    }

    /**
     * 当前 verifier 只覆盖具备稳定页面结构约束的前端页面文件。
     * 普通 html 工作区文件（如 demo/game 页面）不应误进入列表页/API 绑定验收链路。
     */
    boolean supportsStructuredPageVerification(String path) {
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.toLowerCase();
        return normalized.endsWith(".vue")
                || normalized.endsWith(".jsx")
                || normalized.endsWith(".tsx")
                || isPageScriptUnderUiDirectory(normalized);
    }

    private String resolveTargetFile(AgentSession session) {
        if (session == null) {
            return null;
        }
        if (!isBlank(session.getTargetPagePath())) {
            return session.getTargetPagePath();
        }
        if (!isBlank(session.getLastEditedFilePath()) && isPageLikeFile(session.getLastEditedFilePath())) {
            return session.getLastEditedFilePath();
        }
        return findFirstPageLikeFile(session.getContextFiles());
    }

    private String resolveApiSpecFile(AgentSession session) {
        if (!isBlank(session.getApiSpecPath())) {
            return session.getApiSpecPath();
        }
        return findFirstApiLikeFile(session.getContextFiles());
    }

    private String findFirstPageLikeFile(List<String> files) {
        if (files == null) {
            return null;
        }
        for (String file : files) {
            if (isPageLikeFile(file)) {
                return file;
            }
        }
        return null;
    }

    private String findFirstApiLikeFile(List<String> files) {
        if (files == null) {
            return null;
        }
        for (String file : files) {
            if (isApiLikeFile(file)) {
                return file;
            }
        }
        return null;
    }

    private boolean isApiLikeFile(String path) {
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.toLowerCase();
        return (normalized.endsWith(".json")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".yml"))
                && (normalized.contains("api")
                || normalized.contains("openapi")
                || normalized.contains("swagger"));
    }

    private boolean isPageScriptUnderUiDirectory(String normalizedPath) {
        if (isBlank(normalizedPath)) {
            return false;
        }
        boolean pageScript = normalizedPath.endsWith(".js") || normalizedPath.endsWith(".ts");
        if (!pageScript) {
            return false;
        }
        return normalizedPath.contains("/views/")
                || normalizedPath.contains("/pages/")
                || normalizedPath.contains("/screens/")
                || normalizedPath.contains("/routes/");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
