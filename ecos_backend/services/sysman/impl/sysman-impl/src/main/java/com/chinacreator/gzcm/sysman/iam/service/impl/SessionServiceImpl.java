package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.iam.service.ISessionService;

/**
 * 会话服务实现类（内存实现，生产环境应使用Redis）
 * 
 * @author CDRC Security Team
 */
@Service
public class SessionServiceImpl implements ISessionService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    // 会话存储（生产环境应使用Redis）
    private Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();
    
    /** 默认会话超时：30分钟（毫秒） */
    private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000;

    /**
     * 系统配置服务（可选注入，不可用时使用默认30分钟超时）
     */
    @Autowired(required = false)
    private SysConfigService sysConfigService;
    
    @Override
    public String createSession(String userId, String username, String ipAddress, String userAgent) 
            throws SessionException {
        try {
            String sessionId = UUID.randomUUID().toString();
            
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", sessionId);
            sessionData.put("userId", userId);
            sessionData.put("username", username);
            sessionData.put("ipAddress", ipAddress);
            sessionData.put("userAgent", userAgent);
            sessionData.put("createdTime", System.currentTimeMillis());
            sessionData.put("lastAccessTime", System.currentTimeMillis());
            
            sessions.put(sessionId, sessionData);
            
            return sessionId;
        } catch (Exception e) {
            throw new SessionException("创建会话失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, Object> getSession(String sessionId) throws SessionException {
        try {
            Map<String, Object> session = sessions.get(sessionId);
            if (session == null) {
                throw new SessionException("会话不存在: " + sessionId);
            }
            
            // 检查会话是否过期
            long lastAccessTime = (Long) session.get("lastAccessTime");
            long timeout = getSessionTimeoutMillis();
            if (System.currentTimeMillis() - lastAccessTime > timeout) {
                sessions.remove(sessionId);
                throw new SessionException("会话已过期: " + sessionId);
            }
            
            // 更新最后访问时间
            session.put("lastAccessTime", System.currentTimeMillis());
            
            return new HashMap<>(session);
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("获取会话失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void updateSession(String sessionId, Map<String, Object> sessionData) throws SessionException {
        try {
            Map<String, Object> session = sessions.get(sessionId);
            if (session == null) {
                throw new SessionException("会话不存在: " + sessionId);
            }
            
            session.putAll(sessionData);
            session.put("lastAccessTime", System.currentTimeMillis());
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("更新会话失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteSession(String sessionId) throws SessionException {
        try {
            sessions.remove(sessionId);
        } catch (Exception e) {
            throw new SessionException("删除会话失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean exists(String sessionId) throws SessionException {
        try {
            return sessions.containsKey(sessionId);
        } catch (Exception e) {
            throw new SessionException("检查会话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取会话超时时间（毫秒）。
     * 优先从 sys_config 表读取 session_timeout（单位：分钟），
     * 若配置不存在或读取失败，使用默认值30分钟。
     */
    private long getSessionTimeoutMillis() {
        if (sysConfigService != null) {
            try {
                int timeoutMinutes = sysConfigService.getInt("session_timeout", 30);
                if (timeoutMinutes > 0) {
                    return timeoutMinutes * 60 * 1000L;
                }
            } catch (Exception e) {
                log.debug("读取 session_timeout 配置失败，使用默认值30分钟: {}", e.getMessage());
            }
        }
        return DEFAULT_SESSION_TIMEOUT;
    }
}

