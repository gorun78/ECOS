package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 表 1 — 项目经营归属 mapper（V167）：{@code ecos_dw.ecos_biz_project_attribution}。
 *
 * <p>行数据以 {@code Map<String,Object>} 承载，键 = camelCase（与
 * {@code FactType#columnWhitelist()} 对齐）。系统列（{@code id}/{@code dqStatus}/
 * {@code isActive}/{@code batchId}/{@code domain}/{@code versionNo}/{@code createBy}/
 * {@code updateBy}/{@code isDeleted}）由 Service 层填充后传入。
 */
@Mapper
public interface AttributionFactMapper {

    /** 批量插入（调用方保证 ≤100，EN03）。 */
    int insertBatch(@Param("rows") List<Map<String, Object>> rows);

    /** 批次总行数（is_deleted=0）。 */
    int countBatch(@Param("batchId") String batchId);

    /** 批次内 dq_status 行数。 */
    int countBatchByStatus(@Param("batchId") String batchId, @Param("dqStatus") String dqStatus);

    /** 批次内原始行（按 create_time 顺）。 */
    List<Map<String, Object>> listBatch(@Param("batchId") String batchId);

    /** 主键对应行的 dq_status（无行 → null）。 */
    String getDqStatusById(@Param("id") String id);

    /** action-outcome 触达：仅 update_time/update_by 更新。 */
    int touchUpdate(@Param("id") String id, @Param("updateBy") String updateBy);

    /** 分页查询（projectId/period(=effective_from)/batchId/dqStatus 可选过滤）。 */
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
