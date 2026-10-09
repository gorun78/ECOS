package com.chinacreator.gzcm.common.trace;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent Loop 结构化日志行单源（详细设计-06 F06-06 要点 3/4）。
 *
 * <p>固定字段序（供运行回放 F06-08 与前端诊断码 F06-19 侧按 key 解析，禁自由文案）：</p>
 * <pre>
 * EVT=AgentLoop op={plan|tool_call|tool_result|llm_call|final} step={n} agent={agentId} \
 * tool={} model={} promptTokens={} completionTokens={} latencyMs={} decision={ALLOW|DENY|FAIL_CLOSED|NA} trace={}
 * </pre>
 *
 * <p>字段序与 F06-06 要点 3 逐字一致，仅在行末追加 {@code trace=} 一位：
 * F06-08 要求"按 traceId 从日志重建时序"，而 MDC 前缀由 logback pattern 决定、
 * 属部署面而非行契约，故检索键必须随行自描述，回放侧才能脱离 pattern 解析。</p>
 *
 * <p>纪律：</p>
 * <ul>
 *   <li>{@code op} 枚举取代旧的 {@code think/act/observe} 三态（X-38 枚举错位），
 *       本类是全仓唯一登记处，分册 07/09 前端诊断码与此对齐；</li>
 *   <li>token 与 latency <b>必填</b>：真值缺失时写 {@link #UNKNOWN}（-1），
 *       <b>禁静默 0</b>（X-38 恒 0 面）；回放侧把 -1 标为"缺失"而非 0；</li>
 *   <li>字符串字段缺失写 {@link #ABSENT}（连字符），禁写入原文（脱敏面）；</li>
 *   <li>本类同时是回放侧的<b>解析器单源</b>（{@link #parse}）—— 写读同处，
 *       禁在回放端另立第二套字段名/分隔符理解（口径漂移即断链）。</li>
 * </ul>
 */
public final class AgentLoopTrace {

    /** 事件名（结构化行第一字段 value，日志侧检索锚点）。 */
    public static final String EVENT = "AgentLoop";

    /** 行前缀标记：grep 与回放扫描的检索锚点（含空格，避免误命中自由文案）。 */
    public static final String MARKER = "EVT=AgentLoop ";

    /**
     * Agent Loop 结构化行的专用 logger 名（logback 侧按此名接文件 appender，
     * 使该文件"只含结构化行"，回放读取端无需过滤噪声）。
     */
    public static final String LOGGER_NAME = "ECOS_AGENT_LOOP";

    /** 数值字段缺失标记 —— 与"实测为 0"严格区分。 */
    public static final long UNKNOWN = -1L;

    /** 字符串字段缺失标记。 */
    public static final String ABSENT = "-";

    /** {@code key=value} 字段对（值不含空格；agent/tool/model 由 {@link #safe} 保证）。 */
    private static final Pattern FIELD = Pattern.compile("([A-Za-z][A-Za-z0-9]*)=(\\S*)");

    /** appender pattern 前缀里的 ISO 时间戳（回放排序用；缺省则按读取顺序）。 */
    private static final Pattern TIMESTAMP_PREFIX =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,3}(?:Z|[+-]\\d{2}:?\\d{2})?)");

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
        private String traceId;

        private Builder(Op op, int step) {
            this.op = op;
            this.step = step;
        }

        /** 行末检索键（F06-08 回放按 traceId 重建的前提）；缺失写 {@link #ABSENT}，禁臆造。 */
        public Builder trace(String traceId) {
            this.traceId = traceId;
            return this;
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
                    .append(" decision=").append(decision == null ? Decision.NA : decision)
                    .append(" trace=").append(safe(traceId));
            return sb.toString();
        }
    }

    /**
     * 解析出的一行（回放侧读模型）。
     *
     * @param seq              读取序号（同一批内稳定排序用）
     * @param timestampMillis  appender 前缀时间戳，缺失为 {@link #UNKNOWN}
     */
    public record Line(long seq, String traceId, Op op, int step, String agent, String tool, String model,
                       long promptTokens, long completionTokens, long latencyMs,
                       Decision decision, long timestampMillis) {

        /** 数值字段是否缺失（-1 态）—— 回放响应里据此输出"未上报"而非 0（X-68 同纪律）。 */
        public static Long value(long v) {
            return v == UNKNOWN ? null : v;
        }
    }

    /**
     * 从一条（可能带 appender 前缀的）日志文本解析结构化行。
     *
     * @return 非 {@code EVT=AgentLoop} 行、op 不可识别或 step 不可解析时返回 {@code null}（调用方跳过，禁抛）
     */
    public static Line parse(String logText, long seq) {
        if (logText == null) {
            return null;
        }
        int anchor = logText.indexOf(MARKER.trim());
        if (anchor < 0) {
            return null;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher m = FIELD.matcher(logText.substring(anchor));
        while (m.find()) {
            fields.putIfAbsent(m.group(1), m.group(2));
        }
        Op op = Op.fromWire(fields.get("op"));
        Integer step = parseInt(fields.get("step"));
        if (op == null || step == null) {
            return null;
        }
        return new Line(seq,
                value(fields.get("trace")),
                op,
                step,
                value(fields.get("agent")),
                value(fields.get("tool")),
                value(fields.get("model")),
                parseLong(fields.get("promptTokens")),
                parseLong(fields.get("completionTokens")),
                parseLong(fields.get("latencyMs")),
                decisionOf(fields.get("decision")),
                parseTimestamp(logText));
    }

    private static Decision decisionOf(String raw) {
        if (raw == null) {
            return Decision.NA;
        }
        try {
            return Decision.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Decision.NA;
        }
    }

    /** 缺失标记还原为 null（字符串面）。 */
    private static String value(String field) {
        return field == null || ABSENT.equals(field) ? null : field;
    }

    private static long parseLong(String field) {
        Integer v = parseInt(field);
        return v == null ? UNKNOWN : v;
    }

    private static Integer parseInt(String field) {
        if (field == null) {
            return null;
        }
        try {
            return Integer.valueOf(field.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 前缀 ISO 时间戳 → epoch 毫秒；无时间戳（如 console pattern）记 {@link #UNKNOWN}，按读取顺序排序。 */
    private static long parseTimestamp(String logText) {
        Matcher m = TIMESTAMP_PREFIX.matcher(logText);
        if (!m.find()) {
            return UNKNOWN;
        }
        String raw = m.group(1);
        try {
            if (raw.endsWith("Z") || raw.contains("+") || raw.matches(".*-\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(raw).toInstant().toEpochMilli();
            }
            return LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return UNKNOWN;
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
