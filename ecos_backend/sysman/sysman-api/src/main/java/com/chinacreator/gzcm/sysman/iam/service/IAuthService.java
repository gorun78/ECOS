package com.chinacreator.gzcm.sysman.iam.service;

import java.util.Map;

/**
 * 认证服务接口
 * 
 * @author CDRC Security Team
 */
public interface IAuthService {
    
    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @param ipAddress IP地址
     * @return 登录结果（包含Token和用户信息）
     * @throws AuthException
     */
    Map<String, Object> login(String username, String password, String ipAddress) throws AuthException;
    
    /**
     * 用户登出
     * 
     * @param token Token
     * @throws AuthException
     */
    void logout(String token) throws AuthException;
    
    /**
     * 刷新Token
     * 
     * @param refreshToken Refresh Token
     * @return 新的Token信息
     * @throws AuthException
     */
    Map<String, Object> refreshToken(String refreshToken) throws AuthException;
    
    /**
     * 验证Token
     * 
     * @param token Token
     * @return 用户信息
     * @throws AuthException
     */
    Map<String, Object> validateToken(String token) throws AuthException;
    
    /**
     * 认证异常
     */
    class AuthException extends Exception {
        private static final long serialVersionUID = 1L;
        private String errorCode;
        
        public AuthException(String message) {
            super(message);
        }
        
        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public AuthException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public AuthException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

