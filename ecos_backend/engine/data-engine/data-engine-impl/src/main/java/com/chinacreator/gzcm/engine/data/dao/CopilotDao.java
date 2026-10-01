package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * CopilotServiceImpl 的 JDBC 数据访问层。
 * <p>表: ecos_pipeline_run。原始 SQL 均在此执行。</p>
 */
@Repository
public class CopilotDao extends JdbcTemplateDaoSupport {

    /** 读取单条 pipeline run 详情（供错误诊断）。 */
    public Map<String, Object> getRunById(String runId) {
        return jdbcTemplate.queryForMap(
            "SELECT * FROM ecos_pipeline_run WHERE id = ?", runId);
    }
}
