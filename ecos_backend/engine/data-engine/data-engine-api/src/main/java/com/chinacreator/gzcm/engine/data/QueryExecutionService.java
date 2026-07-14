package com.chinacreator.gzcm.engine.data;

import java.util.Map;

/**
 * 统一 SQL 查询执行服务。
 * 通过 ConnectorFactory 获取对应数据源的 Connector，在目标库执行 SQL。
 *
 * @author ECOS Data Engine Team
 * @since 2026-07-11
 */
public interface QueryExecutionService {

    /**
     * 执行 SQL 查询。
     *
     * @param datasourceId   数据源 ID
     * @param sql            SQL 语句
     * @param params         参数映射（:param 占位符替换）
     * @param maxRows        最大返回行数
     * @param timeoutSeconds 超时秒数
     * @return {columns, rows, rowCount, elapsedMs, historyId}
     */
    Map<String, Object> execute(String datasourceId, String sql,
                                Map<String, Object> params,
                                int maxRows, int timeoutSeconds);

    /**
     * 获取 Schema 树结构（数据源 → Schema → 表 → 字段）。
     * 通过 ConnectorFactory + JDBC DatabaseMetaData 获取。
     *
     * @param datasourceId 数据源 ID
     * @return Schema 树 JSON
     */
    Map<String, Object> getSchemaTree(String datasourceId);

    /**
     * 保存查询模板。
     *
     * @param body 模板信息（name, description, datasource_id, sql_content, params_json, ...）
     * @return 保存后的模板
     */
    Map<String, Object> saveTemplate(Map<String, Object> body);

    /**
     * 查询模板列表（分页）。
     *
     * @param datasourceId 数据源 ID（可选过滤）
     * @param page         页码（1-based）
     * @param pageSize     每页条数
     * @return 分页结果
     */
    Map<String, Object> listTemplates(String datasourceId, int page, int pageSize);

    /**
     * 获取单个模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    Map<String, Object> getTemplate(String id);

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     */
    void deleteTemplate(String id);

    /**
     * 查询执行历史（分页）。
     *
     * @param page     页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Map<String, Object> getQueryHistory(int page, int pageSize);

    /**
     * 取消正在执行的查询。
     *
     * @param historyId 查询历史 ID
     */
    void cancelQuery(String historyId);
}
