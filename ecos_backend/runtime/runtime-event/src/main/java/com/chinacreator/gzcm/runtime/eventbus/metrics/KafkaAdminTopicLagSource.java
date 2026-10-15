package com.chinacreator.gzcm.runtime.eventbus.metrics;

import com.chinacreator.gzcm.common.event.TopicLagSource;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * {@link TopicLagSource} 的 Kafka 实现：经 {@code AdminClient} 取「最新偏移 − 消费者组已提交偏移」
 * 求每 topic 合计滞后量，喂给 §3.3 {@code listEventTopics}（{@code GET /api/v1/system/events/topics}）。
 *
 * <p><b>为什么必须有这个类</b>（差距文档 #142 / B4-1 端点缺口第 2 项）：设计 §3.3.2 要求事件总线
 * 「DLQ 命名 {@code <topic>.DLT} 并<b>暴露 lag 指标</b>」，而全仓 {@code lag} 只有
 * {@code DltConsumer.reportLag(topic, lag)} 这个<b>写入</b>面（外部喂入才有效，且只保留滞后窗口
 * 开启时刻，不留最新值）——即「有阈值判定、无指标来源」。端点若直接返 {@code lag:0} 就是
 * 差距文档定义的假绿（把「取不到」冒充「无积压」）。</p>
 *
 * <p><b>装配口径与 {@code KafkaEventBusServiceImpl} 严格同开关</b>：
 * {@code ecos.event.kafka.enabled=true} + 类路径有 {@code KafkaTemplate} 才装配。
 * 默认（该开关未配置 = false）容器内<b>没有</b>本 Bean，gateway 侧
 * {@code Optional<TopicLagSource>} 为空 ⇒ 端点走「指标未接」降级分支，
 * {@code lag} 呈现 {@code null}。本机/开发态因此不会新增任何 Kafka 连接。</p>
 *
 * <p><b>fail-soft，不抛穿</b>：broker 不可达、AdminClient 超时、topic 无消费者组，
 * 一律 WARN + 返回空 Map（或跳过该 topic），绝不把异常抛进只读诊断端点。
 * 也<b>绝不</b>在缺数据时补 0。</p>
 *
 * <p>验收：{@code mvn -o test -pl runtime/runtime-event -Dtest='KafkaAdminTopicLagSourceTest'}
 * （聚合算法 {@link #aggregateLag} 为纯函数，无 broker 可测）。</p>
 */
@Service
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class KafkaAdminTopicLagSource implements TopicLagSource {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminTopicLagSource.class);

    public static final String SOURCE_ID = "KAFKA_ADMIN";

    /** AdminClient 侧超时（毫秒）——只读诊断链路，宁可无指标也不阻塞请求。 */
    private static final int ADMIN_TIMEOUT_MS = 3000;

    /** 内部 topic 前缀（Kafka 自身 __consumer_offsets 等），不计入业务 lag。 */
    private static final String INTERNAL_TOPIC_PREFIX = "__";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private volatile AdminClient admin;

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Map<String, Long> snapshotLag() {
        try {
            AdminClient client = adminClient();
            List<String> groups = client.listConsumerGroups().all().get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .stream().map(g -> g.groupId()).toList();
            if (groups.isEmpty()) {
                log.debug("[lag] 无消费者组，返回空快照（不补 0）: bootstrap={}", bootstrapServers);
                return Map.of();
            }
            Map<TopicPartition, Long> endByPartition = new HashMap<>();
            ListConsumerGroupOffsetsOptions offOpts = new ListConsumerGroupOffsetsOptions()
                    .timeoutMs(ADMIN_TIMEOUT_MS);
            // kafka-clients 3.6.1 只有 (String) 与 (Map<String, ListConsumerGroupOffsetsSpec>) 两种入参形态，
            // 无 Collection<String> 重载 ⇒ 用 Map 形态一次请求覆盖全部消费组
            Map<String, ListConsumerGroupOffsetsSpec> specs = new LinkedHashMap<>();
            groups.forEach(g -> specs.put(g, new ListConsumerGroupOffsetsSpec()));
            ListConsumerGroupOffsetsResult offsetsResult =
                    client.listConsumerGroupOffsets(specs, offOpts);
            // 位点必须按消费组分别保存：lag 的定义是「每个组各自 end−committed，再跨组求和」。
            // 若先把各组 committed 直接相加（早期草稿写法），两个组消费同一 topic 时
            // 合计位点会大于最新偏移 ⇒ lag 被夹成 0，指标虚低，正是红线禁止的
            // 「把取不到/算错冒充无积压」。
            Map<String, Map<TopicPartition, Long>> committedByGroup = new LinkedHashMap<>();
            Map<TopicPartition, Boolean> partitionsSeen = new LinkedHashMap<>();
            for (String group : groups) {
                Map<TopicPartition, OffsetAndMetadata> perGroup;
                try {
                    perGroup = offsetsResult.partitionsToOffsetAndMetadata(group)
                            .get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // 单组取不到位点 ⇒ 该组不参与合计（缺数据≠0，也≠「无滞后」）
                    log.warn("[lag] 消费组位点获取失败，跳过该组: {} ({})", group, e.getMessage());
                    continue;
                }
                Map<TopicPartition, Long> groupOffsets = new LinkedHashMap<>();
                perGroup.forEach((tp, om) -> {
                    if (tp.topic().startsWith(INTERNAL_TOPIC_PREFIX)) return;
                    groupOffsets.put(tp, om.offset());
                    partitionsSeen.put(tp, Boolean.TRUE);
                });
                if (!groupOffsets.isEmpty()) committedByGroup.put(group, groupOffsets);
            }
            if (committedByGroup.isEmpty()) {
                log.debug("[lag] 已提交偏移为空（组均无位点），返回空快照: groups={}", groups.size());
                return Map.of();
            }
            Map<TopicPartition, OffsetSpec> spec = new LinkedHashMap<>();
            partitionsSeen.keySet().forEach(tp -> spec.put(tp, new OffsetSpec.LatestSpec()));
            ListOffsetsResult lor = client.listOffsets(spec);
            for (TopicPartition tp : spec.keySet()) {
                try {
                    endByPartition.put(tp, lor.partitionResult(tp)
                            .get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS).offset());
                } catch (Exception e) {
                    // 单 partition 取不到 ⇒ 该 partition 不计入合计（缺数据≠0）
                    log.warn("[lag] listOffsets 失败，跳过 partition: {} ({})", tp, e.getMessage());
                }
            }
            Map<String, Long> total = new LinkedHashMap<>();
            for (Map<TopicPartition, Long> groupOffsets : committedByGroup.values()) {
                aggregateLag(endByPartition, groupOffsets)
                        .forEach((topic, lag) -> total.merge(topic, lag, Long::sum));
            }
            return total;
        } catch (Exception e) {
            log.warn("[lag] AdminClient 取滞后指标失败（只读诊断降级为空快照，不补 0）: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 纯聚合：同一 {@link TopicPartition} 上 {@code max(0, endOffset − committedOffset)} 按 topic 求和。
     *
     * <p>抽成静态纯函数是为「无 broker 也要能验算」——lag 是本端点唯一容易被冒充的字段，
     * 聚合口径必须由单测钉死（含 end 缺失跳过、跨组由调用方逐组调用后求和、负值夹 0 三条）。</p>
     *
     * @param endByPartition      partition → 最新偏移（缺键＝未取到，该 partition 不参与）
     * @param committedByPartition partition → <b>单个消费组</b>的已提交偏移（禁跨组合并后再传入）
     * @return topic → 该组的滞后量合计；两侧无交集时返回空 Map
     */
    public static Map<String, Long> aggregateLag(Map<TopicPartition, Long> endByPartition,
                                                 Map<TopicPartition, Long> committedByPartition) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (endByPartition == null || committedByPartition == null) return out;
        for (Map.Entry<TopicPartition, Long> ce : committedByPartition.entrySet()) {
            TopicPartition tp = ce.getKey();
            if (tp == null || tp.topic() == null || tp.topic().startsWith(INTERNAL_TOPIC_PREFIX)) continue;
            Long end = endByPartition.get(tp);
            if (end == null) continue;
            long committedOffset = ce.getValue() == null ? 0L : ce.getValue();
            long lag = Math.max(0L, end - committedOffset);
            out.merge(tp.topic(), lag, Long::sum);
        }
        return out;
    }

    private AdminClient adminClient() {
        AdminClient local = admin;
        if (local != null) return local;
        synchronized (this) {
            if (admin == null) {
                Properties props = new Properties();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "ecos-topic-lag-source");
                props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, ADMIN_TIMEOUT_MS);
                props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, ADMIN_TIMEOUT_MS);
                admin = AdminClient.create(props);
                log.info("[lag] TopicLagSource 已就绪: bootstrap={} timeoutMs={}",
                        bootstrapServers, ADMIN_TIMEOUT_MS);
            }
            return admin;
        }
    }

    @PreDestroy
    public void close() {
        AdminClient local = admin;
        if (local != null) {
            try {
                local.close(java.time.Duration.ofSeconds(1));
            } catch (Exception e) {
                log.debug("[lag] AdminClient 关闭异常（忽略）: {}", e.getMessage());
            }
            admin = null;
        }
    }

    /** 供单测注入 bootstrap（避免真实连接）。 */
    void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }
}
