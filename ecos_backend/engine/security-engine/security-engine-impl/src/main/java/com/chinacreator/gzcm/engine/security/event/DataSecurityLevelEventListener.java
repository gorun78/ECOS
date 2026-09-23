package com.chinacreator.gzcm.engine.security.event;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 数据资产打级/表演度事件消费者（PMO-data10）。
 *
 * <p>data-engine 在 <b>人工确认</b>字段级敏感度 {@code confirm=true + level∈{L3,L4}}
 * 时发出 {@code security-tagged} 事件（topic = {@link KafkaTopics#DATA_SECURITY_TAGGED}）。
 * 本监听者消费后：
 * <ol>
 *   <li>写 {@code ecos_cls_policy}：针对命中的 L3/L4 字段，合并 {@code blocked_cols}
 *       （完全阻断）+ 对 {@code mask_strategy=none} 的字段（敏感字）进 {@code blocked_cols}
 *       （由调用方后续用 {@code DataMaskingService.applyMaskingByStrategy} 脱敏）；</li>
 *   <li>写 {@code ecos_rls_policy}：若资产级 {@code L4}，追加
 *       {@code clearance_level >= 4} {@code filter_expr}（行级准入）。</li>
 * </ol>
 *
 * <p>消费路径（与 {@code EcosOntologyEventConsumer} 对齐简版）：
 * <ul>
 *   <li><b>内存 fallback</b>（{@code dbus.event.kafka.enabled != true}）：
 *       {@link EventBusService#subscribe} → 启动时 {@link #init()} 编程式挂载此 bean；
 *       由 {@code MemoryEventBusServiceImpl#publish} 同步 in-process 分发；</li>
 *   <li><b>Kafka 路径</b>（classpath 有 spring-kafka + {@code dbus.event.kafka.enabled=true}）：
 *       {@code KafkaEventBusService} 将本 bean 的 {@link #onKafka(Object)} 当做 handler
 *       （反序列化 JSON → 同一路径），topic 对齐 {@link KafkaTopics#DATA_SECURITY_TAGGED}，
 *       group {@code dccheng-security-consumer}。</li>
 * </ul>
 *
 * <p>写策略表的铁律（/database 规范）：
 * <ul>
 *   <li>V154 已 ADD {@code resource_id} 列（IR03 只加不删）— 老行 fallback
 *       走 {@code table_name} 兜底，新写入同时落两列，保证运行期
 *       {@code WHERE (table_name = ? OR resource_id = ?)} 双向命中；</li>
 *   <li>同一 (resource_id, ...) 已存在策略行 → upsert（合并 {@code blocked_cols} 合并逻辑待
 *       调用者基于 JSONB 在 PG 端用 {@code ?::jsonb || ?::jsonb} 实现在下一轮迭代，
 *       当前先覆盖式更新（行为直观 + 幂等更清晰）；</li>
 *   <li>审计落 Kafka {@code ecos.audit}（ST06）— 失败仅 warn 不阻塞主流程。</li>
 * </ul>
 */
@Component
public class DataSecurityLevelEventListener {

    private static final Logger log = LoggerFactory.getLogger(DataSecurityLevelEventListener.class);
    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String AUDIT_TOPIC = KafkaTopics.AUDIT;
    private static final String POLICY_PREFIX = "asset-";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<EventBusService> eventBus;

    public DataSecurityLevelEventListener(JdbcTemplate jdbc,
                                          ObjectProvider<EventBusService> eventBusProvider) {
        this.jdbc = jdbc;
        this.eventBus = eventBusProvider;
    }

    /** 启动时注册编程式订阅（覆盖 Kafka + 内存双路径）。 */
    @PostConstruct
    void init() {
        EventBusService bus = this.eventBus.getIfAvailable();
        if (bus == null) {
            log.warn("DataSecurityLevelEventListener: EventBusService 不可用 — 跳过事件订阅");
            return;
        }
        bus.subscribe(KafkaTopics.DATA_SECURITY_TAGGED,
                DataAssetSecurityTaggedEvent.class, this::onKafka);
        log.info("DataSecurityLevelEventListener: subscribed topic={} groupId=dccheng-security-consumer",
                KafkaTopics.DATA_SECURITY_TAGGED);
    }

    /**
     * 事件入口 — payload 类型:
     * <ul>
     *   <li>内存 fallback: {@link DataAssetSecurityTaggedEvent} 对象直传；</li>
     *   <li>Kafka: JSON 字符串 — {@link #onKafka} 内部折叠。</li>
     * </ul>
     * 防御侧：反序列化失败/字段缺失均记 WARN 不抛（连续失败交由 DLQ 处理）。
     */
    public void onKafka(Object payload) {
        DataAssetSecurityTaggedEvent evt;
        try {
            if (payload instanceof DataAssetSecurityTaggedEvent e) {
                evt = e;
            } else if (payload instanceof String json) {
                if (json == null || json.isEmpty()) {
                    log.warn("DataSecurityLevelEventListener: kafka payload null/empty, skip");
                    return;
                }
                evt = MAPPER.readValue(json, DataAssetSecurityTaggedEvent.class);
            } else {
                log.warn("DataSecurityLevelEventListener: unknown payload type={} skip",
                        payload == null ? "null" : payload.getClass());
                return;
            }
        } catch (Exception e) {
            log.warn("DataSecurityLevelEventListener kafka deserialize failed err={}",
                    e.getMessage(), e);
            return;
        }
        if (evt == null || evt.getAssetId() == null || evt.getResourceId() == null) {
            log.debug("DataSecurityLevelEventListener: skipped incomplete event");
            return;
        }
        applyPolicies(evt);
    }

    /**
     * 核心处理：CLS（L3/L4 字段字段级脱敏）+ RLS（L4 准入 clearance_level >= 4）。
     */
    public void applyPolicies(DataAssetSecurityTaggedEvent evt) {
        String assetId = evt.getAssetId();
        String resourceId = evt.getResourceId();
        String tableName = (evt.getTableName() == null || evt.getTableName().isBlank()) ? "unknown" : evt.getTableName();
        String operator = (evt.getOperator() == null || evt.getOperator().isBlank()) ? "system" : evt.getOperator();
        List<DataAssetSecurityTaggedEvent.TaggedField> fields =
                evt.getFields() == null ? Collections.emptyList() : evt.getFields();

        try {
            // 1. CLS 字段级脱敏（L3+ 命中字段进 blocked_cols）
            if (!fields.isEmpty()) {
                upsertCls(assetId, resourceId, tableName, fields, operator);
            }
            // 2. RLS 行级准入（仅资产级 L4 才追加 clearance 约束）
            if (isL4OrAbove(evt.getSensitivityLevel())) {
                upsertRls(assetId, resourceId, tableName, operator);
            }
            // 3. 审计 Kafka（ST06 兜底；失败不阻塞）
            emitAudit(assetId, resourceId, tableName, evt.getSensitivityLevel(), operator);
            log.info("DataSecurityLevelEventListener: 策略写入完成 asset={} resource={} level={}",
                    assetId, resourceId, evt.getSensitivityLevel());
        } catch (Exception e) {
            log.error("DataSecurityLevelEventListener failed: asset={} resource={} err={}",
                    assetId, resourceId, e.getMessage(), e);
        }
    }

    /**
     * L3/L4 字段 → {@code ecos_cls_policy}：
     * 合并命中字段进 {@code blocked_cols}（运行期由 {@code DataMaskingService} 按
     * {@code field_data_type} 实际脱敏，实现"列返回 ***"，详见 {@code DataMaskingController}）。
     * <p>写入带 {@code resource_id}（V154 新增列）+ {@code table_name} 兜底（向前兼容）。
     */
    private void upsertCls(String assetId, String resourceId, String tableName,
                           List<DataAssetSecurityTaggedEvent.TaggedField> fields, String operator) {
        // 收集命中字段名（L3+ 字段进 blocked_cols — L3 已含 PII，必脱敏）
        Set<String> blockedCols = new LinkedHashSet<>();
        for (DataAssetSecurityTaggedEvent.TaggedField f : fields) {
            if (f == null || f.getFieldName() == null) continue;
            if (isL3OrAbove(f.getFieldSensitivity())) {
                blockedCols.add(f.getFieldName());
            }
        }
        if (blockedCols.isEmpty()) return;
        String blockedJson = toJson(new ArrayList<>(blockedCols));

        // 查找既有策略（resource_id 优先，table_name 兜底向后兼容）
        String existingId = findExistingPolicy("ecos_cls_policy", resourceId, tableName);
        if (existingId != null) {
            // 覆盖式更新 — 行为直观 + 幂等清晰（仅写老列，避免迁移前缺列造成 SQL 报错）
            jdbc.update(
                "UPDATE ecos_cls_policy SET blocked_cols = ?, description = ?, created_by = ? " +
                "WHERE id = ?",
                blockedJson,
                "PMO-data10 资产 " + assetId + " 字段级脱敏",
                operator, existingId);
            log.info("CLS policy 更新: id={} resource={} blockedCols={}",
                    existingId, resourceId, blockedCols.size());
        } else {
            String id = UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO ecos_cls_policy (" +
                "  id, policy_name, table_name, resource_id, " +
                "  visible_cols, blocked_cols, description, " +
                "  enabled, priority, created_by" +
                ") VALUES (?, 'asset-' || ?, ?, ?, ?, ?, ?, true, 10, ?)",
                id, assetId, tableName, resourceId,
                "[]", blockedJson,
                "PMO-data10 资产 " + assetId + " 字段级脱敏",
                operator);
            log.info("CLS policy 新建: id={} resource={} blockedCols={}",
                    id, resourceId, blockedCols.size());
        }
    }

    /**
     * 资产级 L4 → {@code ecos_rls_policy}：追加 {@code clearance_level >= 4} 准入约束。
     * <p>写入带 {@code resource_id}（V154 新增列）+ {@code table_name} 兜底。
     */
    private void upsertRls(String assetId, String resourceId, String tableName, String operator) {
        String filterExpr = "clearance_level >= 4";

        String existingId = findExistingPolicy("ecos_rls_policy", resourceId, tableName);
        if (existingId != null) {
            jdbc.update(
                "UPDATE ecos_rls_policy SET filter_expr = ?, description = ?, created_by = ? WHERE id = ?",
                filterExpr, "PMO-data10 资产 " + assetId + " L4 准入",
                operator, existingId);
            log.info("RLS policy 更新: id={} resource={} filter='{}'",
                    existingId, resourceId, filterExpr);
        } else {
            String id = UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
                "INSERT INTO ecos_rls_policy (" +
                "  id, policy_name, table_name, resource_id, filter_expr, " +
                "  description, enabled, priority, created_by" +
                ") VALUES (?, 'asset-' || ?, ?, ?, ?, ?, true, 10, ?)",
                id, assetId, tableName, resourceId, filterExpr,
                "PMO-data10 资产 " + assetId + " L4 准入",
                operator);
            log.info("RLS policy 新建: id={} resource={} filter='{}'",
                    id, resourceId, filterExpr);
        }
    }

    /** 查找已存在策略 id（V154 resource_id 优先，table_name 兜底向后兼容）。 */
    private String findExistingPolicy(String policyTable, String resourceId, String tableName) {
        try {
            String sql = "SELECT id FROM " + policyTable +
                    " WHERE enabled = true " +
                    " AND (resource_id = ? OR table_name = ?) LIMIT 1";
            List<String> ids = jdbc.queryForList(sql, String.class, resourceId, tableName);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("查找既有 {} 策略失败: {}", policyTable, e.getMessage());
            return null;
        }
    }

    private static String toJson(List<String> vals) {
        try {
            return MAPPER.writeValueAsString(vals);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean isL4OrAbove(String level) {
        if (level == null) return false;
        String compact = level.toUpperCase().trim();
        if (compact.startsWith("L")) compact = compact.substring(1);
        try {
            return Integer.parseInt(compact) >= 4;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** L3+ （= L3 与 L4），CLS 必脱敏；L1/L2 不动。 */
    private static boolean isL3OrAbove(String level) {
        if (level == null) return false;
        String compact = level.toUpperCase().trim();
        if (compact.startsWith("L")) compact = compact.substring(1);
        try {
            return Integer.parseInt(compact) >= 3;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 审计 Kafka（ST06；不可用时 log 兜底不阻塞主流程）。 */
    private void emitAudit(String assetId, String resourceId, String tableName,
                           String level, String operator) {
        try {
            EventBusService bus = this.eventBus.getIfAvailable();
            if (bus != null) {
                Map<String, Object> audit = new LinkedHashMap<>();
                audit.put("action", "data.security.policy-generated");
                audit.put("asset_id", assetId);
                audit.put("resource_id", resourceId);
                audit.put("table_name", tableName);
                audit.put("sensitivity_level", level);
                audit.put("operator", operator);
                bus.publish(AUDIT_TOPIC, audit);
            }
        } catch (Exception e) {
            log.warn("[AUDIT][KAFKA-FALLBACK] topic={} err={}", AUDIT_TOPIC, e.getMessage());
        }
    }
}