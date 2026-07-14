package com.chinacreator.gzcm.workspace.controller;

import java.util.*;
import java.util.regex.Pattern;

/**
 * ObjectQL 查询解析器 — 将 JSON 查询 DSL 转为参数化 SQL。
 *
 * <h3>语法</h3>
 * <pre>{@code
 * {
 *   "entity": "Supplier",               // 表名（必填，仅允许 [a-zA-Z_][a-zA-Z0-9_]*）
 *   "fields": ["id", "name"],            // 字段列表（可选，默认 SELECT *）
 *   "filter": {                          // 单条件过滤（可选）
 *     "field": "creditScore",
 *     "op": ">=",
 *     "value": 80
 *   },
 *   "filters": [                         // 多条件过滤（可选，与 filter 互斥）
 *     {"field": "creditScore", "op": ">=", "value": 80},
 *     {"field": "status", "op": "=", "value": "active", "logic": "OR"}
 *   ],
 *   "links": [{                          // 跨对象 Link 遍历（可选）
 *     "entity": "Supplier",
 *     "alias": "supplier",
 *     "on": {"from": "supplier_id", "to": "supplier.id"}
 *   }],
 *   "sort": "creditScore DESC",          // 排序（可选）
 *   "limit": 10,                         // 限制行数（可选，默认 100）
 *   "offset": 0                          // 偏移量（可选，默认 0）
 * }
 * }</pre>
 *
 * <h3>支持的操作符</h3>
 * {@code =, !=, >, <, >=, <=, LIKE, NOT LIKE, IN, NOT IN, IS NULL, IS NOT NULL}
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>entity/field 名正则校验，拒绝特殊字符</li>
 *   <li>值通过 PreparedStatement 参数化，防 SQL 注入</li>
 *   <li>操作符白名单校验</li>
 * </ul>
 */
public final class ObjectQLParser {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> ALLOWED_OPS = Set.of(
        "=", "!=", "<>", ">", "<", ">=", "<=",
        "LIKE", "NOT LIKE", "IN", "NOT IN",
        "IS NULL", "IS NOT NULL"
    );
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    /** 统一多态表名 */
    private static final String POLYMORPHIC_TABLE = "ecos_object_data";

    /**
     * 系统列 — 这些列存在于表结构中而非 JSONB object_data 内。
     * 不在白名单中的字段 → 从 object_data JSONB 中提取。
     */
    private static final Set<String> SYSTEM_COLS = Set.of(
        "id", "entity_code", "status", "created_at", "updated_at",
        "created_by", "classification", "sensitivity", "tenant_id"
    );

    private ObjectQLParser() {}

    /**
     * 从 JSON 查询中提前提取 links 引用的目标实体名列表，
     * 供 Controller 层做安全校验（查 ecos_ontology_entity 表）。
     *
     * @param json 查询 JSON 字符串
     * @return links 中各 link 的 entity 名称列表（可能为空）
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractLinkEntities(String json) {
        List<String> entities = new ArrayList<>();
        try {
            Map<String, Object> root = parseJsonFlat(json);
            Object linksObj = root.get("links");
            if (linksObj instanceof List) {
                for (Object item : (List<?>) linksObj) {
                    if (item instanceof Map) {
                        Map<String, Object> link = (Map<String, Object>) item;
                        // 格式 A: entity 字段
                        String linkEntity = string(link, "entity");
                        if (linkEntity != null && !linkEntity.isBlank()) {
                            entities.add(linkEntity);
                        }
                        // 格式 B: targetType 字段
                        String targetType = string(link, "targetType");
                        if (targetType != null && !targetType.isBlank() && !targetType.equals(linkEntity)) {
                            entities.add(targetType);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败时返回空列表，让后续 parse() 报详细错误
        }
        return entities;
    }

    /**
     * 解析 ObjectQL JSON 字符串，生成 {@link ParsedQuery}。
     *
     * @param json 查询 JSON
     * @return 含 SQL 模板 + 参数列表的解析结果
     * @throws IllegalArgumentException JSON 格式错误或校验失败
     */
    @SuppressWarnings("unchecked")
    public static ParsedQuery parse(String json) {
        Map<String, Object> root;
        try {
            root = parseJsonFlat(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 ObjectQL JSON: " + e.getMessage(), e);
        }

        // 1. entity — 必填
        String entity = string(root, "entity");
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("entity 为必填字段");
        }
        if (!IDENTIFIER.matcher(entity).matches()) {
            throw new IllegalArgumentException("非法的 entity 名称: " + entity);
        }

        // 1b. links — 跨对象 Link 遍历
        List<LinkDef> links = parseLinks(root, entity);

        // 2. fields（支持 table.field 点号语法）
        List<String> fields = null;
        Object fieldsObj = root.get("fields");
        if (fieldsObj instanceof List) {
            fields = new ArrayList<>();
            for (Object f : (List<?>) fieldsObj) {
                String fn = String.valueOf(f).trim();
                validateFieldWithDots(fn);
                fields.add(fn);
            }
        }

        // 3. where 条件
        List<Object> params = new ArrayList<>();
        String where = buildWhere(root, params);

        // 4. sort
        String sort = string(root, "sort");

        // 5. limit / offset
        int limit = Math.min(
            root.containsKey("limit") ? intVal(root, "limit") : DEFAULT_LIMIT,
            MAX_LIMIT
        );
        int offset = root.containsKey("offset") ? intVal(root, "offset") : 0;

        // 6. 组装 SQL — 使用统一多态表 ecos_object_data
        // ★ 修复: entity 不再是表名，而是 entity_code 过滤值
        List<Object> paramList = new ArrayList<>(params); // params 已由 buildWhere 填充
        params = new ArrayList<>(); // 重建 params，按 SQL 中 ? 出现顺序填充

        StringBuilder sql = new StringBuilder("SELECT ");
        if (fields != null && !fields.isEmpty()) {
            List<String> wrappedFields = new ArrayList<>();
            for (String fn : fields) {
                wrappedFields.add(wrapFieldForSelect(fn));
            }
            sql.append(String.join(", ", wrappedFields));
        } else {
            // ★ SELECT * → 显式列出系统列 + object_data（加表前缀避 JOIN 歧义）
            sql.append(POLYMORPHIC_TABLE).append(".id, ")
               .append(POLYMORPHIC_TABLE).append(".entity_code, ")
               .append(POLYMORPHIC_TABLE).append(".status, ")
               .append(POLYMORPHIC_TABLE).append(".created_at, ")
               .append(POLYMORPHIC_TABLE).append(".updated_at, ")
               .append(POLYMORPHIC_TABLE).append(".created_by, ")
               .append(POLYMORPHIC_TABLE).append(".classification, ")
               .append(POLYMORPHIC_TABLE).append(".sensitivity, ")
               .append(POLYMORPHIC_TABLE).append(".tenant_id, ")
               .append(POLYMORPHIC_TABLE).append(".object_data");
        }
        sql.append(" FROM ").append(POLYMORPHIC_TABLE);

        // 拼接 LEFT JOIN
        List<Object> linkParams = new ArrayList<>();
        for (LinkDef link : links) {
            if (link.viaLinksTable) {
                // 格式 B: 通过 ecos_object_links 中间表
                String linkAlias = "__l" + link.linkIndex;
                sql.append(" LEFT JOIN ecos_object_links ").append(linkAlias)
                   .append(" ON ").append(POLYMORPHIC_TABLE).append(".id = ").append(linkAlias).append(".source_id")
                   .append(" AND ").append(linkAlias).append(".relation_code = ?");
                linkParams.add(link.relationCode);
                sql.append(" LEFT JOIN ").append(POLYMORPHIC_TABLE)
                   .append(" AS ").append(link.alias)
                   .append(" ON ").append(linkAlias).append(".target_id = ").append(link.alias).append(".id");
            } else {
                // ★ 格式 A: 直接 LEFT JOIN — 改用 ecos_object_data + JSONB 字段访问
                sql.append(" LEFT JOIN ").append(POLYMORPHIC_TABLE)
                   .append(" AS ").append(link.alias)
                   .append(" ON (").append(POLYMORPHIC_TABLE).append(".object_data->>'").append(link.onFrom).append("')")
                   .append(" = (").append(link.alias).append(".object_data->>'").append(link.onToField).append("')")
                   .append(" AND ").append(link.alias).append(".entity_code = ?)");
                linkParams.add(link.entity);
            }
        }
        // 将 link 参数合并（LEFT JOIN 中的 ? 最先出现）
        params.addAll(linkParams);
        // ★ entity_code 参数在 WHERE 中最先出现
        params.add(entity);
        // where 参数追加（WHERE 中 entity_code 之后）
        params.addAll(paramList);

        // ★ WHERE: 先 entity_code 过滤，再业务条件
        sql.append(" WHERE ").append(POLYMORPHIC_TABLE).append(".entity_code = ?");
        if (!where.isEmpty()) {
            sql.append(" AND ").append(where);
        }
        if (sort != null && !sort.isBlank()) {
            sql.append(" ORDER BY ").append(wrapSortField(sort));
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }

        return new ParsedQuery(sql.toString(), params);
    }

    // ── Link 遍历支持 ──

    /**
     * Link 定义 — 描述一次 LEFT JOIN 遍历。
     * 支持两种格式:
     * <ul>
     *   <li><b>直接 JOIN</b>: entity + alias + on(from/to)</li>
     *   <li><b>关系表 JOIN</b>: relationCode + targetType + alias（通过 ecos_object_links 中间表）</li>
     * </ul>
     */
    private static class LinkDef {
        final String entity;       // 目标实体表名（直接 JOIN 格式）
        final String alias;        // JOIN 别名
        final String onFrom;       // 源实体外键字段（直接 JOIN 格式）
        final String onToField;    // 目标实体字段（直接 JOIN 格式）
        final boolean viaLinksTable; // true: 通过 ecos_object_links 遍历
        final String relationCode;   // 关系代码（viaLinksTable 格式）
        final int linkIndex;         // link 序号，用于生成唯一中间表别名

        LinkDef(String entity, String alias, String onFrom, String onToField,
                boolean viaLinksTable, String relationCode, int linkIndex) {
            this.entity = entity;
            this.alias = alias;
            this.onFrom = onFrom;
            this.onToField = onToField;
            this.viaLinksTable = viaLinksTable;
            this.relationCode = relationCode;
            this.linkIndex = linkIndex;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LinkDef> parseLinks(Map<String, Object> root, String sourceEntity) {
        List<LinkDef> result = new ArrayList<>();
        Object linksObj = root.get("links");
        if (!(linksObj instanceof List)) return result;

        int linkIdx = 0;
        for (Object item : (List<?>) linksObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> link = (Map<String, Object>) item;

            // 检测格式: relationCode + targetType → 关系表 JOIN; entity + on → 直接 JOIN
            String relationCode = string(link, "relationCode");
            String targetType = string(link, "targetType");

            if (relationCode != null && !relationCode.isBlank() && targetType != null && !targetType.isBlank()) {
                // ── 格式 B: relationCode + targetType + alias（通过 ecos_object_links） ──
                if (!IDENTIFIER.matcher(targetType).matches()) {
                    throw new IllegalArgumentException("非法的 link targetType: " + targetType);
                }
                if (!IDENTIFIER.matcher(relationCode).matches()) {
                    throw new IllegalArgumentException("非法的 link relationCode: " + relationCode);
                }

                String alias = string(link, "alias");
                if (alias == null || alias.isBlank()) {
                    throw new IllegalArgumentException("link 缺少 alias 字段");
                }
                if (!IDENTIFIER.matcher(alias).matches()) {
                    throw new IllegalArgumentException("非法的 link alias: " + alias);
                }

                result.add(new LinkDef(targetType, alias, null, null, true, relationCode, linkIdx));
            } else {
                // ── 格式 A: entity + alias + on(from/to)（直接 JOIN） ──
                String linkEntity = string(link, "entity");
                if (linkEntity == null || linkEntity.isBlank()) {
                    throw new IllegalArgumentException("link 缺少 entity 字段");
                }
                if (!IDENTIFIER.matcher(linkEntity).matches()) {
                    throw new IllegalArgumentException("非法的 link entity: " + linkEntity);
                }

                String alias = string(link, "alias");
                if (alias == null || alias.isBlank()) {
                    throw new IllegalArgumentException("link 缺少 alias 字段");
                }
                if (!IDENTIFIER.matcher(alias).matches()) {
                    throw new IllegalArgumentException("非法的 link alias: " + alias);
                }

                // link.on — 连接条件
                Object onObj = link.get("on");
                if (!(onObj instanceof Map)) {
                    throw new IllegalArgumentException("link 缺少 on 连接条件");
                }
                Map<String, Object> on = (Map<String, Object>) onObj;

                // on.from — 源实体外键字段
                String onFrom = string(on, "from");
                if (onFrom == null || onFrom.isBlank()) {
                    throw new IllegalArgumentException("link.on 缺少 from 字段");
                }
                if (!IDENTIFIER.matcher(onFrom).matches()) {
                    throw new IllegalArgumentException("非法的 link.on.from: " + onFrom);
                }

                // on.to — 格式 {alias}.{field}
                String onTo = string(on, "to");
                if (onTo == null || onTo.isBlank()) {
                    throw new IllegalArgumentException("link.on 缺少 to 字段");
                }
                int dotIdx = onTo.indexOf('.');
                if (dotIdx <= 0 || dotIdx >= onTo.length() - 1) {
                    throw new IllegalArgumentException("非法的 link.on.to 格式，期望 {alias}.{field}，实际: " + onTo);
                }
                String toAlias = onTo.substring(0, dotIdx);
                String toField = onTo.substring(dotIdx + 1);
                if (!IDENTIFIER.matcher(toAlias).matches()) {
                    throw new IllegalArgumentException("非法的 link.on.to alias: " + toAlias);
                }
                if (!IDENTIFIER.matcher(toField).matches()) {
                    throw new IllegalArgumentException("非法的 link.on.to field: " + toField);
                }
                if (!toAlias.equals(alias)) {
                    throw new IllegalArgumentException(
                        "link.on.to 中的 alias (" + toAlias + ") 与 link.alias (" + alias + ") 不一致");
                }

                result.add(new LinkDef(linkEntity, alias, onFrom, toField, false, null, linkIdx));
            }
            linkIdx++;
        }
        return result;
    }

    /**
     * 校验带点号的字段名（如 "Contract.id" 或 "supplier.name"）。
     * 按点号拆分后对每段分别用 IDENTIFIER 正则校验。
     */
    private static void validateFieldWithDots(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        // 特殊处理 *（SELECT * 场景）
        if ("*".equals(field)) return;
        int dotIdx = field.indexOf('.');
        if (dotIdx < 0) {
            // 无点号：直接校验
            if (!IDENTIFIER.matcher(field).matches()) {
                throw new IllegalArgumentException("非法的字段名: " + field);
            }
        } else {
            // 有点号：拆分后逐段校验
            String[] parts = field.split("\\.");
            for (String part : parts) {
                if (part.isEmpty()) {
                    throw new IllegalArgumentException("非法的字段名（空段）: " + field);
                }
                if (!IDENTIFIER.matcher(part).matches()) {
                    throw new IllegalArgumentException("非法的字段名: " + field + " (段: " + part + ")");
                }
            }
        }
    }

    // ── JSONB 字段包装辅助 ──

    /** 判断是否为系统列（存在于表结构中，非 JSONB object_data 内） */
    private static boolean isSystemCol(String field) {
        // 带别名前缀的处理：alias.field → 只看 field 部分
        String basename = field;
        int dotIdx = field.indexOf('.');
        if (dotIdx > 0) basename = field.substring(dotIdx + 1);
        return SYSTEM_COLS.contains(basename.toLowerCase());
    }

    /** SELECT 中的字段包装：非系统列 → object_data->>'field' AS field */
    private static String wrapFieldForSelect(String field) {
        if ("*".equals(field)) return POLYMORPHIC_TABLE + ".id, " + POLYMORPHIC_TABLE + ".entity_code, "
            + POLYMORPHIC_TABLE + ".status, " + POLYMORPHIC_TABLE + ".object_data, "
            + POLYMORPHIC_TABLE + ".created_at, " + POLYMORPHIC_TABLE + ".updated_at";
        if (isSystemCol(field)) {
            // ★ 系统列加表前缀避免 JOIN 歧义（如 LEFT JOIN ecos_object_data AS ord 导致 id 冲突）
            int dotIdx = field.indexOf('.');
            if (dotIdx > 0) return field; // 已有前缀
            return POLYMORPHIC_TABLE + "." + field;
        }
        // 带表前缀的字段 → object_data->>'field' AS field（去掉前缀）
        int dotIdx = field.indexOf('.');
        if (dotIdx > 0) {
            String basename = field.substring(dotIdx + 1);
            if (SYSTEM_COLS.contains(basename.toLowerCase())) return field;
            return POLYMORPHIC_TABLE + ".object_data->>'" + basename + "' AS " + basename;
        }
        return POLYMORPHIC_TABLE + ".object_data->>'" + field + "' AS " + field;
    }

    /** WHERE 中的字段包装：非系统列 → object_data->>'field' */
    private static String wrapFieldForWhere(String field) {
        if (isSystemCol(field)) return field;
        int dotIdx = field.indexOf('.');
        if (dotIdx > 0) {
            String basename = field.substring(dotIdx + 1);
            if (SYSTEM_COLS.contains(basename.toLowerCase())) return field;
            return POLYMORPHIC_TABLE + ".object_data->>'" + basename + "'";
        }
        return POLYMORPHIC_TABLE + ".object_data->>'" + field + "'";
    }

    /** ORDER BY 字段包装：非系统列 → object_data->>'field' */
    private static String wrapSortField(String sortExpr) {
        String trimmed = sortExpr.trim();
        // 拆分 field + ASC/DESC
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) return sortExpr;
        String field = parts[0];
        String direction = parts.length > 1 ? " " + parts[1] : "";
        if (isSystemCol(field)) return sortExpr;
        return POLYMORPHIC_TABLE + ".object_data->>'" + field + "'" + direction;
    }

    // ── where 条件构建 ──

    @SuppressWarnings("unchecked")
    private static String buildWhere(Map<String, Object> root, List<Object> params) {
        // filters 数组优先
        Object filtersObj = root.get("filters");
        if (filtersObj instanceof List && !((List<?>) filtersObj).isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) filtersObj) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> cond = (Map<String, Object>) item;
                String logic = string(cond, "logic");
                if (sb.length() > 0) {
                    sb.append(" OR".equalsIgnoreCase(logic) ? " OR " : " AND ");
                }
                sb.append(buildCondition(cond, params));
            }
            return sb.toString();
        }

        // 单 filter
        Object filterObj = root.get("filter");
        if (filterObj instanceof Map && !((Map<?, ?>) filterObj).isEmpty()) {
            return buildCondition((Map<String, Object>) filterObj, params);
        }

        return "";
    }

    @SuppressWarnings("unchecked")
    private static String buildCondition(Map<String, Object> cond, List<Object> params) {
        String field = string(cond, "field");
        String op = string(cond, "op");
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("filter 缺少 field");
        }
        validateFieldWithDots(field);
        // ★ 非系统列 → 包装为 object_data->>'field'
        field = wrapFieldForWhere(field);
        if (op == null) op = "=";
        String opUpper = op.toUpperCase();
        if (!ALLOWED_OPS.contains(opUpper) && !ALLOWED_OPS.contains(op)) {
            throw new IllegalArgumentException("不支持的操作符: " + op);
        }

        // 无值操作符
        if ("IS NULL".equals(opUpper) || "IS NOT NULL".equals(opUpper)) {
            return field + " " + opUpper;
        }

        // IN / NOT IN
        if ("IN".equals(opUpper) || "NOT IN".equals(opUpper)) {
            Object val = cond.get("value");
            // ★ 数值 IN 列表：JSONB ->> 需 cast 为 numeric
            String inField = field;
            boolean hasNumericValue = false;
            if (val instanceof List) {
                for (Object v : (List<?>) val) {
                    if (v instanceof Number) { hasNumericValue = true; break; }
                }
            } else if (val instanceof Number) {
                hasNumericValue = true;
            }
            if (hasNumericValue && field.contains("object_data->>")) {
                inField = "(" + inField + ")::numeric";
            }
            if (val instanceof List) {
                List<String> placeholders = new ArrayList<>();
                for (Object v : (List<?>) val) {
                    placeholders.add("?");
                    params.add(v);
                }
                return inField + " " + opUpper + " (" + String.join(", ", placeholders) + ")";
            }
            // 单个值也当 IN 处理
            params.add(val);
            return inField + " " + opUpper + " (?)";
        }

        // 普通操作符
        Object value = cond.get("value");
        params.add(value);
        // ★ 数值比较：JSONB ->> 返回 text，需显式 cast 为 numeric 才能与数值参数比较
        String finalField = field;
        if (value instanceof Number && field.contains("object_data->>")) {
            finalField = "(" + field + ")::numeric";
        }
        return finalField + " " + op + " ?";
    }

    // ── JSON 解析（无依赖，仅支持一层嵌套） ──

    /**
     * 简单 JSON 对象解析器。仅支持一层嵌套的 {@code {key:value,...}}，
     * 其中 value 可以是 String/Number/Boolean/null/Array/Object。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonFlat(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("JSON 必须以 { 开头 } 结尾");
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) return result;

        List<String> pairs = splitJson(body);
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = unquote(pair.substring(0, colon).trim());
            String rawValue = pair.substring(colon + 1).trim();
            result.put(key, parseValue(rawValue));
        }
        return result;
    }

    private static Object parseValue(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if ("null".equals(raw)) return null;
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        if (raw.startsWith("\"")) return unquote(raw);
        if (raw.startsWith("[")) return parseArray(raw);
        if (raw.startsWith("{")) return parseJsonFlat(raw);
        // number
        try {
            if (raw.contains(".")) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw; // fallback: treat as string
        }
    }

    private static List<Object> parseArray(String raw) {
        String inner = raw.substring(1, raw.length() - 1).trim();
        if (inner.isEmpty()) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (String item : splitCsv(inner)) {
            result.add(parseValue(item.trim()));
        }
        return result;
    }

    private static List<String> splitJson(String body) {
        // 按逗号分割，但忽略 {}, [], "" 内部的逗号
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == '}' || c == ']') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static List<String> splitCsv(String s) {
        // 按逗号分割，忽略 {}, [], \"\" 内部的逗号
        List<String> result = new ArrayList<>();
        boolean inString = false;
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                current.append(c);
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = false;
            } else if (c == '"') {
                inString = true;
                current.append(c);
            } else if (c == '{' || c == '[') {
                depth++;
                current.append(c);
            } else if (c == '}' || c == ']') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return t;
    }

    private static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── 解析结果 ──

    public static class ParsedQuery {
        private final String sql;
        private final List<Object> params;

        ParsedQuery(String sql, List<Object> params) {
            this.sql = sql;
            this.params = Collections.unmodifiableList(params);
        }

        public String getSql() { return sql; }
        public List<Object> getParams() { return params; }
        public Object[] getParamsArray() { return params.toArray(); }

        @Override
        public String toString() {
            return "ParsedQuery{sql='" + sql + "', params=" + params + "}";
        }
    }

    /**
     * 在已解析的 SQL 中注入额外的 WHERE 条件（用于 ABAC 行过滤）。
     * 在 ORDER BY / LIMIT / OFFSET 之前插入条件。
     *
     * @param pq          已解析的查询
     * @param extraWhere  额外的 WHERE 条件片段（如 "department_id = 'dept1'"）
     * @return 新的 ParsedQuery（SQL 已修改，params 不变）
     */
    public static ParsedQuery appendWhereCondition(ParsedQuery pq, String extraWhere) {
        if (extraWhere == null || extraWhere.isBlank()) return pq;
        String sql = pq.getSql();
        String extra = "(" + extraWhere + ")";

        // 寻找 ORDER BY / LIMIT / OFFSET 的位置
        String upper = sql.toUpperCase();
        int insertPos = sql.length();
        for (String keyword : new String[]{" ORDER BY ", " LIMIT ", " OFFSET "}) {
            int pos = upper.indexOf(keyword);
            if (pos > 0 && pos < insertPos) {
                insertPos = pos;
            }
        }

        String newSql;
        if (upper.contains(" WHERE ")) {
            // 已有 WHERE → AND 追加
            newSql = sql.substring(0, insertPos) + " AND " + extra + " " + sql.substring(insertPos).trim();
        } else {
            // 无 WHERE → WHERE 插入
            newSql = sql.substring(0, insertPos) + " WHERE " + extra + " " + sql.substring(insertPos).trim();
        }
        return new ParsedQuery(newSql.trim(), pq.getParams());
    }
}
