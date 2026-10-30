package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;

/**
 * 口径属主引擎不可得（#146ac 附录A 第 31 条读面三枚，HTTP 503 / {@code ONTOLOGY_UNAVAILABLE}）。
 *
 * <p>口径主权归本体域，workspace 面只经属主 REST 委托取数（铁律 §3.1 跨引擎不操作对方表）。
 * 委托通道的<b>传输层故障</b>（连不上／超时／非 2xx／应答不可解析）一律归本态：
 * 「读不到」与「没有数据」是两个态（X-43/X-44），禁折叠成 200 空列表冒充查询成功，
 * 也不得降级成 403 冒充「无权限」（详设-01 §4.7.2「不可用 ≠ 无权限」）。</p>
 *
 * <p>X-41：下游原文（异常 message／URL／堆栈）由调用点 {@code log.warn} 落日志面，
 * 不进本异常的 message 与 details。</p>
 */
public class CaliberOwnerUnavailableException extends WorkspaceDomainException {

    public CaliberOwnerUnavailableException(String operation) {
        super(503, FCScenarioErrorCode.CODE_ONTOLOGY_UNAVAILABLE, "口径属主引擎暂不可用，请稍后重试或凭追踪号联系管理员");
        putDetail("engine", "ontology");
        putDetail("operation", operation);
    }
}
