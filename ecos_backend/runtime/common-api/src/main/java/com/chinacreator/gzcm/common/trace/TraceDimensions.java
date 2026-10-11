package com.chinacreator.gzcm.common.trace;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨引擎统一 Trace 维度常量（《ECOS 认知域与智能域补充详细设计 v1.0》§12.2）。
 *
 * <p>既有 traceId / runId 之外，跨引擎调用必须透传下表列出的全部维度键：
 * 场景层（scenario / blueprint / run / step）、上下文摘要（contextDigest）、
 * 能力层（capabilityId / toolContractVersion / modelRef / modelVersion）、
 * 证据层（sourceRefs）、租户（tenantId）、结果与审批（resultRef / approvalRef）。
 * 本类是<b>全仓唯一键名单源</b>，调用方禁自造字符串字面量（X-38 同型纪律：
 * 键名分头维护 ⇒ 事件/日志解析断链）。</p>
 *
 * <p>携带纪律：</p>
 * <ul>
 *   <li>{@link #SOURCE_REFS} 只写引用 ID + 引用版本，禁引敏感正文（§12.2 尾段）；
 *       事件 payload 使用方须实现"仅引用"过滤器（防止 chunk/凭据正文随行扩散）。</li>
 *   <li>{@link #TENANT_ID} 按安全规范传递（越权隔离键，禁自动填充 missing 值）。</li>
 *   <li>跨引擎调用必须保留真实 HTTP 状态与错误码，不得把依赖失败包装成
 *       "成功但空"（§12.2 末句）—— 若调用失败，Trace 仍随行，但 resultRef 记录为 null +
 *       outcome=FAILED，禁伪造。</li>
 * </ul>
 *
 * <p>本类仅为常量 + 最小构建/合并工具，<b>不</b>自动挂 HTTP 头、不自动发事件；
 * 承运由调用方显式决定（避免"隐式挂载"造成不可追踪的旁路）。</p>
 */
public final class TraceDimensions {

    /** 链路 ID（由 {@link EcosTraceContext#currentTraceId()} 单源提供，此处兼容 §12.2 维度命名）。 */
    public static final String TRACE_ID = "traceId";

    /** 场景 ID（Scenario 主键 / code）。 */
    public static final String SCENARIO_ID = "scenarioId";

    /** 蓝图 ID（Scenario Blueprint 主键）。 */
    public static final String BLUEPRINT_ID = "blueprintId";

    /** 蓝图版本（Blueprint 版本串，发布门禁关键点）。 */
    public static final String BLUEPRINT_VERSION = "blueprintVersion";

    /** Run ID（一次 W Agent Run 主键）。 */
    public static final String RUN_ID = "runId";

    /** Step ID（Run 内 Step 主键；如无 Step 则缺省 null）。 */
    public static final String STEP_ID = "stepId";

    /** 上下文摘要（Context Pack 摘要哈希或版本 ID，禁传正文）。 */
    public static final String CONTEXT_DIGEST = "contextDigest";

    /** 能力 ID（capability 概念 ID，对应小模型/Function 词典）。 */
    public static final String CAPABILITY_ID = "capabilityId";

    /** 工具契约版本（Tool Contract 版本号，V270 tool_contract_ref 同源）。 */
    public static final String TOOL_CONTRACT_VERSION = "toolContractVersion";

    /** 模型引用（模型 ID / code，不写模型二进制）。 */
    public static final String MODEL_REF = "modelRef";

    /** 模型版本（模型版本串；模型不可变版本口径）。 */
    public static final String MODEL_VERSION = "modelVersion";

    /** 来源引用（引用 ID 列表；仅 ID + 版本，禁正文）。 */
    public static final String SOURCE_REFS = "sourceRefs";

    /** 租户 ID（跨域隔离键，禁自动填充 missing）。 */
    public static final String TENANT_ID = "tenantId";

    /** 结果引用（本次调用的持久化产物 ID；依赖失败=true 时可为 null 表示"未落盘"，禁装硬）。 */
    public static final String RESULT_REF = "resultRef";

    /** 审批引用（存在审批动作时填 approval_ref；无审批则为 null）。 */
    public static final String APPROVAL_REF = "approvalRef";

    /**
     * §12.2 十四维度冻结清单（保持设计口径清单：合成 modelRef + modelVersion = 1 项）。
     * 调用方追加键前必查此清单；新增维度须同步设计及本常量。
     */
    public static final List<String> ALL = List.of(
            TRACE_ID, SCENARIO_ID, BLUEPRINT_ID, BLUEPRINT_VERSION,
            RUN_ID, STEP_ID, CONTEXT_DIGEST,
            CAPABILITY_ID, TOOL_CONTRACT_VERSION, MODEL_REF, MODEL_VERSION,
            SOURCE_REFS, TENANT_ID, RESULT_REF, APPROVAL_REF);

    private TraceDimensions() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 按 §12.2 十四维度构建一个 payload 快照（缺省 = null 值不写入，避免"零值幻觉"）。
     *
     * <p>纪律（对应设计红线）：</p>
     * <ol>
     *   <li>{@code sourceRefs} 只允许 ID / 引用版本串，调用方若傳正文须自行截断并省略正文；</li>
     *   <li>{@code tenantId} 必须非空；若为空快速抛（防越权迭代）—— 允许显式 "default"
     *       由调用方声明，不在本类兜底。</li>
     *   <li>键名永与 {@link #ALL} 一致；调用方通过 {@link Builder} 获得，禁手写字面量。</li>
     * </ol>
     */
    public static final class Builder {
        private final Map<String, Object> m = new LinkedHashMap<>();

        private Builder() {
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder traceId(String v)    { set(TRACE_ID, v); return this; }
        public Builder scenarioId(String v) { set(SCENARIO_ID, v); return this; }
        public Builder blueprintId(String v) { set(BLUEPRINT_ID, v); return this; }
        public Builder blueprintVersion(String v) { set(BLUEPRINT_VERSION, v); return this; }
        public Builder runId(String v)       { set(RUN_ID, v); return this; }
        public Builder stepId(String v)      { set(STEP_ID, v); return this; }
        public Builder contextDigest(String v) { set(CONTEXT_DIGEST, v); return this; }
        public Builder capabilityId(String v) { set(CAPABILITY_ID, v); return this; }
        public Builder toolContractVersion(String v) { set(TOOL_CONTRACT_VERSION, v); return this; }
        public Builder modelRef(String v)    { set(MODEL_REF, v); return this; }
        public Builder modelVersion(String v) { set(MODEL_VERSION, v); return this; }

        /** 来源引用列表；调用方保证只装 ID / 版本串。 */
        public Builder sourceRefs(java.util.Collection<String> refs) {
            java.util.List<String> filtered = null;
            if (refs != null) {
                filtered = new java.util.ArrayList<>(refs.size());
                for (String r : refs) {
                    if (r != null && !r.isBlank()) {
                        filtered.add(r.trim());
                    }
                }
                set(SOURCE_REFS, filtered.isEmpty() ? null : filtered);
            }
            return this;
        }

        public Builder tenantId(String v) {
            if (v == null || v.isBlank()) {
                throw new IllegalStateException(
                        "TraceDimensions.tenantId 非法（禁自动补 default；由调用方显式声明或上游 UserContext 提供）");
            }
            set(TENANT_ID, v);
            return this;
        }

        public Builder resultRef(String v)  { set(RESULT_REF, v); return this; }
        public Builder approvalRef(String v) { set(APPROVAL_REF, v); return this; }

        /** 通用 setter（仅允许 {@link #ALL} 中已登记的键；避免调用方自造第二套维度）。 */
        public Builder set(String key, Object value) {
            if (!ALL.contains(key)) {
                throw new IllegalArgumentException(
                        "TraceDimensions 未登记键: " + key + "（登记入口 = 修改本类 + §12.2）");
            }
            if (value == null || (value instanceof String s && s.isBlank())) {
                m.remove(key);
            } else {
                m.put(key, value);
            }
            return this;
        }

        /** 冻结 snapshot（不可变浅拷贝；选 {@link LinkedHashMap} 保留插入顺序便于事件回放对齐）。 */
        public Map<String, Object> build() {
            return new LinkedHashMap<>(m);
        }

        /** 合并进现有事件 payload（只写本 builder 出现的键；已存在非空键不回写，保留上游权威值）。 */
        public void mergeInto(Map<String, Object> target) {
            if (target == null) {
                return;
            }
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Object cur = target.get(e.getKey());
                if (cur == null) {
                    target.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    /**
     * 从既有 payload 读回 §12.2 十四维度（缺失 = null，不填占位）。
     * 调用方可据此做跨引擎调用的维度完整性断言（缺 tenantId 等硬项 ⇒ 显式拒绝）。
     */
    public static Map<String, Object> readFrom(Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return out;
        }
        for (String key : ALL) {
            Object v = payload.get(key);
            if (v != null) {
                out.put(key, v);
            }
        }
        return out;
    }

    /** 缺失维度清单（用于"跨引擎调用不完整 Trace"的显式告警，禁静默补偿）。 */
    public static Set<String> missingIn(Map<String, Object> payload) {
        Set<String> missing = new LinkedHashSet<>();
        for (String key : ALL) {
            Object v = payload == null ? null : payload.get(key);
            if (v == null || (v instanceof String s && s.isBlank())) {
                missing.add(key);
            }
        }
        return missing;
    }
}
