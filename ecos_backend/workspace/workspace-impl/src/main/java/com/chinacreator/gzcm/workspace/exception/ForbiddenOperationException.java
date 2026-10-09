package com.chinacreator.gzcm.workspace.exception;

/**
 * 授权拒绝（security-engine evaluate DENY 或引擎不可用默认 DENY，HTTP 403 / PERMISSION_DENIED）。
 *
 * <p>铁律 §0.2.1：安全一律经 security-engine，不可用即默认 DENY。403 不回显资源存在性。</p>
 */
public class ForbiddenOperationException extends WorkspaceDomainException {

    public ForbiddenOperationException(String resource) {
        super(403, "PERMISSION_DENIED", "无权限执行该操作");
        putDetail("resource", resource);
    }
}
