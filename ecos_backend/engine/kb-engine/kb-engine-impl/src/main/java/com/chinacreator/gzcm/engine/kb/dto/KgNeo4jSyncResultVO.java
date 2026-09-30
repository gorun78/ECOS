package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * Neo4j 同步提交结果 VO（PMO-74 H11-T4）。
 *
 * <p>承接 {@code EcosKnowledgeGraphService#syncToNeo4j()} 的 Map 结果，
 * 键集合固定为 status / message / jobId / nodesAvailable / edgesAvailable。</p>
 */
@Data
public class KgNeo4jSyncResultVO {

    /** 固定 "ready_to_sync"（fire-and-forget 提交态） */
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
