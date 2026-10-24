package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 认知模型资产 DAO（详细设计-05 F05-02 / V187 / V188 / R-13 ①）。
 *
 * <p>严格遵守 D.5/F05-13：表名 {@code {schema}.} 限定、{@code is_deleted=0} 恒带、
 * 全列显式（禁 SELECT *）、无 PostgreSQL 专有方言（jsonb 强转 / 大小写不敏感 LIKE / RETURNING）。</p>
 */
@Repository
public class MindAssetDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(MindAssetDao.class);

    static final String MIND_TABLE  = "ecos_cognitive.ecos_cognitive_mind";
    static final String SCEN_MIND_TABLE = "ecos_cognitive.ecos_cognitive_scenario_mind";

    static final String MIND_COLUMNS =
            "id, mind_key, name, description, capability_mask_json, closed_loop_bounds_json, status, " +
            "is_active, mind_version, trace_id, domain, version_no, is_deleted, create_by, update_by, " +
            "create_time, update_time";

    static final String SCEN_MIND_COLUMNS =
            "id, scenario_id, mind_id, binding_role, priority, trace_id, " +
            "domain, version_no, is_deleted, create_by, update_by, create_time, update_time";

    /**
     * schema 预检：@see 设计-05 C.1 前置门（to_regclass IS NULL → 503 ECOS-COG-501）。
     *
     * <p><b>#142/B4-3e-58（差距 D-B4-88）</b>：收口前本方法在 {@code catch (Exception)} 顶层
     * {@code return true}，注释自称「保守」实为<b>宽松降级</b>——探测通道故障时把「存在性未知」
     * 报成「表在」，与该文件 javadoc 自述的「缺表 → 503 {@code ECOS-COG-501}」前置门自相矛盾
     * （违 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」）。现按同包 {@link BeliefScorer#readScoreInputs}
     * 既有形态改抛 {@link CognitiveGuardException}：探测失败与「探测到缺表」都落 503，
     * 但只有<b>真取到</b> {@code false} 才走返回值，未知值不再冒充真值。</p>
     */
    public boolean mindTableExists() {
        try {
            Boolean present = jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?) IS NOT NULL",
                    Boolean.class, "ecos_cognitive.ecos_cognitive_mind");
            return Boolean.TRUE.equals(present);
        } catch (Exception e) {
            log.error("schema 预检通道不可用：to_regclass({}) 探测失败", MIND_TABLE, e);
            throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                    CognitiveErrorCode.MSG_SCHEMA_NOT_READY, MIND_TABLE);
        }
    }

    /** E1 JOIN：查场景下所有 is_active=1 且 is_deleted=0 的 Mind。 */
    public List<Map<String, Object>> detectActiveMindsForScenario(String scenarioId) {
        String sql =
                "SELECT m.id AS mind_id, m.mind_key, m.name AS mind_name, m.description, " +
                "       m.capability_mask_json, m.closed_loop_bounds_json, m.is_active " +
                "  FROM " + MIND_TABLE + " m " +
                "  JOIN " + SCEN_MIND_TABLE + " sm ON sm.mind_id = m.id " +
                " WHERE sm.scenario_id = ? AND sm.is_deleted = 0 " +
                "   AND m.is_active = 1 AND m.is_deleted = 0 " +
                " ORDER BY sm.priority ASC, m.name ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, scenarioId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /** MindScopeGuard：判定 mind 是否绑定到给定场景（is_deleted=0）。 */
    public boolean isMindBoundToScenario(String scenarioId, String mindId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + SCEN_MIND_TABLE +
                " WHERE scenario_id = ? AND mind_id = ? AND is_deleted = 0",
                Integer.class, scenarioId, mindId);
        return n != null && n > 0;
    }

    /** 判断 mind 注册行是否存在（未软删）。 */
    public boolean mindExists(String mindId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT 1 x FROM " + MIND_TABLE + " WHERE id = ? AND is_deleted = 0",
                    mindId) != null;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    /** E2 前置：读 Mind 的 capabilityMask 用于评分过滤（未映射 → null，不丢行）。 */
    public String readCapabilityMask(String mindId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT capability_mask_json FROM " + MIND_TABLE +
                    " WHERE id = ? AND is_deleted = 0",
                    String.class, mindId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
