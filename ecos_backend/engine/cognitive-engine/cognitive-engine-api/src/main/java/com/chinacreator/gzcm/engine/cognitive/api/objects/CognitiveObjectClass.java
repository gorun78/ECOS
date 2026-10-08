package com.chinacreator.gzcm.engine.cognitive.api.objects;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * C 层 11 类认知对象目录（PRD-05 REQ-COG-06 规格 1 / 详细设计-05 §8.1 接缝「C 层 11 类对象目录」）。
 *
 * <p>附件第二册 §16.2 冻结的 11 类对象在 ECOS 的<b>权威落点单源</b>：每类只能处于两态之一 ——
 * {@link Kind#PHYSICAL}（唯一物理载体，schema 限定名）或 {@link Kind#REFERENCE}（引用态、无表，
 * 以引用表达式表达）。构造期不变量即「二选一、不得两态并存」，由
 * {@code CognitiveObjectCatalogTest#everyCClassHasExactlyOneCarrierOrNone} 从外部再证一次。</p>
 *
 * <p><b>载体口径</b>：{@code ecos_cognitive} / {@code ecos_dw} 侧载体是 <b>R-13 ① / R-17 ① 已批准的
 * 权威落点</b>，迁移脚本已落单源目录文件（V187~V197）但<b>未对库实跑</b>（报告 §14.4 未授权项）；
 * {@code legacyWriteSuspended} 列出的旧形态（{@code public.*} 镜像）按 IR03 只停写、不 DROP、不改名。</p>
 *
 * <p><b>禁名</b>：{@code kb_cognitive_*}、{@code kb_mind_registry}、{@code cog_/agt_/aim_} 裸前缀
 * 系 ADR-8 §5 / Q11 / DR01+ST07 明令禁用误名，本目录一律不出现（守卫见同册测试）。</p>
 */
public enum CognitiveObjectClass {

    /** 证据：认知持久化四层之一（ADR-9）。 */
    EVIDENCE("evidence", Kind.PHYSICAL, "ecos_cognitive.ecos_cognitive_evidence", null,
            List.of("public.ecos_cognitive_evidence"),
            "PRD-05 §6.1 / V127→V191", "evidenceRefs 与假设/信念的支撑链入口"),

    /** 假设：状态机终态 4 值（R-15 ①）。 */
    HYPOTHESIS("hypothesis", Kind.PHYSICAL, "ecos_cognitive.ecos_cognitive_hypothesis", null,
            List.of("public.ecos_cognitive_hypothesis"),
            "PRD-05 §6.1 / V128→V191", "PROPOSED/EVIDENCED/BELIEVED/REFUTED（旧值只读别名映射）"),

    /** 信念：心智状态，含 probability（CM-07 禁写列）。 */
    BELIEF("belief", Kind.PHYSICAL, "ecos_cognitive.ecos_cognitive_belief", null,
            List.of("public.ecos_cognitive_belief"),
            "PRD-05 §6.1 / V129→V191", "分布走 distribution_json TEXT，MC02 不建索引"),

    /** 认知模型<b>运行实例</b>：与模型<b>资产</b>表 {@code ecos_cognitive_model} 严禁共用一表。 */
    COGNITIVE_MODEL_INSTANCE("cognitive_model_instance", Kind.REFERENCE, null,
            "modelId + version + runId（响应内引用表达，不落 cognitive 表）",
            List.of(),
            "PRD-05 §6.1 / R-42", "是否落盘随 R-42，未裁前只以引用态表达"),

    /** 情境/上下文：请求态，不建表（§五 REQ-COG-05 已判「Context 不建表」）。 */
    CONTEXT_SITUATION("context_situation", Kind.REFERENCE, null,
            "请求态 context（可回放快照落业务域 §6.4 产物，而非 cognitive 表）",
            List.of(),
            "PRD-05 §6.1 / §五 REQ-COG-05", "Run 的 context/situation 引用即本对象的消费形态"),

    /** 推理轨迹：以 run_id 关联审计流承载（ST06 Kafka ecos.audit）。 */
    REASONING_TRACE("reasoning_trace", Kind.REFERENCE, null,
            "run_id → ecos.audit 审计事件流（ST06，经 runtime-event 单通道）",
            List.of(),
            "PRD-05 §6.1 / R-42", "附件 cog_ 前缀 DDL 形态不采纳（MC01/DR01/ST07）"),

    /** 预测<b>产物</b>：确定性计算结果落业务域 APPLICATION 层，cognitive 侧只保留引用。 */
    PREDICTION("prediction", Kind.PHYSICAL, "ecos_dw.ecos_application_forecast_run", null, /* read-only-carrier-descriptor */
            List.of("public.ecos_scenario_run"),
            "PRD-05 §6.4 REQ-COG-09 / ADR-14 / R-17 ① / V197",
            "写入只经 data-engine 唯一写通道，cognitive 禁直连 ecos_dw（ST08）"),

    /** 情景/场景：权威载体在场景/世界模型侧，引擎 schema 内的同名镜像属错放（R-13 ① 定性停写）。 */
    SCENARIO("scenario", Kind.PHYSICAL, "public.ecos_world_scenarios", null,
            List.of("ecos_cognitive.ecos_wm_scenario", "ecos_cognitive.ecos_world_scenarios"),
            "PRD-05 §6.1 / R-13 ①", "双源先定性：ecos_cognitive 内的业务/WM 表只停写不迁移"),

    /** 因果结果：无等价载体，禁以 {@code ecos_decision_causal_link} 冒充。 */
    CAUSAL_RESULT("causal_result", Kind.REFERENCE, null,
            "因果链在诊断响应内表达（CausalChainResult），需要持久化载体时随 R-42 单独立项",
            List.of(),
            "PRD-05 §6.1", "邻近物 ecos_decision_causal_link 语义不等价 ⇒ 不冒充"),

    /** 模式匹配：与 reasoning_trace 同处置路径。 */
    PATTERN_MATCH("pattern_match", Kind.REFERENCE, null,
            "run_id 关联 + 响应内引用（与 reasoning_trace 同路径）",
            List.of(),
            "PRD-05 §6.1 / R-42", "不新建 cognitive 侧表"),

    /** 决策依据：{@code ecos_decision*} 表族定性为 C 层资产（不改表、不改既有 API）。 */
    DECISION_BASIS("decision_basis", Kind.PHYSICAL, "public.ecos_decision", null,
            List.of("ecos_agent.ecos_decision_case", "ecos_ai.ecos_decision_case"),
            "PRD-05 §6.3 REQ-COG-08 / R-42 / V47",
            "对 W Agent 只读（引用 decisionId + version + hash）；case 镜像写权威须实测");

    /** 载体两态。 */
    public enum Kind { PHYSICAL, REFERENCE }

    private final String objectKey;
    private final Kind kind;
    private final String carrier;
    private final String reference;
    private final List<String> legacyWriteSuspended;
    private final String citation;
    private final String note;

    CognitiveObjectClass(String objectKey, Kind kind, String carrier, String reference,
                         List<String> legacyWriteSuspended, String citation, String note) {
        this.objectKey = objectKey;
        this.kind = kind;
        this.carrier = carrier;
        this.reference = reference;
        this.legacyWriteSuspended = List.copyOf(legacyWriteSuspended);
        this.citation = citation;
        this.note = note;
        assertExactlyOneState(kind, carrier, reference);
    }

    /** 构造期不变量：PHYSICAL ⇒ 有且仅有 schema 限定载体；REFERENCE ⇒ 有且仅有引用表达式。 */
    private static void assertExactlyOneState(Kind kind, String carrier, String reference) {
        boolean hasCarrier = carrier != null && !carrier.isBlank();
        boolean hasReference = reference != null && !reference.isBlank();
        if (hasCarrier == hasReference) {
            throw new IllegalStateException("C 对象目录违例：载体与引用态必须二选一");
        }
        if (kind == Kind.PHYSICAL && !hasCarrier) {
            throw new IllegalStateException("PHYSICAL 态必须给出唯一物理载体");
        }
        if (kind == Kind.REFERENCE && !hasReference) {
            throw new IllegalStateException("REFERENCE 态必须给出引用表达式");
        }
        if (hasCarrier && !carrier.contains(".")) {
            throw new IllegalStateException("物理载体必须 schema 限定（DR01）: " + carrier);
        }
    }

    public String objectKey() {
        return objectKey;
    }

    public Kind kind() {
        return kind;
    }

    /** 唯一物理载体（schema 限定名）；引用态返回 null。 */
    public Optional<String> carrier() {
        return Optional.ofNullable(carrier);
    }

    /** 引用表达式；物理载体态返回 null。 */
    public Optional<String> reference() {
        return Optional.ofNullable(reference);
    }

    /** 只停写的旧形态（IR03：不 DROP、不改名、不加列），<b>不是</b>载体。 */
    public List<String> legacyWriteSuspended() {
        return legacyWriteSuspended;
    }

    public String citation() {
        return citation;
    }

    public String note() {
        return note;
    }

    /** 目录条目（只读解析端点与契约文档共用同一份，禁第二套清单）。 */
    public CatalogEntry toEntry() {
        return new CatalogEntry(objectKey, kind.name(), carrier, reference, legacyWriteSuspended,
                citation, note);
    }

    /** 按附件对象名查目录（大小写不敏感；未登记 ⇒ empty，由调用方 fail-loud）。 */
    public static Optional<CognitiveObjectClass> byKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase();
        for (CognitiveObjectClass c : values()) {
            if (c.objectKey.equals(normalized)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** 目录对象名集合（封闭枚举，新增必先改 PRD-05 §6.1 再入码）。 */
    public static Set<String> objectKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (CognitiveObjectClass c : values()) {
            keys.add(c.objectKey);
        }
        return keys;
    }

    /** 全部物理载体（schema 限定名），顺序稳定。 */
    public static List<String> physicalCarriers() {
        List<String> carriers = new ArrayList<>();
        for (CognitiveObjectClass c : values()) {
            c.carrier().ifPresent(carriers::add);
        }
        return List.copyOf(carriers);
    }

    /** 目录条目（不可变视图）。 */
    public record CatalogEntry(String objectKey,
                               String carrierKind,
                               String carrier,
                               String reference,
                               List<String> legacyWriteSuspended,
                               String citation,
                               String note) {
        public CatalogEntry {
            legacyWriteSuspended = List.copyOf(legacyWriteSuspended);
        }
    }
}
