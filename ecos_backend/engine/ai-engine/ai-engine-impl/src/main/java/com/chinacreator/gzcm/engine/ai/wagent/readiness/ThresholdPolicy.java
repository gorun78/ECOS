package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Readiness 阈值单源门面（详细设计-10 F10-08「{@code ThresholdPolicy.resolve(profileId, checkCode)}
 * 经 sysman 配置单源」；§10.2.8 {@code config_group='wagent.readiness.threshold'}；
 * V265 seed 12 行的读取端）。
 *
 * <p><b>类内零阈值字面量</b>：设计原文「阈值不得硬编码进代码」，且 §10.2.8 的"默认"列写的是
 * "见 seed"（不是某个数字）。故本类<b>不做代码兜底默认值</b>——键取不到即返回空集合，
 * 由 {@link ReadinessCheckCode} 记 {@code UNKNOWN}（F10-08 硬规则②禁 fail-open）。
 * 这与 {@code WAgentBudgetService} 的"代码冻结默认"形态有意不同：预算六值是设计给出的
 * 确定数字（60/40/60s/30min/3/3），阈值是产品档位（default/forecast 各异），代码无权固化。</p>
 *
 * <p><b>档位回落链</b>：先 {@code wagent.readiness.{profile}.{fragment}}，
 * 未命中再 {@code wagent.readiness.default.{fragment}}（seed 注释原文"profile 专属行优先于
 * default"）。两者皆无 ⇒ 该片段缺席；一个检查项的片段<b>全部缺席或部分缺席都不可判档</b>
 * （禁"半截阈值 + 半截猜测"）。</p>
 *
 * <p><b>sysman 未装配</b>（单测切片/启动早期）⇒ 记 WARN 并返回空集合，不抛异常：
 * 探测面本身还能给出"层可达性"结论，阈值缺席只是让逐项档位落 UNKNOWN，
 * 而 UNKNOWN 会被 {@code ReadinessGrader} 压到 C 档——保守方向，不是假绿方向。</p>
 */
@Component
public class ThresholdPolicy {

    private static final Logger log = LoggerFactory.getLogger(ThresholdPolicy.class);

    /** §10.2.8 冻结的配置组名（V265 seed {@code config_group} 列值）。 */
    public static final String CONFIG_GROUP = "wagent.readiness.threshold";

    /** seed 兜底档档位名（V265 组 1 的 9 条 default 行）。 */
    public static final String PROFILE_DEFAULT = "default";

    /** 键模板：{@code wagent.readiness.<profile>.<fragment>}（fragment 自带 engine 段与点号）。 */
    private static final String KEY_TEMPLATE = "wagent.readiness.%s.%s";

    private final ObjectProvider<SysConfigService> sysConfigProvider;

    public ThresholdPolicy(ObjectProvider<SysConfigService> sysConfigProvider) {
        this.sysConfigProvider = sysConfigProvider;
    }

    /**
     * 解析一个检查项在本档下的阈值集合。
     *
     * @param profileId 档位（{@code default} / {@code forecast}；null/空 ⇒ {@code default}）
     * @param code      检查项（声明它需要哪些阈值片段）
     * @return 命中片段集合；全缺席 ⇒ {@link ThresholdSet#unset(String)}
     */
    public ThresholdSet resolve(String profileId, ReadinessCheckCode code) {
        String profile = normalize(profileId);
        SysConfigService cfg = sysConfigProvider.getIfAvailable();
        if (cfg == null) {
            log.warn("[F10-08] SysConfigService 未装配，阈值单源不可读 ⇒ check_code={} 记 UNKNOWN（禁代码兜底默认）",
                    code.checkCode());
            return ThresholdSet.unset(profile);
        }
        Map<String, ThresholdSet.ThresholdValue> hit = new LinkedHashMap<>();
        for (String fragment : code.thresholdFragments()) {
            String profileKey = String.format(KEY_TEMPLATE, profile, fragment);
            String defaultKey = String.format(KEY_TEMPLATE, PROFILE_DEFAULT, fragment);
            String raw = cfg.getString(profileKey);
            String resolvedFrom = profile;
            if (raw == null || raw.isBlank()) {
                raw = cfg.getString(defaultKey);
                resolvedFrom = PROFILE_DEFAULT;
            }
            if (raw == null || raw.isBlank()) {
                continue; // 缺席 ⇒ 该片段不进集合 ⇒ hasAll=false ⇒ UNKNOWN
            }
            hit.put(fragment, new ThresholdSet.ThresholdValue(fragment,
                    PROFILE_DEFAULT.equals(resolvedFrom) ? defaultKey : profileKey,
                    raw.trim(), resolvedFrom));
        }
        return hit.isEmpty() ? ThresholdSet.unset(profile) : new ThresholdSet(profile, hit);
    }

    /**
     * 意图 → 档位（V265 组 1 只落 {@code default} 与 {@code forecast} 两档 ⇒
     * 只有 {@code FORECAST} 意图命中加严档，其余意图一律 default；
     * 新增档位必须先有 seed 行，禁代码自造档位名）。——裁决 D-B2④-8
     */
    public static String profileForIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return PROFILE_DEFAULT;
        }
        try {
            return GoalIntent.valueOf(intent.trim().toUpperCase(java.util.Locale.ROOT)) == GoalIntent.FORECAST
                    ? ReadinessCheckCode.PROFILE_FORECAST : PROFILE_DEFAULT;
        } catch (IllegalArgumentException unknownIntent) {
            return PROFILE_DEFAULT;
        }
    }

    /** 档位名归一（空 ⇒ default）。 */
    public static String normalize(String profileId) {
        return profileId == null || profileId.isBlank() ? PROFILE_DEFAULT : profileId.trim();
    }

    /** 供落库/排障复用的键名（禁调用点自拼字符串）。 */
    public static String keyOf(String profileId, String fragment) {
        return String.format(KEY_TEMPLATE, normalize(profileId), fragment);
    }

    /** 检查项声明的全部片段（透传，便于测试与快照）。 */
    public static List<String> fragmentsOf(ReadinessCheckCode code) {
        return code.thresholdFragments();
    }
}
