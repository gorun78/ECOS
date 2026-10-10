package com.chinacreator.gzcm.engine.ai.wagent;

import com.chinacreator.gzcm.common.error.AiErrorClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * W Agent 编排面错误码单源（详细设计-10 §5.3 全表 + F10-07 口径锁 {@code ECOS_WA_CALIBER_LOCKED}）。
 *
 * <p><b>复用不新造</b>：每码映射到既有 {@link AiErrorClass}（common/error，卷 06 §5.5 七类矩阵）
 * 承载日志级别 / 审计 / 重试机制分类，<b>禁</b>另立第二套错误基类；HTTP 状态与客户端重试口径
 * 按 §5.3 冻结（与 AiErrorClass 自带 httpStatus 不同时，§5.3 为 W 域出口口径，
 * AiErrorClass 仅作分类委托）。</p>
 *
 * <p><b>消息纪律（§5.3 / X-49 同族）</b>：响应消息一律取本类 {@link #renderMessage}，
 * 必含诊断码 {@code traceId}（与 MDC 同源）；<b>禁回显异常堆栈/原文</b>
 * （对卷 07 C159「异常原文入体」的同类预防）。响应包裹沿用 {@code ApiResponse}
 * （code/message/data/traceId；traceId 字段依赖卷 06 C135 补齐，未交付前 W 面自行兜底）。</p>
 *
 * <p>E-NOT-FOUND 与 E-POLICY 文案不可区分（防资源枚举，§5.3）。</p>
 */
public final class WAgentErrorCodes {

    private WAgentErrorCodes() {}

    /** §5.3 码 + 口径锁的冻结登记项。 */
    public enum WAgentErrorCode {

        /** 400 入参/Schema 校验失败；文案指出具体字段。 */
        E_VALIDATION("E-VALIDATION", 400, AiErrorClass.INVALID_ARGUMENT, false,
                "请求参数不合法（诊断码 {traceId}）"),

        /** 401 未认证；引导重新登录（主路径由 gateway JWT 承流，本码为引擎面兜底）。 */
        E_AUTH("E-AUTH", 401, AiErrorClass.GUARDRAIL_DENIED, false,
                "登录状态已失效，请重新登录（诊断码 {traceId}）"),

        /** 403 裁决拒绝（含 Kill Switch）；不泄露存在性细节。 */
        E_POLICY("E-POLICY", 403, AiErrorClass.GUARDRAIL_DENIED, false,
                "当前权限不支持该操作（诊断码 {traceId}）"),

        /** 404 对象不可见；文案与 403 不可区分（防枚举）。 */
        E_NOT_FOUND("E-NOT-FOUND", 404, AiErrorClass.NOT_FOUND, false,
                "当前权限不支持该操作，或对象不存在（诊断码 {traceId}）"),

        /** 409 状态机冲突 / 口径锁定 / 候选过期；提示当前状态与允许操作。 */
        E_WA_STATE("E-WA-STATE", 409, AiErrorClass.INVALID_ARGUMENT, false,
                "当前状态不允许该操作（诊断码 {traceId}）"),

        /** 409 幂等键冲突；返回既有 run_id（E-IDEMPOTENT-CONFLICT，§5.3/F10-11）。 */
        E_IDEMPOTENT_CONFLICT("E-IDEMPOTENT-CONFLICT", 409, AiErrorClass.INVALID_ARGUMENT, false,
                "重复请求已命中既有运行（诊断码 {traceId}）"),

        /** 429 配额/预算超限；可退避重试（§5.3「是（退避）」→ 委托 LLM_RATE_LIMITED 指数退避 ≤3）。 */
        E_RATE("E-RATE", 429, AiErrorClass.LLM_RATE_LIMITED, true,
                "额度不足，请稍后重试或降低请求规模（诊断码 {traceId}）"),

        /** 429 Run 预算耗尽；不可重试，提示拆分问题（§5.3；委托 CONTEXT_OVERFLOW 的 0 重试截断降级语义）。 */
        E_WA_BUDGET("E-WA-BUDGET", 429, AiErrorClass.CONTEXT_OVERFLOW, false,
                "本次运行预算已耗尽，请拆分为多个问题重试（诊断码 {traceId}）"),

        /** 502/503 Engine/底座依赖不可用；可重试 + 降级标注；委托 TOOL_BACKEND_ERROR（固定退避 ≤1）。 */
        E_WA_DEP("E-WA-DEP", 502, AiErrorClass.TOOL_BACKEND_ERROR, true,
                "依赖服务暂不可用（{service}），请稍后重试（诊断码 {traceId}）"),

        /** 500 数字一致性校验失败；重生成 ≤2 次（§5.3 与 F10-24/卷 06 PARSE_FAILURE 共用配额）。 */
        E_WA_NARRATIVE_NUM("E-WA-NARRATIVE-NUM", 500, AiErrorClass.PARSE_FAILURE, true,
                "结论数值与依据不一致，已触发重生成（诊断码 {traceId}）"),

        /** 500 未分类内部错误；含 trace_id、禁堆栈；委托 PARSE_FAILURE 的 500/ERROR/可重试分类，重试上限取 AiErrorClass.maxRetries()。 */
        E_INTERNAL("E-INTERNAL", 500, AiErrorClass.PARSE_FAILURE, true,
                "系统内部错误，请稍后重试（诊断码 {traceId}）"),

        /** F10-07：对已被 Run 引用的 Goal 做口径修订 ⇒ 409（新版本链走 supersedes_goal_id）。 */
        ECOS_WA_CALIBER_LOCKED("ECOS_WA_CALIBER_LOCKED", 409, AiErrorClass.INVALID_ARGUMENT, false,
                "该目标口径已被运行引用，请创建新版本（诊断码 {traceId}）");

        private final String wireCode;
        private final int httpStatus;
        private final AiErrorClass errorClass;
        private final boolean clientRetryable;
        private final String messageTemplate;

        WAgentErrorCode(String wireCode, int httpStatus, AiErrorClass errorClass,
                        boolean clientRetryable, String messageTemplate) {
            if (!messageTemplate.contains("{traceId}")) {
                throw new IllegalStateException("W 域消息模板必须含诊断码 {traceId}: " + wireCode);
            }
            this.wireCode = wireCode;
            this.httpStatus = httpStatus;
            this.errorClass = errorClass;
            this.clientRetryable = clientRetryable;
            this.messageTemplate = messageTemplate;
        }

        /** 上线冻结的响应码字面量（§5.3 原形，含短横线）。 */
        public String wireCode() {
            return wireCode;
        }

        /** §5.3 HTTP 出口口径（E-WA-DEP 文档载 502/503 两态，默认 502，依赖不可达态由调用点传 503）。 */
        public int httpStatus() {
            return httpStatus;
        }

        /** 分类委托：日志级别 / 审计位 / 退避形态取既有矩阵。 */
        public AiErrorClass errorClass() {
            return errorClass;
        }

        /** 客户端是否可重试（§5.3 重试列冻结；服务端退避另见 errorClass().backoffMillis）。 */
        public boolean clientRetryable() {
            return clientRetryable;
        }

        public String messageTemplate() {
            return messageTemplate;
        }

        /** 渲染用户可读消息；traceId 缺失以 {@code -} 占位，禁留花括号、禁拼异常原文。 */
        public String renderMessage(String traceId) {
            return renderMessage(traceId, null);
        }

        /** {@code service} 占位仅 E-WA-DEP 消费（委托 AiErrorClass 渲染再套 W 模板）。 */
        public String renderMessage(String traceId, String service) {
            String tid = (traceId == null || traceId.isBlank()) ? "-" : traceId;
            String svc = (service == null || service.isBlank()) ? "-" : service;
            return messageTemplate.replace("{traceId}", tid).replace("{service}", svc);
        }
    }

    private static final Map<String, WAgentErrorCode> BY_WIRE_CODE;

    static {
        Map<String, WAgentErrorCode> map = new LinkedHashMap<>();
        for (WAgentErrorCode c : WAgentErrorCode.values()) {
            map.put(c.wireCode(), c);
        }
        BY_WIRE_CODE = Collections.unmodifiableMap(map);
    }

    /** §5.3 全码 + 口径锁的冻结码集（DDL/契约测试比对基准）。 */
    public static final Set<String> ALL_WIRE_CODES = Collections.unmodifiableSet(BY_WIRE_CODE.keySet());

    /** 按上线字面量反查（大小写敏感，短横线形态）；未注册码直接 fail-loud。 */
    public static WAgentErrorCode fromWireCode(String wireCode) {
        WAgentErrorCode c = wireCode == null ? null : BY_WIRE_CODE.get(wireCode.trim());
        if (c == null) {
            throw new IllegalArgumentException("未注册的 W 域错误码: " + wireCode);
        }
        return c;
    }

    /** V234 {@code error_code VARCHAR(24)} 落库形态：枚举名去 E_ 前缀、连字符冻结（与 wireCode 同值）。 */
    public static String wireCodeOf(WAgentErrorCode code) {
        return code.wireCode();
    }

    /** 长度自检：全部码可落入 V234 error_code VARCHAR(24)（当前最长 E-WA-NARRATIVE-NUM=17）。 */
    public static boolean fitsRunStepErrorCodeColumn() {
        for (String code : ALL_WIRE_CODES) {
            if (code.length() > 24) {
                return false;
            }
        }
        return true;
    }

    static {
        if (!fitsRunStepErrorCodeColumn()) {
            throw new IllegalStateException("存在超出 V234 error_code VARCHAR(24) 的错误码，禁静默截断");
        }
        if (!ALL_WIRE_CODES.contains("E-WA-STATE".toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("错误码集缺 E-WA-STATE");
        }
    }
}
