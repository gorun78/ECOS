package com.chinacreator.gzcm.runtime.core.lineage;

/**
 * 数据血缘记录器 — 内核埋点接口。
 *
 * 在数据管道关键节点（数据接入、ETL转换、融合、发布）调用此接口记录血缘。
 * 内核不负责存储和查询，只负责生成事件并交给 SPI 实现处理。
 *
 * <p>使用方式：
 * <pre>{@code
 * LineageEvent event = LineageEvent.builder()
 *     .operationType("TRANSFORM")
 *     .sourceIds(List.of("src-dataset-1", "src-dataset-2"))
 *     .targetId("target-dataset-3")
 *     .transformation("字段映射 + 去重")
 *     .fieldMappings(Map.of("id_card", "cert_number", "name", "full_name"))
 *     .context(Map.of("taskId", "etl-20260603-001", "module", "buszhi"))
 *     .build();
 * lineageRecorder.record(event);
 * }</pre>
 *
 * <p>上层 DC-CHENG 模块通过 {@link com.chinacreator.gzcm.runtime.core.lineage.spi.LineageRecorderSpi}
 * 注册存储实现，负责血缘图的构建、查询和可视化。
 *
 * @see com.chinacreator.gzcm.runtime.core.lineage.spi.LineageRecorderSpi
 */
public interface LineageRecorder {

    /**
     * 记录一条血缘事件。
     *
     * @param event 血缘事件，包含数据源、目标、变换信息
     */
    void record(LineageEvent event);
}
