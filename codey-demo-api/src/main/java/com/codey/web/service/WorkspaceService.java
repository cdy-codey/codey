package com.codey.web.service;

import com.codey.workspace.WorkspaceDirectoryException;
import com.codey.workspace.WorkspaceSnapshot;
import com.codey.workspace.WorkspaceDirectoryService;
import com.codey.web.config.WebDemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Web Demo 工作区服务适配层。
 * 负责把通用工作区目录服务接入 Spring，并统一转换成 HTTP 错误。
 */
@Service
public class WorkspaceService {
    private static final Set<String> STANDARD_IGNORED_ENTRY_NAMES = new HashSet<String>(Arrays.asList(
            ".git", ".idea", "node_modules", "target", "dist"
    ));

    private final WorkspaceDirectoryService delegate;

    public WorkspaceService(WebDemoProperties properties) {
        this.delegate = new WorkspaceDirectoryService(
                properties.resolveWorkingDirectoryRoot(),
                buildIgnoredEntryNames(properties.getSessionDirectory()),
                properties::toVisiblePath
        );
    }

    public WorkspaceSnapshot query(String relativePath) {
        try {
            return delegate.query(relativePath);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    /**
     * create 接口同时支持创建文件和目录，便于后续扩展目录管理能力。
     */
    public WorkspaceSnapshot createEntry(String relativePath, boolean directory, String content) {
        try {
            return delegate.createEntry(relativePath, directory, content);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    public WorkspaceSnapshot updateFile(String relativePath, String content) {
        try {
            return delegate.updateFile(relativePath, content);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    /**
     * update 接口除了保存文件内容，也支持只修改当前节点名称，避免继续膨胀新路由。
     */
    public WorkspaceSnapshot renameEntry(String relativePath, String newName) {
        try {
            return delegate.renameEntry(relativePath, newName);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    public WorkspaceSnapshot deleteEntry(String relativePath) {
        try {
            return delegate.deleteEntry(relativePath);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    public String getVisibleWorkspaceRoot() {
        return delegate.getVisibleWorkspaceRoot();
    }

    private Set<String> buildIgnoredEntryNames(String sessionDirectory) {
        Set<String> ignoredNames = new HashSet<String>(STANDARD_IGNORED_ENTRY_NAMES);
        Path normalizedSessionDirectory = Paths.get(sessionDirectory).normalize();
        Path fileName = normalizedSessionDirectory.getFileName();
        if (fileName != null) {
            ignoredNames.add(fileName.toString());
        }
        return ignoredNames;
    }

    private ResponseStatusException toResponseStatusException(WorkspaceDirectoryException exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (exception.getErrorType() == WorkspaceDirectoryException.ErrorType.BAD_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getErrorType() == WorkspaceDirectoryException.ErrorType.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception.getErrorType() == WorkspaceDirectoryException.ErrorType.CONFLICT) {
            status = HttpStatus.CONFLICT;
        }
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }
}
