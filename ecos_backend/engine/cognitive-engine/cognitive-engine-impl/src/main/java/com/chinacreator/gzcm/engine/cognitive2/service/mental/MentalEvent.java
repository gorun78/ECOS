package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认知心智层事件契约（PMO-59 P2b / ADR-9，topic {@code ecos.cognitive}）。
 *
 * <p>强类型 Payload（禁止 Map 裸传——铁律与 §禁止清单 6）：三类事件
 * <ul>
 *   <li>{@code COGNITIVE_HYPOTHESIS_INVALIDATED} — 假设失效（自动检测/人工兜底）</li>
 *   <li>{@code COGNITIVE_BELIEF_UPDATED} — 不确定性判断版本变更（新证据加权更新/人工覆写）</li>
 *   <li>{@code COGNITIVE_EVIDENCE_REGISTERED} — 新证据登记（含冲突标记，补算扫描输入）</li>
 * </ul>
 * 所有事件携带 {@code eventId}/{@code timestamp}/{@code source} 信封字段；
 * {@code faultContext} 为结构化故障上下文（预留"周一故障复盘"对接，用户已确认字段口径）。</p>
 */
public interface MentalEvent {

    /** 事件类型枚举值（Kafka key 维度 + 消费方路由键）。 */
    String getEventType();

    /** 全局事件 id（cog_evt_ 前缀 + UUID12，幂等溯源）。 */
    String getEventId();

    /** 事件产生时刻（ISO-8601 字符串，JSON 序列化友好）。 */
    String getTimestamp();

    /** 生产方标识（cognitive-engine / runtime-task scan）。 */
    String getSource();

    /** 结构化故障上下文（周一故障复盘预留：incidentRef/reviewTag/chain 等键值对）。 */
    Map<String, Object> getFaultContext();

    // ── 静态工厂基元 ──

    /** 生成事件 id（cog_evt_ 前缀 + UUID 12 位）。 */
    static String newEventId() {
        return "cog_evt_" + java.util.UUID.randomUUID().toString().substring(0, 12);
    }

    /** 当前时刻 ISO-8601。 */
    static String nowIso() {
        return LocalDateTime.now().toString();
    }
}
