package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import com.chinacreator.gzcm.runtime.core.monitor.service.impl.MetricRecorderServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 详细设计-05 C.7-4 指标装配 —— 认知域计数经 runtime-monitor 门面，禁引擎自建注册表（铁律 §2.5-5）。
 *
 * <p>{@link MetricRecorderServiceImpl} 是公共底座实现（与 {@code WarnLogServiceImpl} 同款约定：
 * runtime-monitor 不自我装配，由使用侧配置类补 Bean，见 {@link CognitiveMentalConfig} 先例）。
 * 宿主若启用 MeterRegistry/actuator，可注册自己的 {@link IMetricRecorder} 实现，
 * {@code @ConditionalOnMissingBean} 让本装配自动让位。</p>
 */
@Configuration
public class CognitiveObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean(IMetricRecorder.class)
    public IMetricRecorder cognitiveMetricRecorder() {
        return new MetricRecorderServiceImpl();
    }
}
