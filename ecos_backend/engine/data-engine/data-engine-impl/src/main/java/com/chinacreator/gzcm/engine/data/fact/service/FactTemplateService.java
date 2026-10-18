package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.FactTemplateVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 导入模板_provider — 详细设计-02 D.2 getFactImportTemplate（PRD-02 §1.4：CSV 含表头/单位/示例/必填标记）。
 *
 * <p>纯数据方法（无 DB）：每 factType 一份静态模板（2 示例行）。
 * 模板列序 = 业务列白名单序（camelCase 导入键），CSV 表头携带单位注释。
 */
@Component
public class FactTemplateService {

    public FactTemplateVO template(FactType type) {
        return switch (type) {
            case ATTRIBUTION -> new FactTemplateVO(
                    "ATTRIBUTION", type.tableName(),
                    "project_id,contract_id,department_id,attribution_ratio(0~1),attribution_type(PRIMARY/PARTNER),effective_from(YYYY-MM),effective_to(YYYY-MM可空),source_evidence",
                    List.of("ID", "ID", "ID", "比率 0~1（NUMERIC(5,4)）", "枚举 PRIMARY/PARTNER",
                            "期间 YYYY-MM", "期间 YYYY-MM 可空=至今", "归属依据（合同条款/审批单号）"),
                    List.of("projectId", "contractId", "departmentId", "attributionRatio",
                            "effectiveFrom"),
                    List.of(
                            List.of("p-001", "c-100", "d-01", "0.6000", "PRIMARY", "2025-01", "", "合同CT-2024-881 第3.2条"),
                            List.of("p-001", "c-100", "d-02", "0.4000", "PARTNER", "2025-01", "2025-06", "合作补充协议B-77")));
            case STAGE -> new FactTemplateVO(
                    "STAGE", type.tableName(),
                    "project_id,department_id,period(YYYY-MM),stage(ACCEPTANCE/COLLECTION/SUBCONTRACT),fact_type(PLAN/ACTUAL),contract_base(元),attribution_ratio(0~1),realization_rate(0~1可空),amount(元可空),currency(CNY),source_type(MANUAL/IMPORT/PIPELINE),source_ref,evidence_ref",
                    List.of("ID", "ID", "期间 YYYY-MM", "环节枚举", "枚举 PLAN/ACTUAL",
                            "元（NUMERIC(18,2)）", "比率 0~1", "比率 0~1 可空", "元 可空",
                            "ISO 4217 3 位（默认 CNY）", "来源枚举", "来源批次/凭证", "证据引用（附件 docId/URL）"),
                    List.of("projectId", "departmentId", "period", "stage", "factType",
                            "contractBase"),
                    List.of(
                            List.of("p-001", "d-01", "2025-03", "ACCEPTANCE", "ACTUAL", "1200000.00",
                                    "0.6000", "", "720000.00", "CNY", "IMPORT", "batch-20250401", "doc-991"),
                            List.of("p-001", "d-01", "2025-04", "COLLECTION", "PLAN", "1200000.00",
                                    "0.6000", "", "360000.00", "CNY", "MANUAL", "", "审批单 AP-2210")));
            case RESOURCE -> new FactTemplateVO(
                    "RESOURCE", type.tableName(),
                    "project_id,department_id,period(YYYY-MM),staff_ref_hash(SHA-256 64hex),staff_hash_algo(默认SHA-256),fte(0~1可空),work_hours(小时可空),hourly_rate(元/时可空),cost_category(默认SALARY),fact_type(PLAN/ACTUAL),source_ref",
                    List.of("ID", "ID", "期间 YYYY-MM",
                            "工号单向哈希（禁明文，V167 脱敏存储）", "哈希算法（默认 SHA-256）",
                            "FTE 0~1", "小时（NUMERIC(8,1)）", "元/时（NUMERIC(18,2)）",
                            "枚举 SALARY 等", "枚举 PLAN/ACTUAL", "来源批次/凭证"),
                    List.of("projectId", "departmentId", "period", "staffRefHash", "factType"),
                    List.of(
                            List.of("p-001", "d-01", "2025-03",
                                    "7f2c1a9e3b5d80ab46e1c2d9f0a7b3c8e4d5f6a1b2c3d4e5f6a7b8c9d0e1f2a3",
                                    "SHA-256", "1.00", "176.0", "320.00", "SALARY", "ACTUAL", "batch-20250401"),
                            List.of("p-002", "d-02", "2025-03",
                                    "9a8b7c6d5e4f30211234567890abcdefabcdefabcdefabcdefabcdefab",
                                    "SHA-256", "0.50", "88.0", "280.00", "SALARY", "ACTUAL", "batch-20250401")));
            case COST -> new FactTemplateVO(
                    "COST", type.tableName(),
                    "project_id(可空=公共池),is_pool(0/1),department_id,period(YYYY-MM),cost_category(SALARY/MANAGEMENT/MARKETING),direct_or_allocated(DIRECT/ALLOCATED),allocation_rule_ref(ALLOCATED必填可空),amount(元),currency(CNY),fact_type(PLAN/ACTUAL),evidence_ref(ALLOCATED/ACTUAL必填可空)",
                    List.of("ID 可空", "0=项目成本 1=公共池", "ID", "期间 YYYY-MM", "枚举",
                            "枚举 DIRECT/ALLOCATED", "分摊规则 ID", "元（NUMERIC(18,2)）",
                            "ISO 4217 3 位（默认 CNY）", "枚举 PLAN/ACTUAL", "证据引用"),
                    List.of("departmentId", "period", "costCategory", "directOrAllocated", "amount",
                            "factType"),
                    List.of(
                            List.of("p-001", "0", "d-01", "2025-03", "SALARY", "DIRECT", "",
                                    "51200.00", "CNY", "ACTUAL", "工资单 doc-331"),
                            List.of("POOL", "1", "d-01", "2025-03", "MANAGEMENT", "ALLOCATED",
                                    "rule-009", "8800.00", "CNY", "ACTUAL", "分摊审批 ap-77")));
            case FORECAST_SNAPSHOT -> new FactTemplateVO(
                    "FORECAST_SNAPSHOT", type.tableName(),
                    "forecast_run_id,as_of_time(ISO-8601),scope_hash(sha256 64hex),fact_refs_json,metric_versions_json,profile_versions_json,assumption_versions_json,caliber_id,caliber_version,checksum(sha256 64hex)",
                    List.of("预测运行 ID", "ISO 时间戳", "范围 sha256", "受控 JSON（TEXT，仅描述）",
                            "受控 JSON", "受控 JSON", "受控 JSON", "口径 ID", "口径版本", "全内容 sha256"),
                    List.of("forecastRunId", "asOfTime", "scopeHash", "caliberId", "caliberVersion",
                            "checksum"),
                    List.of(
                            List.of("fr-2025-001", "2025-04-01T02:00:00", "aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011",
                                    "{\"stageFactIds\":[\"f1\"],\"resourceFactIds\":[],\"costFactIds\":[\"c1\"],\"attributionIds\":[\"a1\"]}",
                                    "{\"m-revenue\":\"v3\"}", "{\"prf-01\":\"v2\"}", "{\"as-01\":\"v1\"}",
                                    "cal-01", "v5", "0f9c8d7e6f5a4b3c2d1e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c"),
                            List.of("fr-2025-002", "2025-07-01T02:00:00", "bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff00112233",
                                    "{\"stageFactIds\":[],\"resourceFactIds\":[],\"costFactIds\":[],\"attributionIds\":[]}",
                                    "{}", "{}", "{}", "cal-01", "v6", "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c")));
        };
    }
}
