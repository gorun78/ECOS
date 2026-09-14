package com.chinacreator.gzcm.sysman.datapermission;

import java.util.List;

import com.chinacreator.gzcm.sysman.datapermission.model.ColumnLevelPolicy;

/**
 * 列级数据权限服务：基于策略对原始 SQL 的 SELECT 子句进行裁剪。
 */
public interface ColumnLevelSecurityService {

    /**
     * 对原始 SELECT SQL 应用列级权限，移除不允许访问的列。
     *
     * @param originalSql 原始 SQL（SELECT）
     * @param policies    列级策略列表
     * @return 裁剪后的 SQL
     * @throws DataPermissionException 解析或重写失败
     */
    String applyPolicies(String originalSql,
                         List<ColumnLevelPolicy> policies) throws DataPermissionException;

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


