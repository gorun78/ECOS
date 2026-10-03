package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * Neo4j 同步提交结果 VO（PMO-74 H11-T4；F04-10 双形态做实后 status 取值变更）。
 *
 * <p>承接 {@code EcosKnowledgeGraphService#syncToNeo4j()} 的 Map 结果。
 * F04-10 删除旧"提交态"伪成功（PG 有数据却谎称准备同步）：
 * {@code status} 现取 {@code notAttempted}（PG 唯一形态，未尝试投影）/
 * {@code synced}（真写 Neo4j 投影 + PG↔Neo4j 计数对账）/ {@code failed}（真失败）。</p>
 */
@Data
public class KgNeo4jSyncResultVO {

    /** notAttempted（PG 唯一形态）/ synced（真写并对账）/ failed（真失败） */
    private String status;

    /** 提示信息（含 jobId） */
    private String message;

    /** 异步任务 id（tg-neo4j-* 前缀，回填 kg_sync_log） */
    private String jobId;

    /** 提交时可用节点数 */
    private Integer nodesAvailable;

    /** 提交时可用边数 */
    private Integer edgesAvailable;
}
