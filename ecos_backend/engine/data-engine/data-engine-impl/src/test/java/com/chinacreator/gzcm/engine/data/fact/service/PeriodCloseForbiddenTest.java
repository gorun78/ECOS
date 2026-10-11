package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.engine.data.fact.dao.PeriodStatusMapper;
import com.chinacreator.gzcm.engine.data.fact.period.OpenPeriodsPeriodLock;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 关账越权 403 fail-closed（H-3 冻结测试类⑤，详设-09 v1.2 §十一 H-3 交付①）：
 * <ul>
 *   <li>OPA 判 DENY → 403 {@link ForbiddenException}，消息带 reason，落库/审计零接触；</li>
 *   <li>security-engine 不可用（evaluate 返回 SECURITY_ENGINE_UNAVAILABLE）→ 同样 403
 *       ——<b>不可用即拒绝（fail-closed），绝不允许降级放行</b>；</li>
 *   <li>DENY 时序在落库之前（先门槛后写入），且 role 为 claims 原值透传
 *       （本域不做"角色名→权限"硬编码映射——测试以 action/resource 入参核对调用形态）；</li>
 *   <li>非法期间形态 → 400 且 OPA/DB 均未触达（入口即拒）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PeriodCloseForbiddenTest {

    @Mock private PeriodStatusMapper periodStatusMapper;
    @Mock private PipelineSecurityService securityService;
    @Mock private EventBusService eventBus;

    private PeriodCloseService service;

    @BeforeEach
    void setUp() {
        service = new PeriodCloseService(periodStatusMapper, securityService,
                new OpenPeriodsPeriodLock(""), Optional.empty(), Optional.of(eventBus));
    }

    @Test
    @DisplayName("OPA DENY（RBAC_DENY）→ 403 ForbiddenException，消息含 reason；DB/事件总线零接触")
    void opaDenyThrows403AndTouchesNothing() {
        when(securityService.evaluate(eq("data.period.close"),
                eq("ecos_data_period_status:2025-01"), any()))
                .thenReturn(PipelineSecurityService.AllowedResult.deny("RBAC_DENY"));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.close("2025-01", null));

        assertEquals(403, ex.getHttpStatus(), "越权必须 403（非 401/409/500）");
        assertEquals("ECOS-DATA-031", ex.getMessage().substring(0, "ECOS-DATA-031".length()),
                "错误码前缀沿用 ECOS-DATA-031（403 文案在消息体内）");
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("RBAC_DENY"),
                "拒绝原因必须回显供审计/前端定位，不得吞掉");
        verifyNoInteractions(periodStatusMapper);   // fail-closed：DENY 前置于任何落库
        verifyNoInteractions(eventBus);             // 更不发 ecos.audit
    }

    @Test
    @DisplayName("security-engine 不可用（SECURITY_ENGINE_UNAVAILABLE）→ 仍 403，不得放行（fail-closed）")
    void securityEngineUnavailableIsDenyNotAllow() {
        when(securityService.evaluate(eq("data.period.close"), anyString(), any()))
                .thenReturn(PipelineSecurityService.AllowedResult.deny("SECURITY_ENGINE_UNAVAILABLE"));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> service.close("202502", null));

        assertEquals(403, ex.getHttpStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("SECURITY_ENGINE_UNAVAILABLE"));
        verifyNoInteractions(periodStatusMapper);
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("OPA 即使对空角色也须被询（role 原值透传，本域不预判放行）")
    void roleClaimsPassedThroughWithoutLocalShortCircuit() {
        // 无 UserContext 环境 role=null —— 关键断言：null 角色不许在本地直接 allow，
        // 必须仍把裁决权交给 security-engine（防"没登录≈系统调用"的隐性后门）。
        when(securityService.evaluate(eq("data.period.close"), anyString(), eq((String) null)))
                .thenReturn(PipelineSecurityService.AllowedResult.allow("RBAC_ALLOW"));
        when(periodStatusMapper.selectByPeriod("2025-03")).thenReturn(null);

        service.close("2025-03", null);

        verify(securityService).evaluate(eq("data.period.close"),
                eq("ecos_data_period_status:2025-03"), eq((String) null));
    }

    @Test
    @DisplayName("非法期间形态 → 400 ECOS-DATA-023，OPA/DB 均未触达（入口即拒，省一次策略往返）")
    void malformedPeriodRejectedBeforePolicyCall() {
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service.close("2025-13", null));
        assertEquals(400, ex.getHttpStatus());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("ECOS-DATA-023"));

        assertThrows(DataBridgeException.class, () -> service.close("abc", null));
        assertThrows(DataBridgeException.class, () -> service.close("  ", null));

        verifyNoInteractions(securityService);
        verifyNoInteractions(periodStatusMapper);
        verifyNoInteractions(eventBus);
    }
}
