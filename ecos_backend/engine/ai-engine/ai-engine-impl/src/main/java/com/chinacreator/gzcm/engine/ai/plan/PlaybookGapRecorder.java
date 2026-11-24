package com.chinacreator.gzcm.engine.ai.plan;

import java.util.Collection;

/**
 * Playbook 缺口登记端口（2026-11-23 去 wagent 化：原 {@code wagent.orchestrator.WAgentPlaybookGapEventService}
 * 直接注入改为端口注入）。
 *
 * <p><b>为什么改端口</b>：缺口事件原经 wagent 的事件发布器（Kafka）落库，wagent 面删除后
 * 该实现随之消失。计划生成（{@link ExplorationPlanner}）是<b>保留功能</b>（内置编排模板依赖它），
 * 但缺口登记只是"供产品沉淀 Playbook 素材"的旁路 —— 故本端口可选实现：无 Bean 时计划生成照常，
 * 只落日志（不阻断主链，也不伪造事件）。</p>
 *
 * <p>实现方若存在（如未来的事件总线适配器），按 {@code EventTypes.Agent.PLAN_GENERATED}
 * 以 {@code gap_type=PLAYBOOK_MISSING} 判别载荷。</p>
 */
public interface PlaybookGapRecorder {

    /** 缺口类型标记单源（消费端判别键）。 */
    String GAP_TYPE_PLAYBOOK_MISSING = "PLAYBOOK_MISSING";

    /**
     * 登记一次缺口。
     *
     * @param tenantId    租户
     * @param runId       运行标识（无 Mission 时同时充当 aggregateId）
     * @param aggregateId 聚合定位（missionId 优先）
     * @param intent      识别出的意图
     * @param slotKeys    槽位键
     * @param toolSequence 工具序列
     * @param operator    操作人
     */
    void recordGap(String tenantId, String runId, String aggregateId, Object intent,
                   Collection<String> slotKeys, Collection<String> toolSequence, String operator);
}
