package com.chinacreator.gzcm.engine.data.fact.masterdata;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主数据完备性检查器只读 mapper — 详细设计-09 v1.2 §十一 H-6 交付⑤（卷 02 底座收口，
 * PRD-07 主数据完备性门禁的检验面）。
 *
 * <p><b>只读</b>：仅 SELECT，零写路径、零 DDL（H-6 场景册禁新增表）。行键 = camelCase
 * （XML 内显式 {@code AS "camelCase"} 别名，不依赖 mapUnderscoreToCamelCase 配置）。
 *
 * <ul>
 *   <li>{@code ecos_dw.ecos_biz_stage_fact} — 明细面（dq_status≠REJECTED 为检验集；
 *       REJECTED 行仅计数，作 DQ BLOCK 趋势面 fc_dq_block_trend 的数据源）</li>
 *   <li>{@code ecos_dw.ecos_biz_project_attribution} — 归属面（is_active=1 且未软删）</li>
 * </ul>
 */
@Mapper
public interface MasterDataCompletenessMapper {

    /** 明细扫描集：dq_status≠REJECTED、is_deleted=0，projectId/period 可选过滤，limit 封顶。 */
    List<Map<String, Object>> selectCompletenessDetails(@Param("projectId") String projectId,
                                                        @Param("period") String period,
                                                        @Param("limit") int limit);

    /** REJECTED 明细计数（DQ BLOCK 趋势面；不计入完备性检验集）。 */
    int countRejectedDetails(@Param("projectId") String projectId,
                             @Param("period") String period);

    /** 归属扫描集：is_active=1、is_deleted=0，projectId 可选过滤，limit 封顶（覆盖窗匹配在 Java 侧）。 */
    List<Map<String, Object>> selectActiveAttributions(@Param("projectId") String projectId,
                                                       @Param("limit") int limit);
}
