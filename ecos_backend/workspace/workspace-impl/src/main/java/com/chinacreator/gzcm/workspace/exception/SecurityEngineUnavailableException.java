package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

/**
 * security-engine <b>进程不可用</b>（详细设计-01 §4.5.4 六×四降级矩阵 / §4.7.1 三通道全 DENY
 * 门禁行 / §4.7.2「security 不可用（全角色）」格，HTTP 503 + {@code ECOS-SEC-501}，
 * 验收载体 {@code SecurityDownAllChannelDenyTest#page} / {@code #export}）。
 *
 * <p><b>为什么需要这个类型</b>：从零实施版 line 2602 明确「503 DENY，<b>禁渲染空列表冒充"无数据"</b>」，
 * 而 §4.2（ai 域同源口径 F06-14）要求「不可用 ≠ 拒绝，禁相互折叠」。整改前
 * {@link com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient} 把连不上 / 超时 /
 * 5xx 与 security 正常应答后的策略 DENY 一并折叠成 {@code "1=0"} / 空列集 / {@code false}，
 * 于是页面通道把"安全引擎故障"渲染成"你没有数据"（HTTP 200 + 空列表），
 * 读咽喉 {@code ScenarioReadGuard} 则把不可用误报成 403 PERMISSION_DENIED。
 * 本类型只承载<b>可用性故障</b>这一态。</p>
 *
 * <p><b>分态口径（不得越界）</b>：security-engine 正常应答后的 DENY、4xx 鉴权失败仍走
 * 各自既有兜底（{@code "1=0"} / 空列集 / {@code false} → 403 语义），分类点唯一在
 * {@link com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient}。
 * <b>#142/B4-3e-6 订正（差距 D-B4-41）</b>：原文把"响应缺段不可解析"归入 403 兜底，
 * 该口径已被推翻 —— 应答里<b>无可识别裁决</b>（{@code UNEVALUATED}：缺 {@code allow} 键／
 * {@code source=fallback}／带 {@code opaError}／{@code policyState} 未评估）属"护栏没说话"，
 * 与连不上同源，一律走本类型（503 + {@code ECOS-SEC-501}），禁冒充"策略拒绝"。</p>
 *
 * <p>X-41 治理沿用：下游原文（path / reason / cause）只进日志面供回查，
 * 不进响应体 message 与 details。</p>
 */
public class SecurityEngineUnavailableException extends WorkspaceDomainException {

    /** 仅日志面可见的下游原文（响应体不带，见类注释 X-41）。 */
    private final String technicalReason;

    public SecurityEngineUnavailableException(String path, String reason, Throwable cause) {
        super(503, RlsExecutionContract.SEC_UNAVAILABLE_CODE,
                "安全引擎暂不可用，操作已按 fail-closed 拒绝，请稍后重试或凭追踪号联系管理员", cause);
        this.technicalReason = "path=" + path + " reason=" + reason;
        putDetail("engine", "security");
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
