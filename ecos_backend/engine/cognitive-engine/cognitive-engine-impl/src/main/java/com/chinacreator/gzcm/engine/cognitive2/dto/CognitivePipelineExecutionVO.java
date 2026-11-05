package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.sql.Timestamp;

/**
 * 认知管线执行回执（DI-2 #147EJ）。
 *
 * <p>键序：execId / pipelineId / status / startTime。</p>
 */
public record CognitivePipelineExecutionVO(String execId, String pipelineId, String status,
                                           Timestamp startTime) {
}
