package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * E4 信念只读（设计-05 F05-06 / C.1 / DG-7；数据访问下沉 DAO 层 F05-13）：
 * <ul>
 *   <li>capability = mask ∩ domainAlias(domain)，空 → null，行不丢（DG-7；前端展示"未映射"）</li>
 *   <li>带 mind → {@code AND mind_id=?} 严格过滤（禁静默兜底）；排序 {@code update_time desc}</li>
 * </ul>
 *
 * <p><b>承参过滤口径（V298 / #146az 方案 A，用户裁决 2026-10-31）</b>：权威载体
 * {@code ecos_cognitive.ecos_cognitive_belief} 按详设 §8.4.2 只承 {@code mind_id/subject_type/
 * subject_id} 三枚归属列，<b>无 scenario_id 列</b>——场景维度经绑表
 * {@code ecos_cognitive.ecos_cognitive_scenario_mind}（多对多，unique(scenario_id,mind_id,is_deleted)）推导：
 * 不带 mind 参时 {@code (mind_id IS NULL OR mind_id IN (场景绑表))}（未挂 Mind 的行属全场景可见，
 * 维持 V129 全量读面行为）；带 mind 参时 {@code mind_id = ?} 严格等值 <b>且</b> 该 mind 必须属于
 * 该场景绑表（禁静默兜底到未绑定 mind）。不镜像 V297 hypothesis 直接建 scenario_id 列策略——
 * 两者读链设计语义不同（hypothesis E3 直挂场景，belief E4 经 Mind 绑定），防列语义错拼。</p>
 *
 * <p>领域→能力映射逻辑归 Service 层（{@code DomainAliasResolver}），DAO 以
 * {@code Function<String,String>} 接收映射函数，避免 dao→service 反向依赖。</p>
 */
@Repository
public class BeliefReader extends JdbcTemplateDaoSupport {

    static final String TABLE = "ecos_cognitive.ecos_cognitive_belief";
    static final String SCENARIO_MIND_SUBQUERY =
        "ecos_cognitive.ecos_cognitive_scenario_mind";

    /** {@code aliasMapper}: domain 名 → capability 枚举名（未匹配返 null）。 */
    public List<CognitiveContractSchema.BeliefVO> listForScenario(String scenarioId, String mindId,
                                                                  List<MindCapabilityMask> caps,
                                                                  Function<String, String> aliasMapper) {
        String sql;
        Object[] args;
        if (mindId == null || mindId.isBlank()) {
            sql = "SELECT id, variable_name, domain, update_time, probability " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 " +
                  "   AND (mind_id IS NULL OR mind_id IN " +
                  "        (SELECT mind_id FROM " + SCENARIO_MIND_SUBQUERY +
                  "         WHERE scenario_id = ? AND is_deleted = 0)) " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST " +
                  " LIMIT 500";
            args = new Object[]{scenarioId};
        } else {
            sql = "SELECT id, variable_name, domain, update_time, probability " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 AND mind_id = ? " +
                  "   AND mind_id IN " +
                  "       (SELECT mind_id FROM " + SCENARIO_MIND_SUBQUERY +
                  "        WHERE scenario_id = ? AND is_deleted = 0) " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST " +
                  " LIMIT 500";
            args = new Object[]{mindId, scenarioId};
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            return rows.stream().map(r -> toVO(r, caps, aliasMapper)).toList();
        } catch (DataAccessException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                    CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
        }
    }

    private CognitiveContractSchema.BeliefVO toVO(Map<String, Object> r,
                                                  List<MindCapabilityMask> caps,
                                                  Function<String, String> aliasMapper) {
        String domain = (String) r.get("domain");
        String cap = null;
        if (caps != null && !caps.isEmpty()) {
            String viaAlias = aliasMapper == null ? null : aliasMapper.apply(domain);
            if (viaAlias != null) {
                for (MindCapabilityMask m : caps) {
                    if (m.name().equalsIgnoreCase(viaAlias)) { cap = m.name(); break; }
                }
            }
        }
        String updatedAt = readUpdatedAt(r.get("update_time"));
        return new CognitiveContractSchema.BeliefVO(
                (String) r.get("id"),
                null,
                (String) r.get("variable_name"),
                domain,
                probabilityOf(r.get("probability")),   // V262 真值上填；null=未标定（禁 0 替真值，禁 clamp）
                null,   // strength：同
                cap,
                updatedAt);
    }

    /** V262 probability NUMERIC(6,5) → Double；NULL/非数值 ⇒ null（未标定，调用方禁补 0）。 */
    static Double probabilityOf(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static String readUpdatedAt(Object ut) {
        if (ut instanceof OffsetDateTime odt) return odt.toString();
        if (ut instanceof java.time.LocalDateTime ldt) return ldt.toString();
        if (ut instanceof java.util.Date d) return d.toInstant().toString();
        return Instant.now().toString();
    }
}
