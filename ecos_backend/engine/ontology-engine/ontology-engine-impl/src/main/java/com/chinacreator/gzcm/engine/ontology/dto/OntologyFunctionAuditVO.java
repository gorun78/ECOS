package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Function 执行审计分页结果 VO。
 *
 * <p>{@code GET /api/v1/ontology/functions/audit} 返回。
 *
 * <p>T16-3 (2026-09-13)：动态 payload 局部豁免说明——{@code items} 为
 * PG {@code ecos_function_audit_log} 表直出 row（JDBC 直查 SELECT *），
 * 字段含义受数据库 schema 演化影响，保留 raw Map 列表避免 schema 变更
 * 时 VO 字段漂移，是"函数沙箱运行时 payload"豁免。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyFunctionAuditVO {

    /** 审计日志列表（PG 表 SELECT * 直出，动态 payload 豁免） */
    private List<Map<String, Object>> items;

    /** 满足条件的总条数 */
    private Integer total;

    /** 当前页码 */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;
}
