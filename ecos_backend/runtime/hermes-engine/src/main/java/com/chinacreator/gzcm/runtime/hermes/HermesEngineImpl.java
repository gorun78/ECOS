package com.chinacreator.gzcm.runtime.hermes;

import com.chinacreator.gzcm.runtime.hermes.callback.CallbackExecutor;
import com.chinacreator.gzcm.runtime.hermes.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import com.chinacreator.gzcm.runtime.hermes.profile.ProfileManager;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentScheduler;
import com.chinacreator.gzcm.runtime.hermes.session.AgentSession;
import com.chinacreator.gzcm.runtime.hermes.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * HermesEngine 实现 — 统一 Agent 执行入口
 * <p>
 * 协调 SessionManager、ProfileManager、AgentScheduler、AgentMetrics、CallbackExecutor 完成完整 Agent 执行流程。
 * </p>
 */
@Primary
@Service
public class HermesEngineImpl implements HermesEngine {

    private static final Logger log = LoggerFactory.getLogger(HermesEngineImpl.class);

    /** 默认执行超时: 10 分钟 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 600L;

    @Autowired
    private AgentScheduler agentScheduler;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ProfileManager profileManager;

    @Autowired
    private AgentMetrics agentMetrics;

    @Autowired
    private CallbackExecutor callbackExecutor;

    @Override
    public AgentResult execute(String subsystem, String profileName, String userMessage) {
        log.info("Executing agent: subsystem={}, profile={}", subsystem, profileName);

        // 获取 profile 配置
        ProfileConfig profile = profileManager.getProfile(subsystem, profileName);

        // 创建会话
        String systemPrompt = profile.getSystemPrompt();
        String sessionId = sessionManager.createSession(subsystem, profileName, systemPrompt);
        AgentSession session = sessionManager.getSession(sessionId);

        if (session == null) {
            return AgentResult.fail(null, "Failed to create session", 0);
        }

        // 调度执行
        try {
            CompletableFuture<AgentResult> future = agentScheduler.schedule(session, userMessage);
            // 等待结果（带超时）
            long timeout = DEFAULT_TIMEOUT_SECONDS;
            if (profile.getSessionTimeoutSec() != null && profile.getSessionTimeoutSec() > 0) {
                timeout = profile.getSessionTimeoutSec();
            }
            AgentResult result = future.get(timeout, TimeUnit.SECONDS);
            log.info("Agent execution completed: session={}, success={}, duration={}ms",
                    sessionId, result.isSuccess(), result.getDurationMs());
            return result;
        } catch (TimeoutException e) {
            log.error("Agent execution timed out after {}s: session={}", DEFAULT_TIMEOUT_SECONDS, sessionId);
            return AgentResult.fail(sessionId, "Execution timed out after " + DEFAULT_TIMEOUT_SECONDS + "s", 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.fail(sessionId, "Execution interrupted: " + e.getMessage(), 0);
        } catch (ExecutionException e) {
            log.error("Agent execution failed: session={}", sessionId, e.getCause());
            return AgentResult.fail(sessionId,
                    "Execution error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), 0);
        }
    }

    @Override
    public AgentResult executeAsync(String subsystem, String profileName, String userMessage,
                                    Consumer<String> callback) {
        log.info("Async agent execution: subsystem={}, profile={}", subsystem, profileName);

        // 获取 profile 配置
        ProfileConfig profile = profileManager.getProfile(subsystem, profileName);

        // 创建会话
        String systemPrompt = profile.getSystemPrompt();
        String sessionId = sessionManager.createSession(subsystem, profileName, systemPrompt);
        AgentSession session = sessionManager.getSession(sessionId);

        if (session == null) {
            return AgentResult.fail(null, "Failed to create session", 0);
        }

        // 注册回调
        if (callback != null) {
            callbackExecutor.registerCallback(sessionId, callback);
        }

        // 调度执行 — 不阻塞等待
        CompletableFuture<AgentResult> future = agentScheduler.schedule(session, userMessage);

        // 异步处理完成/失败回调
        if (callback != null) {
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    callbackExecutor.onError(sessionId,
                            "Async execution error: " + throwable.getMessage());
                } else if (result.isSuccess()) {
                    callbackExecutor.onComplete(sessionId, result);
                } else {
                    callbackExecutor.onError(sessionId, result.getErrorMsg());
                }
            });
        }

        // 非流式模式下，等待结果返回
        try {
            long timeout = DEFAULT_TIMEOUT_SECONDS;
            if (profile.getSessionTimeoutSec() != null && profile.getSessionTimeoutSec() > 0) {
                timeout = profile.getSessionTimeoutSec();
            }
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Async agent execution timed out: session={}", sessionId);
            return AgentResult.fail(sessionId, "Execution timed out after " + DEFAULT_TIMEOUT_SECONDS + "s", 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.fail(sessionId, "Execution interrupted: " + e.getMessage(), 0);
        } catch (ExecutionException e) {
            log.error("Async agent execution failed: session={}", sessionId, e.getCause());
            return AgentResult.fail(sessionId,
                    "Execution error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), 0);
        }
    }

    @Override
    public AgentResult executeWithSession(String sessionId, String userMessage) {
        log.info("Continuing session: {}", sessionId);

        AgentSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            return AgentResult.fail(sessionId, "Session not found or has expired", 0);
        }

        if (!"active".equals(session.getStatus())) {
            return AgentResult.fail(sessionId,
                    "Session is not active (status: " + session.getStatus() + ")", 0);
        }

        // 调度执行
        try {
            CompletableFuture<AgentResult> future = agentScheduler.schedule(session, userMessage);
            AgentResult result = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Session execution completed: session={}, success={}, duration={}ms",
                    sessionId, result.isSuccess(), result.getDurationMs());
            return result;
        } catch (TimeoutException e) {
            return AgentResult.fail(sessionId, "Execution timed out", 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.fail(sessionId, "Execution interrupted: " + e.getMessage(), 0);
        } catch (ExecutionException e) {
            return AgentResult.fail(sessionId,
                    "Execution error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), 0);
        }
    }

    @Override
    public AgentSession createSession(String subsystem, String profileName, String userMessage) {
        log.info("Creating session: subsystem={}, profile={}", subsystem, profileName);

        ProfileConfig profile = profileManager.getProfile(subsystem, profileName);
        String systemPrompt = profile.getSystemPrompt();
        String sessionId = sessionManager.createSession(subsystem, profileName, systemPrompt);
        AgentSession session = sessionManager.getSession(sessionId);

        // 如果提供了 userMessage，作为首条消息处理（仅记录日志，不执行 LLM 调用）
        if (userMessage != null && !userMessage.isEmpty() && session != null) {
            log.debug("Session [{}] created with initial message: {}", sessionId, truncate(userMessage, 100));
        }

        return session;
    }

    @Override
    public void closeSession(String sessionId) {
        log.info("Closing session: {}", sessionId);
        sessionManager.closeSession(sessionId);
        callbackExecutor.unregisterCallback(sessionId);
    }

    @Override
    public Map<String, Object> getSubsystemStats(String subsystem) {
        Map<String, Object> stats = agentMetrics.getSubsystemStats(subsystem);
        // 补充活跃会话数
        int activeSessions = sessionManager.getActiveSessionCount(subsystem);
        stats.put("activeSessions", activeSessions);
        return stats;
    }

    @Override
    public Map<String, Object> getGlobalStats() {
        return agentMetrics.getGlobalStats();
    }

    @Override
    public List<ProfileConfig> listProfiles(String subsystem) {
        return profileManager.listProfiles(subsystem);
    }

    @Override
    public void refreshProfileCache(String subsystem) {
        profileManager.refreshCache(subsystem);
        log.info("Profile cache refreshed for subsystem [{}]", subsystem);
    }

    /**
     * 截断字符串到指定长度（用于日志）
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
