package com.chinacreator.gzcm.engine.ai.wagent.event;

import com.chinacreator.gzcm.common.event.DomainEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * W Agent 域事件信封（详细设计-10 §9.1.1 五缺失字段承载体；§9.7 批准口径 **R-68=②+③**）。
 *
 * <p><b>R-68② 纪律</b>：{@code DomainEvent}（runtime/common-api）是全仓共享信封
 * （maven-enforcer 守门），本册<b>不修改</b>它——缺失的 {@code tenant_id / run_id / trace_id /
 * actor / schema_version} 五字段以本子类 additive 扩展；{@code tenant_id/actor/trace_id}
 * 在过渡期同时以 payload 约定键冗余（R-68③），跨租户读写=0 必须实测而非文档承诺。</p>
 *
 * <p><b>R-69② 纪律</b>：Agent 域 action 对象另立聚合名 {@link #AGGREGATE_WAGENT_ACTION}
 * （{@code "wagent_action"}），不改 {@code KafkaTopics.topicForAggregate()} 路由表、
 * 不动既有生产者；该聚合名经 default 路由落 {@code ecos.audit}，路由表收口随 R-69 单独批准。</p>
 *
 * <p>事件名单源 = {@code com.chinacreator.gzcm.common.event.EventTypes.Agent}（本册 additive
 * 注册 30 PascalCase 名，§9.1.2）；topic 归属沿用 {@code topicForAggregate()}，不自立路由。
 * 载荷只带引用与摘要（§6.3 引用原则），禁带 SQL 原文 / 工具入参原文 / 业务明细正文；
 * 审计事件一律 {@code ecos.audit}（ST06）。</p>
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与本包
 * {@code com.chinacreator.gzcm.engine.ai.wagent} 属同义（F10-01）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WAgentEvent extends DomainEvent {

    /** 信封 schema 版本（§9.1.1 缺失字段⑤；破坏性变更升版本并双发一个过渡周期 = §9.1.4/附件 §11.4）。 */
    public static final String SCHEMA_VERSION_V1 = "1";

    /** W 域 Run 聚合名 → topicForAggregate("agent") 之外的独立形态：run 事件统一以 "execution" 聚合路由 ecos.agent（在位路由表内词）。 */
    public static final String AGGREGATE_RUN = "execution";
    /** Goal/Mission 事件聚合名（"mission" 在位路由 ecos.agent）。 */
    public static final String AGGREGATE_MISSION = "mission";
    /** 候选事件聚合名（tool→ecos.agent；发布态按目标 Engine 的 aggregate 复路由 ontology/knowledge，§9.1.2）。 */
    public static final String AGGREGATE_CANDIDATE = "agent";
    /** 审批/决定事件聚合名（approval → ecos.workflow，§9.1.2 括注）。 */
    public static final String AGGREGATE_APPROVAL = "approval";
    /** R-69②：Agent 域 action 另立聚合名（不改路由表、不与本体 "action" 混用）。 */
    public static final String AGGREGATE_WAGENT_ACTION = "wagent_action";
    /** 安全事件聚合名（null/未知聚合 → ecos.audit 默认路由，§9.1.2「SecurityEventRaised 走 audit」）。 */
    public static final String AGGREGATE_SECURITY_EVENT = "wagent_security";

    /** actor.via 三值（谁触发：agent／user／system，§9.1.1 缺失字段④）。 */
    public static final String VIA_AGENT = "agent";
    public static final String VIA_USER = "user";
    public static final String VIA_SYSTEM = "system";

    /** 触发主体（§9.1.1 actor{user_id, via}）。 */
    public static final class Actor {
        private String userId;
        private String via;

        public Actor() {}

        public Actor(String userId, String via) {
            this.userId = userId;
            this.via = via;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        /** {agent|user|system} 之一（常量取本类 VIA_*，禁字面量）。 */
        public String getVia() { return via; }
        public void setVia(String via) { this.via = via; }
    }

    private String tenantId;
    private String runId;
    private String traceId;
    private Actor actor;
    private String schemaVersion = SCHEMA_VERSION_V1;

    public WAgentEvent() {
        super();
    }

    public WAgentEvent(String eventType, String source, String aggregateType, String aggregateId, Object payload) {
        super(eventType, source, aggregateType, aggregateId, payload);
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    /** MDC traceId 同源（REQ-AI-03 链路要求；缺失即未接runtime-event 单通道，禁静默发事件）。 */
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Actor getActor() { return actor; }
    public void setActor(Actor actor) { this.actor = actor; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    /** 发布前自检：五个 additive 字段齐备方可出门（禁半信封入 Kafka）。 */
    public void assertEnvelopeComplete() {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("WAgentEvent 缺 tenant_id（R-68②，跨租户隔离依据）");
        }
        if (getEventType() == null || getEventType().isBlank()) {
            throw new IllegalStateException("WAgentEvent 缺 eventType（单源 EventTypes.Agent）");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("WAgentEvent 缺 trace_id（REQ-AI-03）");
        }
        if (actor == null || actor.getVia() == null) {
            throw new IllegalStateException("WAgentEvent 缺 actor{user_id,via}（ST06 审计链）");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalStateException("WAgentEvent 缺 schema_version（§9.1.4 双发过渡依据）");
        }
    }
}
