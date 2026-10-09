package com.chinacreator.gzcm.common.trace;

/**
 * Agent Loop 结构化日志行单源（详细设计-06 F06-06 要点 3/4）。
 *
 * <p>固定字段序（供运行回放 F06-08 与前端诊断码 F06-19 侧按 key 解析，禁自由文案）：</p>
 * <pre>
 * EVT=AgentLoop op={plan|tool_call|tool_result|llm_call|final} step={n} agent={agentId} \
 * tool={} model={} promptTokens={} completionTokens={} latencyMs={} decision={ALLOW|DENY|FAIL_CLOSED|NA}
 * </pre>
 *
 * <p>纪律：</p>
 * <ul>
 *   <li>{@code op} 枚举取代旧的 {@code think/act/observe} 三态（X-38 枚举错位），
 *       本类是全仓唯一登记处，分册 07/09 前端诊断码与此对齐；</li>
 *   <li>token 与 latency <b>必填</b>：真值缺失时写 {@link #UNKNOWN}（-1），
 *       <b>禁静默 0</b>（X-38 恒 0 面）；回放侧把 -1 标为"缺失"而非 0；</li>
 *   <li>字符串字段缺失写 {@link #ABSENT}（连字符），禁写入原文（脱敏面）。</li>
 * </ul>
 */
public final class AgentLoopTrace {

    /** 事件名（结构化行第一字段 value，日志侧检索锚点）。 */
    public static final String EVENT = "AgentLoop";

    /** 数值字段缺失标记 —— 与"实测为 0"严格区分。 */
    public static final long UNKNOWN = -1L;

    /** 字符串字段缺失标记。 */
    public static final String ABSENT = "-";

    /** Loop 步骤枚举（F06-06 要点 4，取代 think/act/observe）。 */
    public enum Op {
        PLAN("plan"),
        TOOL_CALL("tool_call"),
        TOOL_RESULT("tool_result"),
        LLM_CALL("llm_call"),
        FINAL("final");

        private final String wire;

        Op(String wire) {
            this.wire = wire;
        }

        /** 日志行上的字面量。 */
        public String wire() {
            return wire;
        }

        /** 按日志字面量反解（回放侧使用）；未知值返回 null。 */
        public static Op fromWire(String wire) {
            if (wire == null) return null;
            for (Op op : values()) {
                if (op.wire.equals(wire)) {
                    return op;
                }
            }
            return null;
        }
    }

    /** 裁决态：与铁律 §2.4 三态一致，无裁决环节写 {@link #NA}。 */
    public enum Decision {
        ALLOW, DENY, FAIL_CLOSED, NA
    }

    private AgentLoopTrace() {
        throw new UnsupportedOperationException("常量/构造类，禁止实例化");
    }

    /** 起始一行：必填 op 与 step，其余字段可选（未设置即按缺失态渲染）。 */
    public static Builder line(Op op, int step) {
        if (op == null) {
            throw new IllegalArgumentException("op 必填（见 AgentLoopTrace.Op 枚举）");
        }
        return new Builder(op, step);
    }

    /** 结构化行构造器 —— 渲染结果字段序固定。 */
    public static final class Builder {

        private final Op op;
        private final int step;
        private String agent;
        private String tool;
        private String model;
        private long promptTokens = UNKNOWN;
        private long completionTokens = UNKNOWN;
        private long latencyMs = UNKNOWN;
        private Decision decision = Decision.NA;

        private Builder(Op op, int step) {
            this.op = op;
            this.step = step;
        }

        public Builder agent(String agentId) {
            this.agent = agentId;
            return this;
        }

        public Builder tool(String toolName) {
            this.tool = toolName;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder promptTokens(long promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(long completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        /** 同一次 LLM 调用的两侧 token（provider 未上报时传 {@link #UNKNOWN}，禁传 0 冒充实测）。 */
        public Builder tokens(long promptTokens, long completionTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder decision(Decision decision) {
            this.decision = decision == null ? Decision.NA : decision;
            return this;
        }

        /** 渲染固定格式行（单行，无换行，供 grep 与回放重建时序）。 */
        public String render() {
            StringBuilder sb = new StringBuilder(192);
            sb.append("EVT=").append(EVENT)
                    .append(" op=").append(op.wire())
                    .append(" step=").append(step)
                    .append(" agent=").append(safe(agent))
                    .append(" tool=").append(safe(tool))
                    .append(" model=").append(safe(model))
                    .append(" promptTokens=").append(promptTokens)
                    .append(" completionTokens=").append(completionTokens)
                    .append(" latencyMs=").append(latencyMs)
                    .append(" decision=").append(decision == null ? Decision.NA : decision);
            return sb.toString();
        }
    }

    /** 字段安全化：去换行防日志注入，空值写缺失标记。 */
    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return ABSENT;
        }
        return value.replace('\n', '_').replace('\r', '_').replace('=', '_');
    }
}
