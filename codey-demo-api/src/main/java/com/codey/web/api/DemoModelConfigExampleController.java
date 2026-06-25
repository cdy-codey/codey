package com.codey.web.api;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.web.common.ApiResponse;
import com.codey.web.service.DemoSessionModelConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo 示例接口：
 * 展示 application.yml 与数据库来源如何统一组装成同一个 modelConfig 再创建会话。
 */
@RestController
@RequestMapping("/api/chat/demo")
public class DemoModelConfigExampleController {

    private final AgentClient agentClient;
    private final DemoSessionModelConfigService demoSessionModelConfigService;

    public DemoModelConfigExampleController(AgentClient agentClient,
                                            DemoSessionModelConfigService demoSessionModelConfigService) {
        this.agentClient = agentClient;
        this.demoSessionModelConfigService = demoSessionModelConfigService;
    }

    /**
     * 示例一：模型配置来源于 application.yml，但进入 core 时仍然走统一的 modelConfig 字段。
     */
    @PostMapping("/sessions/yml")
    public ApiResponse<ChatSession> openSessionByYaml(@RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = demoSessionModelConfigService.applyYamlModelConfig(request);
            ChatSession session = agentClient.openSession(normalized);
            return ApiResponse.success("已按 yml 配置创建示例会话", session);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    /**
     * 示例二：模型配置来源于数据库查询结果，但进入 core 时仍然走统一的 modelConfig 字段。
     */
    @PostMapping("/sessions/database")
    public ApiResponse<ChatSession> openSessionByDatabase(@RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = demoSessionModelConfigService.applyDatabaseModelConfig(request);
            ChatSession session = agentClient.openSession(normalized);
            return ApiResponse.success("已按数据库配置创建示例会话", session);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
