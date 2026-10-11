package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * 场景只读 DAO（设计-05 C.1 ScenarioGuard：场景是否存在 + 是否软删）。
 *
 * <p>场景表 {@code ecos_ai.ecos_business_scenario}（V200 治理后权威落点，ST07 schema 归属；
 * {@code public} 副本已清除，2026-10-11 information_schema 实测仅 ecos_ai 一实例。X-16 现无 java 读取方；
 * 404 语义区分 not_found / deleted）。
 * 本 DAO 仅 SELECT，跨域读走对方引擎 REST 铁律在 S3 切流后收紧；当前阶段本域 read-through 供 E1~E4 前置门；
 * 后续切 REST 后本类退役。</p>
 */
@Repository
public class ScenarioDao extends JdbcTemplateDaoSupport {

    static final String SCENARIO_TABLE = "ecos_ai.ecos_business_scenario";

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
