package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据引用契约（《认知域与智能域补充详细设计 v1.0》§4.1 / 附录 A EvidenceRef / REQ-COG-03）。
 *
 * <p>"指向证据源、版本、时间和可解析位置的引用"。三个定位字段
 * {@code sourceId} / {@code version} / {@code timestamp} <b>全部必填</b>（SUP-CTX-01：
 * 缺少来源的事实类项被拒绝）；缺失时 {@link #validate()} 返回违规项，
 * 由 Context Builder 以 400 {@code ECOS-COG-400S} 拦截。</p>
 *
 * <p>纯 POJO 契约：不复制证据正文（正文由权威来源保存，§8.1 层 D）。</p>
 */
@Data
public class EvidenceRef {

    /** 证据稳定 ID（必填，如 ev-001）。 */
    private String sourceId;

    /** 证据源版本/快照标识（必填，如 "12"）。 */
    private String version;

    /** 证据产生/采集时点（必填，ISO-8601 UTC）。 */
    private String timestamp;

    /**
     * 数据时点（必填，ISO-8601 UTC；§5.6 可追溯测试 / SUP-CTX-01：事实类引用可解析到
     * 来源 ID、版本和时点三要素）。与 {@code timestamp}（证据采集时点）语义区分：
     * 前者是业务数据的"截至时点"（as-of），后者是证据登记时刻。
     */
    private String asOfTime;

    /** 可解析位置（文档页码/图谱边/表列区间等，可选）。 */
    private String locator;

    /** 来源引擎标识（data / ontology / kb / cognitive / ai，§12.2 sourceRefs 溯源）。 */
    private String sourceEngine;

    /** 内容哈希（可选；用于核验引用未漂移）。 */
    private String hash;

    /**
     * 契约校验：返回缺失的必填字段名列表（顺序固定：sourceId, version, timestamp）。
     * 空列表 = 合法；非空 = 该引用不可进入 Context Pack（SUP-CTX-01）。
     */
    public List<String> validate() {
        List<String> missing = new ArrayList<>();
        if (isBlank(sourceId)) missing.add("sourceId");
        if (isBlank(version)) missing.add("version");
        if (isBlank(timestamp)) missing.add("timestamp");
        return missing;
    }

    /**
     * 可追溯校验（《补充详细设计 v1.0》§5.6 可追溯测试 / SUP-CTX-01）：
     * 事实类引用必须可解析到 <b>sourceId + version + asOfTime</b> 三要素，
     * 缺任一即违规。顺位固定：sourceId, version, asOfTime。
     *
     * <p>与 {@link #validate()} 的边界：后者是既有登记面校验（含 timestamp 采集时点）；
     * 本方法是 §5.6 "可解析到来源 ID、版本和时点" 的独立校验面，
     * Context Builder 装配事实/引用时以本方法为门禁。</p>
     *
     * @return 缺失字段名列表（空 = 合法）
     */
    public List<String> validateTraceability() {
        List<String> missing = new ArrayList<>();
        if (isBlank(sourceId)) missing.add("sourceId");
        if (isBlank(version)) missing.add("version");
        if (isBlank(asOfTime)) missing.add("asOfTime");
        return missing;
    }

    /**
     * 构造工厂（§5.6 可追溯测试："缺任一字段构造即报错"）：
     * sourceId / version / asOfTime 任一为空白 ⇒ 抛 {@link IllegalArgumentException}，
     * 禁止半残引用进入任何 Context Pack 关联链路。
     *
     * <p>注意：Lombok 无参构造 + setter 路径（Jackson 反序列化）仍走
     * {@link #validateTraceability()} 门禁（fail-loud，装配期拒绝），
     * 本工厂是显式程序化构造的立即失败入口。</p>
     */
    public static EvidenceRef of(String sourceId, String version, String asOfTime) {
        EvidenceRef ref = new EvidenceRef();
        ref.setSourceId(sourceId);
        ref.setVersion(version);
        ref.setAsOfTime(asOfTime);
        List<String> missing = ref.validateTraceability();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "EvidenceRef traceability incomplete, missing=" + missing
                            + "（§5.6 可追溯三要素 sourceId/version/asOfTime 必填）");
        }
        return ref;
    }

    /** 布尔快捷判定。 */
    public boolean isComplete() {
        return validate().isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
