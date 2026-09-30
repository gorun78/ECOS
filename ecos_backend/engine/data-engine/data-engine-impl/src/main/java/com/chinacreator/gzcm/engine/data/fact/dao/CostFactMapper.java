package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 表 4 — 成本事实 mapper（V167）：{@code ecos_dw.ecos_biz_cost_fact}。
 *
 * <p>{@code is_pool SMALLINT} 取代 COALESCE 表达式唯一索引；{@code project_id} 可空。
 */
@Mapper
public interface CostFactMapper {

    int insertBatch(@Param("rows") List<Map<String, Object>> rows);

    int countBatch(@Param("batchId") String batchId);

    int countBatchByStatus(@Param("batchId") String batchId, @Param("dqStatus") String dqStatus);

    List<Map<String, Object>> listBatch(@Param("batchId") String batchId);

    String getDqStatusById(@Param("id") String id);

    int touchUpdate(@Param("id") String id, @Param("updateBy") String updateBy);

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
