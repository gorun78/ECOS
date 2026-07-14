package com.chinacreator.gzcm.runtime.hermes.callback;

import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;

import java.util.function.Consumer;

/**
 * 回调执行器接口 — 支持流式 Token 回调和任务完成/错误回调
 */
public interface CallbackExecutor {

    /**
     * 注册会话回调（按 sessionId 注册一个文本回调消费者）
     *
     * @param sessionId 会话 ID
     * @param callback  回调消费者，接收文本片段（stream token 或最终内容）
     */
    void registerCallback(String sessionId, Consumer<String> callback);

    /**
     * 注销会话回调
     */
    void unregisterCallback(String sessionId);

    /**
     * 触发 Token 回调（流式输出时逐段调用）
     */
    void onToken(String sessionId, String token);

    /**
     * 触发完成回调
     */
    void onComplete(String sessionId, AgentResult result);

    /**
     * 触发错误回调
     */
    void onError(String sessionId, String error);
}
