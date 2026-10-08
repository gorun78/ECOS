package com.chinacreator.gzcm.engine.cognitive2.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.springframework.stereotype.Service;

/**
 * 认知引擎配置数据访问服务 — sys_config（config_group='cognitive'）的读 / 写查询。
 *
 * <p>供 {@code CognitiveConfigController} 调用：读写全部下沉 Service 层
 * （架构铁律 §3.6：Controller 不得直接访问数据库）。</p>
 *
 * <p>F05-13 收口：sys_config 的 SELECT / UPDATE / INSERT 不再自持 DAO（旧
 * {@code ConfigQueryDao} 直写他域表违反跨域边界），统一委托 sysman  sanctioned
 * 配置门面 {@link SysConfigService}（与 data-engine 同款声明，label 推导由门面完成）；
 * 异常语义不变：门面抛出的 {@code DataAccessException} 原样上浮，由调用方 Controller
 * 捕获并降级返回，与既有 HTTP 行为一致。</p>
 */
@Service
public class CognitiveConfigQueryService {

    /**
     * 详设-05 §E.5 冻结的四枚认知域配置键（**单源**：全仓只准在本类出现键字面量并被消费方调用）。
     *
     * <p>E.5 红线"禁 {@code @Value} 散落默认值造成两态漂移"的落地方式 = 键名与缺省裁决都收在这一个类，
     * 业务侧只调门面方法；缺省值只在门面声明一次（yml 不再定义同名键，故不存在两态）。</p>
     */
    public static final String KEY_ENGINE_ENABLED = "ecos.cognitive.enabled";
    public static final String KEY_DEMO = "ecos.cognitive.demo";
    public static final String KEY_EVAL_TREND_WEIGHT = "ecos.cognitive.eval.trend_weight";
    public static final String KEY_DOMAIN_ALIAS = "ecos.cognition.domain_alias";

    /** E2 本期口径：{@code trend_weight} 恒 1（PRD-05 §1.1 E2 规格 1，配置为预留入口、不实现动态）。 */
    public static final double TREND_WEIGHT_DEFAULT = 1.0d;

    /** 门面组名（sys_config.config_group）。 */
    public static final String GROUP_COGNITIVE = "cognitive";

    private final SysConfigService sysConfigService;

    public CognitiveConfigQueryService(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 拉取指定组的全部配置行（active），按 sort_order、config_key 排序。
     *
     * @param group 配置组（如 {@code cognitive}）
     * @return 原始行列表（config_key / config_value / description / config_type / config_label）
     */
    public List<Map<String, Object>> listActiveConfigRows(String group) {
        return sysConfigService.listByGroup(group);
    }

    /**
     * 单条 upsert：已存在 → UPDATE；不存在 → 经门面 INSERT（config_type=string）。
     *
     * @param key   配置 key
     * @param value 配置值（非 null，调用方保证空值传 ""）
     * @param group 配置组
     * @return 实际 UPDATE 命中行数（命中即视为已存在；insert 命中返回 0）
     */
    public int upsertConfigValue(String key, String value, String group) {
        Map<String, Object> existing = sysConfigService.getByKey(key);
        if (existing != null) {
            if (sysConfigService.updateValue(key, value)) {
                return 1;
            }
            // 并发窗口：行刚被删除 → 走门面补插（返回值仍记 insert）
        }
        sysConfigService.upsertValue(key, value, group, "string", "");
        return 0;
    }

    // ── E.5 类型化读面（业务侧唯一入口，键名/缺省单源在本类）──────────────

    /** {@code ecos.cognitive.enabled}：未配置 ⇒ 引擎启用（与既有 yml 缺省一致，缺省只在此声明一次）。 */
    public boolean engineEnabled() {
        return sysConfigService.getBoolean(KEY_ENGINE_ENABLED, true);
    }

    /** {@code ecos.cognitive.demo}：未配置 ⇒ false（生产不可达，F05-16 要点 5）。 */
    public boolean demoEnabled() {
        return sysConfigService.getBoolean(KEY_DEMO, false);
    }

    /**
     * {@code ecos.cognitive.eval.trend_weight}：非数/未配置 ⇒ {@link #TREND_WEIGHT_DEFAULT}（=1）。
     *
     * <p>不抛异常是刻意的：E2 是读路径，权重解析失败降级为 1 与"恒 1"的本期口径同值，
     * 但响应里的 {@code trendWeightSource} 必须如实带出实际生效值来源（禁伪造已配置）。</p>
     */
    public double evalTrendWeight() {
        String raw = sysConfigService.getString(KEY_EVAL_TREND_WEIGHT);
        if (raw == null || raw.isBlank()) {
            return TREND_WEIGHT_DEFAULT;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isFinite(v) && v >= 0d ? v : TREND_WEIGHT_DEFAULT;
        } catch (NumberFormatException e) {
            return TREND_WEIGHT_DEFAULT;
        }
    }

    /** trend_weight 生效来源标注（配置行缺失 ⇒ 标 DEFAULT，供响应侧诚实声明）。 */
    public String evalTrendWeightSource() {
        String raw = sysConfigService.getString(KEY_EVAL_TREND_WEIGHT);
        return raw == null || raw.isBlank()
                ? KEY_EVAL_TREND_WEIGHT + "(DEFAULT)"
                : KEY_EVAL_TREND_WEIGHT;
    }

    /**
     * {@code ecos.cognition.domain_alias}：{@code domain=CAPABILITY} 以 {@code ;} 或换行分隔。
     *
     * <p>REQ-COG-04 规格 3"新增别名不改代码"的载体。<b>未配置 ⇒ 空表</b>（禁内置别名兜底，
     * 那正是代码/配置两态漂移）；未匹配的 domain 由调用方按 DG-7 记 {@code capability=null}，行不丢。</p>
     */
    public Map<String, String> domainAliases() {
        String raw = sysConfigService.getString(KEY_DOMAIN_ALIAS);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String pair : raw.split("[;\n]")) {
            String item = pair.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0 || eq == item.length() - 1) {
                continue;   // 形态不符的条目直接忽略（不猜），capability 归 null 由调用方兜语义
            }
            out.put(item.substring(0, eq).trim(), item.substring(eq + 1).trim().toUpperCase(java.util.Locale.ROOT));
        }
        return Map.copyOf(out);
    }
}
