package com.codey.web.service;

import com.codey.workspace.WorkspaceDirectoryException;
import com.codey.workspace.WorkspaceFile;
import com.codey.workspace.WorkspaceSnapshot;
import com.codey.workspace.WorkspaceDirectoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Web Demo 工作区服务适配层。
 * 负责把通用工作区目录服务接入 Spring，并统一转换成 HTTP 错误。
 */
@Service
public class WorkspaceService {

    private final WorkspaceDirectoryService delegate;
    private final ObjectMapper objectMapper;

    public WorkspaceService(WorkspaceDirectoryService delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    public WorkspaceSnapshot query(String relativePath) {
        try {
            return delegate.query(relativePath);
        } catch (WorkspaceDirectoryException exception) {
            throw toResponseStatusException(exception);
        }
    }

    /**
     * 统一收口工作区写操作，供 Web 层通过一个 push 接口处理创建、保存和重命名。
     */
    public WorkspaceSnapshot push(String relativePath, boolean directory, String content, String newName) {
        try {
            return delegate.push(relativePath, directory, content, newName);
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

    /**
     * 供 AI 工作结果等 JSON 文件读取场景使用。
     * 直接返回解析后的 JSON，避免前端再从快照里手动提取 currentFile.content。
     */
    public JsonNode queryFileContentAsJson(String relativePath) {
        WorkspaceSnapshot snapshot = query(relativePath);
        WorkspaceFile currentFile = snapshot.getCurrentFile();
        if (currentFile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请传入 JSON 文件路径");
        }
        String content = currentFile.getContent();
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容不是有效的 JSON", exception);
        }
    }

    public String getVisibleWorkspaceRoot() {
        return delegate.getVisibleWorkspaceRoot();
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
