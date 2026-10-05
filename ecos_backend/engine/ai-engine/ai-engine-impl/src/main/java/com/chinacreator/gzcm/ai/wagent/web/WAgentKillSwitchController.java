package com.chinacreator.gzcm.ai.wagent.web;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.governance.KillSwitch;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

/**
 * 分册10 §5.2 #11 · Kill Switch 编排 Controller（class-level {@code @Operation}）。
 * <p>scope=SYSTEM 且 caller 非 admin → 403 E-POLICY（caller 角色由上游
 * SecurityConfig/ClearanceInterceptor 写入 {@code X-User-Role} header；SYSTEM 操作
 * 强制 admin，include 审计在 {@link KillSwitch} 内登记）。
 * 无 double，无 JdbcTemplate，无 Scheduler。</p>
 */
@RestController
@Tag(name = "wagent-kill-switch", description = "W Agent Kill Switch（租户/系统双粒度）")
public class WAgentKillSwitchController {

    private static final Logger log = LoggerFactory.getLogger(WAgentKillSwitchController.class);
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SCOPE_TENANT = "TENANT";

    private final KillSwitch killSwitch;

    public WAgentKillSwitchController(KillSwitch killSwitch) {
        this.killSwitch = killSwitch;
    }

    // #11 POST /flags/kill-switch → 200 / 403 SYSTEM+非 admin
    @Operation(operationId = "setKillSwitch", summary = "Kill Switch（租户/系统双粒度）")
    @PostMapping(WAgentApiPaths.SET_KILL_SWITCH)
    @ResponseStatus(OK)
    public ApiResponse<Map<String, Object>> setKillSwitch(
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "") String userRole,
            @RequestBody KillSwitchDto dto) {
        log.debug("wagent setKillSwitch scope={} tenant={} enabled={} role={}",
                dto.scope(), dto.tenantId(), dto.enabled(), userRole);
        log.debug("wagent setKillSwitch routing=POST {}", WAgentApiPaths.SET_KILL_SWITCH);
        log.debug("wagent setKillSwitch authorize check scope=SYSTEM & role={}=", userRole);
        if (SCOPE_SYSTEM.equals(dto.scope()) && !"admin".equalsIgnoreCase(userRole)) {
            log.debug("wagent setKillSwitch SYSTEM scope denied for role={} -> 403 E-POLICY", userRole);
            throw new BusinessException(403, "E-POLICY kill-switch SYSTEM scope requires admin");
        }
        KillSwitch.Scope scope = new KillSwitch.Scope(dto.scope(), dto.tenantId(), dto.enabled(), dto.reason());
        KillSwitch.Scope stored = killSwitch.set(scope, "wagent-web-caller");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("scope", stored.scope());
        out.put("tenantId", stored.tenantId());
        out.put("enabled", stored.enabled());
        out.put("reason", stored.reason());
        out.put("pausedRunCount", killSwitch.pausedRunCount()); // integer-only
        return ApiResponse.success(out);
    }

    // ── 内联 DTO ──
    public record KillSwitchDto(String scope, String tenantId, boolean enabled, String reason) {
        public KillSwitchDto {
            Objects.requireNonNull(scope, "scope");
            if (!SCOPE_SYSTEM.equals(scope) && !SCOPE_TENANT.equals(scope)) {
                throw new BusinessException(400, "E-WA-V: scope 必须是 SYSTEM 或 TENANT");
            }
            if (SCOPE_TENANT.equals(scope) && (tenantId == null || tenantId.isBlank())) {
                throw new BusinessException(400, "E-WA-V: TENANT scope 必带 tenantId");
            }
        }
    }
}
