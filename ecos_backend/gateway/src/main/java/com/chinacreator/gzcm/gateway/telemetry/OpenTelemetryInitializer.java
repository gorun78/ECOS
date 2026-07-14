package com.chinacreator.gzcm.gateway.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;

/**
 * P3-5 OpenTelemetry SDK 初始化器。
 * <p>
 * 在 Spring Context 刷新后初始化 OTel SDK：
 * <ul>
 *   <li>创建 SdkTracerProvider 并配置 PostgresSpanExporter</li>
 *   <li>注册为 GlobalOpenTelemetry，供 TelemetryInterceptor 使用</li>
 * </ul>
 */
@Component
public class OpenTelemetryInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInitializer.class);

    private final JdbcTemplate jdbc;

    public OpenTelemetryInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initOpenTelemetry() {
        try {
            // 1. 创建 Resource（标识服务）
            Resource resource = Resource.builder()
                    .put(SERVICE_NAME, "ecos-gateway")
                    .put("service.version", "P3-5")
                    .build();

            // 2. 创建 PostgresSpanExporter
            PostgresSpanExporter exporter = new PostgresSpanExporter(jdbc);

            // 3. 创建 SdkTracerProvider
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(Sampler.alwaysOn())
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                            .setMaxQueueSize(2048)
                            .setMaxExportBatchSize(512)
                            .build())
                    .build();

            // 4. 构建 OpenTelemetrySdk 并注册为全局实例
            OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .buildAndRegisterGlobal();

            log.info("P3-5 OpenTelemetry SDK initialized with PostgresSpanExporter");

        } catch (Exception e) {
            log.error("Failed to initialize OpenTelemetry SDK: {}", e.getMessage(), e);
        }
    }
}
