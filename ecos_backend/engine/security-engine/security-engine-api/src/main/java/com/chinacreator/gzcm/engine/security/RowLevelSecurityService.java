package com.chinacreator.gzcm.engine.security;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.datapermission.model.RowLevelPolicy;

/**
 * 行级数据权限服务：基于策略对原始 SQL 注入行级过滤条件。
 */
public interface RowLevelSecurityService {

    /**
     * 对单条 SQL 应用所有行级策略。
     *
     * @param originalSql 原始 SQL（SELECT）
     * @param policies    需要应用的行级策略集合
     * @param context     动态变量上下文，如当前用户部门、角色、时间等
     * @return 注入行级权限条件后的 SQL
     * @throws DataPermissionException 解析或重写失败
     */
    String applyPolicies(String originalSql,
                         List<RowLevelPolicy> policies,
                         Map<String, Object> context) throws DataPermissionException;

    class DataPermissionException extends Exception {
        private static final long serialVersionUID = 1L;

        public DataPermissionException(String message) {
            super(message);
        }

        public DataPermissionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
