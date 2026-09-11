package com.chinacreator.gzcm.sysman.iam.service;

import java.util.Map;

/**
 * 会话服务接口
 * 
 * @author CDRC Security Team
 */
public interface ISessionService {
    
    /**
     * 创建会话
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @return 会话ID
     * @throws SessionException
     */
    String createSession(String userId, String username, String ipAddress, String userAgent) throws SessionException;
    
    /**
     * 获取会话信息
     * 
     * @param sessionId 会话ID
     * @return 会话信息
     * @throws SessionException
     */
    Map<String, Object> getSession(String sessionId) throws SessionException;
    
    /**
     * 更新会话
     * 
     * @param sessionId 会话ID
     * @param sessionData 会话数据
     * @throws SessionException
     */
    void updateSession(String sessionId, Map<String, Object> sessionData) throws SessionException;
    
    /**
     * 删除会话
     * 
     * @param sessionId 会话ID
     * @throws SessionException
     */
    void deleteSession(String sessionId) throws SessionException;
    
    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话ID
     * @return 是否存在
     * @throws SessionException
     */
    boolean exists(String sessionId) throws SessionException;
    
    /**
     * 会话异常
     */
    class SessionException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public SessionException(String message) {
            super(message);
        }
        
        public SessionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

