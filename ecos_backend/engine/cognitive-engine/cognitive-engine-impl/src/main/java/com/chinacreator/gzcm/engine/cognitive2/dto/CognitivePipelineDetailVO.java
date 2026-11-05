package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.sql.Timestamp;
import java.util.List;

import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;

/**
 * 认知管线详情（DI-2 #147EJ）。
 *
 * <p>键序：id / name / description / status / createdBy / nodes / createdAt。
 * nodes 原样透传持久化节点（nodeId/nodeType/config/dependsOn）。</p>
 */
public record CognitivePipelineDetailVO(String id, String name, String description, String status,
                                        String createdBy, List<CognitivePipelineNode> nodes,
                                        Timestamp createdAt) {
}
