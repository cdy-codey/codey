package com.codey.web.api;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import com.codey.client.SessionEventHub;
import com.codey.web.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public ChatController(AgentClient agentClient,
                                 SessionEventHub sessionEventHub,
                                 ChatSessionDisplayOptionsStore displayOptionsStore) {
        this.agentClient = agentClient;
        this.sessionEventHub = sessionEventHub;
        this.displayOptionsStore = displayOptionsStore;
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
    public ApiResponse<RunResult> sendMessage(@PathVariable("sessionId") String sessionId,
                                                   @RequestBody(required = false) RunRequest request) {
        try {
            RunRequest normalized = normalize(request);
            saveDisplayOptions(sessionId, normalized);
            return ApiResponse.success("消息已发送", agentClient.runTurn(sessionId, normalized));
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

    private RunRequest normalize(RunRequest request) {
        return request == null ? new RunRequest() : request;
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
}
