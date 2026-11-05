package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 认知管线执行状态（DI-2 #147EJ）。
 *
 * <p>键序：execId / pipelineId / status / nodeStatuses / startTime / endTime / logs。
 * endTime 运行中为 null（保留键）；nodeStatuses 为 节点ID→状态 原样透传。</p>
 */
public record CognitivePipelineExecutionStatusVO(String execId, String pipelineId, String status,
                                                 Map<String, String> nodeStatuses,
                                                 Timestamp startTime, Timestamp endTime,
                                                 List<String> logs) {
}
