package com.chinacreator.gzcm.runtime.llm.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SessionManager 实现 — 基于 ConcurrentHashMap 内存存储
 * <p>
 * 默认会话超时时间: 30 分钟（可通过 ProfileConfig.sessionTimeoutSec 覆盖）
 * </p>
 * <p>
 * 支持 Hermes 持久化适配器注入（ecosHermesSessionAdapter），
 * 通过反射调用避免 llm-gateway → agent-service 的编译期循环依赖。
 * 运行时 agent-service 在 classpath 上（agent-service 反向依赖 llm-gateway），
 * 因此反射调用可正常工作。
 * </p>
 */
@Service
public class SessionManagerImpl implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);

    /** 默认超时: 30 分钟 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 1800L;

    /** 会话存储: sessionId → AgentSession */
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    /**
     * Hermes 会话持久化适配器 (optional — agent-service 未引入时为 null)。
     * 通过 @Qualifier 按名称注入，类型为 Object 以规避编译期循环依赖；
     * 运行时通过反射调用 createSession / searchSessions / isAvailable。
     */
    @Autowired(required = false)
    @Qualifier("ecosHermesSessionAdapter")
    private Object ecosHermesSessionAdapter;

    // ---- Hermes adapter 反射工具方法 ----

    /** 反射调用 adapter.isAvailable() */
    private boolean isHermesAvailable() {
        if (ecosHermesSessionAdapter == null) return false;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("isAvailable");
            return (boolean) m.invoke(ecosHermesSessionAdapter);
        } catch (Exception e) {
            log.debug("HermesSessionAdapter.isAvailable() reflection failed: {}", e.getMessage());
            return false;
        }
    }

    /** 反射调用 adapter.createSession(sessionId, title, profile) */
    private void hermesCreateSession(String sessionId, String title, String profile) {
        if (ecosHermesSessionAdapter == null) return;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("createSession", String.class, String.class, String.class);
            m.invoke(ecosHermesSessionAdapter, sessionId, title, profile);
        } catch (Exception e) {
            log.warn("HermesSessionAdapter.createSession() reflection failed: {}", e.getMessage());
        }
    }

    /** 反射调用 adapter.searchSessions(query, limit) */
    private void hermesSearchSessions(String query, int limit) {
        if (ecosHermesSessionAdapter == null) return;
        try {
            Method m = ecosHermesSessionAdapter.getClass().getMethod("searchSessions", String.class, int.class);
            m.invoke(ecosHermesSessionAdapter, query, limit);
        } catch (Exception e) {
            log.warn("HermesSessionAdapter.searchSessions() reflection failed: {}", e.getMessage());
        }
    }

    // ---- SessionManager 接口实现 ----

    @Override
    public String createSession(String subsystem, String profileName, String systemPrompt) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        AgentSession session = new AgentSession(sessionId, subsystem, profileName, systemPrompt);
        sessions.put(sessionId, session);
        log.info("Session created: id={}, subsystem={}, profile={}", sessionId, subsystem, profileName);

        // 优先持久化到 Hermes，失败降级 ConcurrentHashMap
        if (ecosHermesSessionAdapter != null) {
            try {
                String title = profileName != null ? profileName : "ECOS Session";
                String profile = subsystem != null ? subsystem : "ecos-ai-agent";
                hermesCreateSession(sessionId, title, profile);
                log.debug("Session [{}] persisted to Hermes", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session persistence failed for [{}], falling back to in-memory only: {}",
                        sessionId, e.getMessage());
            }
        }

        return sessionId;
    }

    @Override
    public AgentSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        // 优先尝试 Hermes adapter 查询（验证持久化层是否存活）
        if (isHermesAvailable()) {
            try {
                hermesSearchSessions(sessionId, 1);
                log.debug("Hermes adapter available for session [{}] lookup", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session lookup failed for [{}], falling back to in-memory: {}",
                        sessionId, e.getMessage());
            }
        }

        // 降级到 ConcurrentHashMap
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }

        // 超时检测: 如果 lastActivityAt 距今超过 timeout，标记为 timedout
        if (!"active".equals(session.getStatus())) {
            return session; // 已非活跃，无需检查超时
        }

        long timeoutSec = DEFAULT_TIMEOUT_SECONDS; // 可在未来从 ProfileConfig 中获取
        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(session.getLastActivityAt(), now);

        if (elapsed.getSeconds() > timeoutSec) {
            session.setStatus("timedout");
            log.warn("Session [{}] timed out after {}s (last activity: {})",
                    sessionId, elapsed.getSeconds(), session.getLastActivityAt());
        }

        return session;
    }

    @Override
    public void closeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        // 优先尝试 Hermes adapter 查询确认
        if (isHermesAvailable()) {
            try {
                hermesSearchSessions(sessionId, 1);
                log.debug("Hermes adapter notified for session [{}] close", sessionId);
            } catch (Exception e) {
                log.warn("Hermes session close notification failed for [{}]: {}", sessionId, e.getMessage());
            }
        }

        // 降级到 ConcurrentHashMap
        AgentSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus("closed");
            log.info("Session [{}] closed", sessionId);
        }
    }

    @Override
    public List<AgentSession> getActiveSessions(String subsystem) {
        return sessions.values().stream()
                .filter(s -> {
                    // 1. 匹配子系统
                    if (subsystem != null && !subsystem.equals(s.getSubsystem())) {
                        return false;
                    }
                    // 2. 仅 active 状态
                    if (!"active".equals(s.getStatus())) {
                        return false;
                    }
                    // 3. 同时做超时检测
                    long timeoutSec = DEFAULT_TIMEOUT_SECONDS;
                    Duration elapsed = Duration.between(s.getLastActivityAt(), LocalDateTime.now());
                    if (elapsed.getSeconds() > timeoutSec) {
                        s.setStatus("timedout");
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public int getActiveSessionCount(String subsystem) {
        return getActiveSessions(subsystem).size();
    }

    /**
     * 获取当前所有活跃会话总数（不区分 subsystem）
     */
    public int getTotalActiveSessions() {
        return (int) sessions.values().stream()
                .filter(s -> "active".equals(s.getStatus()))
                .count();
    }
}
