package com.codey.web.api;

import com.codey.workspace.WorkspaceSnapshot;
import com.codey.web.common.ApiResponse;
import com.codey.web.service.WorkspaceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单工作目录控制器。
 * 只保留查询、创建、编辑、删除四个最小操作。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * path 为空时返回整棵目录树；path 指向文件时额外回传文件内容。
     */
    @PostMapping("/query")
    public ApiResponse<WorkspaceSnapshot> query(@RequestBody(required = false) QueryRequest request) {
        WorkspaceSnapshot snapshot = workspaceService.query(request == null ? null : request.getPath());
        return ApiResponse.success("工作目录查询成功", snapshot);
    }

    @PostMapping("/create")
    public ApiResponse<WorkspaceSnapshot> create(@RequestBody CreateRequest request) {
        WorkspaceSnapshot snapshot = workspaceService.createEntry(
                request == null ? null : request.getPath(),
                request != null && request.isDirectory(),
                request == null ? null : request.getContent()
        );
        return ApiResponse.success("工作目录创建成功", snapshot);
    }

    @PostMapping("/update")
    public ApiResponse<WorkspaceSnapshot> update(@RequestBody UpdateRequest request) {
        WorkspaceSnapshot snapshot;
        if (request != null && request.getNewName() != null && !request.getNewName().trim().isEmpty()) {
            snapshot = workspaceService.renameEntry(
                    request.getPath(),
                    request.getNewName()
            );
            return ApiResponse.success("工作目录重命名成功", snapshot);
        }
        snapshot = workspaceService.updateFile(
                request == null ? null : request.getPath(),
                request == null ? null : request.getContent()
        );
        return ApiResponse.success("工作目录保存成功", snapshot);
    }

    @PostMapping("/delete")
    public ApiResponse<WorkspaceSnapshot> delete(@RequestBody DeleteRequest request) {
        WorkspaceSnapshot snapshot = workspaceService.deleteEntry(request == null ? null : request.getPath());
        return ApiResponse.success("工作目录删除成功", snapshot);
    }

    public static class QueryRequest {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class CreateRequest extends QueryRequest {
        private boolean directory;
        private String content;

        public boolean isDirectory() {
            return directory;
        }

        public void setDirectory(boolean directory) {
            this.directory = directory;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class UpdateRequest extends QueryRequest {
        private String content;
        private String newName;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getNewName() {
            return newName;
        }

        public void setNewName(String newName) {
            this.newName = newName;
        }
    }

    public static class DeleteRequest extends QueryRequest {
    }
}
