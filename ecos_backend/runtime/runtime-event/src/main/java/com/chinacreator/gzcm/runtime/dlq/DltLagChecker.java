package com.chinacreator.gzcm.runtime.dlq;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * DLQ 消费组 lag 巡检（C.5.2：lag&gt;100/5min → 告警）。
 * <p>
 * 周期 5 分钟经 {@link AdminClient#listConsumerGroupOffsets} +
 * {@link AdminClient#listOffsets(Map)} 计算 {@code ecos-dlt-consumer}
 * 组在每个 {@code <topic>.DLT} 上的 lag = latest - committed；
 * 任一 partition lag &gt; 100 → 触发规则 {@code kafka.dlt.lag} 同一规则告警
 * （severity=warn，rule 不存在时自动创建）。
 * <p>
 * 降级（Spec：不可用则 log.warn）：AdminClient 创建失败 / broker 不可达 /
 * 消费组尚未向 broker 注册 → catch 后仅 WARN，不打 ERROR、不刷栈。
 *
 * <p>实现约束：使用 Kafka clients 3.1.2 兼容 API（{@code partitionsToOffsetAndLag}
 * 只在 3.6+ 提供；项目锁 3.1.2，故走 listConsumerGroupOffsets + listOffsets 双路组合）。
 */
@Component
@ConditionalOnClass(AdminClient.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class DltLagChecker {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DltLagChecker.class);
    /** Spec 阈值：lag > 100 */
    static final long LAG_THRESHOLD = 100L;
    /** AdminClient Future 超时（秒） */
    private static final long FUTURE_TIMEOUT_SEC = 15L;

    private final ObjectProvider<IAlertService> alertServiceProvider;
    private final String bootstrapServers;
    /** AdminClient 缓存（首次懒建；create 失败置 null 并下轮重试） */
    private final Map<String, AdminClient> adminClientHolder = new ConcurrentHashMap<>(1);

    public DltLagChecker(ObjectProvider<IAlertService> alertServiceProvider,
                         @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.alertServiceProvider = alertServiceProvider;
        this.bootstrapServers = bootstrapServers;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void checkLag() {
        AdminClient client = null;
        try {
            client = probeAdminClient();
            if (client == null) {
                log.warn("[DltLagChecker] AdminClient 不可用（bootstrap={}），本轮降级跳过", bootstrapServers);
                return;
            }
            List<String> dltTopics = lagTopics();
            if (dltTopics.isEmpty()) return;

            Set<TopicPartition> allTps = describeDltPartitions(client, dltTopics);
            if (allTps.isEmpty()) {
                // 组或 topic 尚未在 broker 上就绪 → 静默跳过
                return;
            }
            Map<TopicPartition, OffsetAndMetadata> committed =
                    client.listConsumerGroupOffsets(DltConsumer.GROUP_ID)
                            .partitionsToOffsetAndMetadata()
                            .get(FUTURE_TIMEOUT_SEC, TimeUnit.SECONDS);
            Map<TopicPartition, OffsetSpec> req = new HashMap<>();
            for (TopicPartition tp : allTps) req.put(tp, OffsetSpec.latest());
            Map<TopicPartition, ListOffsetsResultInfo> latest =
                    client.listOffsets(req).all().get(FUTURE_TIMEOUT_SEC, TimeUnit.SECONDS);

            for (TopicPartition tp : allTps) {
                ListOffsetsResultInfo li = latest.get(tp);
                long end = li != null ? li.offset() : Long.MIN_VALUE;
                OffsetAndMetadata co = committed.get(tp);
                long committedOff = co != null ? co.offset() : 0L;
                if (end == Long.MIN_VALUE) continue;
                long lag = Math.max(0L, end - committedOff);
                if (lag > LAG_THRESHOLD) {
                    raiseLagAlert(tp.topic(), lag);
                    return; // 单轮只报首个超限，防风暴
                }
            }
        } catch (Exception e) {
            // Spec 降级：不可用则 log.warn
            log.warn("[DltLagChecker] lag 巡检不可用（broker/组未就绪）errType={} msg={}",
                    e.getClass().getSimpleName(), e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignore) {
                    // best-effort
                }
            }
        }
    }

    /** 枚举所有 DLT topic 的 partition（topic 分区名均为 0..N-1） */
    private Set<TopicPartition> describeDltPartitions(AdminClient client, List<String> dltTopics) throws Exception {
        Map<String, TopicDescription> descs =
                client.describeTopics(dltTopics).allTopicNames().get(FUTURE_TIMEOUT_SEC, TimeUnit.SECONDS);
        Set<TopicPartition> all = new HashSet<>();
        for (TopicDescription d : descs.values()) {
            for (TopicPartitionInfo pi : d.partitions()) {
                all.add(new TopicPartition(d.name(), pi.partition()));
            }
        }
        return all;
    }

    private void raiseLagAlert(String dltTopic, long lag) {
        IAlertService alertService = alertServiceProvider.getIfAvailable();
        if (alertService == null) {
            log.warn("[DltLagChecker] lag={} topic={} 超阈值但 IAlertService 缺席", lag, dltTopic);
            return;
        }
        try {
            alertService.triggerAlertByRuleCode(
                    DltConsumer.RULE_DLT_LAG, "runtime-event", DltConsumer.RULE_DLT_LAG, "warn", null, null,
                    "DLT_LAG", null, null,
                    "consumer-group=" + DltConsumer.GROUP_ID + " topic=" + dltTopic + " lag=" + lag + " > 100");
        } catch (Exception e) {
            log.warn("[DltLagChecker] lag 告警触发失败 topic={} errType={}",
                    dltTopic, e.getClass().getSimpleName());
        }
    }

    private AdminClient probeAdminClient() {
        synchronized (this) {
            AdminClient c = adminClientHolder.get("client");
            if (c == null) {
                try {
                    java.util.Map<String, Object> conf = java.util.Map.of(
                            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                            AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8_000);
                    c = AdminClient.create(conf);
                    adminClientHolder.put("client", c);
                } catch (Exception e) {
                    log.warn("[DltLagChecker] AdminClient 创建失败 errType={} msg={}",
                            e.getClass().getSimpleName(), e.getMessage());
                    return null;
                }
            }
            return c;
        }
    }

    private static List<String> lagTopics() {
        List<String> out = new java.util.ArrayList<>();
        for (String t : DltConsumer.resolveBusinessTopics()) {
            out.add(t + ".DLT");
        }
        return out;
    }
}
