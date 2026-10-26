package com.chinacreator.gzcm.engine.data.fact.dao;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
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

    /**
     * 声明域真值读回（#142/B4-3e-79，差距 D-B4-36）：按受控项目集读
     * {@code (projectId, departmentId)} 去重组，供 security 侧解析服务派生真实所属域。
     *
     * <p><b>只读权威态</b>：{@code is_deleted = 0 AND is_active = 1 AND
     * dq_status IN ('PASSED','PUBLISHED')}。PENDING／REJECTED 行尚未通过 DQ 门禁，
     * 拿它们展开域等于让未验收数据参与裁决；{@code is_active = 0} 是被废止的历史态。
     * 三条件与 {@code FactImportService} 的写入态（accepted ⇒ PASSED／is_active=1）同源。</p>
     *
     * <p>本方法<b>不做任何判定</b>——一个项目多部门、项目缺归属、跨项目部门不一致
     * 都原样返回，判定唯一收敛在消费侧（{@code DeclaredScopeResolutionService}），
     * 否则同一个真值面会有两处不同的"算不算数"口径。</p>
     *
     * @param projectIds 受控项目集（调用方保证非空且 ≤ 上限；IN 逐值参数化，IR05）
     * @return 每行 {@code {projectId, departmentId}}；无任何权威归属行时为空表
     */
    List<Map<String, Object>> listDepartmentRowsByProjectIds(
            @Param(DataScopeContract.KEY_PROJECT_IDS) List<String> projectIds);
}
