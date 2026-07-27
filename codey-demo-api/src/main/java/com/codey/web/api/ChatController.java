package com.codey.web.api;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.SessionEventHub;
import com.codey.web.common.ApiResponse;
import com.codey.web.common.CoreRulesProvider;
import com.codey.web.service.DemoSessionModelConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 最小会话 REST 接口。
 * 直接复用 starter 暴露的统一客户端 DTO。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final AgentClient agentClient;
    private final SessionEventHub sessionEventHub;
    private final ChatSessionDisplayOptionsStore displayOptionsStore;
    private final DemoSessionModelConfigService demoSessionModelConfigService;
    private final CoreRulesProvider coreRulesProvider;

    public ChatController(AgentClient agentClient,
                          SessionEventHub sessionEventHub,
                          ChatSessionDisplayOptionsStore displayOptionsStore,
                          DemoSessionModelConfigService demoSessionModelConfigService,
                          CoreRulesProvider coreRulesProvider) {
        this.agentClient = agentClient;
        this.sessionEventHub = sessionEventHub;
        this.displayOptionsStore = displayOptionsStore;
        this.demoSessionModelConfigService = demoSessionModelConfigService;
        this.coreRulesProvider = coreRulesProvider;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSession> openSession(@RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = normalize(request);
            ChatSession session = agentClient.openSession(normalized);
            saveDisplayOptions(session == null ? null : session.getSessionId(), normalized);
            return ApiResponse.success("会话已创建", session);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    /**
     * 显式恢复历史会话。
     * 与新建会话分开后，前端语义更清晰，后续也便于替换成真正的持久化恢复实现。
     */
    @PostMapping("/sessions/{sessionId}/resume")
    public ApiResponse<ChatSession> resumeSession(@PathVariable("sessionId") String sessionId,
                                                       @RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = normalize(request);
            normalized.setSessionId(sessionId);
            ChatSession session = agentClient.openSession(normalized);
            saveDisplayOptions(sessionId, normalized);
            return ApiResponse.success("会话已恢复", session);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<MessageAcceptedResponse> sendMessage(@PathVariable("sessionId") String sessionId,
                                                            @RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = normalize(request);
            saveDisplayOptions(sessionId, normalized);
            agentClient.submitTurn(sessionId, normalized);
            return ApiResponse.success("消息已送达，处理结果将通过事件流返回",
                    new MessageAcceptedResponse(sessionId, "accepted"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(resolveStateErrorStatus(ex), ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void closeSession(@PathVariable("sessionId") String sessionId) {
        agentClient.closeSession(sessionId);
        // 关闭会话时一并释放示例里的内存事件缓存。
        sessionEventHub.clear(sessionId);
        displayOptionsStore.clear(sessionId);
    }

    /**
     * 前端业务口径统一使用 POST；SSE 之外的聊天控制接口均提供 POST 入口。
     */
    @PostMapping("/sessions/{sessionId}/close")
    public ApiResponse<Void> closeSessionByPost(@PathVariable("sessionId") String sessionId) {
        closeSession(sessionId);
        return ApiResponse.success("会话已关闭", null);
    }

    @DeleteMapping("/sessions/{sessionId}/delete")
    public void deleteSession(@PathVariable("sessionId") String sessionId) {
        agentClient.deleteSessionContent(sessionId);
    }

    /**
     * 删除会话内容时同时清理归档和临时工作目录，便于前端统一走 POST 语义。
     */
    @PostMapping("/sessions/{sessionId}/delete")
    public ApiResponse<Void> deleteSessionByPost(@PathVariable("sessionId") String sessionId) {
        deleteSession(sessionId);
        return ApiResponse.success("会话内容已删除", null);
    }

    private RunRequest normalize(RunRequest request) {
        RunRequest normalized = request == null ? new RunRequest() : request;
        // 自动注入全局铁律，前端可显式传 coreRules 覆盖
        if (normalized.getCoreRules() == null || normalized.getCoreRules().trim().isEmpty()) {
            normalized.setCoreRules(coreRulesProvider.getCoreRules());
        }
        // Web Demo 正式走会话级模型配置注入，配置来源可替换为数据库查询结果。
        //return demoSessionModelConfigService.applyDatabaseModelConfig(request);
        //走默认配置入口
        return normalized;
    }

    private void saveDisplayOptions(String sessionId, RunRequest request) {
        if (request == null) {
            return;
        }
        displayOptionsStore.saveIncludeThinking(sessionId, request.isIncludeThinking());
    }

    /**
     * 只把明确的会话不存在映射为 404，其他运行期异常保持为 500，
     * 避免把下游模型鉴权失败等问题错误包装成“会话未找到”。
     */
    private HttpStatus resolveStateErrorStatus(IllegalStateException exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message != null && message.startsWith("未找到会话")) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 只回给前端“消息已受理”的确认，真正模型输出继续走 SSE。
     */
    static final class MessageAcceptedResponse {
        private final String sessionId;
        private final String status;

        MessageAcceptedResponse(String sessionId, String status) {
            this.sessionId = sessionId;
            this.status = status;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getStatus() {
            return status;
        }
    }
}
