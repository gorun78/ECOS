package com.chinacreator.gzcm.engine.cognitive2.mind;

import java.util.Arrays;
import java.util.Optional;

/**
 * 认知模型资产（Mind）能力掩码 —— 唯一权威枚举（分册05 F05-11 / REQ-COG-04 §1.1）。
 *
 * <p><b>为什么在 api 模块</b>：E1 detect 的 {@code capabilityMask[]} 出参 / E4
 * {@code belief.domain ↔ capability} 投影都必须由前后端共用同一份枚举真源，
 * 故按 F05-11 要点放到 {@code cognitive-engine-api}（下游 impl/前端 serializer 只引用，
 * 不得在 impl 内再造私有枚举或字符串常量）。
 *
 * <p><b>五个成员（REQ-COG-04 §2.2 表）</b>：DETECT / FORECAST / SIMULATE / PLAN / REVIEW ——
 * 值与 yaml/前端契约一致，DB 存储在 {@code capability_mask_json} TEXT 列（V187，DR04 _json 后缀 +
 * TEXT），序列化一律经 {@link MindCapabilityMaskCodec}，禁散点字符串常量。
 *
 * <p><b>禁项</b>（F05-01 契约单源）：
 * <ol>
 *   <li>impl 包内不得再出现同成员的私有 {@code enum}（ArchUnit/扫描护栏，见 MindCapabilityMaskTest）；</li>
 *   <li>DB 类型不直接落 JSONB（V187 显式 TEXT + {@code _json} 后缀，MC02）；</li>
 *   <li>值形态 = 全大写字母串，与合同文档 §2.2 逐字对齐。</li>
 * </ol>
 *
 * <p><b>别名表 / KB 维度 recode</b>（F05-11 要点3）：本期未实现，中文别名与 KB 维度左联
 * 属 sysman 配置门面事项，不在此类暴露。
 */
public enum MindCapabilityMask {

    /** 感知/探测能力（E1 detect 场景可用 Mind 的意识底座）。 */
    DETECT("DETECT"),
    /** 预测/指标外推（E2 operation-eval 评分域）。 */
    FORECAST("FORECAST"),
    /** 反事实/情景模拟（F05-10 REQ-COG-03 探索式通道；exploratory=true 非核算口径）。 */
    SIMULATE("SIMULATE"),
    /** 计划/动作建议（未来 REQ-COG-04 §4.1 正式 KB 维度左联预留位）。 */
    PLAN("PLAN"),
    /** 复盘/审计反馈（对抗 B-1"推理结果不落盘"的例外——审计事件走 runtime-event，非本库）。 */
    REVIEW("REVIEW");

    private final String wire;

    MindCapabilityMask(String wire) {
        this.wire = wire;
    }

    /** 契约字面（DB/JSON/前端统一形态，大写）。 */
    public String wire() {
        return wire;
    }

    /** 大小写不敏感解析；未识别 → empty（读侧由调用方决定 issue 记录还是抛 400）。 */
    public static Optional<MindCapabilityMask> fromWire(String v) {
        if (v == null) return Optional.empty();
        String u = v.trim().toUpperCase();
        return Arrays.stream(values()).filter(e -> e.wire.equalsIgnoreCase(u)).findFirst();
    }
}
