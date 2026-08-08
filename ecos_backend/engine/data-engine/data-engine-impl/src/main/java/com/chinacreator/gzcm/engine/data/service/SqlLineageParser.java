package com.chinacreator.gzcm.engine.data.service;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSqlParser 字段级 SQL 血缘解析器 (JSqlParser 4.9 API)。
 * <p>
 * 解析 INSERT/SELECT/JOIN/CTE/子查询中的字段级映射关系，
 * 输出节点 ({@code nodes}) 和有向边 ({@code edges})。
 * 国产数据库方言解析失败时自动降级为表级正则匹配。
 * </p>
 */
public class SqlLineageParser {

    private static final Logger log = LoggerFactory.getLogger(SqlLineageParser.class);

    private static final int MAX_DEPTH = 5;

    private final List<Map<String, String>> nodes = new ArrayList<>();
    private final List<Map<String, String>> edges = new ArrayList<>();
    private final Set<String> seenNodes = new LinkedHashSet<>();

    /**
     * 解析 SQL 语句，生成字段级血缘关系。
     */
    public Map<String, Object> parse(String sql) {
        nodes.clear();
        edges.clear();
        seenNodes.clear();

        if (sql == null || sql.isBlank()) {
            return emptyResult();
        }

        try {
            Statement stmt = CCJSqlParserUtil.parse(sanitize(sql));
            if (stmt instanceof Insert insert) {
                parseInsert(insert, 0);
            } else if (stmt instanceof Select select) {
                parseSelectStmt(select, "RESULT", 0);
            } else {
                parseTableLevel(sql);
            }
        } catch (JSQLParserException | StackOverflowError e) {
            log.debug("JSqlParser 解析失败，回退表级正则: {}", e.getMessage());
            parseTableLevel(sql);
        }

        if (nodes.isEmpty()) {
            parseTableLevel(sql);
        }

        return buildResult();
    }

    // ── INSERT 解析 ──

    private void parseInsert(Insert insert, int depth) {
        if (depth >= MAX_DEPTH) return;

        Table targetTable = insert.getTable();
        String targetName = tableName(targetTable);
        addNode(targetName, "table", targetName);

        Select select = insert.getSelect();
        if (select != null) {
            parseSelectAsSource(select, targetName, depth + 1);
        }

        // INSERT ... VALUES（无字段血缘，仅标记目标列）
        if (insert.getColumns() != null && select == null) {
            for (Column col : insert.getColumns()) {
                addNode(targetName + "." + col.getColumnName(), "field", targetName);
            }
        }
    }

    // ── SELECT 语句入口 ──

    private void parseSelectStmt(Select select, String targetName, int depth) {
        if (depth >= MAX_DEPTH) return;

        if (select instanceof PlainSelect ps) {
            parsePlainSelect(ps, targetName, depth);
        } else if (select instanceof SetOperationList sol) {
            for (Select sub : sol.getSelects()) {
                parseSelectStmt(sub, targetName + "_union", depth);
            }
        } else if (select instanceof ParenthesedSelect ps) {
            parseSelectStmt(ps.getSelect(), targetName, depth);
        }
    }

    // ── SELECT 作为目标（顶层查询） ──

    private void parsePlainSelect(PlainSelect ps, String targetName, int depth) {
        addNode(targetName, "table", targetName);

        // 源表
        parseFromItem(ps.getFromItem(), targetName, depth + 1);
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                parseFromItem(join.getFromItem(), targetName, depth + 1);
            }
        }

        // 字段级映射
        List<SelectItem<?>> items = ps.getSelectItems();
        if (items != null) {
            for (SelectItem<?> item : items) {
                String alias;
                if (item.getAlias() != null) {
                    alias = item.getAlias().getName();
                } else {
                    alias = columnName(item.getExpression());
                }
                String targetField = targetName + "." + alias;
                addNode(targetField, "field", targetName);
                resolveFieldSources(item.getExpression(), targetField);
            }
        }
    }

    // ── SELECT 作为源（INSERT...SELECT / 子查询） ──

    private void parseSelectAsSource(Select select, String targetName, int depth) {
        if (depth >= MAX_DEPTH) return;

        if (select instanceof PlainSelect ps) {
            parseFromItem(ps.getFromItem(), targetName, depth + 1);
            if (ps.getJoins() != null) {
                for (Join join : ps.getJoins()) {
                    parseFromItem(join.getFromItem(), targetName, depth + 1);
                }
            }
            for (SelectItem<?> item : ps.getSelectItems()) {
                String alias;
                if (item.getAlias() != null) {
                    alias = item.getAlias().getName();
                } else {
                    alias = columnName(item.getExpression());
                }
                String targetField = targetName + "." + alias;
                addNode(targetField, "field", targetName);
                resolveFieldSources(item.getExpression(), targetField);
            }
        } else if (select instanceof SetOperationList sol) {
            for (Select sub : sol.getSelects()) {
                parseSelectAsSource(sub, targetName, depth);
            }
        } else if (select instanceof ParenthesedSelect ps) {
            parseSelectAsSource(ps.getSelect(), targetName, depth);
        }
    }

    // ── FROM 子句解析 ──

    private void parseFromItem(FromItem fromItem, String parentName, int depth) {
        if (depth >= MAX_DEPTH || fromItem == null) return;

        if (fromItem instanceof Table table) {
            String name = tableName(table);
            addNode(name, "table", name);
            addEdge(name, parentName, "read");
        } else if (fromItem instanceof ParenthesedSelect ps) {
            String alias = aliasOrFrom(fromItem);
            parseSelectAsSource(ps.getSelect(), alias, depth);
            addEdge(alias, parentName, "subquery");
        } else if (fromItem instanceof LateralSubSelect lss) {
            String alias = aliasOrFrom(fromItem);
            parseSelectAsSource(lss.getSelect(), alias, depth);
        } else if (fromItem instanceof ParenthesedFromItem pfi) {
            parseFromItem(pfi.getFromItem(), parentName, depth);
        }
    }

    // ── 字段来源追溯 ──

    private void resolveFieldSources(Expression expr, String targetField) {
        if (expr == null) return;

        if (expr instanceof Column col) {
            String srcTable = col.getTable() != null ? col.getTable().getName() : "";
            if (!srcTable.isEmpty()) {
                String srcField = srcTable + "." + col.getColumnName();
                addNode(srcField, "field", srcTable);
                addEdge(srcField, targetField, "direct");
            } else {
                String srcField = "?." + col.getColumnName();
                addNode(srcField, "field_unknown", "?");
                addEdge(srcField, targetField, "direct");
            }
        } else if (expr instanceof Function func) {
            String funcName = func.getName() != null ? func.getName() : "fn";
            if (func.getParameters() != null) {
                for (Expression param : func.getParameters()) {
                    resolveFieldSources(param, targetField);
                }
            }
            updateEdgeTransform(targetField, funcName);
        } else if (expr instanceof CaseExpression caseExpr) {
            if (caseExpr.getElseExpression() != null) {
                resolveFieldSources(caseExpr.getElseExpression(), targetField);
            }
            if (caseExpr.getWhenClauses() != null) {
                for (WhenClause wc : caseExpr.getWhenClauses()) {
                    resolveFieldSources(wc.getThenExpression(), targetField);
                }
            }
            updateEdgeTransform(targetField, "CASE");
        } else if (expr instanceof BinaryExpression bin) {
            resolveFieldSources(bin.getLeftExpression(), targetField);
            resolveFieldSources(bin.getRightExpression(), targetField);
        } else if (expr instanceof CastExpression cast) {
            resolveFieldSources(cast.getLeftExpression(), targetField);
        } else if (expr instanceof Parenthesis paren) {
            resolveFieldSources(paren.getExpression(), targetField);
        } else if (expr instanceof Select subSelect) {
            // 标量子查询
            String alias = "SUB_" + targetField.replace(".", "_");
            parseSelectAsSource(subSelect, alias, 0);
            addEdge(alias, targetField, "scalar_subquery");
        }
    }

    // ── 表级正则回退 ──

    private void parseTableLevel(String sql) {
        log.debug("使用表级正则解析");

        // 目标表
        Matcher tm = Pattern.compile(
            "(?i)INSERT\\s+(?:INTO|OVERWRITE)\\s+TABLE\\s+([`\\w.]+)|INSERT\\s+(?:INTO|OVERWRITE)\\s+([`\\w.]+)|CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\\w.]+)\\s+AS|SELECT\\s+.*?\\s+INTO\\s+([`\\w.]+)",
            Pattern.DOTALL).matcher(sql);

        String target = "RESULT";
        if (tm.find()) {
            for (int i = 1; i <= 4; i++) {
                if (tm.group(i) != null) {
                    target = cleanTableName(tm.group(i));
                    break;
                }
            }
        }
        addNode(target, "table", target);

        // 源表
        Matcher fm = Pattern.compile(
            "(?i)(?:FROM|JOIN)\\s+(?:ONLY\\s+)?([`\\w.]+)(?:\\s+(?:AS\\s+)?(\\w+))?",
            Pattern.DOTALL).matcher(sql);
        while (fm.find()) {
            String srcTable = cleanTableName(fm.group(1));
            if (srcTable.equalsIgnoreCase(target)) continue;
            addNode(srcTable, "table", srcTable);
            addEdge(srcTable, target, "read");
        }

        // 字段级
        Matcher colm = Pattern.compile(
            "(?i)(\\w+)\\.(\\w+)|(\\w+)\\s+AS\\s+(\\w+)",
            Pattern.DOTALL).matcher(sql);
        while (colm.find()) {
            if (colm.group(1) != null && colm.group(2) != null) {
                String srcField = colm.group(1) + "." + colm.group(2);
                addNode(srcField, "field", colm.group(1));
            }
        }
    }

    // ── 辅助 ──

    private String tableName(Table table) {
        if (table == null) return "unknown";
        String schema = table.getSchemaName();
        String name = table.getName();
        if (schema != null && !schema.isEmpty()) return schema + "." + name;
        return name;
    }

    private String aliasOrFrom(FromItem item) {
        return item.getAlias() != null ? item.getAlias().getName() : item.toString();
    }

    private String columnName(Expression expr) {
        if (expr instanceof Column col) return col.getColumnName();
        if (expr instanceof Function func) return func.getName() != null ? func.getName() : "fn";
        String s = expr != null ? expr.toString() : "expr";
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }

    private String cleanTableName(String raw) {
        return raw.replaceAll("[`\"'\\[\\]]", "").trim();
    }

    private void addNode(String id, String type, String table) {
        if (id == null || id.isEmpty()) return;
        if (!seenNodes.add(id)) return;
        Map<String, String> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("table", table);
        nodes.add(node);
    }

    private void addEdge(String source, String target, String transform) {
        if (source == null || target == null || source.equals(target)) return;
        Map<String, String> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("transform", transform);
        edges.add(edge);
    }

    private void updateEdgeTransform(String targetField, String newTransform) {
        for (int i = edges.size() - 1; i >= 0; i--) {
            Map<String, String> e = edges.get(i);
            if (targetField.equals(e.get("target"))) {
                String existing = e.get("transform");
                if (existing == null || "direct".equals(existing)) {
                    e.put("transform", newTransform);
                }
                break;
            }
        }
    }

    /**
     * 预处理 SQL：移除部分国产数据库方言（Hive/Spark/MySQL hint 等）。
     */
    private String sanitize(String sql) {
        String s = sql.trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
        s = s.replaceAll("(?i)/\\*\\+.*?\\*/", "");
        s = s.replaceAll("(?i)TBLPROPERTIES\\s*\\([^)]*\\)", "");
        s = s.replaceAll("(?i)PARTITIONED\\s+BY\\s*\\([^)]*\\)", "");
        s = s.replaceAll("(?i)CLUSTERED\\s+BY\\s*\\([^)]*\\)\\s+INTO\\s+\\d+\\s+BUCKETS", "");
        s = s.replaceAll("(?i)STORED\\s+AS\\s+\\w+", "");
        s = s.replaceAll("(?i)LOCATION\\s+'[^']*'", "");
        return s;
    }

    private Map<String, Object> buildResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", new ArrayList<>(nodes));
        result.put("edges", new ArrayList<>(edges));
        result.put("total_nodes", nodes.size());
        result.put("total_edges", edges.size());
        return result;
    }

    private Map<String, Object> emptyResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", List.of());
        result.put("edges", List.of());
        result.put("total_nodes", 0);
        result.put("total_edges", 0);
        return result;
    }
}
