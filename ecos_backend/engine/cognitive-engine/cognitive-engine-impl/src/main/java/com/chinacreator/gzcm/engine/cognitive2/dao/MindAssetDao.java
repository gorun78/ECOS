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

    /**
     * {@code GET /api/v1/cognitive/minds} 读面（无场景归属心智构建列表）：
     * 列 id / mind_key / name / description / is_active；
     * 全显式列、{@code is_deleted=0} 恒带、ORDER BY (id ASC) 兜底；
     * LIMIT 夹取调用方（1~200）。
     */
    public List<Map<String, Object>> listAll(int limit) {
        int lim = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, mind_key, name, description, is_active FROM " + MIND_TABLE +
                " WHERE is_deleted = 0 ORDER BY name ASC, id ASC LIMIT " + lim);
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * R4（V292）三维状态三轴只读探测：{validityStatus, reviewStatus, adoptionStatus, changeReason, supersededBy}。
     *
     * <p><b>未迁移动判据</b>：V292 三轴列未落库的库上，SELECT 会落
     * {@link org.springframework.dao.DataAccessException}（BadSqlGrammar）——本方法<b>返回 null</b>
     * 表达「三轴投影不可用」（诚实的「未迁移」态，由上层置 404A），
     * 禁把列缺失折叠成 DRAFT/PENDING_REVIEW 假值。
     * 探测列用在库时的判别由 {@link #r4AxisColumnsExist()} 提供（无行 mindId 也可判）。</p>
     *
     * @return null=三轴列未落库 或 无行（未注册/已软删，由 mindExists 预检区分）；
     *         非 null=三轴+版本历史字段投影（列值本身可空）。
     */
    public Map<String, Object> readAxisView(String mindId) {
        String sql = "SELECT validity_status, review_status, adoption_status, " +
                "change_reason, superseded_by " +
                "  FROM " + MIND_TABLE + " WHERE id = ? AND is_deleted = 0";
        try {
            return jdbcTemplate.queryForMap(sql, mindId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (org.springframework.dao.TransientDataAccessException e) {
            throw e; // 瞬时故障必须上抛（→ 5xx 原样穿透），禁冒充「未迁移」
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException e) {
            throw e; // 连接/权限类故障 → 上抛，禁折叠
        } catch (org.springframework.dao.DataAccessException e) {
            // BadSqlGrammarException：V292 三轴列未落库 → 白纸「未迁移」，禁冒充假值。
            return null;
        }
    }

    /** R4：三轴列是否在库内（information_schema 探测；通道故障按「未迁移」处置，禁冒充已迁移）。 */
    public boolean r4AxisColumnsExist() {
        // 表名按 {schema}.{table} 拆开传参，禁把裸表名写死在 SQL 字面（SchemaDriftLint §四附则1）
        String[] st = MIND_TABLE.split("\\.", 2);
        String sql = "SELECT COUNT(1) FROM information_schema.columns " +
                "  WHERE table_schema = ? " +
                "   AND table_name = ? " +
                "   AND column_name IN ('validity_status','review_status','adoption_status')";
        Integer n;
        try {
            n = jdbcTemplate.queryForObject(sql, Integer.class, st[0], st[1]);
        } catch (Exception e) {
            log.warn("R4 三轴列可用性探测通道不可用，按「未迁移」处置（禁冒充已迁移）", e);
            return false;
        }
        return n != null && n >= 3;
    }

    /**
     * R4：批量三轴投影（E1 单查询防 N+1）。
     *
     * @return mindId → {validity_status, review_status, adoption_status, change_reason, superseded_by}；
     *         空/缺失 id 不在结果中（调用方按 null 投影，禁假值）。
     */
    public java.util.Map<String, java.util.Map<String, Object>> readAxisViews(java.util.Collection<String> mindIds) {
        if (mindIds == null || mindIds.isEmpty()) return java.util.Collections.emptyMap();
        String sql = "SELECT id, validity_status, review_status, adoption_status, " +
                "change_reason, superseded_by " +
                "  FROM " + MIND_TABLE + " WHERE id IN (" +
                mindIds.stream().map(x -> "?").reduce((a, b) -> a + "," + b).orElse("null") +
                ") AND is_deleted = 0";
        List<Object> args = new java.util.ArrayList<>(mindIds);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            java.util.Map<String, java.util.Map<String, Object>> out =
                    new java.util.LinkedHashMap<>(rows.size());
            for (Map<String, Object> r : rows) {
                out.put(String.valueOf(r.get("id")), r);
            }
            return out;
        } catch (org.springframework.dao.DataAccessException e) {
            // 列消失（探测与读取之间迁移窗口）→ 按「不可用」空投影，上层按未迁移处理
            return java.util.Collections.emptyMap();
        }
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
