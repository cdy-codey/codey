package com.codey.web.api;

import com.codey.web.common.ApiResponse;
import com.codey.web.service.HumanConfirmationCoordinator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 前端人工确认回传接口：收到写文件/改表单前的确认请求后，
 * 使用者通过该接口提交批准或拒绝决策。
 */
@RestController
@RequestMapping("/api/chat")
public class HumanConfirmationController {

    private final HumanConfirmationCoordinator coordinator;

    public HumanConfirmationController(HumanConfirmationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/sessions/{sessionId}/confirmations/{confirmationId}")
    public ApiResponse<Void> submitDecision(@PathVariable("sessionId") String sessionId,
                                            @PathVariable("confirmationId") String confirmationId,
                                            @RequestBody(required = false) DecisionRequest request) {
        if (request == null || request.getApproved() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少批准或拒绝决策");
        }
        boolean resolved = coordinator.resolve(
                confirmationId,
                Boolean.TRUE.equals(request.getApproved()),
                request.getFeedback());
        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "确认请求不存在或已超时");
        }
        return ApiResponse.success("决策已提交", null);
    }

    static final class DecisionRequest {
        private Boolean approved;
        private String feedback;

        public Boolean getApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public String getFeedback() {
            return feedback == null ? "" : feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }
    }
}
