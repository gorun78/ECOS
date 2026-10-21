package com.chinacreator.gzcm.engine.data.governance;

import java.io.Serial;

import com.chinacreator.gzcm.common.exception.DataBridgeException;

/**
 * security-engine ABAC 裁决拒绝（§7.5：写操作必须过统一裁决）。
 * HTTP 403 语义（同 {@code ForbiddenException}），由 Controller / 全局异常处理转 403。
 * <b>只用于策略 DENY</b>：security-engine 不可用（UNEVALUATED）属"护栏没说话"，
 * 按 §4.7.2 另落 503／{@code ECOS-SEC-501}（见 {@code DataGovernanceCandidateService#abacUnavailable}），
 * 不得复用本异常冒充"用户无权限"。
 */
public class SecurityDenyException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 403;
    public static final int DEFAULT_ERROR_CODE = 403;

    public SecurityDenyException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }
}
