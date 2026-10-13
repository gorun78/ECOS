package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kill Switch 端点（详细设计-10 §5.2 #11 setKillSwitch，F10-21）。
 *
 * <p><b>在位语义（本批交付）</b>：入参校验（scope ∈ {tenant, system}、enabled 必填、
 * reason 必填）+ <b>admin 角色闸 fail-closed</b>（非 admin ⇒ 403 {@code E-POLICY}，
 * §5.3"含 Kill Switch"条目）+ 审计意图声明。角色唯一来源 =
 * {@link WAgentIdentityResolver#requireWithRoles()}（token 上下文，禁请求体自报）。</p>
 *
 * <p><b>明确降级（无假真理）</b>：标志位持久化单源（sysman 配置面 {@code wagent.flags} 组，
 * 跨切约束"配置只经 sysman api 门面"）与 Run 管线侧 paused 联动（F10-21 依赖 #130 调度接驳）
 * 均未接线 ⇒ 校验通过后如实 {@code 501 NOT_IMPLEMENTED}，<b>禁</b>返回"开关已置位"伪成功。</p>
 */
@RestController
public class WAgentKillSwitchController {

    /** 作用域两值单源（§5.2 #11 租户/系统级）。 */
    static final Set<String> SCOPES = Set.of("tenant", "system");

    private final WAgentIdentityResolver identityResolver;

    public WAgentKillSwitchController(WAgentIdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    /** #11 请求体（system 级与 tenant 级共用；tenant 恒取认证主体，禁自报）。 */
    public record KillSwitchRequest(String scope, Boolean enabled, String reason) {
    }

    @PostMapping(WAgentApiPaths.FLAGS_KILL_SWITCH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> setKillSwitch(
            @RequestBody KillSwitchRequest req) {
        if (req == null || req.scope() == null || req.scope().isBlank()) {
            throw WAgentOrchestrationException.validation("scope 必填（tenant|system）");
        }
        String scope = req.scope().trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw WAgentOrchestrationException.validation("scope 越界（tenant|system 单源）: " + req.scope());
        }
        if (req.enabled() == null) {
            throw WAgentOrchestrationException.validation("enabled 必填（true|false）");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw WAgentOrchestrationException.validation("reason 必填（kill-switch 变更必须留痕）");
        }
        IdentityWithRoles identity = identityResolver.requireWithRoles();
        if (!identity.isAdmin()) {
            // 403 fail-closed：非 admin 不得改旗（§5.3 E-POLICY 含 Kill Switch 明文）
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "kill-switch 仅 admin 角色可操作（当前主体角色不满足，fail-closed）", null);
        }
        return WAgentDegradation.notImplemented(
                "F10-21 标志位存储(sysman config wagent.flags 未接线)+Run 管线 paused 联动(#130)");
    }
}
