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
     * 读取用户的<b>有效权限决策</b>（PEP 读面；§2.9.8③ 缓存消费点，additive）。
     *
     * <p>返回 {@code permissionId → "effect:priority"}（如 {@code "ALLOW:10"}）。本方法走
     * {@code PermissionCacheService} 的缓存读路径：命中即回放，未命中则现算并回写——
     * 是「权限缓存真正被请求路径消费」的读面，<b>不改变任何鉴权结论</b>（执法仍由
     * {@code RequirePermission} 切面/网关裁决，缓存不做授权权威）。</p>
     *
     * @param userId 用户标识
     * @return 决策映射；无权限/无租户上下文时为空映射（fail-closed，调用方按"无授权"理解）
     * @throws Exception 取数异常（调用方须显式报错，禁当空集处理）
     */
    java.util.Map<String, String> getUserEffectiveDecisions(String userId) throws Exception;
    
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


