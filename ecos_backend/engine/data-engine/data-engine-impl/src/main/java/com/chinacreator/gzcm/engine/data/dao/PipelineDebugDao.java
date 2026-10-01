package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 断点调试链路数据访问（系统库直连 SQL 子集）。
 *
 * <p>仅收编 PipelineDebugService 中 4 处直接 JdbcTemplate 调用（transform SQL 查询/DML、
 * OUTPUT_OBJECT 行插入、调试执行历史查询）；其余元数据访问走既有 PipelineRepository。</p>
 */
@Repository
public class PipelineDebugDao extends JdbcTemplateDaoSupport {

    /** TRANSFORM_SQL 查询类（SELECT/SHOW/DESCRIBE/WITH）：返回结果行。 */
    public List<Map<String, Object>> queryForListRaw(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    /** TRANSFORM_SQL DML/DDL 类：返回受影响行数。 */
    public int updateRaw(String sql) {
        return jdbcTemplate.update(sql);
    }

    /** OUTPUT_OBJECT：单行 INSERT（列名来自 row key）。 */
    public int updateInsert(String sql, Object[] params) {
        return jdbcTemplate.update(sql, params);
    }

    /** 调试执行历史取行（ecos_pipeline_execution，按 pipeline_id 分页）。 */
    public List<Map<String, Object>> queryExecutionRows(String definitionId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, pipeline_id, status, started_at, finished_at, error_message, rows_processed "
                        + "FROM ecos_pipeline_execution WHERE pipeline_id = ? "
                        + "ORDER BY started_at DESC LIMIT ? OFFSET ?",
                definitionId, limit, offset);
    }
}
