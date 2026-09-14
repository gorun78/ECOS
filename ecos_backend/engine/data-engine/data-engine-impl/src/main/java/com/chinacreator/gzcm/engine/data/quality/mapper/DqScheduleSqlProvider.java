package com.chinacreator.gzcm.engine.data.quality.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.annotations.Param;

/**
 * DqScheduleMapper 动态 SQL Provider — countChecksToday 规则 ID 列表 IN 子句。
 *
 * <p>与 DqRuleSqlProvider 同风格：@SelectProvider 拿到的参数已按 @Param 解构，
 * 直接方法参数传入；占位符用 {@code ?}（PG JDBC 标准）。</p>
 *
 * @author PMO-48-C T11
 */
public class DqScheduleSqlProvider {

    /**
     * 统计当日指定规则集合的执行检查数。
     *
     * @param ruleIds 规则 ID 列表（MyBatis 已从 @Param 解构注入）
     * @return SQL 文本
     */
    public String countChecksToday(@Param("ruleIds") List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return "SELECT 0";
        }
        String placeholders = ruleIds.stream().map(r -> "?").collect(Collectors.joining(", "));
        return "SELECT COUNT(*) FROM ecos_dq.dq_rule_check"
                + " WHERE executed_at >= CURRENT_DATE"
                + " AND rule_id IN (" + placeholders + ")";
    }
}
