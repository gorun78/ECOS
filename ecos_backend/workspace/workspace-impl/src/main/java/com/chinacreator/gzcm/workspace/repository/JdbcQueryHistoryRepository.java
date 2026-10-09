package com.chinacreator.gzcm.workspace.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC 实现的查询历史存储（卷07 F07-21）。
 *
 * <p><b>启用条件</b>：{@code ecos.workspace.query-history.persistent=true}。
 * 默认<b>关闭</b>，因为落库表 {@code ecos_query_history} 尚不存在，而 DDL 迁移
 * 属主在他册（本卷不新增 migration）。表就绪后只需翻这个开关，
 * {@code QueryHistoryService} 无需改动。</p>
 *
 * <p>需要的列（供 DDL 属主对齐，<b>本类不建表</b>）：
 * {@code id VARCHAR(36) PK, subject_id VARCHAR(64), question VARCHAR(512),
 * sql_digest VARCHAR(64), row_count INT, trace_id VARCHAR(64),
 * created_at TIMESTAMP}。</p>
 *
 * <p>铁律 §2.5：数据访问只在这一层用 JdbcTemplate；
 * Service / Controller 持 JdbcTemplate 由 {@code DataAccessArchTest} 拦截。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "ecos.workspace.query-history", name = "persistent", havingValue = "true")
public class JdbcQueryHistoryRepository implements QueryHistoryRepository {

    private static final String TABLE = "ecos_query_history";

    private static final RowMapper<QueryHistoryEntry> MAPPER = (ResultSet rs, int n) ->
            new QueryHistoryEntry(
                    rs.getString("id"),
                    rs.getString("subject_id"),
                    rs.getString("question"),
                    rs.getString("sql_digest"),
                    rs.getInt("row_count"),
                    rs.getString("trace_id"),
                    rs.getTimestamp("created_at") == null ? null
                            : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public JdbcQueryHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(QueryHistoryEntry entry) {
        jdbc.update("INSERT INTO " + TABLE
                        + " (id, subject_id, question, sql_digest, row_count, trace_id, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                entry.id(), entry.subjectId(), entry.question(), entry.sqlDigest(),
                entry.rowCount(), entry.traceId(),
                entry.createdAt() == null ? new Timestamp(System.currentTimeMillis())
                        : Timestamp.valueOf(entry.createdAt()));
    }

    @Override
    public List<QueryHistoryEntry> findPage(String subjectId, int limit, int offset) {
        return jdbc.query("SELECT * FROM " + TABLE
                        + " WHERE subject_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                MAPPER, subjectId, limit, offset);
    }

    @Override
    public Optional<QueryHistoryEntry> findById(String id) {
        return jdbc.query("SELECT * FROM " + TABLE + " WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM " + TABLE + " WHERE id = ?", id) > 0;
    }

    @Override
    public int clearBySubject(String subjectId) {
        return subjectId == null
                ? jdbc.update("DELETE FROM " + TABLE)
                : jdbc.update("DELETE FROM " + TABLE + " WHERE subject_id = ?", subjectId);
    }
}
