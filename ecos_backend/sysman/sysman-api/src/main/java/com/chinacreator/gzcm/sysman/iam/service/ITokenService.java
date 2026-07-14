package com.chinacreator.gzcm.sysman.iam.service;

import java.util.Map;

/**
 * Token服务接口
 * 
 * @author CDRC Security Team
 */
public interface ITokenService {
    
    /**
     * 生成Access Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param claims 其他声明
     * @return Access Token
     * @throws TokenException
     */
    String generateAccessToken(String userId, String username, Map<String, Object> claims) throws TokenException;
    
    /**
     * 生成Refresh Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @return Refresh Token
     * @throws TokenException
     */
    String generateRefreshToken(String userId, String username) throws TokenException;
    
    /**
     * 验证Token
     * 
     * @param token Token
     * @return Token声明
     * @throws TokenException
     */
    Map<String, Object> validateToken(String token) throws TokenException;
    
    /**
     * 将Token加入黑名单
     * 
     * @param token Token
     * @param expirationTime 过期时间（秒）
     * @throws TokenException
     */
    void addToBlacklist(String token, long expirationTime) throws TokenException;
    
    /**
     * 检查Token是否在黑名单中
     * 
     * @param token Token
     * @return 是否在黑名单中
     * @throws TokenException
     */
    boolean isBlacklisted(String token) throws TokenException;
    
    /**
     * Token异常
     */
    class TokenException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public TokenException(String message) {
            super(message);
        }
        
        public TokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

