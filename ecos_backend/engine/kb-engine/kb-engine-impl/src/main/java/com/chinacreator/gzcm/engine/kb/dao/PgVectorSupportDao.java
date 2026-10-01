package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：pgvector 扩展探测 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code repo.PgVectorSupport#refresh}（表 {@code pg_extension}，
 * <b>无 schema 前缀</b>，verbatim 保留）。</p>
 */
@Repository
public class PgVectorSupportDao extends JdbcTemplateDaoSupport {

    /** 探测 pgvector 扩展行（extname / extversion）。 */
    public Map<String, Object> fetchVectorExtension() {
        return jdbcTemplate.queryForMap(
                "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector'");
    }
}
