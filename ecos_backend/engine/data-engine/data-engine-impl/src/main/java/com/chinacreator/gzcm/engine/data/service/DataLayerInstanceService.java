package com.chinacreator.gzcm.engine.data.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.dto.DataLayerRowsVO;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;

/**
 * DW 层实例行读取服务（PMO-B3-1 T2 / 方案 §5.3「数据→知识 实例行读取」）。
 *
 * <p>职责边界（数据湖存储分层规范 §四）：本服务归**数据工作台**，是 DW 层表（{@code layer=CURATED}）
 * 唯一的对外读取方；知识/本体工作台经 REST 只读消费，不得直读近源层或外部源系统。
 *
 * <p>安全与性能约束（架构铁律 §六 SQL 规范 + 方案风险 R3）：
 * <ul>
 *   <li>禁 {@code SELECT *}：列清单取自 PG 目录（{@code information_schema.columns}）并逐列做标识符白名单校验；</li>
 *   <li>schema / 表名来自已登记资源 {@code td_data_resource.source_path}，且再次过标识符白名单（防 SQL 注入）；</li>
 *   <li>强制限量：rows 默认 1000 / 上限 5000，sample 默认 100 / 上限 1000，防慢 SQL；</li>
 *   <li>增量读取按「水位列」（优先 update_time/updated_at，回退主键）严格 {@code >} 水位，回带 nextWatermark。</li>
 * </ul>
 */
@Service
public class DataLayerInstanceService {

    private static final Logger log = LoggerFactory.getLogger(DataLayerInstanceService.class);

    /** rows 端点默认返回行数（方案 R3：强制限量防慢 SQL） */
    private static final int DEFAULT_ROWS_LIMIT = 1000;

    /** rows 端点返回行数上限 */
    private static final int MAX_ROWS_LIMIT = 5000;

    /** sample 端点默认抽样行数（dry-run 预览用） */
    private static final int DEFAULT_SAMPLE_LIMIT = 100;

    /** sample 端点抽样行数上限 */
    private static final int MAX_SAMPLE_LIMIT = 1000;

    /** 标识符白名单：字母开头，仅字母/数字/下划线（schema、表名、列名，防 SQL 注入） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    /** 水位列候选（按优先级：更新时间列优先，回退主键） */
    private static final List<String> WATERMARK_COLUMN_CANDIDATES = List.of("update_time", "updated_at");

    /** 允许参与水位比较的列类型白名单（来自 PG 目录，非用户输入） */
    private static final Set<String> WATERMARK_TYPE_WHITELIST = Set.of(
            "character varying", "character", "text", "smallint", "integer", "bigint",
            "numeric", "real", "double precision", "date",
            "timestamp without time zone", "timestamp with time zone");

    private final JdbcTemplate jdbc;

    private final DataResourceRepository resourceRepository;

    public DataLayerInstanceService(JdbcTemplate jdbc, DataResourceRepository resourceRepository) {
        this.jdbc = jdbc;
        this.resourceRepository = resourceRepository;
    }

    /**
     * 增量读取 DW 层资源实例行（水位线之后）。
     *
     * @param layer     分层名（须为合法 {@link DataLayer} 枚举）
     * @param resourceId 数据资源 ID
     * @param watermark  上次读取水位（空 = 从头读）
     * @param limit      返回行数上限（空 = 默认 1000，硬上限 5000）
     * @return 实例行结果（含 nextWatermark）
     */
    public DataLayerRowsVO readRows(String layer, String resourceId, String watermark, Integer limit) {
        DataLayer dataLayer = requireLayer(layer);
        DataResource resource = requireResource(dataLayer, resourceId);
        int effectiveLimit = resolveLimit(limit, DEFAULT_ROWS_LIMIT, MAX_ROWS_LIMIT);
        TableTarget target = resolveTable(resource);
        List<ColumnDef> columns = loadColumns(target);
        ColumnDef watermarkColumn = resolveWatermarkColumn(target, columns);

        List<Object> params = new ArrayList<>(2);
        StringBuilder sql = new StringBuilder("SELECT ").append(quoteColumnList(columns))
                .append(" FROM ").append(target.qualified());
        if (watermark != null && !watermark.isBlank()) {
            sql.append(" WHERE ").append(quote(watermarkColumn.name()))
                    .append(" > CAST(? AS ").append(watermarkColumn.dataType()).append(")");
            params.add(watermark.trim());
        }
        sql.append(" ORDER BY ").append(quote(watermarkColumn.name())).append(" ASC LIMIT ?");
        params.add(effectiveLimit);

        List<Map<String, Object>> rows = queryRows(sql.toString(), params.toArray());
        String nextWatermark = extractLastWatermark(rows, watermarkColumn.name());

        DataLayerRowsVO vo = new DataLayerRowsVO();
        vo.setLayer(dataLayer.name());
        vo.setResourceId(resource.getResourceId());
        vo.setRows(rows);
        vo.setCount(rows.size());
        vo.setNextWatermark(nextWatermark);
        vo.setHasMore(rows.size() == effectiveLimit);
        log.info("DW 层增量读取: layer={} resourceId={} table={} watermarkCol={} watermark={} limit={} rows={}",
                dataLayer.name(), resourceId, target.qualified(), watermarkColumn.name(),
                watermark, effectiveLimit, rows.size());
        return vo;
    }

    /**
     * 抽样读取 DW 层资源实例行（dry-run 预览用，不做水位过滤）。
     *
     * @param layer      分层名（须为合法 {@link DataLayer} 枚举）
     * @param resourceId 数据资源 ID
     * @param limit      抽样行数上限（空 = 默认 100，硬上限 1000）
     * @return 抽样结果（不返回 nextWatermark）
     */
    public DataLayerRowsVO sampleRows(String layer, String resourceId, Integer limit) {
        DataLayer dataLayer = requireLayer(layer);
        DataResource resource = requireResource(dataLayer, resourceId);
        int effectiveLimit = resolveLimit(limit, DEFAULT_SAMPLE_LIMIT, MAX_SAMPLE_LIMIT);
        TableTarget target = resolveTable(resource);
        List<ColumnDef> columns = loadColumns(target);

        StringBuilder sql = new StringBuilder("SELECT ").append(quoteColumnList(columns))
                .append(" FROM ").append(target.qualified());
        // 有水位列时按水位列稳定排序，保证抽样结果可复现
        ColumnDef orderColumn = tryResolveWatermarkColumn(target, columns);
        if (orderColumn != null) {
            sql.append(" ORDER BY ").append(quote(orderColumn.name())).append(" ASC");
        }
        sql.append(" LIMIT ?");

        List<Map<String, Object>> rows = queryRows(sql.toString(), new Object[]{effectiveLimit});

        DataLayerRowsVO vo = new DataLayerRowsVO();
        vo.setLayer(dataLayer.name());
        vo.setResourceId(resource.getResourceId());
        vo.setRows(rows);
        vo.setCount(rows.size());
        vo.setNextWatermark(null);
        vo.setHasMore(rows.size() == effectiveLimit);
        log.info("DW 层抽样读取: layer={} resourceId={} table={} limit={} rows={}",
                dataLayer.name(), resourceId, target.qualified(), effectiveLimit, rows.size());
        return vo;
    }

    // ═══════════════ 入参校验 ═══════════════

    /** 校验 layer 为合法 {@link DataLayer} 枚举（非法值抛 {@link ValidationException}）。 */
    private DataLayer requireLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            throw new ValidationException("layer", "分层名不能为空");
        }
        try {
            return DataLayer.valueOf(layer.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layer", "非法分层名: " + layer);
        }
    }

    /** 校验资源存在且分层匹配（不存在抛 {@link NotFoundException}，分层不符抛 {@link ValidationException}）。 */
    private DataResource requireResource(DataLayer dataLayer, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new ValidationException("resourceId", "数据资源 ID 不能为空");
        }
        DataResource resource = resourceRepository.findById(resourceId.trim());
        if (resource == null) {
            throw new NotFoundException("数据资源不存在: id=" + resourceId);
        }
        if (!dataLayer.name().equalsIgnoreCase(resource.getLayer())) {
            throw new ValidationException("resourceId",
                    "资源 " + resourceId + " 分层为 " + resource.getLayer() + "，与请求分层 " + dataLayer.name() + " 不符");
        }
        return resource;
    }

    /** limit 归一化：空取默认值，越界收敛到 [1, max]。 */
    private int resolveLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null) {
            return defaultValue;
        }
        if (limit < 1) {
            throw new ValidationException("limit", "limit 必须为正整数");
        }
        return Math.min(limit, maxValue);
    }

    // ═══════════════ 表 / 列元数据解析 ═══════════════

    /** 解析资源物理表定位（source_path = schema.table，缺省 schema 回退 public）。 */
    private TableTarget resolveTable(DataResource resource) {
        String sourcePath = resource.getSourcePath();
        String schema = "public";
        String table;
        if (sourcePath != null && sourcePath.contains(".")) {
            int idx = sourcePath.indexOf('.');
            schema = sourcePath.substring(0, idx);
            table = sourcePath.substring(idx + 1);
        } else if (sourcePath != null && !sourcePath.isBlank()) {
            table = sourcePath;
        } else {
            table = resource.getResourceName();
        }
        requireIdentifier(schema, "schema");
        requireIdentifier(table, "table");
        return new TableTarget(schema, table);
    }

    /** 标识符白名单校验（非法抛 {@link ValidationException}）。 */
    private void requireIdentifier(String identifier, String field) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ValidationException(field, "非法标识符: " + identifier);
        }
    }

    /** 从 PG 目录读取表列定义（列名/类型来自 DB，非用户输入）。 */
    private List<ColumnDef> loadColumns(TableTarget target) {
        List<ColumnDef> columns = jdbc.query(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> new ColumnDef(rs.getString("column_name"), rs.getString("data_type")),
                target.schema(), target.table());
        if (columns.isEmpty()) {
            throw new NotFoundException("DW 层表不存在或未采集列定义: " + target.qualified());
        }
        for (ColumnDef column : columns) {
            requireIdentifier(column.name(), "column");
        }
        return columns;
    }

    /** 解析水位列（优先 update_time/updated_at，回退主键；均无则抛 {@link ValidationException}）。 */
    private ColumnDef resolveWatermarkColumn(TableTarget target, List<ColumnDef> columns) {
        ColumnDef column = tryResolveWatermarkColumn(target, columns);
        if (column == null) {
            throw new ValidationException("watermark",
                    "目标表 " + target.qualified() + " 无 update_time/updated_at 列且无主键，不支持增量读取");
        }
        return column;
    }

    /** 尝试解析水位列；表既无更新时间列也无主键时返回 null。 */
    private ColumnDef tryResolveWatermarkColumn(TableTarget target, List<ColumnDef> columns) {
        for (String candidate : WATERMARK_COLUMN_CANDIDATES) {
            for (ColumnDef column : columns) {
                if (candidate.equalsIgnoreCase(column.name())) {
                    requireWatermarkType(column);
                    return column;
                }
            }
        }
        String primaryKey = findPrimaryKeyColumn(target);
        if (primaryKey == null) {
            return null;
        }
        for (ColumnDef column : columns) {
            if (primaryKey.equalsIgnoreCase(column.name())) {
                requireWatermarkType(column);
                return column;
            }
        }
        return null;
    }

    /** 水位列类型须在白名单内（否则无法安全 CAST 比较，抛 {@link ValidationException}）。 */
    private void requireWatermarkType(ColumnDef column) {
        if (!WATERMARK_TYPE_WHITELIST.contains(column.dataType().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("watermark",
                    "水位列 " + column.name() + " 类型 " + column.dataType() + " 不支持增量比较");
        }
    }

    /** 查询表主键列名（无主键返回 null）。 */
    private String findPrimaryKeyColumn(TableTarget target) {
        List<String> keys = jdbc.queryForList(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0] "
                        + "WHERE n.nspname = ? AND c.relname = ? AND i.indisprimary "
                        + "ORDER BY i.indexrelid LIMIT 1",
                String.class, target.schema(), target.table());
        return keys.isEmpty() ? null : keys.get(0);
    }

    // ═══════════════ SQL 组装与执行 ═══════════════

    /** 列清单加双引号（列名已过标识符白名单）。 */
    private String quoteColumnList(List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        for (ColumnDef column : columns) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(quote(column.name()));
        }
        return sb.toString();
    }

    /** 标识符加双引号。 */
    private String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    /** 执行查询并把行转成小写列名的有序 Map（保持 SELECT 列序）。 */
    private List<Map<String, Object>> queryRows(String sql, Object[] params) {
        try {
            List<Map<String, Object>> raw = jdbc.queryForList(sql, params);
            List<Map<String, Object>> rows = new ArrayList<>(raw.size());
            for (Map<String, Object> row : raw) {
                Map<String, Object> ordered = new LinkedHashMap<>();
                row.forEach((k, v) -> ordered.put(k, v));
                rows.add(ordered);
            }
            return rows;
        } catch (DataAccessException e) {
            log.error("DW 层实例读取失败: sql={}", sql, e);
            throw new ValidationException("resource", "DW 层实例读取失败: " + e.getMostSpecificCause().getMessage());
        }
    }

    /** 取最后一行水位值作为下次读取水位（无行返回 null）。 */
    private String extractLastWatermark(List<Map<String, Object>> rows, String watermarkColumn) {
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> last = rows.get(rows.size() - 1);
        Object value = null;
        for (Map.Entry<String, Object> entry : last.entrySet()) {
            if (watermarkColumn.equalsIgnoreCase(entry.getKey())) {
                value = entry.getValue();
                break;
            }
        }
        return value == null ? null : String.valueOf(value);
    }

    /** 物理表定位（schema + table + 限定名）。 */
    private record TableTarget(String schema, String table) {

        /** 返回 schema.table 限定名（均已过标识符白名单）。 */
        private String qualified() {
            return "\"" + schema + "\".\"" + table + "\"";
        }
    }

    /** 列定义（名称 + PG 目录 data_type）。 */
    private record ColumnDef(String name, String dataType) {
    }
}
