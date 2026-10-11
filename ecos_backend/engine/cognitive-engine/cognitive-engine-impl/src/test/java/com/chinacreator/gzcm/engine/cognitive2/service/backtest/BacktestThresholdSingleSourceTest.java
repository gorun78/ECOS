package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阈值配置单源冻结测试（详设-09 v1.2 §十一 H-4 要点 3 / W226；R-66 ① 的库侧对偶面）。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li>{@link BacktestReviewThresholdReader} 是 {@code Thresholds} 的唯一构造点，且<b>零缺省常量</b>
 *       —— 未配置一律 {@code null} 上浮（禁"代码默认 vs 库内值"两态漂移）；</li>
 *   <li>键名字面量只准出现在 {@link CognitiveConfigQueryService}（与 §E.5 同纪律）；</li>
 *   <li>生效来源标注可区分"未配置 (UNSET)"与"配置了但值非法 (INVALID)"（DG-9 定位串不得混同）。</li>
 * </ol>
 */
@DisplayName("H-4 阈值配置单源（BacktestReviewThresholdReader）")
class BacktestThresholdSingleSourceTest {

    private static final List<String> ALL_KEYS = List.of(
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES,
            CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO,
            CognitiveConfigQueryService.KEY_BACKTEST_ON_TARGET_BAND,
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_INTERVAL_COVERAGE,
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_DATA_COVERAGE);

    private static final Path MAIN_SRC = Path.of("src", "main", "java");

    private static SysConfigService configStore(Map<String, String> values) {
        SysConfigService svc = mock(SysConfigService.class);
        when(svc.getString(anyString())).thenAnswer(inv -> values.get(inv.<String>getArgument(0)));
        when(svc.getByKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return values.containsKey(key) ? Map.of("config_key", key, "config_value", values.get(key)) : null;
        });
        return svc;
    }

    private static BacktestReviewThresholdReader reader(Map<String, String> values) {
        return new BacktestReviewThresholdReader(new CognitiveConfigQueryService(configStore(values)));
    }

    @Test
    @DisplayName("五枚阈值全部未配置 ⇒ 逐枚 null + (UNSET) 来源，禁任何缺省兜底")
    void unsetThresholdsStayNull() {
        BacktestMetricCalculator.Thresholds t = reader(Map.of()).read();
        assertNull(t.onTargetBandRatio());
        assertNull(t.mapeRatio());
        assertNull(t.minSampleCount());
        assertNull(t.minIntervalCoverage());
        assertNull(t.minDataCoverage());
        assertEquals(5, t.sourcesOrEmpty().size());
        ALL_KEYS.forEach(k -> assertEquals(k + "(UNSET)", t.sourcesOrEmpty().get(k)));
    }

    @Test
    @DisplayName("配置齐备 ⇒ 逐枚取库内真值（BigDecimal 承载，MC02 禁 double）")
    void configuredThresholdsComeFromStore() {
        BacktestMetricCalculator.Thresholds t = reader(Map.of(
                CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, "5",
                CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO, "0.15",
                CognitiveConfigQueryService.KEY_BACKTEST_ON_TARGET_BAND, "0.03",
                CognitiveConfigQueryService.KEY_BACKTEST_MIN_INTERVAL_COVERAGE, "0.80",
                CognitiveConfigQueryService.KEY_BACKTEST_MIN_DATA_COVERAGE, "0.90")).read();
        assertEquals(Integer.valueOf(5), t.minSampleCount());
        assertEquals(0, new BigDecimal("0.15").compareTo(t.mapeRatio()));
        assertEquals(0, new BigDecimal("0.03").compareTo(t.onTargetBandRatio()));
        assertEquals(0, new BigDecimal("0.80").compareTo(t.minIntervalCoverage()));
        assertEquals(0, new BigDecimal("0.90").compareTo(t.minDataCoverage()));
        ALL_KEYS.forEach(k -> assertEquals(k, t.sourcesOrEmpty().get(k)));
    }

    @Test
    @DisplayName("配置行存在但值非数 ⇒ null + (INVALID)，与 (UNSET) 严格可分")
    void invalidValueIsLabelledDistinctlyFromUnset() {
        BacktestMetricCalculator.Thresholds t = reader(Map.of(
                CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO, "not-a-number")).read();
        assertNull(t.mapeRatio());
        assertEquals(CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO + "(INVALID)",
                t.sourcesOrEmpty().get(CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO));
        assertEquals(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES + "(UNSET)",
                t.sourcesOrEmpty().get(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES));
    }

    @Test
    @DisplayName("配置为 0 与未配置是两种真态：0 必须照常生效")
    void zeroIsARealValueNotAnAbsence() {
        BacktestMetricCalculator.Thresholds t = reader(Map.of(
                CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, "0")).read();
        assertEquals(Integer.valueOf(0), t.minSampleCount());
        assertEquals(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES,
                t.sourcesOrEmpty().get(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES));
    }

    @Test
    @DisplayName("非整数/越界的 min_samples ⇒ null 上浮，禁截断猜值")
    void nonIntegralMinSampleDoesNotGetTruncated() {
        BacktestMetricCalculator.Thresholds fractional = reader(
                Map.of(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, "4.5")).read();
        assertNull(fractional.minSampleCount());
        // 配置行确实存在且可作小数解析 ⇒ 来源标注为"已配置"，但整数读面拒绝截断（两态各归各）
        assertEquals(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES,
                fractional.sourcesOrEmpty().get(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES));
        assertNull(reader(Map.of(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, "  ")).read()
                .minSampleCount());
    }

    @Test
    @DisplayName("键名零散落：回测阈值全名字面量只准出现在配置门面源文件")
    void keyLiteralsLiveOnlyInConfigFacade() throws IOException {
        for (String key : ALL_KEYS) {
            List<String> offenders = new ArrayList<>();
            try (Stream<Path> files = Files.walk(MAIN_SRC)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        // 门面自身以常量声明；javadoc 引用不算字面量，故只看带引号的形态
                        if (!p.endsWith("CognitiveConfigQueryService.java") && text.contains("\"" + key + "\"")) {
                            offenders.add(MAIN_SRC.relativize(p).toString());
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            assertTrue(offenders.isEmpty(), "阈值键名字面量散落（须只在 CognitiveConfigQueryService）："
                    + key + " -> " + offenders);
        }
    }

    @Test
    @DisplayName("读缝零缺省：BacktestReviewThresholdReader 源内不得出现数值字面量或缺省常量")
    void readerCarriesNoDefaultConstants() throws IOException {
        Path src = MAIN_SRC.resolve("com").resolve("chinacreator").resolve("gzcm").resolve("engine")
                .resolve("cognitive2").resolve("service").resolve("backtest")
                .resolve("BacktestReviewThresholdReader.java");
        String text = Files.readString(src, StandardCharsets.UTF_8);
        assertFalse(text.contains("DEFAULT_"), "读缝禁缺省阈值常量");
        assertFalse(text.contains("new BigDecimal(\""), "读缝禁硬编码数值");
        assertFalse(text.contains("BigDecimal.valueOf"), "读缝禁硬编码数值");
        CONSTANT_BY_KEY.values().forEach(c -> assertTrue(text.contains("CognitiveConfigQueryService." + c),
                "读缝必须引用门面常量：" + c));
    }

    /** 门面常量名（键名 → 常量），用于校验读缝只经门面取值。 */
    private static final Map<String, String> CONSTANT_BY_KEY = Map.of(
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, "KEY_BACKTEST_MIN_SAMPLES",
            CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO, "KEY_BACKTEST_MAPE_RATIO",
            CognitiveConfigQueryService.KEY_BACKTEST_ON_TARGET_BAND, "KEY_BACKTEST_ON_TARGET_BAND",
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_INTERVAL_COVERAGE, "KEY_BACKTEST_MIN_INTERVAL_COVERAGE",
            CognitiveConfigQueryService.KEY_BACKTEST_MIN_DATA_COVERAGE, "KEY_BACKTEST_MIN_DATA_COVERAGE");
}
