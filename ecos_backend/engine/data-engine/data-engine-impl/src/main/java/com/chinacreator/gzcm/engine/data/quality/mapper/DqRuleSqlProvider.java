package com.chinacreator.gzcm.engine.data.quality.mapper;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleQuery;

/**
 * DqRuleMapper 动态 SQL Provider — 拼装可选过滤条件 + 分页。
 *
 * @author PMO-48-A T3
 */
public final class DqRuleSqlProvider {

    private DqRuleSqlProvider() {
    }

    /**
     * 列表查询 SQL：公共过滤条件 + 排序 + 分页。
     */
    public String listByFilter(DqRuleQuery q, Integer limit, Integer offset) {
        return "SELECT " + DqRuleMapper.COLUMNS
                + " FROM ecos_dq.dq_rule"
                + whereClause(q)
                + " ORDER BY updated_at DESC"
                + " LIMIT " + sanitize(limit)
                + " OFFSET " + sanitize(offset);
    }

    /**
     * 计数 SQL：与列表同 WHERE。
     */
    public String countByFilter(DqRuleQuery q) {
        return "SELECT COUNT(*) FROM ecos_dq.dq_rule" + whereClause(q);
    }

    /**
     * 公共 WHERE 子句（is_deleted 恒过滤，其余字段非空才拼接）。
     */
    private static String whereClause(DqRuleQuery q) {
        StringBuilder sb = new StringBuilder(" WHERE is_deleted = FALSE");
        if (q != null) {
            if (isNotBlank(q.getCategory())) {
                sb.append(" AND category = #{q.category}");
            }
            if (isNotBlank(q.getStatus())) {
                sb.append(" AND status = #{q.status}");
            }
            if (isNotBlank(q.getDomain())) {
                sb.append(" AND domain = #{q.domain}");
            }
            if (isNotBlank(q.getRuleType())) {
                sb.append(" AND rule_type = #{q.ruleType}");
            }
            if (isNotBlank(q.getTargetKind())) {
                sb.append(" AND target_kind = #{q.targetKind}");
            }
            if (isNotBlank(q.getKeyword())) {
                sb.append(" AND rule_name LIKE CONCAT('%', #{q.keyword}, '%')");
            }
        }
        return sb.toString();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 分页参数防注入兜底（仅允许正整数原文拼入 SQL）。 */
    private static String sanitize(Integer value) {
        if (value == null || value <= 0) {
            return "0";
        }
        return String.valueOf(value);
    }
}
