package com.chinacreator.gzcm.workspace.exception;

import java.util.List;

/**
 * 口径状态前置不成立（#146ad 附录A 第 31 条写面，HTTP 409，code={@code CALIBER_STATE_PRECONDITION}）。
 *
 * <p>契约错误码列原文（详设-12 §12.4.2 line 8253/8255）要求送审与升版在版本行状态不满足
 * 前置时回 409；本枚是该码在仓内的<b>唯一持有者</b>（grep 实扫：改前全仓 0 命中，不存在第二套文案）。
 * 属主面对同一情形只回 400 {@code ECOS-ONTO-040}（HTTP 恒 200、语义在信封码面），
 * 委托侧不照抄属主码，而是在出网前按属主详情面的版本行状态自行判定并回契约态。</p>
 */
public class CaliberStatePreconditionException extends WorkspaceDomainException {

    public CaliberStatePreconditionException(String caliberId, String code, String versionNo,
                                             String observedStatus, List<String> expectedStatuses) {
        super(409, "CALIBER_STATE_PRECONDITION",
                "口径版本状态不满足该操作前置: " + code);
        putDetail("caliberId", caliberId);
        putDetail("caliberCode", code);
        putDetail("versionNo", versionNo);
        putDetail("observedStatus", observedStatus);
        putDetail("expectedStatuses", expectedStatuses);
    }
}
