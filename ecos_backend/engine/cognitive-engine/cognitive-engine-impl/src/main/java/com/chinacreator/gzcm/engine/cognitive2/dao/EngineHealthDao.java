package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code EngineOpenHealthQueryService}
 * 内联的 Open Health 探活 SQL 落点（DB 探活 + kb_cognitive_pipeline 计数）。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * {@code DataAccessException} 原样上浮语义不变（由调用方 Controller 分段降级）。</p>
 */
@Repository
public class EngineHealthDao extends JdbcTemplateDaoSupport {

    /** PG (sys_man) 探活：{@code SELECT 1}，成功返回 1。 */
    public Long probeDb() {
        return jdbcTemplate.queryForObject("SELECT 1", Long.class);
    }

    /** {@code kb_cognitive_pipeline} 未删除行数（is_deleted=0）。 */
    public Long countPipelines() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_cognitive_pipeline WHERE is_deleted = 0",
                Long.class);
    }

    /** {@code kb_cognitive_pipeline} 中 status='ACTIVE' 且未删除的行数。 */
    public Long countActivePipelines() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_cognitive_pipeline WHERE status = 'ACTIVE' AND is_deleted = 0",
                Long.class);
    }
}
