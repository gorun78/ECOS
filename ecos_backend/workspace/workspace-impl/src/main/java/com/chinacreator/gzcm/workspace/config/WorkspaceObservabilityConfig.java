package com.chinacreator.gzcm.workspace.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import com.chinacreator.gzcm.runtime.core.monitor.service.impl.MetricRecorderServiceImpl;

/**
 * 分册09v1.2 H-6 监控装配 —— workspace 侧 {@link IMetricRecorder} 门面 Bean（形态对齐
 * 认知域 {@code CognitiveObservabilityConfig} 先例：runtime-monitor 不自我装配，
 * 由使用侧配置类补 Bean，铁律 §2.5-5 禁业务侧自建注册表）。
 *
 * <p>宿主若启用 MeterRegistry/actuator，可注册自己的 {@link IMetricRecorder} 实现，
 * {@code @ConditionalOnMissingBean} 让本装配自动让位。</p>
 */
@Configuration
public class WorkspaceObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean(IMetricRecorder.class)
    public IMetricRecorder workspaceMetricRecorder() {
        return new MetricRecorderServiceImpl();
    }
}
