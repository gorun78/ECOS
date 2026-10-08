package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.HypothesisStatus;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * E3 假设只读（设计-05 F05-05 / C.1 / R-15 ①；数据访问下沉 DAO 层 F05-13）：
 * <ul>
 *   <li>状态终态四值 {@link HypothesisStatus#name()}；存量 VALID/INVALIDATED/ARCHIVED 只读别名映射（禁写入）</li>
 *   <li>必带 {@code is_deleted=0} + {@code scenario_id=?}；带 mind → {@code AND mind_id=?} 严格过滤（禁静默兜底）</li>
 *   <li>排序 {@code update_time desc nulls last + create_time desc}</li>
 * </ul>
 */
@Repository
public class HypothesisReader extends JdbcTemplateDaoSupport {

    static final String TABLE = "public.ecos_cognitive_hypothesis";

    public List<CognitiveContractSchema.HypothesisVO> listForScenario(String scenarioId, String mindId) {
        String sql;
        Object[] args;
        if (mindId == null) {
            sql = "SELECT id, hypothesis_code, reason, confidence, status, update_time " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 AND scenario_id = ? " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST";
            args = new Object[]{scenarioId};
        } else {
            sql = "SELECT id, hypothesis_code, reason, confidence, status, update_time " +
                  "  FROM " + TABLE +
                  " WHERE is_deleted = 0 AND scenario_id = ? AND mind_id = ? " +
                  " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST";
            args = new Object[]{scenarioId, mindId};
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            return rows.stream().map(this::toVO).toList();
        } catch (DataAccessException e) {
            throw new CognitiveGuardException("ECOS-COG-501",
                    com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
        }
    }

    private CognitiveContractSchema.HypothesisVO toVO(Map<String, Object> r) {
        String rawStatus = (String) r.get("status");
        HypothesisStatus canonical = HypothesisStatus.fromDbValue(rawStatus);
        double confidence = 0.0d;
        if (r.get("confidence") instanceof Number n) {
            double v = n.doubleValue();
            if (v < 0.0 || v > 1.0) {
                throw new CognitiveGuardException("ECOS-COG-504",
                        com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_INTERNAL);
            }
            confidence = v;
        }
        String updatedAt;
        Object ut = r.get("update_time");
        if (ut instanceof OffsetDateTime odt) {
            updatedAt = odt.toString();
        } else if (ut instanceof java.time.LocalDateTime ldt) {
            updatedAt = ldt.toString();
        } else if (ut instanceof java.util.Date d) {
            updatedAt = d.toInstant().toString();
        } else {
            updatedAt = Instant.now().toString();
        }
        return new CognitiveContractSchema.HypothesisVO(
                (String) r.get("id"),
                null,
                (String) r.get("hypothesis_code"),
                (String) r.get("reason"),
                confidence,
                canonical == null ? rawStatus : canonical.name(),
                null,
                updatedAt);
    }
}
