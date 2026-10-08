package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

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
 * <p>领域→能力映射逻辑归 Service 层（{@code DomainAliasResolver}），DAO 以
 * {@code Function<String,String>} 接收映射函数，避免 dao→service 反向依赖。</p>
 */
@Repository
public class BeliefReader extends JdbcTemplateDaoSupport {

    static final String TABLE = "public.ecos_cognitive_belief";

    /** {@code aliasMapper}: domain 名 → capability 枚举名（未匹配返 null）。 */
    public List<CognitiveContractSchema.BeliefVO> listForScenario(String scenarioId, String mindId,
                                                                  List<MindCapabilityMask> caps,
                                                                  Function<String, String> aliasMapper) {
        String sql;
        Object[] args;
        if (mindId == null || mindId.isBlank()) {
            sql = "SELECT id, variable_name, domain, update_time " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 AND scenario_id = ? " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST " +
                  " LIMIT 500";
            args = new Object[]{scenarioId};
        } else {
            sql = "SELECT id, variable_name, domain, update_time " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 AND scenario_id = ? AND mind_id = ? " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST " +
                  " LIMIT 500";
            args = new Object[]{scenarioId, mindId};
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            return rows.stream().map(r -> toVO(r, caps, aliasMapper)).toList();
        } catch (DataAccessException e) {
            throw new CognitiveGuardException("ECOS-COG-501",
                    com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
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
                null,   // probability：R-DDL 补齐列后上填（禁 clamp，禁静默）
                null,   // strength：同
                cap,
                updatedAt);
    }

    private static String readUpdatedAt(Object ut) {
        if (ut instanceof OffsetDateTime odt) return odt.toString();
        if (ut instanceof java.time.LocalDateTime ldt) return ldt.toString();
        if (ut instanceof java.util.Date d) return d.toInstant().toString();
        return Instant.now().toString();
    }
}
