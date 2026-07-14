package com.chinacreator.gzcm.runtime.core.security;

import java.util.Collections;
import java.util.Set;

/**
 * 列级权限 — 定义用户对特定列的访问权限。
 *
 * <p>示例：
 * <pre>{@code
 * ColumnPermission perms = new ColumnPermission(
 *     "person_info",
 *     Set.of("name", "age", "address"),  // 可见列
 *     Set.of("id_card", "bank_account"),  // 脱敏列
 *     Set.of("income")                     // 禁止访问列
 * );
 * }</pre>
 */
public class ColumnPermission {

    private final String datasetId;

    /** 用户有权查看的列 */
    private final Set<String> visibleColumns;

    /** 用户可查看但需脱敏的列（如身份证显示为 342***1234） */
    private final Set<String> maskedColumns;

    /** 用户完全不可见的列（返回时剔除） */
    private final Set<String> forbiddenColumns;

    public ColumnPermission(String datasetId,
                            Set<String> visibleColumns,
                            Set<String> maskedColumns,
                            Set<String> forbiddenColumns) {
        this.datasetId = datasetId;
        this.visibleColumns = visibleColumns != null
            ? Collections.unmodifiableSet(visibleColumns)
            : Collections.emptySet();
        this.maskedColumns = maskedColumns != null
            ? Collections.unmodifiableSet(maskedColumns)
            : Collections.emptySet();
        this.forbiddenColumns = forbiddenColumns != null
            ? Collections.unmodifiableSet(forbiddenColumns)
            : Collections.emptySet();
    }

    /** 返回所有允许访问的列（可见 + 脱敏） */
    public Set<String> getAllowedColumns() {
        Set<String> allowed = new java.util.HashSet<>(visibleColumns);
        allowed.addAll(maskedColumns);
        return Collections.unmodifiableSet(allowed);
    }

    public String getDatasetId() { return datasetId; }
    public Set<String> getVisibleColumns() { return visibleColumns; }
    public Set<String> getMaskedColumns() { return maskedColumns; }
    public Set<String> getForbiddenColumns() { return forbiddenColumns; }

    @Override
    public String toString() {
        return "ColumnPermission{" + datasetId
            + " visible=" + visibleColumns.size()
            + " masked=" + maskedColumns.size()
            + " forbidden=" + forbiddenColumns.size() + "}";
    }
}
