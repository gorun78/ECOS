package com.chinacreator.gzcm.runtime.hermes.callback;

import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * CallbackExecutor 实现 — 基于 ConcurrentHashMap 存储会话回调
 * <p>
 * 支持按 sessionId 注册/注销回调消费者，用于流式 token 推送和完成通知。
 * </p>
 */
@Primary
@Service
public class CallbackExecutorImpl implements CallbackExecutor {

    private static final Logger log = LoggerFactory.getLogger(CallbackExecutorImpl.class);

    /** sessionId → 回调消费者 */
    private final ConcurrentHashMap<String, Consumer<String>> callbacks = new ConcurrentHashMap<>();

    @Override
    public void registerCallback(String sessionId, Consumer<String> callback) {
        if (sessionId == null || callback == null) {
            log.warn("Attempted to register callback with null sessionId or callback");
            return;
        }
        callbacks.put(sessionId, callback);
        log.debug("Callback registered for session [{}]", sessionId);
    }

    @Override
    public void unregisterCallback(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Consumer<String> removed = callbacks.remove(sessionId);
        if (removed != null) {
            log.debug("Callback unregistered for session [{}]", sessionId);
        }
    }

    @Override
    public void onToken(String sessionId, String token) {
        if (sessionId == null || token == null) {
            return;
        }
        Consumer<String> callback = callbacks.get(sessionId);
        if (callback != null) {
            try {
                callback.accept(token);
            } catch (Exception e) {
                log.warn("Callback onToken error for session [{}]: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void onComplete(String sessionId, AgentResult result) {
        if (sessionId == null) {
            return;
        }
        // 发送完成标记
        if (result != null && result.isSuccess()) {
            onToken(sessionId, result.getContent());
        }
        // 发送 [DONE] 标记
        onToken(sessionId, "[DONE]");
        // 注销回调
        unregisterCallback(sessionId);
        log.debug("Session [{}] callback completed", sessionId);
    }

    @Override
    public void onError(String sessionId, String error) {
        if (sessionId == null) {
            return;
        }
        // 发送错误消息
        String errorPayload = "[ERROR] " + (error != null ? error : "Unknown error");
        onToken(sessionId, errorPayload);
        // 注销回调
        unregisterCallback(sessionId);
        log.warn("Session [{}] callback error: {}", sessionId, error);
    }
}
