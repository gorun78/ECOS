package com.chinacreator.gzcm.engine.cognitive2.config;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IWarnLogService;
import com.chinacreator.gzcm.runtime.core.monitor.service.impl.WarnLogServiceImpl;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * PMO-59 P2b 心智层装配配置 — 仅补"公共底座缺口"，不新建引擎自有基建（铁律 §2.5 补强而非自建）。
 *
 * <p>注册项（Kafka 相关均 gate 在 {@code ecos.event.kafka.enabled=true} 时才生效，未启用态零 Bean 零连接）：
 * <ol>
 *   <li><b>IWarnLogService</b>：runtime-monitor 既有实现 {@code WarnLogServiceImpl} 原本无 Spring
 *       装配（runtime 缺口），按 PMO-59 指令 §禁止清单 7 授权例外补 Bean，
 *       使 {@code CognitiveHypothesisService} 失效告警可达 runtime-monitor（铁律 §2.5 监控收敛）。
 *       内存态实现（warn 正文含 faultContext 结构化字段，周一故障复盘可经 findByPage 查询），
 *       落库/通知通道 Phase 3+ 按 runtime-monitor 演进补齐。</li>
 *   <li><b>类型化 Kafka producer（{@link DefaultKafkaProducerFactory} + {@code KafkaTemplate<String,String>}）</b>：
 *       {@code KafkaEventBusServiceImpl} 构造器要求 {@code KafkaTemplate<String,String>}，
 *       而 Spring Kafka AutoConfiguration 只产 {@code KafkaTemplate<Object,Object>}（泛型不变性），
 *       若再依赖 auto-config 还会产双 KafkaTemplate 二义性 —— 故由本配置显式供给类型化模板
 *       （host 应用侧缺口补强，producer 属性与 gateway {@code spring.kafka.producer} 口径一致：
 *       StringSerializer + acks=1）。业务事件仍统一走 runtime-event EventBusService 契约（铁律 §2.5 不破坏）。</li>
 * </ol>
 *
 * <p><b>双 EventBus 互斥说明（2026-09-14 PMO-59 P2b 实证修复）</b>：
 * {@code KafkaEventBusServiceImpl} 与 {@code MemoryEventBusServiceImpl} 的互斥原依赖
 * {@link com.chinacreator.gzcm.runtime.eventbus.MemoryEventBusFallbackCondition} 的 beanDefinition 扫描，
 * 但与组件扫描顺序相关存在缺陷（两 @Service 同批扫描，顺序不定 → 曾双 bean 同存致启动崩）。
 * 已按 runtime 缺口补强修复该 Condition（增加配置面短路，语义与 Kafka 实现激活条件完全一致）；
 * 本配置类<b>不再自行注册 EventBus bean</b>（v3 @Import 显式 bean 方案已废弃：其注册晚于 Condition 求值）。
 * </p>
 */
@Configuration
public class CognitiveMentalConfig {

    private static final Logger log = LoggerFactory.getLogger(CognitiveMentalConfig.class);

    /**
     * 告警日志服务（runtime-monitor 公共底座；补强既有 impl 的 Spring 装配缺口）。
     */
    @Bean
    @ConditionalOnMissingBean(IWarnLogService.class)
    public IWarnLogService cognitiveWarnLogService() {
        log.info("[CognitiveMental] IWarnLogService 已按 PMO-59 授权例外补装配 (runtime-monitor 缺口补强)");
        return new WarnLogServiceImpl();
    }

    /** Kafka 启用且 spring-kafka 在 classpath 时，生成本配置自有的 producer factory（类型化键值）。 */
    @Bean
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
    public DefaultKafkaProducerFactory<String, String> cognitiveKafkaProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        log.info("[CognitiveMental] Kafka producer 已装配 (host 缺口补强): bootstrap={}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /** 类型化 KafkaTemplate——{@link com.chinacreator.gzcm.runtime.eventbus.KafkaEventBusServiceImpl} 构造器注入用。 */
    @Bean
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
    public KafkaTemplate<String, String> cognitiveKafkaTemplate(
            DefaultKafkaProducerFactory<String, String> cognitiveKafkaProducerFactory) {
        return new KafkaTemplate<>(cognitiveKafkaProducerFactory);
    }
}
