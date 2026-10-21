package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * agent 工具腿 → 属主引擎调用的<b>结论词表单源</b>（#142/B4-3e-22，随 <b>D-B4-49 余项</b>写腿收口上收）。
 *
 * <p><b>为什么要在此上收</b>：这套词表原本内联在 {@code QueryPhysicalTableTool} 的私有枚举里
 * （#142/B4-3e-16 只关了「任意 SQL」子项，把分态判据留在了单条腿内）。同批在册的另外两支
 * <b>写腿</b>（{@code TriggerPipelineTool}／{@code RunQualityCheckTool}）根本没有词表——
 * 它们在 agent 进程里往属主的台账插一行，然后<b>无条件</b>回 {@code status="triggered"}：
 * 属主侧的只读冲突（409）、已下线（410）、拒绝（403）、不可用（503）四个不同的事实，
 * 在本腿的机读面上全都长成同一个「已触发」。判据散在腿里的结果就是这个样子：
 * 一条腿有词表、两条腿没有，第三条腿出现时又得再造一套。故词表与其<b>映射判据</b>
 * （{@link #fromHttpStatus}／{@link #fromEnvelope}）一并单源化，本文件之外的
 * {@code tools/} 源码不许再自带 {@code case 403}/{@code case 503} 之类的状态分派
 * （由 {@code AgentToolOwnerWriteChannelSingleSourceTest} 钉）。</p>
 *
 * <p><b>八个态各自对应一件事</b>：「前置判据没过（没发请求）」「传输断了（结论未知）」
 * 「没权限」「安全护栏不可用」「属主把这条通道关了」「属主报错」「取到了数据」「属主受理了写」
 * ——折叠任何两件都会让调用方作出错误的下一步（把不可用当拒绝而放弃、把关了的通道当成功而重试）。
 * §4.7.2「不可用 ≠ 无权限」在此落成 {@link #SECURITY_UNAVAILABLE} 与 {@link #DENIED} 两态；
 * 新增的 {@link #OWNER_CHANNEL_CLOSED} 既不是「无权限」也不是「故障」，它意味着
 * <b>该变更面在属主侧已按裁决下线</b>（如 data-engine 的 YAML 管道只读桩、DQ legacy 写通道 410），
 * 报成 403 会误导调用方去申请权限，报成 500 会误导它重试。</p>
 *
 * <p><b>{@link #ACCEPTED} 与 {@link #DATA_RETURNED} 为什么分开</b>：写腿成功时属主给的是
 * 「已受理」而不是「取到的行」，两者机读值不同，前端与 LLM 才能区分「查到了」和「排进去了」。
 * 读腿继续用 {@link #DATA_RETURNED}（#142/B4-3e-16 已登记的机读词表，一字未动）。</p>
 */
enum Outcome {

    /** 前置判据不通过（非只读／跨表／入参形态不合法等），<b>未发起出向调用</b>。 */
    GUARD_REJECTED,

    /** 基址不可解析、传输层失败或应答形态不可识别：结论未知，不冒充任何裁决。 */
    UNREACHABLE,

    /** 属主或网关明确拒绝（401/403）：无权限。 */
    DENIED,

    /** 安全护栏不可用（503／{@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}）：可告警可重试，非「无权限」。 */
    SECURITY_UNAVAILABLE,

    /**
     * 属主显式关闭该调用面（409 只读冲突／410 已下线）。
     *
     * <p>与 {@link #DENIED} 的区别是「谁能拒」：这里是属主按其架构裁决拒掉整条通道，
     * 与本请求的主语无关；换任何调用方来都是同一结论。</p>
     */
    OWNER_CHANNEL_CLOSED,

    /** 属主返回其它非零业务/系统码：执行失败，与权限无关。 */
    ENGINE_FAILED,

    /** 读腿取数成功，透传属主 {@code data} 段。 */
    DATA_RETURNED,

    /** 写腿被属主受理（本腿不自造 runId／checkId，受理凭据一律取属主应答）。 */
    ACCEPTED;

    /** 是否为「拿到肯定结论」的出口——{@code available} 只在这两态为真。 */
    boolean succeeded() {
        return this == DATA_RETURNED || this == ACCEPTED;
    }

    /**
     * 真 HTTP 状态行 → 结论态（网关前置过滤器、属主 {@code ResponseEntity} 保真腿都走这里）。
     *
     * @param status 非 2xx 状态码
     */
    static Outcome fromHttpStatus(int status) {
        return switch (status) {
            case 401, 403 -> DENIED;
            case 503 -> SECURITY_UNAVAILABLE;
            case 409, 410 -> OWNER_CHANNEL_CLOSED;
            default -> ENGINE_FAILED;
        };
    }

    /**
     * 属主 {@code ApiResponse} 信封（{@code code}／{@code errorCode}／{@code message}）→ 结论态。
     *
     * <p>分态权威先看 {@code errorCode}：{@code ECOS-SEC-501} 在 {@code code} 上可与其它态重叠，
     * 键面单源优先。{@code code} 缺失 ⇒ {@link #UNREACHABLE}（结论未知），
     * 禁把「没答」读成「拒绝」。</p>
     *
     * @param onSuccess  {@code code == 0} 时的终态：读腿传 {@link #DATA_RETURNED}，写腿传 {@link #ACCEPTED}
     */
    static Outcome fromEnvelope(Integer code, String errorCode, Outcome onSuccess) {
        if (code == null) {
            return UNREACHABLE;
        }
        if (code == 0) {
            return onSuccess;
        }
        if (RlsExecutionContract.SEC_UNAVAILABLE_CODE.equals(errorCode)) {
            return SECURITY_UNAVAILABLE;
        }
        return switch (code) {
            case 401, 403 -> DENIED;
            case 503 -> SECURITY_UNAVAILABLE;
            case 409, 410 -> OWNER_CHANNEL_CLOSED;
            default -> ENGINE_FAILED;
        };
    }

    /** 读腿用的默认形态（{@code code == 0 ⇒ }{@link #DATA_RETURNED}），保持 #142/B4-3e-16 的词表不变。 */
    static Outcome fromEnvelope(Integer code, String errorCode) {
        return fromEnvelope(code, errorCode, DATA_RETURNED);
    }

    /**
     * 组本域统一机读载荷：{@code available} ＋ {@code state} ＋（有则）{@code message}。
     *
     * <p>键面单源在 {@link AgentToolResponses}，本方法只多一个 {@code state} 键，
     * 禁各腿自造 {@code ok}/{@code success}/{@code triggered} 之类的外形键。</p>
     */
    static Map<String, Object> payload(Outcome outcome, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(AgentToolResponses.KEY_AVAILABLE, outcome.succeeded());
        out.put(AgentToolResponses.KEY_STATE, outcome.name());
        if (message != null && !message.isBlank()) {
            out.put(AgentToolResponses.KEY_MESSAGE, message);
        }
        return out;
    }

    /** 在既有载荷（如 {@link AgentToolResponses#unreachable(String)}）上补 {@code state} 键，不改其既有键值。 */
    static Map<String, Object> stateOn(Map<String, Object> payload, Outcome outcome) {
        Map<String, Object> out = new LinkedHashMap<>(payload);
        out.put(AgentToolResponses.KEY_STATE, outcome.name());
        return out;
    }

    /** 信封 {@code code} 段（非数字／缺失一律 {@code null}，交由 {@link #fromEnvelope} 归成未知态）。 */
    static Integer codeOf(Map<String, Object> envelope) {
        return envelope.get("code") instanceof Number n ? n.intValue() : null;
    }

    /** 信封 {@code errorCode} 段（护栏不可用的权威键）。 */
    static String errorCodeOf(Map<String, Object> envelope) {
        Object v = envelope.get("errorCode");
        return v == null ? null : v.toString();
    }

    /** 信封 {@code message} 段（属主理由必须如实回传，不许吞掉）。 */
    static String messageOf(Map<String, Object> envelope) {
        Object v = envelope.get("message");
        return v == null ? null : v.toString();
    }
}
