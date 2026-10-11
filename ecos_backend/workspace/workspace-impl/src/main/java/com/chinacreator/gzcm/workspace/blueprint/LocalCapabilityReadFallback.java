package com.chinacreator.gzcm.workspace.blueprint;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 能力契约读侧的本地可选桩（补充设计 v1.0 §6.6 验收门槛 3）。
 *
 * <p>aiming 侧的能力中心（Model &amp; Capability Center）尚未对本域提供只读契约实现
 * （接口不在 ai 模块公开面），本配置以 {@link ConditionalOnMissingBean} 提供 fail-closed 桩：
 * 任一 required capability 一律判「契约不可用」→ 发布门槛判不过（409），
 * <b>不伪造可用性</b>（§13.1 禁依赖失败伪装为空结果/成功）。
 * 能力中心落地后以 {@code @Primary} 实现覆盖本桩即可，不改接口签名。</p>
 */
@Configuration
public class LocalCapabilityReadFallback {

    /**
     * 方法名必须不同于配置类默认 bean 名（localCapabilityReadFallback）——
     * 同名会让 factory-bean 指回自身定义，Spring 启动即抛
     * BeanDefinitionStoreException（2026-10-11 全栈拉起实测修复）。
     */
    @Bean
    @ConditionalOnMissingBean(CapabilityReadService.class)
    public CapabilityReadService fallbackCapabilityReadService() {
        return (capabilityId, contractVersion) -> CapabilityReadService.CapabilityAvailability.unavailable(
                "能力中心契约尚未接线（local fallback，fail-closed）: capabilityId="
                        + capabilityId + (contractVersion == null ? "" : "@v" + contractVersion));
    }
}
