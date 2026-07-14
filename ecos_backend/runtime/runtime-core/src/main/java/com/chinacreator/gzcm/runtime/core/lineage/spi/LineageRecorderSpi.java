package com.chinacreator.gzcm.runtime.core.lineage.spi;

import com.chinacreator.gzcm.runtime.core.lineage.LineageEvent;

/**
 * 血缘记录器 SPI — 由上层模块（DC-CHENG）实现并注册。
 *
 * <p>内核只负责在关键节点产生血缘事件，实际的持久化、索引、
 * 图构建、可视化由 DC-CHENG 模块提供。
 *
 * <p>SPI 加载顺序：通过 ServiceLoader 或 Spring Bean 注入。
 */
public interface LineageRecorderSpi {

    /**
     * 处理一条血缘事件（持久化到存储）。
     *
     * @param event 血缘事件
     */
    void persist(LineageEvent event);

    /**
     * 查询指定数据源的上游血缘。
     *
     * @param dataId 数据标识
     * @param depth  追溯深度（-1 表示不限）
     * @return 上游血缘链
     */
    java.util.List<LineageEvent> getUpstream(String dataId, int depth);

    /**
     * 查询指定数据源的下游影响。
     *
     * @param dataId 数据标识
     * @param depth  追溯深度（-1 表示不限）
     * @return 下游影响链
     */
    java.util.List<LineageEvent> getDownstream(String dataId, int depth);
}
