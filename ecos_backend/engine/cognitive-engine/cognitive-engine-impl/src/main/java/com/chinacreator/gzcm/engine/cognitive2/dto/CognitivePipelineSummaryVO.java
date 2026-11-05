package com.chinacreator.gzcm.engine.cognitive2.dto;

import java.sql.Timestamp;

/**
 * 认知管线列表项（DI-2 #147EJ）。
 *
 * <p>键序：id / name / description / nodeCount / createdAt。</p>
 */
public record CognitivePipelineSummaryVO(String id, String name, String description,
                                         int nodeCount, Timestamp createdAt) {
}
