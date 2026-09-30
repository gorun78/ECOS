package com.chinacreator.gzcm.engine.ontology.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 本体自动发现预览取数服务 — 承载预览链路的只读计数查询。
 *
 * <p>供 {@code AutoDiscoverPreviewController} 调用：预览（dryRun）需要
 * 「数据源是否存在」与「实体码在该域下是否已存在」两项只读判定，
 * 数据库访问统一收敛在 Service 层（铁律 §3.6：Controller 不直连数据库）。
 *
 * <p>本服务只执行 SELECT COUNT 并原样返回结果，
 * 降级/容错策略（异常时放行或视为不存在）保留在调用侧。
 */
@Service
public class AutoDiscoverPreviewQueryService {

    private final JdbcTemplate jdbc;

    public AutoDiscoverPreviewQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 按主键统计数据源行数（存在性判定）。
     *
     * @param datasourceId 数据源 ID
     * @return 匹配行数（0 或 1）
     */
    public Integer countDatasourceById(String datasourceId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM td_datasource WHERE datasource_id = ?",
            Integer.class, datasourceId);
    }

    /**
     * 按实体码 + 业务域统计数据模型行数（已发现判定）。
     *
     * @param entityCode 由资源表名推导出的实体码
     * @param domainCode 业务域代码
     * @return 匹配行数
     */
    public Integer countEntityByCodeAndDomain(String entityCode, String domainCode) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity " +
            "WHERE code = ? AND domain_id = " +
            "(SELECT id FROM ecos_domain WHERE code = ?)",
            Integer.class, entityCode, domainCode);
    }
}
