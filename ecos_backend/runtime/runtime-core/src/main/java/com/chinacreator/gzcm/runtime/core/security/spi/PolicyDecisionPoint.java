package com.chinacreator.gzcm.runtime.core.security.spi;

import com.chinacreator.gzcm.runtime.core.security.ColumnPermission;
import com.chinacreator.gzcm.runtime.core.security.RowFilter;

import java.util.List;

/**
 * 策略决策点 SPI — 由上层 SYS-MAN 模块实现。
 *
 * <p>内核 {@link com.chinacreator.gzcm.runtime.core.security.PolicyEnforcementPoint}
 * 每次收到数据访问请求时，调用此接口获取决策结果。
 * SYS-MAN 负责：
 * <ul>
 *   <li>用户/角色/权限的 CRUD 管理</li>
 *   <li>策略决策逻辑（RBAC → 行/列过滤条件映射）</li>
 *   <li>策略缓存和刷新</li>
 * </ul>
 *
 * <p>建议 PDP 实现做本地缓存（Caffeine/Guava Cache），
 * 避免每次数据查询都 RPC 调 SYS-MAN。
 */
public interface PolicyDecisionPoint {

    /**
     * 判断用户是否可以对指定数据集执行某个操作，并返回访问控制策略。
     *
     * @param userId    用户标识
     * @param datasetId 数据集标识
     * @param operation 操作类型
     * @return 决策结果
     */
    Decision decide(String userId, String datasetId, String operation);

    /**
     * PDP 决策结果。
     */
    class Decision {
        private final boolean allowed;
        private final String reason;
        private final List<RowFilter> rowFilters;
        private final ColumnPermission columnPermission;

        public Decision(boolean allowed, String reason,
                        List<RowFilter> rowFilters, ColumnPermission columnPermission) {
            this.allowed = allowed;
            this.reason = reason;
            this.rowFilters = rowFilters;
            this.columnPermission = columnPermission;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public List<RowFilter> getRowFilters() { return rowFilters; }
        public ColumnPermission getColumnPermission() { return columnPermission; }
    }
}
