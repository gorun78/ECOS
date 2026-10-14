package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F10-08 阈值单源门禁（详细设计-10 §10.2.8 + V265 seed 12 行的读取端语义）。
 *
 * <p>钉三件事：①<b>代码零兜底默认</b>——键取不到就是缺席（缺席⇒UNKNOWN），
 * 不许"代码里藏一个看起来合理的数字"；②<b>命中链</b>——profile 专属行优先于 default 行，
 * 且 {@code resolvedFrom} 必须留痕（同一检查项在不同档位取到不同界，是定级差异的唯一解释来源）；
 * ③<b>sysman 缺席不炸链</b>——记空集合让逐项落 UNKNOWN（保守方向），而不是抛异常把整个评估拖死。</p>
 */
class ThresholdPolicyTest {

    @Test
    @DisplayName("键形态单源：wagent.readiness.{profile}.{fragment}（禁调用点自拼）")
    void keyFormatIsSingleSourced() {
        assertEquals("wagent.readiness.default.data.check_coverage",
                ThresholdPolicy.keyOf(null, "data.check_coverage"));
        assertEquals("wagent.readiness.forecast.data.check_coverage",
                ThresholdPolicy.keyOf("forecast", "data.check_coverage"));
        assertEquals("wagent.readiness.forecast.data.check_coverage",
                ThresholdPolicy.keyOf("  forecast  ", "data.check_coverage"), "档位名先归一再拼键");
        assertEquals("wagent.readiness.threshold", ThresholdPolicy.CONFIG_GROUP,
                "组名必须等于 V265 seed 的 config_group 列值");
    }

    @Test
    @DisplayName("profile 专属行优先；未命中回落 default 行并留痕 resolvedFrom")
    void profileRowWinsAndDefaultFallbackIsRecorded() {
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString("wagent.readiness.forecast.data.check_coverage")).thenReturn("0.85");
        when(cfg.getString("wagent.readiness.default.data.check_coverage")).thenReturn("0.7");
        ThresholdPolicy policy = policyWith(cfg);

        ThresholdSet forecast = policy.resolve("forecast", ReadinessCheckCode.DATA_COVERAGE);
        assertTrue(forecast.present());
        assertEquals("0.85", forecast.value("data.check_coverage").orElseThrow().raw());
        assertEquals("forecast", forecast.value("data.check_coverage").orElseThrow().resolvedFrom());
        assertEquals("wagent.readiness.forecast.data.check_coverage",
                forecast.value("data.check_coverage").orElseThrow().effectiveKey());

        ThresholdSet def = policy.resolve("default", ReadinessCheckCode.DATA_COVERAGE);
        assertEquals("0.7", def.value("data.check_coverage").orElseThrow().raw());
        assertEquals("default", def.value("data.check_coverage").orElseThrow().resolvedFrom());

        // forecast 档下没有 forecast 专属行 ⇒ 回落 default，resolvedFrom 必须写 default（禁冒充 profile 命中）
        when(cfg.getString("wagent.readiness.forecast.data.staleness_days")).thenReturn(null);
        when(cfg.getString("wagent.readiness.default.data.staleness_days")).thenReturn("7");
        ThresholdSet fell = policy.resolve("forecast", ReadinessCheckCode.DATA_STALENESS);
        assertEquals("7", fell.value("data.staleness_days").orElseThrow().raw());
        assertEquals("default", fell.value("data.staleness_days").orElseThrow().resolvedFrom());
    }

    @Test
    @DisplayName("两档皆无该键 ⇒ 缺席（present=false ⇒ hasAll=false ⇒ 检查项 UNKNOWN），禁代码兜底默认")
    void absentKeysYieldUnsetNotCodeDefault() {
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString(anyString())).thenReturn(null);
        ThresholdPolicy policy = policyWith(cfg);

        ThresholdSet result = policy.resolve("forecast", ReadinessCheckCode.DATA_COVERAGE);
        assertFalse(result.present(), "缺席必须如实缺席——代码里没有任何阈值字面量可兜");
        assertFalse(result.hasAll(List.of("data.check_coverage")));
        assertEquals(List.of("data.check_coverage"),
                result.missing(ReadinessCheckCode.DATA_COVERAGE.thresholdFragments()));
    }

    @Test
    @DisplayName("多片段项只命中一半 ⇒ hasAll=false（禁'半截阈值 + 半截猜测'混算）")
    void partialFragmentHitIsNotJudgeable() {
        SysConfigService cfg = mock(SysConfigService.class);
        when(cfg.getString("wagent.readiness.default.know.sample_size_min")).thenReturn("30");
        when(cfg.getString("wagent.readiness.default.know.sample_size_trend_floor")).thenReturn("  ");
        ThresholdSet result = policyWith(cfg).resolve(null, ReadinessCheckCode.KNOW_SAMPLE_SIZE);

        assertTrue(result.present(), "命中一条也算命中，但……");
        assertFalse(result.hasAll(ReadinessCheckCode.KNOW_SAMPLE_SIZE.thresholdFragments()),
                "……声明的片段没全命中就是不可判档");
        assertEquals(List.of("know.sample_size_trend_floor"),
                result.missing(ReadinessCheckCode.KNOW_SAMPLE_SIZE.thresholdFragments()));
    }

    @Test
    @DisplayName("sysman 未装配 ⇒ 空集合 + WARN，不抛异常（探测面结论仍可用，档位保守落 UNKNOWN）")
    void missingSysConfigBeanDoesNotBreakEvaluation() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SysConfigService> absent = mock(ObjectProvider.class);
        when(absent.getIfAvailable()).thenReturn(null);

        ThresholdSet result = new ThresholdPolicy(absent)
                .resolve("default", ReadinessCheckCode.ONTO_CALIBER_DEFINED);
        assertFalse(result.present());
        assertEquals("default", result.profileId(), "即便取不到值也要留档位名（threshold_json 可复盘）");
    }

    @Test
    @DisplayName("意图→档位：V265 只有 default/forecast 两档 ⇒ 仅 FORECAST 命中加严档")
    void intentMapsOnlyToSeededProfiles() {
        assertEquals(ReadinessCheckCode.PROFILE_FORECAST, ThresholdPolicy.profileForIntent("FORECAST"));
        assertEquals(ReadinessCheckCode.PROFILE_FORECAST, ThresholdPolicy.profileForIntent(" forecast "));
        assertEquals(ThresholdPolicy.PROFILE_DEFAULT, ThresholdPolicy.profileForIntent(null));
        assertEquals(ThresholdPolicy.PROFILE_DEFAULT, ThresholdPolicy.profileForIntent(""));
        // 其余意图一律 default：新增档位必须先有 seed 行，禁代码自造档位名
        for (String intent : List.of("EXPLAIN", "MONITOR", "OPTIMIZE", "NOT_A_REAL_INTENT")) {
            assertEquals(ThresholdPolicy.PROFILE_DEFAULT, ThresholdPolicy.profileForIntent(intent),
                    intent + " 不得映射到未 seed 的档位");
        }
        assertEquals("default", ThresholdPolicy.normalize("  "));
    }

    @Test
    @DisplayName("八项检查项的片段声明与键模板互推（check_code ↔ 配置键同源，禁一码多义）")
    void fragmentsAreDerivableFromCheckCodes() {
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            List<String> fragments = ThresholdPolicy.fragmentsOf(code);
            assertFalse(fragments.isEmpty(), code.checkCode() + " 必须声明阈值片段");
            for (String fragment : fragments) {
                String key = ThresholdPolicy.keyOf(ThresholdPolicy.PROFILE_DEFAULT, fragment);
                assertTrue(key.startsWith("wagent.readiness.default."), key);
                // 片段名必须落在该检查项自己的 check_code 命名空间内（同源约束）
                assertTrue(fragment.startsWith(code.checkCode().split("\\.")[0] + "."),
                        code.checkCode() + " 的片段 " + fragment + " 与其引擎段不同源");
            }
        }
    }

    private static ThresholdPolicy policyWith(SysConfigService cfg) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SysConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cfg);
        return new ThresholdPolicy(provider);
    }
}
