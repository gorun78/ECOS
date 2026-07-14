package com.chinacreator.gzcm.runtime.core.security;

import java.util.Collections;
import java.util.List;

/**
 * 策略执行点 (Policy Enforcement Point) — 数据通道上的安全门禁。
 *
 * <p>所有数据访问请求（查询、写入、导出）在到达数据源之前，
 * 都必须经过 PEP 的检查。PEP 不自己做决策，而是调用上层
 * SYS-MAN 模块的 {@link com.chinacreator.gzcm.runtime.core.security.spi.PolicyDecisionPoint}
 * 获取策略，然后执行。
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>行级安全</b> — 对查询自动注入 WHERE 过滤条件</li>
 *   <li><b>列级安全</b> — 返回结果前剔除/脱敏敏感列</li>
 *   <li><b>操作审计</b> — 记录每次数据访问请求</li>
 * </ul>
 *
 * <p>上层 SYS-MAN 模块负责：用户/角色管理、权限分配、策略决策逻辑。
 *
 * @see com.chinacreator.gzcm.runtime.core.security.spi.PolicyDecisionPoint
 */
public interface PolicyEnforcementPoint {

    /**
     * 对查询请求执行访问控制。返回过滤后的查询条件 + 列权限。
     *
     * @param userId    请求用户ID
     * @param datasetId 目标数据集
     * @param operation 操作类型：SELECT / INSERT / UPDATE / DELETE / EXPORT
     * @return 安全上下文，包含行过滤条件和列权限
     */
    SecurityContext enforce(String userId, String datasetId, String operation);

    /**
     * 安全上下文 — PEP 执行结果。
     */
    class SecurityContext {
        private final String userId;
        private final String datasetId;
        private final String operation;
        private final boolean allowed;
        private final String denyReason;
        private final List<RowFilter> rowFilters;
        private final ColumnPermission columnPermission;

        public SecurityContext(String userId, String datasetId, String operation,
                               boolean allowed, String denyReason,
                               List<RowFilter> rowFilters,
                               ColumnPermission columnPermission) {
            this.userId = userId;
            this.datasetId = datasetId;
            this.operation = operation;
            this.allowed = allowed;
            this.denyReason = denyReason;
            this.rowFilters = rowFilters != null
                ? Collections.unmodifiableList(rowFilters)
                : Collections.emptyList();
            this.columnPermission = columnPermission;
        }

        /** 创建“允许访问”的上下文 */
        public static SecurityContext allow(String userId, String datasetId, String operation,
                                             List<RowFilter> rowFilters,
                                             ColumnPermission columnPermission) {
            return new SecurityContext(userId, datasetId, operation, true, null, rowFilters, columnPermission);
        }

        /** 创建“拒绝访问”的上下文 */
        public static SecurityContext deny(String userId, String datasetId, String operation,
                                            String reason) {
            return new SecurityContext(userId, datasetId, operation, false, reason, null, null);
        }

        public String getUserId() { return userId; }
        public String getDatasetId() { return datasetId; }
        public String getOperation() { return operation; }
        public boolean isAllowed() { return allowed; }
        public String getDenyReason() { return denyReason; }
        public List<RowFilter> getRowFilters() { return rowFilters; }
        public ColumnPermission getColumnPermission() { return columnPermission; }
    }
}
