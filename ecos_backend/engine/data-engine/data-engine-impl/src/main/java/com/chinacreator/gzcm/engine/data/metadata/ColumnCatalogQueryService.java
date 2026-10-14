package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.dao.ColumnCatalogDao;
import com.chinacreator.gzcm.engine.data.model.MetadataColumnsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * P0-3（2026-10-14 差距补齐）跨引擎列元数据供数服务 ——
 * 把 {@code tableRef}（{@code schema.table} 限定名或裸表名）解析成该表的<b>真实列清单</b>，
 * 供本体域 V3 血缘门禁（{@code DatNetLineageGuard}）经 REST 只读消费。
 *
 * <p>设计锚点：详设《从零实施版》§3073 行登记
 * {@code GET /api/v1/datanet/metadata/columns | listColumnsByTableRef |
 * 跨引擎供数：本体域 V3 血缘校验取列 | L1 | tableRef 限定名 | 列集合（超时上限 3s）| ECOS-DATA-041}。</p>
 *
 * <p><b>三条硬约束</b>（不遵守就会把只读元数据端点做成信息泄露面）：
 * <ol>
 *   <li><b>schema 封闭清单</b>：只允许数据库访问规范 ST07 的 8 个 schema（5 引擎 + public/ecos_control）
 *       加业务域 {@code ecos_dw}。清单外的 schema（如 {@code pg_catalog}）一律 400 拒绝，
 *       禁全库枚举系统目录；</li>
 *   <li><b>标识符白名单</b>：schema/表名逐段过 {@code [A-Za-z_][A-Za-z0-9_]{0,62}}（与
 *       {@code DataLayerInstanceService.IDENTIFIER_PATTERN} 同规则），拒绝任何注入形态；</li>
 *   <li><b>表不存在不报依赖故障</b>：返回 {@code exists=false} + 空列集（HTTP 200），
 *       让调用侧 V3 把它折算成「列缺失」的门禁否决（ECOS-ONTO-030），
 *       而不是 ECOS-DATA-041/503「datanet 不可用」——后者会让运维误判为链路故障。</li>
 * </ol>
 * </p>
 *
 * <p><b>裸表名择一规则</b>：同名表可能存在于多 schema，按
 * {@link #SCHEMA_PRECEDENCE}（业务域 ecos_dw 优先，其后 5 引擎，最后 public）取第一命中，
 * 并在响应 {@code resolvedTable} 里回带实际限定名，杜绝「静默命中另一 schema 同表」。</p>
 */
@Service
public class ColumnCatalogQueryService {

    private static final Logger log = LoggerFactory.getLogger(ColumnCatalogQueryService.class);

    /** 标识符白名单：字母/下划线开头，仅字母数字下划线，长度 ≤63（PG 标识符上限） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    /** 允许的 schema 及其择一优先级（ST07 封闭枚举 8 值 + 业务域 ecos_dw） */
    static final List<String> SCHEMA_PRECEDENCE = List.of(
            "ecos_dw", "ecos_data", "ecos_ontology", "ecos_knowledge",
            "ecos_ai", "ecos_cognitive", "ecos_control", "public");

    private final ColumnCatalogDao columnCatalogDao;

    public ColumnCatalogQueryService(ColumnCatalogDao columnCatalogDao) {
        this.columnCatalogDao = columnCatalogDao;
    }

    /**
     * 按 tableRef 取真实列清单。
     *
     * @param tableRef {@code schema.table} 限定名或裸表名
     * @return 出参 VO（表不存在 = {@code exists=false} + 空列集）
     * @throws ValidationException tableRef 为空/段数非法/标识符非法/schema 不在封闭清单
     */
    public MetadataColumnsVO query(String tableRef) {
        if (tableRef == null || tableRef.isBlank()) {
            throw new ValidationException("tableRef 不能为空");
        }
        String trimmed = tableRef.trim();
        String[] parts = trimmed.split("\\.");
        if (parts.length > 2) {
            throw new ValidationException("tableRef 形态非法（仅接受 table 或 schema.table）: " + trimmed);
        }
        if (parts.length == 2) {
            String schema = requireIdentifier(parts[0], trimmed);
            String table = requireIdentifier(parts[1], trimmed);
            requireAllowedSchema(schema, trimmed);
            return resolveQualified(tableRef, schema, table);
        }
        String table = requireIdentifier(parts[0], trimmed);
        return resolveBare(tableRef, table);
    }

    /** 限定名：直接按 (schema, table) 取列；空列集即表不存在（PG 无零列表）。 */
    private MetadataColumnsVO resolveQualified(String tableRef, String schema, String table) {
        for (String candidate : caseCandidates(table)) {
            List<String> columns = columnCatalogDao.columnNames(schema, candidate);
            if (!columns.isEmpty()) {
                return new MetadataColumnsVO(tableRef, schema + "." + candidate, true, columns);
            }
        }
        log.info("metadata/columns: 表未命中 tableRef={} target={}.{}", tableRef, schema, table);
        return new MetadataColumnsVO(tableRef, null, false, List.of());
    }

    /** 裸表名：先定位所在 schema（按优先级择一），再取列。 */
    private MetadataColumnsVO resolveBare(String tableRef, String table) {
        for (String candidate : caseCandidates(table)) {
            List<String> heldSchemas = columnCatalogDao.schemasHolding(candidate);
            String chosen = pickByPrecedence(heldSchemas);
            if (chosen == null) {
                continue;
            }
            List<String> columns = columnCatalogDao.columnNames(chosen, candidate);
            if (columns.isEmpty()) {
                // 目录竞态（表刚被删）：存在性以列清单为准，按未命中处理
                continue;
            }
            return new MetadataColumnsVO(tableRef, chosen + "." + candidate, true, columns);
        }
        log.info("metadata/columns: 裸表名未命中任何允许 schema tableRef={}", tableRef);
        return new MetadataColumnsVO(tableRef, null, false, List.of());
    }

    /** 在命中的 schema 里按 {@link #SCHEMA_PRECEDENCE} 取第一；清单外 schema 一律忽略。 */
    private static String pickByPrecedence(List<String> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return null;
        }
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String schema : schemas) {
            if (schema == null) {
                continue;
            }
            int rank = SCHEMA_PRECEDENCE.indexOf(schema.toLowerCase(Locale.ROOT));
            if (rank >= 0 && rank < bestRank) {
                bestRank = rank;
                best = schema.toLowerCase(Locale.ROOT);
            }
        }
        return best;
    }

    /** 原样 + 小写两次尝试（PG 未加引号的 DDL 落库即小写，调用方常传大写形态）。 */
    private static List<String> caseCandidates(String table) {
        String lower = table.toLowerCase(Locale.ROOT);
        return lower.equals(table) ? List.of(table) : List.of(table, lower);
    }

    private static String requireIdentifier(String value, String tableRef) {
        String candidate = value.trim();
        if (!IDENTIFIER_PATTERN.matcher(candidate).matches()) {
            throw new ValidationException("tableRef 含非法标识符（仅允许字母/数字/下划线，字母或下划线开头）: " + tableRef);
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    private static void requireAllowedSchema(String schema, String tableRef) {
        if (!SCHEMA_PRECEDENCE.contains(schema)) {
            throw new ValidationException(
                    "tableRef 的 schema 不在允许清单（ST07 封闭枚举 + ecos_dw）: " + tableRef);
        }
    }
}
