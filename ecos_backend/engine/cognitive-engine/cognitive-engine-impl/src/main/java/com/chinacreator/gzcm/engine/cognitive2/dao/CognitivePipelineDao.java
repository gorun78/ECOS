package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code CognitivePipelineRepository}
 * 内联的 {@code kb_cognitive_pipeline} SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Repository 逐字节一致；
 * RowMapper（行 → {@code CognitivePipeline}）与 config JSON 构造/解析留在 Repository 层
 * （RowMapper 无法整体搬入 DAO——业务对象映射属 Service 语义）。</p>
 */
@Repository
public class CognitivePipelineDao extends JdbcTemplateDaoSupport {

    /** 管线行选择列（与既有查询逐字一致）。 */
    public static final String PIPELINE_COLUMNS =
            "id, pipeline_id, name, status, config, created_by, created_at, updated_at, result, is_deleted";

    /** 更新管线 name + config（未删除；返回命中行数，0 表示需 INSERT 兜底）。 */
    public int updatePipeline(String pipelineId, String name, String configJson) {
        return jdbcTemplate.update(
                "UPDATE kb_cognitive_pipeline SET " +
                "  name = ?, config = ?::jsonb, updated_at = NOW() " +
                "WHERE pipeline_id = ? AND is_deleted = 0",
                name, configJson, pipelineId);
    }

    /** 新建管线（DRAFT / 审计列置位）。 */
    public int insertPipeline(String pipelineId, String name, String configJson) {
        return jdbcTemplate.update(
                    "INSERT INTO kb_cognitive_pipeline " +
                    "(pipeline_id, name, status, config, created_by, created_at, updated_at, is_deleted) " +
                    "VALUES (?, ?, 'DRAFT', ?::jsonb, 'system', NOW(), NOW(), 0)",
                    pipelineId, name, configJson);
    }

    /** 按 pipeline_id 查管线行（未删除），以调用方 RowMapper 映射（最多 1 行）。 */
    public <T> List<T> findById(String pipelineId, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(
                "SELECT " + PIPELINE_COLUMNS + " " +
                "FROM kb_cognitive_pipeline WHERE pipeline_id = ? AND is_deleted = 0",
                rowMapper, pipelineId);
    }

    /** 全量管线（未删除，created_at 倒序，上限 200），以调用方 RowMapper 映射。 */
    public <T> List<T> findAll(RowMapper<T> rowMapper) {
        return jdbcTemplate.query(
                "SELECT " + PIPELINE_COLUMNS + " " +
                "FROM kb_cognitive_pipeline WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT 200",
                rowMapper);
    }

    /** 逻辑删除（铁律 4.8）— DELETE 不物理删除，仅标记 is_deleted = 1。 */
    public int logicalDelete(String pipelineId) {
        return jdbcTemplate.update(
                "UPDATE kb_cognitive_pipeline SET is_deleted = 1, updated_at = NOW() " +
                "WHERE pipeline_id = ? AND is_deleted = 0",
                pipelineId);
    }
}
