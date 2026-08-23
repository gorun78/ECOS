package com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc;

import com.chinacreator.gzcm.engine.data.datasource.storage.model.FilterCondition;
import java.util.List;

/**
 * WHERE子句构建策略接口 — 每种 FilterCondition.ConditionType 对应一个策略实现。
 *
 * <p>从 BaseJdbcAdapter.buildWhereClause 拆出（PMO-C3 P1-2），
 * 将原递归 switch-case 拆为操作符策略模式，单方法复杂度 ≤10。
 * SQL 语义与拆分前完全一致。
 */
interface WhereClauseStrategy {

    /**
     * 构建该条件类型对应的 SQL 片段。
     *
     * @param filter     过滤条件
     * @param params     参数列表（策略实现将绑定参数追加到此列表）
     * @param builder    WHERE构建器（用于逻辑分支 AND/OR/NOT 递归调用子条件）
     * @return SQL 片段字符串，或 null 表示忽略该条件
     */
    String build(FilterCondition filter, List<Object> params, WhereClauseBuilder builder);
}
