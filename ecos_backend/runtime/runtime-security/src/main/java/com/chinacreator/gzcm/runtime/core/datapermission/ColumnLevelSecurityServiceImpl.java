package com.chinacreator.gzcm.runtime.core.datapermission;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.chinacreator.gzcm.sysman.datapermission.ColumnLevelSecurityService.DataPermissionException;
import com.chinacreator.gzcm.sysman.datapermission.model.ColumnLevelPolicy;
import com.chinacreator.gzcm.sysman.datapermission.ColumnLevelSecurityService;

/**
 * 列级数据权限简单实现：
 * - 将多个策略的 allowedColumns 合并为一个允许列集合；
 * - 重写 SELECT 列表：
 *   - 如果原 SQL 为 SELECT *，则改写为 SELECT col1, col2, ...；
 *   - 如果为显式列列表，则移除不在允许集合内的列；
 * - 不修改 WHERE / GROUP BY / ORDER BY / LIMIT 等后续子句。
 *
 * 注意：这是基于字符串的轻量实现，适合作为策略下推引擎的前置能力，
 * 后续可用完整 SQL Parser 替换。
 */
public class ColumnLevelSecurityServiceImpl implements ColumnLevelSecurityService {

    @Override
    public String applyPolicies(String originalSql, List<ColumnLevelPolicy> policies) throws DataPermissionException {
        if (originalSql == null) {
            throw new DataPermissionException("原始SQL不能为空");
        }
        if (policies == null || policies.isEmpty()) {
            return originalSql;
        }
        Set<String> allowed = new HashSet<String>();
        for (ColumnLevelPolicy p : policies) {
            if (p != null && p.getAllowedColumns() != null) {
                allowed.addAll(p.getAllowedColumns());
            }
        }
        if (allowed.isEmpty()) {
            throw new DataPermissionException("列级权限策略不允许访问任何列");
        }

        return rewriteSelectList(originalSql, allowed);
    }

    private String rewriteSelectList(String sql, Set<String> allowedColumns) throws DataPermissionException {
        String lower = sql.toLowerCase(Locale.ROOT);
        int selectIdx = lower.indexOf("select ");
        int fromIdx = lower.indexOf(" from ");
        if (selectIdx != 0 || fromIdx < 0) {
            // 简化：只处理以 SELECT 开头的语句
            throw new DataPermissionException("暂不支持的SQL格式，仅支持简单 SELECT ... FROM 语句");
        }
        String selectPart = sql.substring("select ".length(), fromIdx);
        String tailPart = sql.substring(fromIdx); // 从 FROM 开始的部分保持不变

        selectPart = selectPart.trim();
        if ("*".equals(selectPart)) {
            // SELECT * 场景，直接用允许列替换
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ");
            appendAllowedColumns(sb, allowedColumns);
            sb.append(tailPart);
            return sb.toString();
        } else {
            // 显式列列表，按逗号切分并过滤
            String[] cols = selectPart.split(",");
            List<String> kept = new ArrayList<String>();
            for (String raw : cols) {
                String colExpr = raw.trim();
                if (colExpr.length() == 0) {
                    continue;
                }
                // 简化处理别名：col as alias / col alias
                String baseName = extractBaseColumnName(colExpr);
                if (baseName != null && allowedColumns.contains(baseName)) {
                    kept.add(colExpr);
                }
            }
            if (kept.isEmpty()) {
                throw new DataPermissionException("列级权限裁剪后无可访问列");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ");
            for (int i = 0; i < kept.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(kept.get(i));
            }
            sb.append(tailPart);
            return sb.toString();
        }
    }

    private void appendAllowedColumns(StringBuilder sb, Set<String> allowedColumns) {
        int i = 0;
        for (String col : allowedColumns) {
            if (i > 0) sb.append(", ");
            sb.append(col);
            i++;
        }
    }

    /**
     * 尝试从列表达式中提取基础列名，例如：
     * - "t.col as c" -> "col"
     * - "col alias"  -> "col"
     * - "col"        -> "col"
     * 复杂表达式（函数、计算等）则返回 null。
     */
    private String extractBaseColumnName(String colExpr) {
        String expr = colExpr.trim();
        // 过滤函数调用等复杂表达式（很粗糙，但足够作为第一版）
        if (expr.contains("(")) {
            return null;
        }
        String lower = expr.toLowerCase(Locale.ROOT);
        int asIdx = lower.indexOf(" as ");
        if (asIdx > 0) {
            expr = expr.substring(0, asIdx).trim();
        } else {
            // 按空格再拆一次，视为 "col alias" 形式
            int spaceIdx = expr.indexOf(' ');
            if (spaceIdx > 0) {
                expr = expr.substring(0, spaceIdx).trim();
            }
        }
        // 去掉前缀表名：t.col -> col
        int dotIdx = expr.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < expr.length() - 1) {
            expr = expr.substring(dotIdx + 1);
        }
        return expr;
    }
}


