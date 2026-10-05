package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W232/C214 · 四源取值优先级 + 缺失时显式拦截（禁静默 0/均值，FC-05 门禁前置）。
 *
 * <p>验收标识：{@code mvn -Dtest=RealizationSourceResolutionTest#recordsSourceAndFallsBackWhenProfileMissing}。</p>
 *
 * <p>优先级（数值越小越优先，0=最高）：
 * ACTUAL(0) → PLAN(1) → PROFILE_IMPUTED(2) → MANUAL_OVERRIDE(3)；COMPUTED(9) 仅聚合父节点使用。</p>
 *
 * <p>R-63② 已批准：前置全缺时先把计算与六要素建起来，取值源<b>必须显式登记</b>到
 * source_ref 结构，禁静默取 0/均值（否则触发 DQ_BLOCK 拦截）。</p>
 */
class RealizationSourceResolutionTest {

    @Test
    @DisplayName("§七 W232/C214 · 四源优先级：ACTUAL 存在时优先于 PROFILE_IMPUTED/MANUAL_OVERRIDE")
    void recordsSourceAndFallsBackWhenProfileMissing() {
        Map<String, Candidate> mixed = new LinkedHashMap<>();
        mixed.put("actual", cand(SourceType.ACTUAL, "100.00", "fact:P1/C1"));
        mixed.put("profile", cand(SourceType.PROFILE_IMPUTED, "200.00", "profile_key:rate_25"));
        mixed.put("manual", cand(SourceType.MANUAL_OVERRIDE, "300.00", "assumption:delay_q4"));
        // 期望：应选 ACTUAL，且其 sourceRef 应可查
        Candidate chosen = FcRunCalc.resolveSource(mixed);
        assertEquals(SourceType.ACTUAL, chosen.type(), "应选 ACTUAL（priority=0）");
        assertEquals(0, chosen.amount().compareTo(new BigDecimal("100.00")));
        assertTrue(chosen.sourceRef() != null && chosen.sourceRef().containsKey("fact:P1/C1"),
                "source_ref 结构应显式登记落地（非静默空 map）");
    }

    @Test
    @DisplayName("缺 ACTUAL ⇒ 回退到 PLAN（不静默 0，选到第二高优先级源）")
    void fallsBackWhenActualMissing() {
        Map<String, Candidate> onlyPlan = new LinkedHashMap<>();
        onlyPlan.put("plan", cand(SourceType.PLAN, "800.00", "plan:V1"));
        onlyPlan.put("profile", cand(SourceType.PROFILE_IMPUTED, "200.00", "profile_key:rate_25"));
        Candidate chosen = FcRunCalc.resolveSource(onlyPlan);
        assertEquals(SourceType.PLAN, chosen.type());
        assertEquals(0, chosen.amount().compareTo(new BigDecimal("800.00")));
        assertTrue(chosen.sourceRef().containsKey("plan:V1"));
    }

    @Test
    @DisplayName("全源缺失 ⇒ 抛（DQ_BLOCK 门禁拦截，禁静默 0/均值）")
    void emptySourcesRejected() {
        assertThrows(IllegalStateException.class,
                () -> FcRunCalc.resolveSource(Map.of()),
                "空 map 必须拦，不能给零/均值默认");
        assertThrows(IllegalStateException.class, () -> FcRunCalc.resolveSource(null));
    }

    @Test
    @DisplayName("优先级数值方法（priority() 单调缩小）——供状态图/界面徽标展示")
    void priorityOrderIsMonotonic() {
        assertTrue(SourceType.ACTUAL.priority() < SourceType.PLAN.priority());
        assertTrue(SourceType.PLAN.priority() < SourceType.PROFILE_IMPUTED.priority());
        assertTrue(SourceType.PROFILE_IMPUTED.priority() < SourceType.MANUAL_OVERRIDE.priority());
        assertTrue(SourceType.MANUAL_OVERRIDE.priority() < SourceType.COMPUTED.priority());
    }

    private static Candidate cand(SourceType t, String amt, String ref) {
        Map<String, Object> refMap = new LinkedHashMap<>();
        refMap.put(ref, true);
        return new Candidate(t, new BigDecimal(amt), refMap);
    }
}
