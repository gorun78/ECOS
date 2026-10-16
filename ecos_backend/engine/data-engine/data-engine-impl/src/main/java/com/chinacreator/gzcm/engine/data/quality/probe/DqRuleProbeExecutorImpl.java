package com.chinacreator.gzcm.engine.data.quality.probe;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * F02-09 补齐：DQ 规则真值探测默认实现。
 * <p>
 * 语义契约（{@link DqRuleProbeExecutor} Javadoc）：
 * <ul>
 *   <li>整探测为只读单语句;
 *       常规规则走 {@code SELECT COUNT(*) AS total,
 *       COALESCE(SUM(CASE WHEN <fail> THEN 1 ELSE 0 END), 0) AS fails FROM <qualified> [WHERE <when>]}；
 *       UNIQUE 走 {@code (COUNT(*) - COUNT(DISTINCT (k1,...)))}；
 *       FRESHNESS 走 {@code MAX(col)} + {@code COUNT(*)}。</li>
 *   <li>所有 identifier 双白名单：正则 {@code [a-z_][a-z0-9_]*} +
 *       {@code information_schema.columns} 存在性；camelCase 自动映射 snake_case；
 *       任何失败 → {@link RuleProbeResult#notExecuted(String)},
 *       评估器回退 F02-09 的 "contextIncomplete=true 降级 UNKNOWN" 语义</li>
 *   <li>F02-09 语义对齐：SQL 异常、类型不支持 (CONSISTENCY) 一律 notExecuted,
 *       不伪装通过 —— 与 IncompleteContextUnknownScoreTest 铁律同源</li>
 * </ul>
 *
 * @author F02-09 / ECOS Data Engine Team
 */
@Component
public class DqRuleProbeExecutorImpl implements DqRuleProbeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DqRuleProbeExecutorImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DqRuleProbeDao dao;

    public DqRuleProbeExecutorImpl(DqRuleProbeDao dao) {
        this.dao = dao;
    }

    @Override
    public RuleProbeResult probe(DqRuleVO rule) {
        if (rule == null) {
            return notExecuted("rule is null");
        }
        if (!"TABLE".equalsIgnoreCase(rule.getTargetKind())) {
            RuleProbeResult r = notExecuted("targetKind != TABLE: " + rule.getTargetKind());
            log.warn("DqRuleProbe notExecuted rule={}: {}", rule.getId(), r.getReason());
            return r;
        }
        String tableRef = rule.getTargetTable();
        if (tableRef == null || !tableRef.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) {
            RuleProbeResult r = notExecuted("targetTable must be schema.table, got: " + tableRef);
            log.warn("DqRuleProbe notExecuted rule={}: {}", rule.getId(), r.getReason());
            return r;
        }
        String[] parts = tableRef.split("\\.", 2);
        String schema = parts[0].toLowerCase(Locale.ROOT);
        String table = parts[1].toLowerCase(Locale.ROOT);
        String qualified = schema + "." + table;

        Long oid = dao.tableOid(qualified);
        if (oid == null || oid == 0L) {
            return notExecuted("table not resolvable: " + qualified);
        }
        List<String> colsRaw = dao.columnWhitelist(schema, table);
        if (colsRaw.isEmpty()) {
            return notExecuted("column whitelist empty (unreachable): " + qualified);
        }
        Set<String> columns = new HashSet<>(colsRaw);

        Map<String, Object> params;
        try {
            params = parseParams(rule.getParametersJson());
        } catch (JsonProcessingException e) {
            return notExecuted("parametersJson parse failed: " + e.getMessage());
        } catch (RuntimeException e) {
            return notExecuted("parametersJson parse failed: " + e.getMessage());
        }

        String ruleType = rule.getRuleType() == null ? "" : rule.getRuleType().trim().toUpperCase(Locale.ROOT);
        RuleProbeResult result;
        try {
            result = switch (ruleType) {
                case "NOT_NULL" -> probeNotNull(rule, qualified, columns);
                case "PRESENCE" -> probePresence(rule, params, qualified, columns);
                case "RANGE" -> probeRange(rule, params, qualified, columns);
                case "ENUM" -> probeEnum(rule, params, qualified, columns);
                case "FORMAT" -> probeFormat(rule, params, qualified, columns);
                case "VALIDITY" -> probeValidity(params, qualified, columns);
                case "FRESHNESS" -> probeFreshness(rule, params, qualified, columns);
                case "UNIQUE" -> probeUnique(rule, params, qualified, columns);
                case "CONSISTENCY" -> notExecuted(
                        "CONSISTENCY requires per-rule predicate spec (multi-column business invariant)");
                default -> notExecuted("rule_type not covered by probe: " + rule.getRuleType());
            };
        } catch (RuntimeException e) {
            log.warn("DqRuleProbe 处理器异常 ruleType={} table={}: {}",
                    ruleType, qualified, e.getMessage(), e);
            result = notExecuted("unexpected probe error: " + e.getMessage());
        }
        if (result.isExecuted()) {
            log.info("DqRuleProbe executed rule={} type={} table={} total={} fails={} digest={}",
                    rule.getId(), ruleType, qualified, result.getTotalRows(), result.getFailedRows(), result.getSqlDigest());
        } else {
            log.warn("DqRuleProbe notExecuted rule={} type={} table={} reason={}",
                    rule.getId(), ruleType, qualified, result.getReason());
        }
        return result;
    }

    // ───────────────────────────── 各 rule_type 分支 ─────────────────────────────

    private RuleProbeResult probeNotNull(DqRuleVO rule, String qualified, Set<String> columns) {
        String col = resolveColumn(rule.getTargetField(), columns);
        if (col == null) {
            return notExecuted("NOT_NULL target_field not in whitelist: " + rule.getTargetField());
        }
        return countWithFails(qualified, col + " IS NULL", null, List.of(),
                "COUNT/SUM NOT_NULL " + qualified + "." + col);
    }

    private RuleProbeResult probePresence(DqRuleVO rule, Map<String, Object> params,
                                          String qualified, Set<String> columns) {
        List<String> requireAny = stringListParam(params, "requireAnyOf");
        String requireOne = strOrNull(params.get("require"));
        String whenClause = null;
        List<Object> whenArgs = List.of();
        Object whenRaw = params.get("when");
        if (whenRaw != null && !whenRaw.toString().isBlank()) {
            String[] w = parseWhen(whenRaw.toString(), columns);
            if (w == null) {
                return notExecuted("PRESENCE when= not parseable: " + whenRaw);
            }
            whenClause = w[0];
            whenArgs = List.of(w[1]);
        }
        if (requireAny != null && !requireAny.isEmpty()) {
            List<String> cols = new ArrayList<>(requireAny.size());
            for (String c : requireAny) {
                String resolved = resolveColumn(c, columns);
                if (resolved == null) {
                    return notExecuted("PRESENCE requireAnyOf column not in whitelist: " + c);
                }
                cols.add(resolved);
            }
            StringBuilder sb = new StringBuilder("NOT (");
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) sb.append(" OR ");
                sb.append("(").append(cols.get(i)).append(" IS NOT NULL AND ").append(cols.get(i)).append(" <> '')");
            }
            sb.append(")");
            return countWithFails(qualified, sb.toString(), whenClause, whenArgs,
                    "COUNT/SUM PRESENCE anyOf " + cols);
        }
        String effective = requireOne != null ? requireOne : rule.getTargetField();
        String col = resolveColumn(effective, columns);
        if (col == null) {
            return notExecuted("PRESENCE require/target_field column not in whitelist: " + effective);
        }
        String fails = "(((" + col + " IS NULL) OR (" + col + " = '')))";
        return countWithFails(qualified, fails, whenClause, whenArgs,
                "COUNT/SUM PRESENCE " + col);
    }

    private RuleProbeResult probeRange(DqRuleVO rule, Map<String, Object> params,
                                       String qualified, Set<String> columns) {
        String col = resolveColumn(rule.getTargetField(), columns);
        if (col == null) {
            return notExecuted("RANGE target_field not in whitelist: " + rule.getTargetField());
        }
        String min = numAsStr(params.get("min"));
        String max = numAsStr(params.get("max"));
        if (min == null && max == null) {
            return notExecuted("RANGE: no min or max bound specified");
        }
        List<String> ors = new ArrayList<>();
        if (min != null) ors.add("(" + col + " < " + min + ")");
        if (max != null) ors.add("(" + col + " > " + max + ")");
        String fails = "(" + String.join(" OR ", ors) + " AND " + col + " IS NOT NULL)";
        return countWithFails(qualified, fails, null, List.of(),
                "COUNT/SUM RANGE " + col + " [" + min + "," + max + "]");
    }

    private RuleProbeResult probeEnum(DqRuleVO rule, Map<String, Object> params,
                                      String qualified, Set<String> columns) {
        String col = resolveColumn(rule.getTargetField(), columns);
        if (col == null) {
            return notExecuted("ENUM target_field not in whitelist: " + rule.getTargetField());
        }
        List<String> values = stringListParam(params, "values");
        if (values == null || values.isEmpty()) {
            return notExecuted("ENUM: values missing");
        }
        StringBuilder ph = new StringBuilder("(");
        List<Object> args = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) ph.append(", ");
            ph.append('?');
            args.add(values.get(i));
        }
        ph.append(")");
        String fails = "(" + col + " IS NULL) OR (" + col + " NOT IN " + ph + ")";
        return countWithFails(qualified, fails, null, args,
                "COUNT/SUM ENUM " + col + " in " + values.size() + " values");
    }

    private RuleProbeResult probeFormat(DqRuleVO rule, Map<String, Object> params,
                                        String qualified, Set<String> columns) {
        String col = resolveColumn(rule.getTargetField(), columns);
        if (col == null) {
            return notExecuted("FORMAT target_field not in whitelist: " + rule.getTargetField());
        }
        Object pattern = params.get("pattern");
        if (pattern == null || pattern.toString().isBlank()) {
            return notExecuted("FORMAT: pattern missing");
        }
        String fails = "(" + col + " IS NULL) OR (" + col + " !~ ?)";
        return countWithFails(qualified, fails, null, List.of(pattern.toString()),
                "COUNT/SUM FORMAT regex=" + pattern);
    }

    private RuleProbeResult probeValidity(Map<String, Object> params,
                                          String qualified, Set<String> columns) {
        String require = strOrNull(params.get("require"));
        if (require == null) {
            return notExecuted("VALIDITY: require missing");
        }
        String col = resolveColumn(require, columns);
        if (col == null) {
            return notExecuted("VALIDITY require column not in whitelist: " + require);
        }
        String whenClause = null;
        List<Object> whenArgs = List.of();
        Object whenRaw = params.get("when");
        if (whenRaw != null && !whenRaw.toString().isBlank()) {
            String[] w = parseWhen(whenRaw.toString(), columns);
            if (w == null) {
                return notExecuted("VALIDITY when= not parseable: " + whenRaw);
            }
            whenClause = w[0];
            whenArgs = List.of(w[1]);
        } else {
            whenClause = "1=1";
        }
        String fails = "(((" + col + " IS NULL) OR (" + col + " = '')))";
        return countWithFails(qualified, fails, whenClause, whenArgs,
                "COUNT/SUM VALIDITY require=" + col);
    }

    private RuleProbeResult probeFreshness(DqRuleVO rule, Map<String, Object> params,
                                           String qualified, Set<String> columns) {
        String col = resolveColumn(rule.getTargetField(), columns);
        if (col == null) {
            return notExecuted("FRESHNESS target_field not in whitelist: " + rule.getTargetField());
        }
        long minutes = positiveLong(params.get("freshness_minutes"),
                positiveLong(params.get("stale_minutes"), 1440L));
        try {
            Timestamp latest = dao.latestTimestamp(qualified, col);
            long[] count = dao.countTotalFail(
                    "SELECT COUNT(*)::bigint AS total, 0::bigint AS fails FROM " + qualified, List.of());
            if (count == null) {
                return notExecuted("FRESHNESS no count row");
            }
            long total = count[0];
            long fails = 0L;
            if (latest == null) {
                fails = total;
            } else {
                long ageMinutes = (System.currentTimeMillis() - latest.getTime()) / 60000L;
                if (ageMinutes > minutes) {
                    fails = total; // 最新一行都超龄 → 全表不新鲜
                }
            }
            return RuleProbeResult.executed(total, fails,
                    "MAX(" + col + ") age<" + minutes + "min");
        } catch (DataAccessException e) {
            log.warn("DqRuleProbe FRESHNESS 探测失败: qualified={}/col={}: {}", qualified, col, e.getMessage());
            return notExecuted("FRESHNESS sql failed: " + e.getMessage());
        }
    }

    private RuleProbeResult probeUnique(DqRuleVO rule, Map<String, Object> params,
                                        String qualified, Set<String> columns) {
        List<String> keys = stringListParam(params, "keys");
        if (keys == null || keys.isEmpty()) {
            String single = resolveColumn(rule.getTargetField(), columns);
            if (single != null) {
                keys = List.of(single);
            } else {
                return notExecuted("UNIQUE: keys missing and target_field not in whitelist");
            }
        }
        List<String> resolved = new ArrayList<>(keys.size());
        for (String k : keys) {
            String rc = resolveColumn(k, columns);
            if (rc == null) {
                return notExecuted("UNIQUE key column not in whitelist: " + k);
            }
            resolved.add(rc);
        }
        String sql = "SELECT COUNT(*)::bigint AS total,"
                + " (COUNT(*) - COUNT(DISTINCT (" + String.join(", ", resolved) + ")))::bigint AS fails"
                + " FROM " + qualified;
        RuleProbeResult r;
        try {
            long[] res = dao.countTotalFail(sql, List.of());
            if (res == null) {
                return notExecuted("UNIQUE no rows");
            }
            r = RuleProbeResult.executed(res[0], res[1], "COUNT/DISTINCT UNIQUE " + resolved);
        } catch (DataAccessException e) {
            log.warn("DqRuleProbe UNIQUE SQL 失败: qualified={}: {}", qualified, e.getMessage());
            r = notExecuted("UNIQUE sql failed: " + e.getMessage());
        }
        return r;
    }

    // ───────────────────────────── 通用工具 ─────────────────────────────

    private RuleProbeResult countWithFails(String qualified, String failExpr,
                                           String whenClause, List<Object> whenArgs,
                                           String digest) {
        String where = (whenClause == null || whenClause.isBlank() || whenClause.equals("1=1"))
                ? "" : " WHERE " + whenClause;
        String sql = "SELECT COUNT(*)::bigint AS total,"
                + " COALESCE(SUM(CASE WHEN " + failExpr + " THEN 1 ELSE 0 END), 0)::bigint AS fails"
                + " FROM " + qualified + where;
        try {
            long[] r = dao.countTotalFail(sql, whenArgs);
            if (r == null) {
                return notExecuted("no rows returned");
            }
            return RuleProbeResult.executed(r[0], r[1], digest);
        } catch (DataAccessException e) {
            log.warn("DqRuleProbe SQL 失败: digest={}, err={}", digest, e.getMessage());
            return notExecuted("sql failed: " + e.getMessage());
        }
    }

    private static RuleProbeResult notExecuted(String reason) {
        return RuleProbeResult.notExecuted(reason);
    }

    /**
     * 解析 "col=value" 形式的 when 子句，返回 {whereClause, argValue}。
     * 列名过 whitelist，值走占位符。
     * 允许 "col='value'" / "col=\"value\"" 双向引号包裹。
     */
    private String[] parseWhen(String raw, Set<String> columns) {
        int eq = raw.indexOf('=');
        if (eq <= 0 || eq + 1 >= raw.length()) return null;
        String colRaw = raw.substring(0, eq).trim();
        String val = raw.substring(eq + 1).trim();
        String col = resolveColumn(colRaw, columns);
        if (col == null) return null;
        if ((val.startsWith("'") && val.endsWith("'") && val.length() >= 2)
                || (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2)) {
            val = val.substring(1, val.length() - 1);
        }
        return new String[]{col + " = ?", val};
    }

    private String resolveColumn(String raw, Set<String> whitelist) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (whitelist.contains(s)) return s;
        String lower = s.toLowerCase(Locale.ROOT);
        if (whitelist.contains(lower)) return lower;
        String snake = camelToSnake(s);
        if (!snake.equals(s) && whitelist.contains(snake)) return snake;
        return null;
    }

    private static String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object e : l) out.add(e == null ? null : e.toString());
            return out;
        }
        return null;
    }

    private long positiveLong(Object o, long fallback) {
        if (o instanceof Number n) {
            long v = n.longValue();
            return v > 0 ? v : fallback;
        }
        return fallback;
    }

    private static String numAsStr(Object o) {
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
            return Double.toString(d);
        }
        if (o != null) return o.toString();
        return null;
    }

    private static String strOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> parseParams(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        return MAPPER.readValue(json, LinkedHashMap.class);
    }
}
