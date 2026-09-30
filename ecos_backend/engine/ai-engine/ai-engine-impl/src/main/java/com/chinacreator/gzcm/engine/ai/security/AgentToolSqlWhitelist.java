package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * H8-T4（PMO-74 L3）— Agent 工具 SQL 的<b>表/列白名单</b>事实源。
 *
 * <p>铁律 §2.4-4/§2.4-7：Agent 工具执行的 SQL 只允许访问被显式授权的表与列；
 * 白名单<b>不可</b>由客户端 / LLM 工具入参自定义，只来自两个受管控位置：</p>
 * <ol>
 *   <li><b>sysman 配置字典门面</b>（后端开发规范 §十 配置/字典单表分组口径）：
 *       {@code sys_config} 表 {@code config_key = 'ecos.agent.tool.sql.whitelist'}、
 *       {@code config_group = 'ai-engine'}，值为 JSON 数组：
 *       <pre>[{"table":"ecos_dw.sales_order","columns":["id","customer","amount"]},
 *        {"table":"public.sys_dict","columns":"*"}]</pre>
 *       {@code columns} 为显式列名数组，或字面量 {@code "*"}（管理员显式授权全列）。
 *       经 {@link SysConfigService} 门面读取（Caffeine 缓存，refreshCache 后生效），
 *       配置写路径本身在 sysman 侧受控并有审计。</li>
 *   <li><b>同模块内置显式注册表</b>（{@link #BUILTIN}）：默认<b>为空</b> = 不授权任何表，
 *       fail-closed；管理员必须通过配置字典显式登记后 SQL 工具才可访问任何表。</li>
 * </ol>
 *
 * <p>合并规则：内置 ∪ 配置；同表以配置条目为准（配置可收窄/扩列）。
 * 配置条目非法（表名/列名不是合法标识符）→ 跳过并 log.warn（不影响其余条目，
 * 跳过只会更严格，属 fail-closed 方向）。</p>
 *
 * <p>门面不可用（独立 ai-engine-boot 进程未扫 sysman 包）→ 按内置注册表判定
 * （即默认拒绝一切表），不降级放行。</p>
 */
@Component
public class AgentToolSqlWhitelist {

    private static final Logger log = LoggerFactory.getLogger(AgentToolSqlWhitelist.class);

    /** sysman 配置字典门面的配置键（config_group='ai-engine'） */
    public static final String CONFIG_KEY = "ecos.agent.tool.sql.whitelist";

    /** 合法表名（可带 schema 限定，一段或两段，禁止空格/分号/注释等注入形态） */
    public static final Pattern TABLE_IDENT = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_$]*(\\.[a-zA-Z_][a-zA-Z0-9_$]*)?$");

    /** 合法列名 */
    public static final Pattern COLUMN_IDENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_$]*$");

    /** 显式全列授权标记 */
    public static final String ALL_COLUMNS = "*";

    /** 内置显式注册表：默认空 = fail-closed，未配置白名单前不放行任何表 */
    private static final Map<String, Set<String>> BUILTIN = Collections.emptyMap();

    private final ObjectProvider<SysConfigService> sysConfigProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 解析快照缓存：配置原文 → 白名单（SysConfigService 缓存值变化时重解析） */
    private volatile String snapshotRaw;
    private volatile Map<String, Set<String>> snapshot = BUILTIN;

    public AgentToolSqlWhitelist(ObjectProvider<SysConfigService> sysConfigProvider) {
        this.sysConfigProvider = sysConfigProvider;
    }

    /** 生效白名单：表名（小写）→ 允许列集合（含 {@link #ALL_COLUMNS} 表示全列） */
    public Map<String, Set<String>> effectiveWhitelist() {
        String raw = readConfigRaw();
        if (!Objects.equals(raw, snapshotRaw)) {
            synchronized (this) {
                if (!Objects.equals(raw, snapshotRaw)) {
                    snapshot = merge(raw);
                    snapshotRaw = raw;
                }
            }
        }
        return snapshot;
    }

    /** 表是否被显式授权 */
    public boolean isTableAllowed(String table) {
        return table != null && effectiveWhitelist().containsKey(normalizeTable(table));
    }

    /**
     * 返回表的授权列集合；未授权返回 null。
     * 含 {@link #ALL_COLUMNS} 元素 = 管理员显式授权全列。
     */
    public Set<String> allowedColumns(String table) {
        if (table == null) return null;
        return effectiveWhitelist().get(normalizeTable(table));
    }

    /** 指定表是否允许该列（全列授权时恒真） */
    public boolean isColumnAllowed(String table, String column) {
        Set<String> cols = allowedColumns(table);
        if (cols == null || column == null) return false;
        String c = column.toLowerCase(Locale.ROOT);
        return cols.contains(ALL_COLUMNS) || cols.contains(c);
    }

    /**
     * 生成路径专用：表必须已授权且列合法，否则抛带 [H8-T4] 前缀的拒绝异常。
     *
     * @return 授权列集合（保序）
     */
    public Set<String> requireAllowedColumns(String table) {
        if (!TABLE_IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("[H8-T4] 非法表名（非标识符形态）: " + table);
        }
        Set<String> cols = allowedColumns(table);
        if (cols == null) {
            throw new IllegalArgumentException("[H8-T4] 表未列入 Agent 工具 SQL 白名单（sys_config."
                    + CONFIG_KEY + " / config_group=ai-engine）: " + table);
        }
        if (cols.isEmpty()) {
            throw new IllegalArgumentException("[H8-T4] 表白名单条目未声明任何列，拒绝: " + table);
        }
        return cols;
    }

    /** 校验 schema 参数名（将拼入 WHERE 子句）：必须为合法标识符且在该表授权列内 */
    public void requireAllowedColumn(String table, String column) {
        if (!COLUMN_IDENT.matcher(column).matches()) {
            throw new IllegalArgumentException("[H8-T4] 非法列/参数名（非标识符形态）: " + column);
        }
        if (!isColumnAllowed(table, column)) {
            throw new IllegalArgumentException("[H8-T4] 列未获白名单授权: " + table + "." + column);
        }
    }

    public static String normalizeTable(String table) {
        return table == null ? null : table.toLowerCase(Locale.ROOT);
    }

    // ── internals ─────────────────────────────────────────────────────────

    private String readConfigRaw() {
        try {
            SysConfigService cfg = sysConfigProvider.getIfAvailable();
            if (cfg == null) {
                log.debug("H8-T4 SysConfigService 门面不可用，白名单按内置注册表判定（默认拒绝）");
                return null;
            }
            return cfg.getString(CONFIG_KEY);
        } catch (Exception e) {
            log.warn("H8-T4 读取白名单配置失败，按内置注册表判定（默认拒绝）: {}", e.getMessage(), e);
            return null;
        }
    }

    private Map<String, Set<String>> merge(String raw) {
        Map<String, Set<String>> merged = new LinkedHashMap<>(BUILTIN);
        if (raw != null && !raw.isBlank()) {
            try {
                List<?> entries = objectMapper.readValue(raw, List.class);
                for (Object o : entries) {
                    if (!(o instanceof Map<?, ?> entry)) {
                        log.warn("H8-T4 白名单条目非对象形态，跳过: {}", o);
                        continue;
                    }
                    String table = str(entry.get("table"));
                    if (table == null || !TABLE_IDENT.matcher(table).matches()) {
                        log.warn("H8-T4 白名单条目表名非法，跳过: {}", table);
                        continue;
                    }
                    Set<String> columns = parseColumns(entry.get("columns"), table);
                    if (columns == null || columns.isEmpty()) {
                        log.warn("H8-T4 白名单条目未声明合法列，跳过: {}", table);
                        continue;
                    }
                    merged.put(normalizeTable(table), columns);
                }
            } catch (Exception e) {
                // 配置整体不可解析 → 只剩内置（空）= 拒绝一切表，fail-closed
                log.warn("H8-T4 白名单配置不可解析，退回内置注册表（默认拒绝一切表）: raw={}",
                        truncate(raw), e);
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseColumns(Object columnsObj, String table) {
        Set<String> cols = new LinkedHashSet<>();
        if (columnsObj instanceof String s) {
            if (ALL_COLUMNS.equals(s.trim())) {
                cols.add(ALL_COLUMNS);
                return cols;
            }
            return null;
        }
        if (columnsObj instanceof List<?> list) {
            for (Object c : list) {
                String col = str(c);
                if (col == null) continue;
                if (ALL_COLUMNS.equals(col.trim())) {
                    cols.add(ALL_COLUMNS);
                    continue;
                }
                if (!COLUMN_IDENT.matcher(col).matches()) {
                    log.warn("H8-T4 白名单列名非法，跳过该列: {}.{}", table, col);
                    continue;
                }
                cols.add(col.toLowerCase(Locale.ROOT));
            }
            return cols;
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) + "...[truncated]" : s);
    }
}
