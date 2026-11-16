package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 场景只读 DAO（设计-05 C.1 ScenarioGuard：场景是否存在 + 是否软删）。
 *
 * <p>场景表 {@code ecos_control.ecos_business_scenario}（2026-11-15 D108 起显式限定 schema）。
 * 2026-11-10 实测：写入侧 workspace 走 search_path 落 {@code ecos_control}（sc001~sc003），
 * 而本类原限定读 {@code ecos_ai}（0 行）⇒ E1~E4 前置门对真实场景恒 404，L2 认知摘要退化成
 * "暂无记录"。此前改成裸表名靠 search_path 解析——但《数据库访问规范》ST07 要求显式限定，
 * 且 {@code SchemaDriftLintTest} 正把裸表名判红（认知契约面 schema 限定门禁）。
 * 现直接限定 {@code ecos_control}：既满足门禁，也不再依赖会话 search_path。
 * 同库同表读，跨服务不复制。404 语义区分 not_found / deleted。本 DAO 仅 SELECT，后续切 REST 后退役。</p>
 */
@Repository
public class ScenarioDao extends JdbcTemplateDaoSupport {

    static final String SCENARIO_TABLE = "ecos_control.ecos_business_scenario";

    /** 存在且未软删 → true；不存在 → 抛 {@link ScenarioNotFoundException}（保留两态）。 */
    public void assertScenarioExists(String scenarioId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + SCENARIO_TABLE + " WHERE id = ?",
                Integer.class, scenarioId);
        if (n == null || n == 0) throw new ScenarioNotFoundException(scenarioId);
        Integer del = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM " + SCENARIO_TABLE + " WHERE id = ?",
                Integer.class, scenarioId);
        if (del != null && del == 1) throw new ScenarioDeletedException(scenarioId);
    }

    public static class ScenarioNotFoundException extends RuntimeException {
        public ScenarioNotFoundException(String id) { super(id); }
    }
    public static class ScenarioDeletedException extends RuntimeException {
        public ScenarioDeletedException(String id) { super(id); }
    }
}
