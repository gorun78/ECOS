package com.chinacreator.gzcm.engine.data.fact;

import java.util.Set;

/**
 * 业务事实五表类型枚举 — 详细设计-02 §F02-06 / §E.2（V167__biz_fact_model.sql）。
 *
 * <p>五表全部落业务域 {@code ecos_dw}（CURATED 层）：四张业务事实表 +
 * 预测输入快照（写权归 05 册预测运行，物理归本域）。
 *
 * <p>每个枚举项绑定：
 * <ul>
 *   <li>schema 限定表名（MC06 基线：XML 内 {@code ecos_dw.} 前缀）；</li>
 *   <li>该类型的<b>业务列白名单</b>（MC02 门禁）——导入接受的行字段全集，
 *       白名单外字段一律拒绝（整批 400），审计五列 / dq_status / is_active /
 *       batch_id / domain / version_no 为系统列，不接受外部传入。</li>
 * </ul>
 */
public enum FactType {

    /** 表 1 — 项目经营归属 */
    ATTRIBUTION("ecos_dw.ecos_biz_project_attribution", Set.of(
            "projectId", "contractId", "departmentId", "attributionRatio",
            "attributionType", "effectiveFrom", "effectiveTo", "sourceEvidence")),

    /** 表 2 — 阶段事实（收入/成本/回款阶段快照） */
    STAGE("ecos_dw.ecos_biz_stage_fact", Set.of(
            "projectId", "departmentId", "period", "stage", "factType",
            "contractBase", "attributionRatio", "realizationRate", "amount",
            "currency", "sourceType", "sourceRef", "evidenceRef")),

    /** 表 3 — 资源投入事实（staffRefHash 工号哈希脱敏存储，staff_ref_hash 非 ST03-A 豁免） */
    RESOURCE("ecos_dw.ecos_biz_resource_fact", Set.of(
            "projectId", "departmentId", "period", "staffRefHash", "staffHashAlgo",
            "fte", "workHours", "hourlyRate", "costCategory", "factType",
            "sourceRef", "evidenceRef")),

    /** 表 4 — 成本事实（isPool 取代 COALESCE 表达式唯一索引） */
    COST("ecos_dw.ecos_biz_cost_fact", Set.of(
            "projectId", "isPool", "departmentId", "period", "costCategory",
            "directOrAllocated", "allocationRuleRef", "amount", "currency",
            "factType", "sourceRef", "evidenceRef")),

    /** 表 5 — 预测输入快照（写权归 05 册预测运行，物理落 datanet） */
    FORECAST_SNAPSHOT("ecos_dw.ecos_forecast_input_snapshot", Set.of(
            "forecastRunId", "asOfTime", "scopeHash", "factRefsJson",
            "metricVersionsJson", "profileVersionsJson", "assumptionVersionsJson",
            "caliberId", "caliberVersion", "checksum", "sourceRef", "evidenceRef"));

    /** 私有 DB schema（docRef 用，读端点 TODO(F02-01-decide) 决策管道后启用）。 */
    public static final String BIZ_DOMAIN_SCHEMA = "ecos_dw";

    private final String tableName;
    private final Set<String> columnWhitelist;

    FactType(String tableName, Set<String> columnWhitelist) {
        this.tableName = tableName;
        this.columnWhitelist = columnWhitelist;
    }

    /** schema 限定表名（MC06）。 */
    public String tableName() {
        return tableName;
    }

    /** 业务列白名单（MC02 门禁）。 */
    public Set<String> columnWhitelist() {
        return columnWhitelist;
    }

    /**
     * 路径参数解析（大小写归一）。
     *
     * @throws IllegalArgumentException 非法 factType（Controller 层转为 400）
     */
    public static FactType fromPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("factType 不能为空");
        }
        String key = raw.trim().toUpperCase(java.util.Locale.ROOT)
                .replace('-', '_');
        for (FactType t : values()) {
            if (t.name().equals(key)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知 factType: " + raw
                + "（合法值: ATTRIBUTION/STAGE/RESOURCE/COST/FORECAST_SNAPSHOT）");
    }
}
