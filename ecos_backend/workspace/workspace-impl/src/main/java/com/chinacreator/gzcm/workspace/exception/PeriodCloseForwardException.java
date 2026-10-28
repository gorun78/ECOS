package com.chinacreator.gzcm.workspace.exception;

import java.util.Map;

/**
 * 期间关账承流面的下游语义载体（#146y，附录A 第 34 条 {@code POST /api/v1/workspace/periods/{yyyMM}/close}）。
 *
 * <p><b>为什么新立一类而非复用既存载体</b>：
 * {@link WorkspaceExceptionHandler#handleDownstream} 把任何 {@code HttpStatusCodeException}
 * 一律折成 500 {@code "INTERNAL"}，而 {@code WorkspaceDomainException} 的既存具体子类
 * （{@code ActionValidationException}／{@code ForbiddenOperationException}／
 * {@code CognitiveEngineUnavailableException}…）都带<b>固定</b> code 与固定语义 ——
 * 借用它们要么覆盖属主（data-engine）给出的机读码（400 {@code ECOS-DATA-023}、
 * 403 {@code ECOS-DATA-031}／{@code ECOS-SEC-501}），要么把「数据引擎不可达」误报成
 * 「认知引擎不可达」。本类的职责只有一条：<b>原样搬运属主状态行 + 属主机读码</b>，
 * 不做任何折叠或归因改写。</p>
 *
 * <p><b>X-41 治理</b>：下游原文（path／reason／cause）经 {@code technicalReason} 只进日志面，
 * 不进响应体 message 与 details；响应体只留按状态分档的固定文案 +
 * {@code engineHttpStatus}／{@code engineErrorCode} 两枚可机读键。</p>
 */
public class PeriodCloseForwardException extends WorkspaceDomainException {

    /** 数据引擎关账通道不可达／应答不可判定（503，禁冒充「关账成功」）。 */
    public static final String CODE_UNAVAILABLE = "DATANET_UNAVAILABLE";

    /** 下游应答既无机读码也不可归类（状态行仍按下游值透出，code 用本域中性词）。 */
    public static final String CODE_FORWARD_FAILED = "PERIOD_CLOSE_FAILED";

    /** 仅日志面可见的下游原文（响应体不带，口径同 {@code SecurityEngineUnavailableException}）。 */
    private final String technicalReason;

    public PeriodCloseForwardException(int httpStatus, String code, String safeMessage,
                                       String technicalReason, String engineErrorCode,
                                       Throwable cause) {
        super(httpStatus, code, safeMessage, cause);
        this.technicalReason = technicalReason;
        putDetail("engineHttpStatus", httpStatus);
        putDetail("engineErrorCode", engineErrorCode == null ? "" : engineErrorCode);
        putDetail("engine", "datanet");
    }

    /** 数据引擎侧不可达／不可判定 —— 503 fail-closed，禁按「无回执即成功」搬运。 */
    public static PeriodCloseForwardException unavailable(String technicalReason, Throwable cause) {
        return new PeriodCloseForwardException(503, CODE_UNAVAILABLE,
                "数据引擎暂不可用，期间关账请求未确认，请稍后重试或凭追踪号联系管理员",
                technicalReason, CODE_UNAVAILABLE, cause);
    }

    /** 按下游状态分档给固定文案；状态行与机读码逐字搬运属主值。 */
    public static PeriodCloseForwardException forwarded(int httpStatus, String engineErrorCode,
                                                        String safeMessage, String technicalReason,
                                                        Throwable cause) {
        String code = engineErrorCode == null || engineErrorCode.isBlank()
                ? CODE_FORWARD_FAILED : engineErrorCode;
        return new PeriodCloseForwardException(httpStatus, code, safeMessage, technicalReason,
                engineErrorCode, cause);
    }

    /** 下游状态 → 无泄露的固定文案（分档口径见 §4.7.2「不可用 ≠ 无权限」）。 */
    public static String safeMessageFor(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> "期间标识形态非法或请求体不可解析（由数据引擎判定）";
            case 401 -> "出站凭证缺失或失效，关账请求未被数据引擎受理";
            case 403 -> "关账权限不足或被策略拒绝（fail-closed，未落库）";
            case 404 -> "数据引擎关账面未承流（契约路径在属主侧不存在）";
            case 409 -> "期间状态冲突，本次关账未执行";
            case 422 -> "关账请求被数据引擎判定为不可处理";
            default -> "数据引擎拒绝本次关账请求";
        };
    }

    public String getTechnicalReason() {
        return technicalReason;
    }

    /** 便于测试断言 details 形状（不改变 {@link #getDetails()} 的只读语义）。 */
    public Map<String, Object> detailView() {
        return getDetails();
    }
}
