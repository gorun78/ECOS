package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回测/复审阈值读取器（详设-09 v1.2 §十一 H-4 要点 3 / W226：阈值配置单源）。
 *
 * <p>本类是 {@link BacktestMetricCalculator.Thresholds} 的<b>唯一</b>构造点，且只准经
 * {@link CognitiveConfigQueryService}（sysman {@code SysConfigService} 门面）取值：</p>
 * <ul>
 *   <li>键名字面量只在门面出现（与 §E.5 同纪律，守卫见 {@code BacktestThresholdSingleSourceTest}）；</li>
 *   <li>未配置 ⇒ 该枚阈值为 {@code null} 并进 {@code sources} 标注 {@code (UNSET)}，
 *       <b>禁</b>在本类写任何缺省常量（那正是"代码默认 vs 库内值"两态漂移）；</li>
 *   <li>请求体永远进不了本类 ⇒ R-66 ①「禁前端传阈值」的结构保证（新端点另有一道 400 拒绝，
 *       见 {@code OutcomeBacktestController}）。</li>
 * </ul>
 */
@Component
public class BacktestReviewThresholdReader {

    private final CognitiveConfigQueryService config;

    public BacktestReviewThresholdReader(CognitiveConfigQueryService config) {
        this.config = config;
    }

    /** 取全部五枚阈值（逐枚带生效来源标注）。 */
    public BacktestMetricCalculator.Thresholds read() {
        Map<String, String> sources = new LinkedHashMap<>();
        Integer minSamples = readInt(CognitiveConfigQueryService.KEY_BACKTEST_MIN_SAMPLES, sources);
        BigDecimal mapeRatio = readDecimal(CognitiveConfigQueryService.KEY_BACKTEST_MAPE_RATIO, sources);
        BigDecimal onTargetBand = readDecimal(CognitiveConfigQueryService.KEY_BACKTEST_ON_TARGET_BAND, sources);
        BigDecimal minIntervalCoverage = readDecimal(
                CognitiveConfigQueryService.KEY_BACKTEST_MIN_INTERVAL_COVERAGE, sources);
        BigDecimal minDataCoverage = readDecimal(CognitiveConfigQueryService.KEY_BACKTEST_MIN_DATA_COVERAGE, sources);
        return new BacktestMetricCalculator.Thresholds(onTargetBand, mapeRatio, minSamples,
                minIntervalCoverage, minDataCoverage, Map.copyOf(sources));
    }

    private Integer readInt(String key, Map<String, String> sources) {
        Integer v = config.integerConfig(key);
        sources.put(key, sourceOf(key));
        return v;
    }

    private BigDecimal readDecimal(String key, Map<String, String> sources) {
        BigDecimal v = config.decimalConfig(key);
        sources.put(key, sourceOf(key));
        return v;
    }

    /**
     * 生效来源：区分"配置行缺失"（{@code (UNSET)}）与"配置行存在但值非法"
     * （{@code (INVALID)}）—— 两者都必须 fail-closed，但运维定位串不能混为一谈（DG-9）。
     */
    private String sourceOf(String key) {
        if (!config.configPresent(key)) {
            return key + "(UNSET)";
        }
        return config.decimalConfig(key) == null ? key + "(INVALID)" : key;
    }
}
