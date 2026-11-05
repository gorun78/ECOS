package com.chinacreator.gzcm.engine.cognitive2.dto;

/**
 * 认知管线创建回执（DI-2 #147EJ）。
 *
 * <p>键序：pipelineId / name / nodeCount。</p>
 */
public record CognitivePipelineCreateVO(String pipelineId, String name, int nodeCount) {
}
