package com.codey.web.api;

import com.codey.workspace.WorkspaceSnapshot;
import com.codey.web.common.ApiResponse;
import com.codey.web.service.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单工作目录控制器。
 * 只保留查询、push 写入和删除三个最小操作。
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

    /**
     * 查询指定 JSON 文件并直接返回解析后的结果。
     * 入参与 push 保持一致，仍然只接收 path。
     */
    @PostMapping("/query-json")
    public ApiResponse<JsonNode> queryJson(@RequestBody(required = false) QueryRequest request) {
        JsonNode json = workspaceService.queryFileContentAsJson(request == null ? null : request.getPath());
        return ApiResponse.success("JSON 工作结果查询成功", json);
    }

    @PostMapping("/push")
    public ApiResponse<WorkspaceSnapshot> push(@RequestBody(required = false) PushRequest request) {
        WorkspaceSnapshot snapshot = workspaceService.push(
                request == null ? null : request.getPath(),
                request != null && request.isDirectory(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getNewName()
        );
        return ApiResponse.success(resolvePushSuccessMessage(request), snapshot);
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

    public static class PushRequest extends QueryRequest {
        private boolean directory;
        private String content;
        private String newName;

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

        public String getNewName() {
            return newName;
        }

        public void setNewName(String newName) {
            this.newName = newName;
        }
    }

    public static class DeleteRequest extends QueryRequest {
    }

    private String resolvePushSuccessMessage(PushRequest request) {
        if (request == null) {
            return "工作目录保存成功";
        }
        String newName = request.getNewName();
        if (newName != null && !newName.trim().isEmpty()) {
            return "工作目录重命名成功";
        }
        return request.isDirectory() ? "工作目录创建成功" : "工作目录保存成功";
    }
}
