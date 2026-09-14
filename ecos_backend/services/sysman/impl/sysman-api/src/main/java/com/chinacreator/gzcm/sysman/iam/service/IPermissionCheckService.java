package com.chinacreator.gzcm.sysman.iam.service;

/**
 * 权限检查服务接口
 */
public interface IPermissionCheckService {

    /**
     * 检查用户是否拥有访问资源的权限
     *
     * @param userId   用户ID
     * @param resource 资源标识（如API路径、功能点编码）
     * @param action   动作（如GET/POST/READ/WRITE）
     * @return true 有权限，false 无权限
     * @throws Exception 权限检查异常
     */
    boolean checkPermission(String userId, String resource, String action) throws Exception;
    
    /**
     * 权限检查异常
     */
    class PermissionCheckException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public PermissionCheckException(String message) {
            super(message);
        }
        
        public PermissionCheckException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


