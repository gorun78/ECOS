package com.chinacreator.gzcm.runtime.hermes.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 */
@Service
public class SessionManagerImpl implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);

    /** 默认超时: 30 分钟 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 1800L;

    /** 会话存储: sessionId → AgentSession */
    private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String createSession(String subsystem, String profileName, String systemPrompt) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        AgentSession session = new AgentSession(sessionId, subsystem, profileName, systemPrompt);
        sessions.put(sessionId, session);
        log.info("Session created: id={}, subsystem={}, profile={}", sessionId, subsystem, profileName);
        return sessionId;
    }

    @Override
    public AgentSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
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
