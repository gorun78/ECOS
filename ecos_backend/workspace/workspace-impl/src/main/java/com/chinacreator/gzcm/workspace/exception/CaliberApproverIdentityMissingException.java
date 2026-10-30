package com.chinacreator.gzcm.workspace.exception;

/**
 * 审批主语不可得（#146ad 附录A 第 31 条 {@code approveCaliber}，HTTP 403，
 * code={@code APPROVER_IDENTITY_MISSING}）。
 *
 * <p>契约入参要点是「审批人取 JWT 主体」（详设-12 line 8254），属主面
 * {@code CaliberController#approve} 也只认 {@code UserContext.getCurrentUsername()}。
 * 本枚承载的是<b>委托侧</b>判定：入口请求既无 {@code Authorization} 也无
 * {@code X-ECOS-USER} 信任头 ⇒ 没有任何可归因的主语，审批不可执行。</p>
 *
 * <p><b>为什么是 403 而不是 503</b>：{@code CallerCredentialSource} 纪律③要求区分
 * 「没有凭证」与「有凭证但被拒」，但两者在本腿上的契约表达同属 403 档位
 * （approve 错误码列 = 403/409/503，其中 503 已被「属主引擎不可得」占用）。
 * 传输故障（属主连不上）走 {@link CaliberOwnerUnavailableException}，与本枚不同源，
 * 禁互相顶替。本枚在出网之前即拦下，属主侧零调用。</p>
 */
public class CaliberApproverIdentityMissingException extends WorkspaceDomainException {

    public CaliberApproverIdentityMissingException(String caliberId) {
        super(403, "APPROVER_IDENTITY_MISSING",
                "审批人身份缺失：入口请求未携带可归因的凭证头，审批不执行");
        putDetail("caliberId", caliberId);
        putDetail("requiredCarriers", "Authorization 或 X-ECOS-USER");
        putDetail("ownerCallExecuted", false);
    }
}
