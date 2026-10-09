package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link MigrationPermissionChecker} 生产实现（卷07 F07-07-3 / F07-18）。
 * <p>裁决一律走 security-engine（{@code evaluate} 失败即 DENY），主体取自 JWT（由调用方传入），
 * 禁本地角色字符串比较、禁请求体自填 userId。</p>
 */
@Component
public class SecurityEngineMigrationPermissionChecker implements MigrationPermissionChecker {

    private final WorkspaceSecurityEngineClient securityClient;

    public SecurityEngineMigrationPermissionChecker(WorkspaceSecurityEngineClient securityClient) {
        this.securityClient = securityClient;
    }

    @Override
    public boolean isAllowed(String permissionPoint, String subject) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("permission", permissionPoint);
        attrs.put("subject", subject == null ? "anonymous" : subject);
        return securityClient.evaluate(permissionPoint, attrs);
    }
}
