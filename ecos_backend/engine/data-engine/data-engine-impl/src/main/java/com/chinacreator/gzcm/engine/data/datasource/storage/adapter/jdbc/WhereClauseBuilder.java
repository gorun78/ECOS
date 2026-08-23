package com.chinacreator.gzcm.engine.data.datasource.storage.adapter.jdbc;

import com.chinacreator.gzcm.engine.data.datasource.storage.model.FilterCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * WHERE子句构建器 — 用策略模式替代原 BaseJdbcAdapter 的递归 switch-case。
 *
 * <p>从 BaseJdbcAdapter.buildWhereClause 拆出（PMO-C3 P1-2）。
 * 保留 MAX_FILTER_DEPTH 深度保护，SQL 语义与拆分前完全一致。
 */
class WhereClauseBuilder {

    private static final Logger logger = LoggerFactory.getLogger(WhereClauseBuilder.class);

    /** 递归深度上限（B3 深度保护，不可丢失） */
    static final int MAX_FILTER_DEPTH = 20;

    private final Map<FilterCondition.ConditionType, WhereClauseStrategy> strategies;
    private final BaseJdbcAdapter adapter;

    WhereClauseBuilder(BaseJdbcAdapter adapter) {
        this.adapter = adapter;
        this.strategies = new EnumMap<>(FilterCondition.ConditionType.class);
        registerStrategies();
    }

    private void registerStrategies() {
        // ── 单值比较操作符 ──
        strategies.put(FilterCondition.ConditionType.EQUALS,
            (f, p, b) -> simpleCompare(f, p, " = ?"));
        strategies.put(FilterCondition.ConditionType.NOT_EQUALS,
            (f, p, b) -> simpleCompare(f, p, " != ?"));
        strategies.put(FilterCondition.ConditionType.GREATER_THAN,
            (f, p, b) -> simpleCompare(f, p, " > ?"));
        strategies.put(FilterCondition.ConditionType.GREATER_THAN_OR_EQUAL,
            (f, p, b) -> simpleCompare(f, p, " >= ?"));
        strategies.put(FilterCondition.ConditionType.LESS_THAN,
            (f, p, b) -> simpleCompare(f, p, " < ?"));
        strategies.put(FilterCondition.ConditionType.LESS_THAN_OR_EQUAL,
            (f, p, b) -> simpleCompare(f, p, " <= ?"));
        strategies.put(FilterCondition.ConditionType.LIKE,
            (f, p, b) -> simpleCompare(f, p, " LIKE ?"));
        strategies.put(FilterCondition.ConditionType.NOT_LIKE,
            (f, p, b) -> simpleCompare(f, p, " NOT LIKE ?"));

        // ── IS NULL / IS NOT NULL ──
        strategies.put(FilterCondition.ConditionType.IS_NULL,
            (f, p, b) -> adapter.escapeIdentifier(f.getField()) + " IS NULL");
        strategies.put(FilterCondition.ConditionType.IS_NOT_NULL,
            (f, p, b) -> adapter.escapeIdentifier(f.getField()) + " IS NOT NULL");

        // ── IN / NOT IN ──
        strategies.put(FilterCondition.ConditionType.IN,
            (f, p, b) -> buildInClause(f, p, "IN"));
        strategies.put(FilterCondition.ConditionType.NOT_IN,
            (f, p, b) -> buildInClause(f, p, "NOT IN"));

        // ── BETWEEN ──
        strategies.put(FilterCondition.ConditionType.BETWEEN, (f, p, b) -> {
            StringBuilder sql = new StringBuilder();
            sql.append(adapter.escapeIdentifier(f.getField())).append(" BETWEEN ? AND ?");
            p.add(f.getStartValue());
            p.add(f.getEndValue());
            return sql.toString();
        });

        // ── 逻辑分支 AND / OR / NOT（递归） ──
        strategies.put(FilterCondition.ConditionType.AND,
            (f, p, b) -> buildLogical(f, p, b, "AND"));
        strategies.put(FilterCondition.ConditionType.OR,
            (f, p, b) -> buildLogical(f, p, b, "OR"));
        strategies.put(FilterCondition.ConditionType.NOT, (f, p, b) -> {
            if (f.getConditions() == null || f.getConditions().isEmpty()) return null;
            return "NOT (" + b.build(f.getConditions().get(0), p) + ")";
        });
    }

    /** 单值比较：field OP ? + params.add(value) */
    private String simpleCompare(FilterCondition f, List<Object> p, String op) {
        StringBuilder sql = new StringBuilder();
        sql.append(adapter.escapeIdentifier(f.getField())).append(op);
        p.add(f.getValue());
        return sql.toString();
    }

    /** IN / NOT IN 子句 */
    private String buildInClause(FilterCondition f, List<Object> p, String keyword) {
        StringBuilder sql = new StringBuilder();
        sql.append(adapter.escapeIdentifier(f.getField())).append(" ").append(keyword).append(" (");
        List<Object> values = f.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            p.add(values.get(i));
        }
        sql.append(")");
        return sql.toString();
    }

    /** AND / OR 逻辑组合（递归子条件） */
    private String buildLogical(FilterCondition f, List<Object> p, WhereClauseBuilder b, String op) {
        if (f.getConditions() == null || f.getConditions().isEmpty()) return null;
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < f.getConditions().size(); i++) {
            if (i > 0) sql.append(" ").append(op).append(" ");
            sql.append("(").append(b.build(f.getConditions().get(i), p)).append(")");
        }
        return sql.toString();
    }

    /**
     * 构建WHERE子句（入口方法）。
     */
    String build(FilterCondition filter, List<Object> params) {
        return build(filter, params, 0);
    }

    /**
     * 递归构建 WHERE 子句，带最大深度保护防止 StackOverflow。
     *
     * @param depth 当前递归深度，超过 MAX_FILTER_DEPTH 返回 null
     */
    String build(FilterCondition filter, List<Object> params, int depth) {
        if (filter == null) {
            return null;
        }
        if (depth > MAX_FILTER_DEPTH) {
            logger.warn("Filter condition depth exceeded {} , ignoring deep conditions", MAX_FILTER_DEPTH);
            return null;
        }

        WhereClauseStrategy strategy = strategies.get(filter.getType());
        if (strategy == null) {
            return null;
        }
        return strategy.build(filter, params, this);
    }
}
