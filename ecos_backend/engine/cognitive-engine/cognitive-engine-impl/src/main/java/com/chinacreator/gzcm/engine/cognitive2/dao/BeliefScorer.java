package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * E2 评分读端（设计-05 F05-04 / C.1 / D·3；数据访问下沉 DAO 层 F05-13）。
 *
 * <p><b>本类不算数值</b>：E2 算式 {@code score = probability × trend_weight} 的唯一计算入口是纯函数
 * {@code CognitiveEvalCalculator}（W120/C102 处置"计算入口单一，可单测、可复算"）。
 * 本类只做三件事：取数（含 V262 的 {@code probability} 列位）、能力映射（经 Service 注入的
 * {@code aliasMapper}，DAO 不自持别名逻辑）、把"未标定"如实上抛（{@code probability=null}）。</p>
 *
 * <p><b>禁 0 替真值</b>（REQ-COG-09 规格 4 / X-44 伪实现族）：旧实现以 {@code score = 0.0} 硬编码兜底，
 * 使 Top-N 排序与响应数值全是伪造。现无概率标定的行以 {@code probability=null} 返回，
 * 由 Service 计入 {@code missingProbability} 并剔除排序集，不产生分数。</p>
 *
 * <p>数据源：{@code ecos_cognitive.ecos_cognitive_belief}（V191 权威载体 + V262 补 probability，R-13 ① 归位）。
 * 载体切换刻意早于写侧（{@code BeliefDao} 仍在双写期 public 面）：{@code probability} 只存在于新载体，
 * 读端不切换就没有真值来源；迁移未执行时按 C.1 前置门回 503 {@code ECOS-COG-501}（禁 fail-open 空列表）。</p>
 */
@Repository
public class BeliefScorer extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_cognitive.ecos_cognitive_belief";

    /**
     * 一条待评分 belief 的读端投影。
     *
     * @param capabilityName mask ∩ 别名映射结果，未映射 ⇒ null（DG-7，行不丢）
     * @param probability    V262 点概率列；{@code null} = 未标定（调用方禁补 0）
     */
    public record ScoreInput(String metricId, String name, String capabilityName, Double probability) {}

    public List<ScoreInput> readScoreInputs(String scenarioId, String mindId,
                                            List<MindCapabilityMask> caps,
                                            Function<String, String> aliasMapper) {
        String sql = "SELECT id AS mid, variable_name AS name, domain, probability " +
                "  FROM " + TABLE +
                " WHERE is_deleted = 0 AND status = 'ACTIVE'"
                + (mindId == null || mindId.isBlank() ? "" : " AND mind_id = ?")
                + (scenarioId == null || scenarioId.isBlank() ? "" : " AND scenario_id = ?")
                + " ORDER BY update_time DESC NULLS LAST LIMIT 500";
        Object[] args = buildArgs(mindId, scenarioId);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, args);
        } catch (DataAccessException e) {
            throw new CognitiveGuardException(CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                    CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
        }
        return rows.stream().map(r -> toInput(r, caps, aliasMapper)).toList();
    }

    private static Object[] buildArgs(String mindId, String scenarioId) {
        boolean hasMind = mindId != null && !mindId.isBlank();
        boolean hasScenario = scenarioId != null && !scenarioId.isBlank();
        if (hasMind && hasScenario) {
            return new Object[]{mindId, scenarioId};
        }
        if (hasMind) {
            return new Object[]{mindId};
        }
        if (hasScenario) {
            return new Object[]{scenarioId};
        }
        return new Object[0];
    }

    private static ScoreInput toInput(Map<String, Object> r, List<MindCapabilityMask> caps,
                                      Function<String, String> aliasMapper) {
        String domain = (String) r.get("domain");
        return new ScoreInput((String) r.get("mid"), (String) r.get("name"),
                capabilityOf(domain, caps, aliasMapper), probabilityOf(r.get("probability")));
    }

    private static Double probabilityOf(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    /** 能力 = mask ∩ (直等 | 别名映射)；空 ⇒ null（DG-7 行不丢，前端展示"未映射"）。 */
    private static String capabilityOf(String domain, List<MindCapabilityMask> caps,
                                       Function<String, String> aliasMapper) {
        if (domain == null || caps == null || caps.isEmpty()) {
            return null;
        }
        String candidate = domain.trim();
        if (aliasMapper != null) {
            String viaAlias = aliasMapper.apply(candidate);
            if (viaAlias != null && !viaAlias.isBlank()) {
                candidate = viaAlias;
            }
        }
        for (MindCapabilityMask m : caps) {
            if (m.name().equalsIgnoreCase(candidate)) {
                return m.name();
            }
        }
        return null;
    }
}
