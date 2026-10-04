package com.chinacreator.gzcm.runtime.llm.harness;

import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-22 / R-18：ABAC 裁决在 <b>aiming service 态</b>不得被静默跳过。
 *
 * <p>R-18 的现实形态：aiming service 独立部署时，security-engine 的
 * {@code OpaPolicyService} 不在其 classpath（llm-gateway 对 security 仅
 * 「类名反射 + 可选 bean」，编译期零依赖）。此态下
 * {@link SecurityEngineBridge#evaluate} 必须 fail-closed <b>DENY</b>，
 * 而<b>不是</b>因「OPA 不可见」被静默当成"无需裁决"而放行。
 *
 * <p>切片 {@link LlmSliceServiceModeApp} 刻意<b>不注册</b> OPA 桩 bean
 * （模拟 security 不可见）。注意：桩类本身仍在本模块 test classpath，
 * 故此处精确复刻的是「OPA 类可见但 bean 未装配」的中间形态 ——
 * {@code Class.forName} 成功但 {@code getBeanNamesForType} 为空 →
 * {@code findBeanByClassName} 返回 null → DENY。二者殊途同归，
 * fail-closed 路径等价（与「OPA 类完全不在 classpath」的 ClassNotFoundException
 * 分支都落在同一 {@code if (bean == null) return false}）。
 *
 * <p>防的假阳性（X-18）：若 {@code abac-eval-enabled=false} 被误当放行开关，
 * 或 bridge 因 OPA 不可见而返回 true，本断言即 FAIL。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = LlmSliceServiceModeApp.class)
class GatewayAbacModeTest {

    @Autowired
    SecurityEngineBridge bridge;

    @Test
    @DisplayName("F06-22 abacDenyIsNotSilentlySkippedInServiceMode — OPA 不可见时 evaluate 必须 DENY")
    void abacDenyIsNotSilentlySkippedInServiceMode() {
        boolean verdict = bridge.evaluate("pm-operator", "default",
                "llm-gateway.call", Map.of("provider", "deepseek"));
        assertFalse(verdict,
                "aiming service 态 OPA 不可见，evaluate 必须 fail-closed DENY，"
                        + "禁止因 security 不可用而静默放行（R-18 / X-18 绕过面）");
    }

    @Test
    @DisplayName("F06-22 isSecurityEngineAvailable() service 态应报 false（区分『不可用』与『断连』）")
    void securityEngineReportedUnavailableInServiceMode() {
        assertFalse(bridge.isSecurityEngineAvailable(),
                "aiming service 态无 ISecretService/IDataEncryptionService bean，"
                        + "isSecurityEngineAvailable 应 false");
    }
}
