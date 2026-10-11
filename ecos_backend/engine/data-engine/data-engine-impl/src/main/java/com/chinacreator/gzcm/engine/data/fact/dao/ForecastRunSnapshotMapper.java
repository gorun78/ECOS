package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 预测运行只读快照头 mapper（H-3 交付③ / V197+V277）：
 * {@code ecos_dw.ecos_application_forecast_run}。
 *
 * <p>本 mapper <b>只读</b>（快照读端点归 cognitive 经 REST 消费，ST08；写路径仍归
 * 预测运行写通道，本撮不新增任何 UPDATE/INSERT）。
 * 查询按 {@code forecast_run_id} 定位（uniq_afcr_run_id 命中，禁全库扫描——
 * ForecastRunSnapshotReader.SnapshotQuery 契约要点 4）。
 */
@Mapper
public interface ForecastRunSnapshotMapper {

    /**
     * 取运行头（六要素列 + 运行上下文 + 三个结果载荷 TEXT 列）。
     *
     * @return 无此行返回 null
     */
    Map<String, Object> selectRunHeader(@Param("forecastRunId") String forecastRunId);
}
