package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context Pack 核心结构（《认知域与智能域补充详细设计 v1.0》§5.3 字段表，静态字段冻结）。
 *
 * <p>请求态 DTO：<b>不新建 cognitive_context 表</b>（§5.2 重要约束 / D 层红底）。
 * Pack 作为单次任务/阶段执行前的结构化上下文，仅在请求声明的 TTL 内可用（§5.4-10）。</p>
 *
 * <p>字段序即 digest 计算序（{@link #normalizedDigestView()} 固定序小写化）：
 * 相同来源版本、相同请求和相同策略下 {@code contextDigest} 稳定（§5.6 稳定性测试）。</p>
 */
@Data
public class ContextPack {

    // ── §5.3 字段表（顺序冻结，digest 输入序）─────────────────────────────────

    /** 规范化 Context Pack 的摘要哈希（{@code sha256:} 前缀；由 Builder 在组装尾部计算）。 */
    private String contextDigest;

    /** 目标、成功条件、停止条件（回显请求 goal，供 Run/Step 关联）。 */
    private ContextBuildRequest.Goal goal;

    /** 租户、业务范围、时间范围、口径、数据时点（回显请求 scope）。 */
    private ContextBuildRequest.Scope scope;

    /** 来源：类型、ID、版本、权威等级、新鲜度、访问结果（§5.2 响应示例 sources[]）。 */
    private List<SourceEntry> sources = new ArrayList<>();

    /** 权威事实的引用/必要投影，必须带来源与时点（§5.3 facts[]）。 */
    private List<FactEntry> facts = new ArrayList<>();

    /** KB 文档/片段/案例引用，不复制完整知识资产（§5.3 knowledgeRefs[]）。 */
    private List<AssetRefEntry> knowledgeRefs = new ArrayList<>();

    /** 本体模型版本、对象/关系和提案状态（§5.3 ontologyRefs[]）。 */
    private List<AssetRefEntry> ontologyRefs = new ArrayList<>();

    /** Mind、hypothesis、belief、decision basis、CM 版本的引用（§5.3 cognitiveRefs[]）。 */
    private List<AssetRefEntry> cognitiveRefs = new ArrayList<>();

    /** 可用能力、输入输出契约、风险等级、模型版本（§5.3 capabilities[] / 请求 requiredCapabilities 对偶）。 */
    private List<CapabilityRefEntry> capabilityRefs = new ArrayList<>();

    /** 确定性计算产物引用（§4.2 / REQ-COG-09；数值可复算锚点）。 */
    private List<CalculationResultRef> calculationRefs = new ArrayList<>();

    /** 冲突项、冲突来源、权威优先级、待处理动作（§5.3 conflicts[]）。 */
    private List<ConflictEntry> conflicts = new ArrayList<>();

    /** 缺失数据、缺失关系、缺失权限、缺失证据等（§5.3 gaps[]）。 */
    private List<Gap> gaps = new ArrayList<>();

    /** 过期、部分覆盖、低置信、降级模式等警告（§5.3 warnings[]）。 */
    private List<String> warnings = new ArrayList<>();

    // ── 装配运营字段（§5.4-8 预算裁剪 / §12.2 trace 维度）─────────────────────

    /** 预算裁剪是否发生（true ⇒ 必须伴随 warnings 中的截断原因，禁静默丢失证据，§5.4-8）。 */
    private boolean truncated;

    /** 被预算裁剪移出 Pack 的来源引用清单（§5.5 超预算：返回未纳入引用清单）。 */
    private List<String> truncatedSources = new ArrayList<>();

    /** 是否就绪（可选来源不可用但成功条件必需 ⇒ ready=false，§5.5）。 */
    private boolean ready = true;

    /** 统一 trace 维度（§12.2）。 */
    private String traceId;

    /** 场景 ID（§12.2）。 */
    private String scenarioId;

    /** 场景蓝图引用回显（SUP-BP-03 运行可回链蓝图版本锚点）。 */
    private ContextBuildRequest.BlueprintRef blueprintRef;

    /** 构建完成时点（ISO-8601 UTC）。 */
    private String createdAt;

    /** Pack 过期时点（createdAt + ttlMillis；§5.4-10 过期与刷新）。 */
    private String expiresAt;

    /** 目标生效时点 / 数据时点（scope.asOfTime 缺省承接）。 */
    private String asOfTime;

    // ── Pack 内部条目（纯 POJO）───────────────────────────────────────────────

    /** source 条目（§5.2 响应示例对齐：sourceType/sourceId/version/authority/freshness/evidenceRefs）。 */
    @Data
    public static class SourceEntry {
        private String sourceType;
        private String sourceId;
        private String version;
        /** 权威等级：AUTHORITATIVE / GOVERNED / CANDIDATE（§8.1 资产分层简化映射）。 */
        private String authority;
        /** 新鲜度：CURRENT / STALE / UNKNOWN。 */
        private String freshness;
        /** 访问结果：OK / DENIED / UNAVAILABLE（SUP-CTX-03 语义不混淆）。 */
        private String accessResult;
        /** 引用时点（ISO-8601 UTC）。 */
        private String asOfTime;
        private List<String> evidenceRefs = new ArrayList<>();
    }

    /** 事实条目：权威事实的引用/必要投影（必须带来源与时点，SUP-CTX-01）。 */
    @Data
    public static class FactEntry {
        private String factId;
        /** 指标/字段名（冲突检测按 (fieldKey) 归组，§5.4-6）。 */
        private String fieldKey;
        /** 值（引用态投影；金额等确定性数值必须经 {@link CalculationResultRef} 可复算来源）。 */
        private String value;
        private String sourceId;
        private String version;
        private String asOfTime;
        private List<String> evidenceRefs = new ArrayList<>();
    }

    /** 资产引用条目（knowledge / ontology / cognitive 三类共用形态）。 */
    @Data
    public static class AssetRefEntry {
        private String refId;
        private String refType;
        private String version;
        /** 状态（本体提案状态 / 认知资产生命周期状态，§8.4；透传来源系统枚举字面量）。 */
        private String status;
        private String asOfTime;
        private List<String> evidenceRefs = new ArrayList<>();
    }

    /** 能力引用条目（§5.3 capabilities[]：输入输出契约、风险等级、模型版本）。 */
    @Data
    public static class CapabilityRefEntry {
        private String capabilityId;
        private String contractVersion;
        private String inputSchemaRef;
        private String outputSchemaRef;
        /** 风险等级（LOW / MEDIUM / HIGH；由 security 裁剪面提供，Router 零复制策略）。 */
        private String riskLevel;
        private String modelRef;
        private String modelVersion;
        private boolean required;
        private boolean available;
        /** 访问/可用性结果：OK / DENIED / UNAVAILABLE。 */
        private String accessResult;
    }

    /** 冲突条目（§5.4-6：禁 LLM 自行裁决为事实；authority 优先级不明 ⇒ 保持 CONFLICT 待人工）。 */
    @Data
    public static class ConflictEntry {
        private String conflictId;
        /** 冲突的语义字段 key（与 {@link FactEntry#getFieldKey()} 同键）。 */
        private String fieldKey;
        /** 冲突各方（sourceId + version + value）。 */
        private List<ContendingValue> values = new ArrayList<>();
        /** 权威优先级解析结果：RESOLVED（有明确权威来源）/ UNRESOLVED（须人工澄清）。 */
        private String resolution;
        private List<String> evidenceRefs = new ArrayList<>();
        /** 待处理动作。 */
        private String requiredAction;
    }

    /** 冲突方取值。 */
    @Data
    public static class ContendingValue {
        private String sourceId;
        private String version;
        private String value;
        private String authority;
        private String asOfTime;
    }

    public static final String RESOLUTION_RESOLVED = "RESOLVED";
    public static final String RESOLUTION_UNRESOLVED = "UNRESOLVED";

    // ── digest 输入视图 ──────────────────────────────────────────────────────

    /**
     * 规范化 digest 视图：{@link LinkedHashMap} 固定字段序 —— 摘要计算（Builder SHA-256）
     * 与回放一致性校验的<b>唯一</b>输入结构。禁止在此追加/改名既有键（只增新键追加尾部）。
     */
    public Map<String, Object> normalizedDigestView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("goal", goal);
        m.put("scope", scope);
        m.put("sources", sources);
        m.put("facts", facts);
        m.put("knowledgeRefs", knowledgeRefs);
        m.put("ontologyRefs", ontologyRefs);
        m.put("cognitiveRefs", cognitiveRefs);
        m.put("capabilityRefs", capabilityRefs);
        m.put("calculationRefs", calculationRefs);
        m.put("conflicts", conflicts);
        m.put("gaps", gaps);
        m.put("warnings", warnings);
        m.put("truncated", truncated);
        m.put("truncatedSources", truncatedSources);
        m.put("ready", ready);
        m.put("scenarioId", scenarioId);
        m.put("blueprintRef", blueprintRef);
        m.put("asOfTime", asOfTime);
        return m;
    }

    /** 是否完全空 Pack（无任何来源/事实/引用）。空 Pack 仅允许 ready=false 或伴随 warnings 透出。 */
    public boolean isEmpty() {
        return sources.isEmpty() && facts.isEmpty() && knowledgeRefs.isEmpty()
                && ontologyRefs.isEmpty() && cognitiveRefs.isEmpty()
                && capabilityRefs.isEmpty() && calculationRefs.isEmpty();
    }
}
