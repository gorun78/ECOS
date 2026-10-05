package com.chinacreator.gzcm.engine.cognitive2.fc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 分册09 §七 W244/C226 · 预测运行绑定认知模型并记录版本。
 *
 * <p>附件册二 CM-03/CM-05 · ADR-20：计算服务必须按 {@code modelId} 解引用
 * {@code ecos_cognitive_model}（V124）注册项并记录 {@code formula_version}；
 * 未接线（尚未绑定模型）的 run 需显式以 {@code bound()==false} 表明，
 * 严禁以 null/空串掩盖。</p>
 *
 * <p>验收标识：{@code mvn -Dtest=ForecastModelBindingTest#runRecordsCognitiveModelAndVersion}。</p>
 */
@DisplayName("C226 认知模型绑定 + 版本记录（W244/CM-03）")
class ForecastModelBindingTest {

    @Test
    @DisplayName("C226 · run 记录 modelId + 版本；未接线显式 bound()=false")
    void runRecordsCognitiveModelAndVersion() {
        FcRunCalc.SixElement six = FcRunCalc.SixElement.of(
                "run-mb-1", "cal_finance", "v1",
                Instant.parse("2026-01-01T00:00:00Z"),
                "snap-1", "formula-v1");
        FcRunCalc.Candidate src = new FcRunCalc.Candidate(
                FcRunCalc.SourceType.ACTUAL, new BigDecimal("100.00"),
                Map.of("sn", "s1"));
        FcRunCalc.CellInput leaf = new FcRunCalc.CellInput("m1", "2026-01", "STAGE",
                "p1", "d1", "S1", Map.of("k", src));
        // 绑定到 CM 注册项 baseline-linear v1
        FcRunCalc.RunOutput bound = FcRunCalc.calculate(six,
                List.of(leaf), "sha-scope", "sha-ovr", "formula-v1",
                new FcRunCalc.ModelBinding("baseline-linear", 1));
        assertNotNull(bound.model());
        assertEquals("baseline-linear", bound.model().modelId());
        assertEquals(1, bound.model().modelVersion());
        assertTrue(bound.model().bound());
        // 版本信息同时体现在六要素 formulaVersion 上（自证：非单方声明）
        assertEquals("formula-v1", bound.six().formulaVersion());

        // 未接线路径：显式 UNBOUND（禁止 null/空串掩盖）
        FcRunCalc.RunOutput unbound = FcRunCalc.calculate(six,
                List.of(leaf), "sha-scope", "sha-ovr", "formula-v1",
                new FcRunCalc.ModelBinding("unbound", 0));
        assertNotNull(unbound.model());
        assertFalse(unbound.model().bound(), "未接线必须经 bound()==false 显式表明");
        assertEquals("unbound", unbound.model().modelId());
        assertEquals(0, unbound.model().modelVersion());

        // 传入 null binding ⇒ fail-loud（禁止"默认"打掩护语义）
        assertThrows(IllegalStateException.class, () -> FcRunCalc.calculate(six,
                List.of(leaf), "sha-scope", "sha-ovr", "formula-v1", null));
    }
}
