# runtime-event (器·Kafka 事件总线) 接口与验收 flows

> 横切底座·器 | PMO-50 A 新增 | 跨模块事件通信统一入口 (铁律 §2.5 公共底座)
> 可降级: Kafka broker 不可用时自动 fallback 内存 SpringEventBus (本地/开发态)
> 源码: EventBusService / KafkaEventBusServiceImpl / MemoryEventBusServiceImpl / MemoryEventBusFallbackCondition

## 定位

- runtime 体系新增横切底座, 与 runtime-task / runtime-monitor / llm-gateway / runtime-access 兄弟
- KafkaTopics 契约常量保留在 `runtime/common-api` (P8-A 收尾后), 本模块只依赖该常量
- 禁止各引擎/服务再自建 KafkaTemplate / producer / consumer — 一律走本模块 EventBusService

## 两类 Bean (条件装配, 互斥)

| Bean | 激活条件 | 行为 |
|------|---------|------|
| `KafkaEventBusServiceImpl` | service 类 `@ConditionalOnClass(KafkaTemplate.class)` + service 级 `@ConditionalOnProperty(ecos.event.kafka.enabled=true)` | Kafka producer; `publish` 走 `KafkaTemplate.send` 异步; `isKafkaAvailable` 走 `describeCluster().nodes()` 探活 |
| `MemoryEventBusServiceImpl` | `@Conditional(MemoryEventBusFallbackCondition)` (容器内无 KafkaEventBusServiceImpl bean) | 内存 Spring `ApplicationEventPublisher` 同步 in-process 分发; `isKafkaAvailable` 永远 false |

二者互斥不冲突: 默认 `ecos.event.kafka.enabled` 未配置 = false → Kafka impl 不加载 → 内存 fallback 自动激活。

## 配置项

```yaml
# application.yml (生产示例)
ecos:
  event:
    kafka:
      enabled: true                # 切 Kafka (默认 false 走内存 fallback)

# Spring Kafka 标准配置 (Spring Kafka 3.1.x AutoConfiguration 读取)
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
    listener:
      auto-startup: true
      ack-mode: batch
      concurrency: 3
```

## 使用样例

### Producer 侧 (buszhi / 任一 publish 模块)

```java
// 条件化注入 — 内存 fallback 时 bean 也在 (Memory impl 提供 runtime-event 的 bean)
private final Optional<EventBusService> eventBus;

public MyPublisher(Optional<EventBusService> eventBus) { this.eventBus = eventBus; }

public void onEvent(MyEvent evt) {
    // 1. 兼容 Spring 内存路径 (既有 @EventListener 监听不变)
    appEventPublisher.publishEvent(evt);
    // 2. 扩展 Kafka 路径 (条件化 Optional, 不可用时 log 兜底不挂)
    eventBus.ifPresentOrElse(
        bus -> bus.publish(KafkaTopics.ONTOLOGY_PUBLISHED, evt),
        () -> log.warn("[EventBus] not available, kafka-only path skipped topic={}",
                       KafkaTopics.ONTOLOGY_PUBLISHED));
}
```

### Consumer 侧 (dccheng / dbus 任一 listen 模块)

```java
// 保留 @EventListener 路径 (内存 fallback 时仍可工作)
@EventListener
public void onSpringEvent(MyEvent evt) { /* 既有逻辑不变 */ }

// 追加 @KafkaListener 分支 (仅 enabled=true 时装配)
@ConditionalOnClass(name = "org.springframework.kafka.annotation.KafkaListener")
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
@KafkaListener(topics = KafkaTopics.ONTOLOGY_PUBLISHED,
               groupId = "dccheng-ontology-consumer",
               containerFactory = "kafkaListenerContainerFactory")
public void onKafkaJson(String json) {
    MyEvent evt = MAPPER.readValue(json, MyEvent.class);
    // 复用同一处理逻辑 (抽取 runSync 方法, 与原 @EventListener 共用)
    runSync(evt);
}
```

## DLQ (Dead Letter Queue)

- 标准 Spring Kafka DLQ topic = `<topic>.DLT` (kafkaTemplate 或 DefaultErrorHandler 配置)
- consumer 反序列化/业务处理连续失败 N 次 (DefaultErrorHandler `maxAttempts` 默认 3) → 投 DLQ
- DLQ 内消息需人工/治理侧定期巡检, 不在本模块实现 (走 sysman / runtime-monitor 侧)

## 别接 (调谁, 已核)

- 引擎禁止 `new KafkaTemplate` 或自建 producer/consumer bean — 走本模块
- 引擎禁止自建 `ApplicationEventPublisher` 做跨 JVM 替代 — 内存路径仅单 JVM
- LLM/HTTP/Web 不入本模块 (各自归 llm-gateway / runtime-access)
- 监控/告警不属本模块 (归 runtime-monitor), 仅复用其健康检查基线 (actuator health 自动带 KafkaHealthIndicator)

## 验收 flows

1. **降级冒烟 (V4)**: 不配置 `ecos.event.kafka.enabled=true` → 应用启动不应挂; 发 `buszhi` 侧 `publish` → dbus 侧 `@EventListener` 收到同 JVM 内存事件; `EventBusService.isKafkaAvailable()` 返回 false
2. **Kafka 路径**: 配置 `ecos.event.kafka.enabled=true` + broker 可达 → 启动; `EventBusService.isKafkaAvailable()` 返回 true; 同上 publish → dbus 侧 `@KafkaListener` 收到
3. **broker 不可达降级**: 配置 enabled=true 但 broker 不可达 → 启动成功; `publish` 不阻塞 (异步 send 失败仅 WARN); `isKafkaAvailable` 返回 false
4. **grep 集成点**: `rg "EventBusService\|KafkaEventBusService\|KafkaTopics.ONTOLOGY_PUBLISHED" --type java` 至少 1 处 contract 常量 (common-api) + 2 处 producer (ontology publish + 可能的 backup) + 1 处 consumer (dccheng)

## 编译门

```bash
# 模块级独立编译 (V3)
& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f D:\workspace\javaprojects\ECOS\ecos_backend\runtime\runtime-event\pom.xml install -DskipTests -q
```

全量 install 见根 pom (architecture-rules §5.4 V3): `mvn -f ecos_backend/pom.xml install -DskipTests` BUILD SUCCESS。
