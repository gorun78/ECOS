package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 表 5 — 预测输入快照 mapper（V167）：{@code ecos_dw.ecos_forecast_input_snapshot}。
 *
 * <p>唯一索引 {@code uniq_fis_run} 由 {@code forecast_run_id} 承载；本批不做业务字段
 * 语义校验（由 05 册预测运行 + DqRowGate 承载），仅提供批次/分页/触达。
 */
@Mapper
public interface ForecastSnapshotMapper {

    int insertBatch(@Param("rows") List<Map<String, Object>> rows);

    int countBatch(@Param("batchId") String batchId);

    int countBatchByStatus(@Param("batchId") String batchId, @Param("dqStatus") String dqStatus);

    List<Map<String, Object>> listBatch(@Param("batchId") String batchId);

    String getDqStatusById(@Param("id") String id);

    int touchUpdate(@Param("id") String id, @Param("updateBy") String updateBy);

    /**
     * 快照表没有 {@code project_id/period} 列；{@code projectId} / {@code period} 参数
     * 在此 mapper 中被<b>忽略</b>（保留接口一致性，由 Service 层承诺不传）。
     */
    List<Map<String, Object>> pageQuery(@Param("offset") int offset, @Param("limit") int limit,
                                        @Param("projectId") String projectId,
                                        @Param("period") String period,
                                        @Param("batchId") String batchId,
                                        @Param("dqStatus") String dqStatus);

    int countQuery(@Param("projectId") String projectId,
                   @Param("period") String period,
                   @Param("batchId") String batchId,
                   @Param("dqStatus") String dqStatus);
}
