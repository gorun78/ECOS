package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V235 {@code ecos_ai.ecos_wagent_run_event} 引用型运行事件 DO（append-only，X-6 不分区）。
 * <p>{@code event_type} 词表单源 = {@code EventTypes.Agent}（common-api，PRD-01 v1.3 REQ-PLT-29，
 * R-68~R-70 批准）——本表 DDL 刻意不设 CHECK（V235 声明纪律：信封/路由单源，表侧不复制词表）。</p>
 * <p>{@code payload_ref_text} 引用型载荷（对象存储/表行指针，§6.3 禁正文）；
 * 审计事件不建 wagent_audit_event，一律 Kafka {@code ecos.audit}（ST06/X-20）。
 * 应用层禁 UPDATE/DELETE（append-only）。</p>
 */
public class WAgentRunEventDO extends WAgentBaseDO {

    private String id;
    private String runId;
    private String stepId;
    private String eventType;
    private String payloadRefText;
    private LocalDateTime occurTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadRefText() { return payloadRefText; }
    public void setPayloadRefText(String payloadRefText) { this.payloadRefText = payloadRefText; }

    public LocalDateTime getOccurTime() { return occurTime; }
    public void setOccurTime(LocalDateTime occurTime) { this.occurTime = occurTime; }
}
