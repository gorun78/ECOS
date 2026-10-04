package com.chinacreator.gzcm.engine.security.service;

import java.util.List;
import java.util.Map;

/**
 * F06-22（X-87 / W164 → C146）ABAC 两态桩 — 对 security-engine 生产实现
 * {@code com.chinacreator.gzcm.engine.security.service.OpaPolicyService} 的最小替代。
 *
 * <p>刻意使用与被替身<b>完全相同的全限定名</b>，落在 llm-gateway 的 {@code src/test/java}：
 * 编译只进入 test classpath，不打进产物。这样
 * {@code SecurityEngineBridge#findBeanByClassName(OPA_SERVICE_FQN)} 的
 * {@code Class.forName + getBeanNamesForType} 两看：gateway 态切片注册本 bean → 命中，
 * 裁决放行；aiming service 态切片不注册 → 命中失败 → 反射 fail-closed DENY。</p>
 *
 * <p>桩语义：一律 ALLOW + 携带 policyId/obligations，与生产 OPA 的响应键
 * （{@code allow}/{@code result}）兼容；不校验入参，纯切片足迹。</p>
 */
public class OpaPolicyService {

    public Map<String, Object> evaluate(String mode, Map<String, Object> input) {
        return Map.of(
                "allow", Boolean.TRUE,
                "result", Boolean.TRUE,
                "policyId", "f06-22-stub-policy",
                "obligations", List.of("redact_pii"),
                "reason", "F06-22 harness stub: ALLOW with obligations for two-mode slice");
    }
}
