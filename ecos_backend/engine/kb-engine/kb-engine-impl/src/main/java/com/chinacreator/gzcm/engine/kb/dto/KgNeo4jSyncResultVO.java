package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.Map;

/**
 * Neo4j 同步提交结果 VO（F04-10 图谱双形态做实，原 PMO-74 H11-T4 形态修正）。
 *
 * <p>承接 {@code EcosKnowledgeGraphService#syncToNeo4j()} 的结果。修正前该 VO 固定
 * {@code status="ready_to_sync"} 伪成功（fire-and-forget），掩盖 standard 档并无 Neo4j 投影的
 * 事实。F04-10 修正后：</p>
 * <ul>
 *   <li>{@code status ∈ {synced, reconcile_drift, notAttempted}}（全仓不再出现
 *       {@code ready_to_sync} 字面量）；</li>
 *   <li>{@code degraded=true} 显式标记当前档位无 Neo4j 投影（PG 为权威形态）；</li>
 *   <li>{@code reconcile} 落 PG↔Neo4j 计数对账结果，落 {@code kg_sync_log} 真结果。</li>
 * </ul>
 */
@Data
public class KgNeo4jSyncResultVO {

    /** 三态：{@code synced} / {@code reconcile_drift} / {@code notAttempted}（原 ready_to_sync 已废止）。 */
    private String status;

    /** 提示信息（含 jobId）。 */
    private String message;

    /** 异步任务 id（tg-neo4j-* 前缀，回填 kg_sync_log）。 */
    private String jobId;

    /** 是否显式降级（当前档位无 Neo4j 投影 / 对账漂移）。 */
    private Boolean degraded;

    /** 降级/漂移原因（阅读友好文案），degraded=true 或 reconcile_drift 时非空。 */
    private String degradeReason;

    /** 对账差异时携带的告警码（ECOS-KB-080）。 */
    private String mismatchCode;

    /** PG 授权权威形态节点数。 */
    private Integer pgNodeCount;

    /** PG 授权权威形态边数。 */
    private Integer pgEdgeCount;

    /** 提交时可读节点数（向后兼容旧键）。 */
    private Integer nodesAvailable;

    /** 提交时可读边数（向后兼容旧键）。 */
    private Integer edgesAvailable;

    /** PG↔Neo4j 计数对账结果（pgNodes/neo4jNodes/pgEdges/neo4jEdges/matched）。 */
    private Map<String, Object> reconcile;
}
