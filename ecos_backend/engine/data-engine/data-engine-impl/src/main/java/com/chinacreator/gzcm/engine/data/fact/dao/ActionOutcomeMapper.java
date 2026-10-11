package com.chinacreator.gzcm.engine.data.fact.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 行动结果回灌载体 mapper（H-3 交付② / V279）：{@code ecos_dw.ecos_application_action_outcome}。
 *
 * <p><b>加表不加列裁定</b>（详设-09 §十一 H-3，理由全文见 V279 脚本头 + FactImportService 决策记录）：
 * outcome 追加行承载（append-only），规避 PUBLISHED 行 UPDATE 违 ECOS-DATA-021 的矛盾。
 *
 * <p>写纪律：本表唯一写路径=data-engine（ADR-14/ST08，BusinessDomainWriteArchTest 把守
 * {@code ecos_dw.} 字面量只准出现在 data-engine-impl）。
 * 查询定位键 forecast_run_id / action_id / idempotency_key 均走索引（禁全库扫描，IR04）。
 */
@Mapper
public interface ActionOutcomeMapper {

    /** 单行 INSERT（应用侧预填 MC01 UUID / 七基线列；金额列 NUMERIC(18,2) 直落）。 */
    int insertRow(Map<String, Object> row);

    /** 幂等键查重（uniq_eaao_idem 命中；无行返回 null）。 */
    Map<String, Object> selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 按预测运行键取回灌行（idx_eaao_run 命中；创建序）。 */
    List<Map<String, Object>> selectByForecastRunId(@Param("forecastRunId") String forecastRunId);

    /** 按行动键取回灌行（idx_eaao_act 命中；创建序）。 */
    List<Map<String, Object>> selectByActionId(@Param("actionId") String actionId);
}
