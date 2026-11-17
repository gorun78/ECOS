package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 经营看板聚合 DAO（D33）。
 *
 * <p>读 {@code ecos_cognitive.ecos_biz_*} 四表 + {@code ecos_control.td_organization}。
 * 这四张 biz 表是「企业经营管控数据初始化」批建立、此前<b>零 Java 读取方</b>；
 * 本 DAO 是其首个读面（D33 修复）。</p>
 *
 * <p>只读、无写；SQL 全量 schema 限定（ST07：跨 schema 显式限定，不依赖 search_path）。</p>
 */
@Repository
public class BizDashboardDao extends JdbcTemplateDaoSupport {

    /** 部门列表（td_organization 未删除行）。 */
    public List<Map<String, Object>> listDepartments() {
        return jdbcTemplate.queryForList(
                "SELECT org_id, org_name, parent_org_id FROM ecos_control.td_organization "
                        + "WHERE status <> 'DELETED' ORDER BY org_id");
    }

    /** 项目状态分布与金额聚合。 */
    public Map<String, Object> projectStats() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "COUNT(*) FILTER (WHERE status = 'in_progress') AS in_progress, "
                        + "COUNT(*) FILTER (WHERE status = 'completed') AS completed, "
                        + "COUNT(*) FILTER (WHERE status = 'planning') AS planning, "
                        + "COALESCE(SUM(contract_amount), 0) AS total_amount "
                        + "FROM ecos_cognitive.ecos_biz_project");
    }

    /** 合同统计：总数 + 总额 + income/procurement 各自笔数。 */
    public Map<String, Object> contractStats() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(amount), 0) AS total_value, "
                        + "COUNT(*) FILTER (WHERE contract_type = 'income') AS income_count, "
                        + "COUNT(*) FILTER (WHERE contract_type <> 'income') AS expense_count "
                        + "FROM ecos_cognitive.ecos_biz_contract");
    }

    /** 指标列表（按月，最多 500 行）。 */
    public List<Map<String, Object>> listMetrics() {
        return jdbcTemplate.queryForList(
                "SELECT id, dept_id, metric_type, metric_value, metric_month "
                        + "FROM ecos_cognitive.ecos_biz_metric ORDER BY metric_month DESC, id LIMIT 500");
    }

    /** 目标列表（按年，最多 500 行）。 */
    public List<Map<String, Object>> listTargets() {
        return jdbcTemplate.queryForList(
                "SELECT id, dept_id, target_type, target_value, target_year "
                        + "FROM ecos_cognitive.ecos_biz_target ORDER BY target_year DESC, id LIMIT 500");
    }

    /** 项目月度趋势（按 start_date 月分组：项目数与合同额合计），供 ops 看板 monthlyTrend。 */
    public List<Map<String, Object>> projectMonthlyTrend() {
        return jdbcTemplate.queryForList(
                "SELECT to_char(start_date, 'YYYY-MM') AS month, "
                        + "COALESCE(SUM(contract_amount), 0) AS value, "
                        + "COUNT(*) AS projects "
                        + "FROM ecos_cognitive.ecos_biz_project "
                        + "WHERE start_date IS NOT NULL "
                        + "GROUP BY to_char(start_date, 'YYYY-MM') ORDER BY month");
    }

    /** 合同月度趋势（按 signed_date 月分组：笔数即 rate 口径的分子素材），供 ops 看板 contractTrend。 */
    public List<Map<String, Object>> contractMonthlyTrend() {
        return jdbcTemplate.queryForList(
                "SELECT to_char(signed_date, 'YYYY-MM') AS month, COUNT(*) AS cnt "
                        + "FROM ecos_cognitive.ecos_biz_contract "
                        + "WHERE signed_date IS NOT NULL "
                        + "GROUP BY to_char(signed_date, 'YYYY-MM') ORDER BY month");
    }
}
