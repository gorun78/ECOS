package com.chinacreator.gzcm.workspace.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC 实现的查询历史存储（卷07 F07-21 / W185 / C167）。
 *
 * <p><b>落库面 = V212 冻结 DDL</b>：{@code public.ecos_scenario_query_history}
 * （MC06 控制域现基线 public，迁 ecos_control 后经 {@code ecos.db.control-schema}
 * 配置注入改前缀，本批不预置）。E-5/X-55 纪律：只存 {@code sql_digest} 摘要，
 * <b>不落 question/DSL 原文与结果集</b>；读取面 question 返回 null，
 * 响应侧原文仅存在于当次进程内（{@code QueryRecord}），跨重启不可复原（设计定版）。</p>
 *
 * <p><b>启用条件</b>：{@code ecos.workspace.query-history.persistent=true}。
 * 默认关闭 —— V212 脚本已落 {@code docs/50-db_script/02-flyway-migration/gateway/}
 * 但按 §14.4 <b>未实跑库</b>；实跑授权后翻开关即可，Service 无需改动。</p>
 *
 * <p>铁律 §2.5：数据访问只在这一层用 JdbcTemplate；
 * Service / Controller 持 JdbcTemplate 由 {@code DataAccessArchTest} 拦截。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "ecos.workspace.query-history", name = "persistent", havingValue = "true")
public class JdbcQueryHistoryRepository implements QueryHistoryRepository {

    private static final String TABLE = "public.ecos_scenario_query_history";

    private static final RowMapper<QueryHistoryEntry> MAPPER = (ResultSet rs, int n) ->
            new QueryHistoryEntry(
                    rs.getString("id"),
                    rs.getString("subject_id"),
                    null,
                    rs.getString("sql_digest"),
                    rs.getInt("row_count"),
                    rs.getString("trace_id"),
                    rs.getTimestamp("create_time") == null ? null
                            : rs.getTimestamp("create_time").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public JdbcQueryHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(QueryHistoryEntry entry) {
        Timestamp now = entry.createdAt() == null
                ? new Timestamp(System.currentTimeMillis())
                : Timestamp.valueOf(entry.createdAt());
        jdbc.update("INSERT INTO public.ecos_scenario_query_history"
                        + " (id, trace_id, subject_id, sql_digest, row_count, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                entry.id(), entry.traceId(), entry.subjectId(), entry.sqlDigest(),
                entry.rowCount(), now, now);
    }

    @Override
    public List<QueryHistoryEntry> findPage(String subjectId, int limit, int offset) {
        return jdbc.query("SELECT id, subject_id, sql_digest, row_count, trace_id, create_time FROM " + TABLE
                        + " WHERE subject_id = ? AND is_deleted = 0 ORDER BY create_time DESC LIMIT ? OFFSET ?",
                MAPPER, subjectId, limit, offset);
    }

    @Override
    public Optional<QueryHistoryEntry> findById(String id) {
        return jdbc.query("SELECT id, subject_id, sql_digest, row_count, trace_id, create_time FROM " + TABLE
                        + " WHERE id = ? AND is_deleted = 0", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public boolean deleteById(String id) {
        return jdbc.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE id = ?", id) > 0;
    }

    @Override
    public int clearBySubject(String subjectId) {
        return subjectId == null
                ? jdbc.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE is_deleted = 0")
                : jdbc.update("UPDATE " + TABLE + " SET is_deleted = 1 WHERE subject_id = ? AND is_deleted = 0", subjectId);
    }
}
