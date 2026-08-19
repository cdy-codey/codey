package com.codey.web.service;

import com.codey.loop.HumanDecision;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 管理前端人工确认的挂起请求。
 * WebHumanConfirmationService 注册后阻塞等待，由回传接口写入决策并唤醒。
 */
@Component
public class HumanConfirmationCoordinator {
    private static final long DEFAULT_TIMEOUT_MILLIS = 120_000L;

    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<String, PendingConfirmation>();

    public String register() {
        PendingConfirmation confirmation = new PendingConfirmation();
        String confirmationId = confirmation.id;
        pending.put(confirmationId, confirmation);
        return confirmationId;
    }

    public boolean resolve(String confirmationId, boolean approved, String feedback) {
        PendingConfirmation confirmation = pending.get(confirmationId);
        if (confirmation == null) {
            return false;
        }
        confirmation.decision = approved
                ? HumanDecision.approve(feedback)
                : HumanDecision.reject(feedback);
        confirmation.latch.countDown();
        return true;
    }

    public HumanDecision await(String confirmationId, long timeoutMillis) {
        PendingConfirmation confirmation = pending.get(confirmationId);
        if (confirmation == null) {
            return null;
        }
        try {
            boolean done = confirmation.latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            return done ? confirmation.decision : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            pending.remove(confirmationId);
        }
    }

    public HumanDecision await(String confirmationId) {
        return await(confirmationId, DEFAULT_TIMEOUT_MILLIS);
    }

    static final class PendingConfirmation {
        final String id = java.util.UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        volatile HumanDecision decision;
    }
}
