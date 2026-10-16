package com.chinacreator.gzcm.engine.data.quality.probe;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/**
 * DqRuleProbeDao — DQ 规则真值探测的只读数据面（F02-09 补齐）。
 * <p>
 * 承载 {@code DqRuleProbeExecutor} 需从 PG 目录/目标表拿的三件事：
 * <ol>
 *   <li>{@link #tableOid(String)} — {@code to_regclass(schema.table)}，缺席或不可解析时返回 null。</li>
 *   <li>{@link #columnWhitelist(String, String)} — {@code information_schema.columns} 一表列名清单。</li>
 *   <li>{@link #countTotalFail(String, List, List)} — 单语句
 *       {@code SELECT COUNT(*)::bigint AS total,
 *        COALESCE(SUM(CASE WHEN <fail-expr> THEN 1 ELSE 0 END),0)::bigint AS fails FROM <from-expr> <where-expr>}。
 *       谓词已由 Executor 侧拼装并过标识符白名单，SQL 免拼接风险；值一律走占位符。</li>
 * </ol>
 * <p>
 * 红线：只读、不落库、不吞异常——{@code DataAccessException} 原样上抛，令 Executor
 * 侧决定是否降级为 {@code executed=false}。
 *
 * @author F02-09 / ECOS Data Engine Team
 */
@Repository
public class DqRuleProbeDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(DqRuleProbeDao.class);

    /** 表存在性：{@code to_regclass} 缺席返回 null；解析失败亦归 0。
     * <p>
     * JDBC parameterized query 下 {@code to_regclass(?)} 返回类型无法推断，
     * PG 驱动只能按 text 处理 → {@code Long.class} 类型转换失败，
     * java 端拿到"不良的类型值 long"。显式 {@code ::bigint} cast
     * 让 PG 先转 oid 再回给 java 端类型认得。
     */
    public Long tableOid(String qualified) {
        try {
            Long oid = jdbcTemplate.queryForObject("SELECT to_regclass(?)::bigint", Long.class, qualified);
            if (oid == null) {
                log.warn("DqRuleProbeDao tableOid 未解析（未注册到 catalog）: qualified={}", qualified);
            }
            return oid;
        } catch (DataAccessException e) {
            log.warn("DqRuleProbeDao tableOid 失败: qualified={}, err={}", qualified, e.getMessage());
            return null;
        }
    }

    /** 列名白名单（PG 小写存储）。空列表 = 表不可达或表名无效。 */
    public List<String> columnWhitelist(String schema, String table) {
        try {
            List<String> cols = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = ? AND table_name = ?",
                    String.class, schema, table);
            if (cols.isEmpty()) {
                log.warn("DqRuleProbeDao columnWhitelist 空: schema={}, table={}", schema, table);
            }
            return cols;
        } catch (DataAccessException e) {
            log.warn("DqRuleProbeDao columnWhitelist 失败: schema={}, table={}, err={}",
                    schema, table, e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行单语句 COUNT/SUM 探测。
     *
     * @param sql     形如 {@code SELECT COUNT(*)::bigint AS total, ... FROM <fromCl>} 的完整语句
     * @param args    SQL 值占位符参数
     * @return {@code [total, fails]}；SQL 空结果（PG 不可能——COUNT 恒 1 行）或异常时返回 null
     */
    public long[] countTotalFail(String sql, List<Object> args) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            long total = ((Number) row.get("total")).longValue();
            long fails = ((Number) row.get("fails")).longValue();
            return new long[]{total, fails};
        } catch (DataAccessException e) {
            throw e; // 上抛令 Executor 决策降级
        }
    }

    /** latest update_time 探针（FRESHNESS 规则的特殊形态）。 */
    public Timestamp latestTimestamp(String qualifiedTable, String column) {
        // qualifiedTable/column 均由 Executor 侧过白名单后传入，且严格匹配 [a-z_][a-z0-9_]*
        return jdbcTemplate.queryForObject(
                "SELECT MAX(" + column + ") FROM " + qualifiedTable,
                Timestamp.class);
    }
}
