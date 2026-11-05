package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.SysConfigDao;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 系统配置查询服务 — 从 SysConfigController 下沉的 JdbcTemplate 访问。
 * <p>铁律 §2.5：SQL 与数据访问下沉到 DAO（{@link SysConfigDao}），本类保留行→强类型记录映射。</p>
 *
 * <p><b>#147CA（DoD DI-2 第六批）</b>：行面由 {@code List<Map<String,Object>>} 改强类型 record
 * （{@link ConfigGroupRow}/{@link ConfigMetaRow}/{@link ConfigAuditRow}/{@link ConfigContractRow}），
 * 使 Controller 不再持有弱类型 Map；SQL 亦从 Controller 收回本类（原 metadata/audit/update 三条
 * 腿在 Controller 里拼 SQL，违「SQL 下沉 DAO」口径）。record 组件用 {@code @JsonProperty} 钉住
 * 旧列名（snake_case），保证 wire 键面逐字不变。</p>
 *
 * <p><b>#147CC（活体缺陷修复）</b>：根列表与元数据两条腿原 SELECT 的两个列
 * （{@code config_options}/{@code impact_scope}）在 DDL 单源零载体，dev PG 实查 13 列无此二者
 * ⇒ 活体恒 500。现只选实存列，两个键置 null 保留键位（禁补空数组冒充「已配置但为空」）。</p>
 */
@Service
public class SysConfigQueryService {


    private final SysConfigDao dao;

    public SysConfigQueryService(SysConfigDao dao) {
        this.dao = dao;
    }

    /** 数据源是否可用（Controller 依赖此判断以返回兜底空结果） */
    public boolean isAvailable() {
        return dao != null;
    }

    /**
     * 列表行（GET /api/v1/system/config 的 data 元素；键面＝旧原始列名）。
     *
     * <p>{@code configOptions}/{@code impactScope} 两键<b>恒为 null</b>：对应列在 DDL 单源不存在
     * （#147CC 活体缺陷的根因），保留键位只为不破坏既有键面。</p>
     */
    public record ConfigGroupRow(
            @JsonProperty("id") String id,
            @JsonProperty("config_group") String configGroup,
            @JsonProperty("config_key") String configKey,
            @JsonProperty("config_value") String configValue,
            @JsonProperty("config_type") String configType,
            @JsonProperty("config_label") String configLabel,
            @JsonProperty("config_label_en") String configLabelEn,
            @JsonProperty("description") String description,
            @JsonProperty("sort_order") Integer sortOrder,
            @JsonProperty("config_options") List<String> configOptions,
            @JsonProperty("impact_scope") String impactScope,
            @JsonProperty("edition") String edition) {
    }

    /** 元数据行（/metadata 的数据源）。 */
    public record ConfigMetaRow(String id, String configGroup, String configKey, String configValue,
                                String configType, String configLabel, String configLabelEn,
                                String description, String impactScope, String edition) {
    }

    /** 消费审计行（/audit 的数据源）。 */
    public record ConfigAuditRow(String configKey, String configGroup, String configLabel,
                                 String description) {
    }

    /** 契约面行（/group 的数据源；{@code updatedAt} 已转 ISO-8601，空值保持 null）。 */
    public record ConfigContractRow(String configKey, String configValue, String configType,
                                    String configGroup, String edition, String updatedAt) {
    }

    /**
     * 按分组/edition 列出配置项。
     *
     * <p><b>#147CC（活体缺陷修复）</b>：原 SELECT 含 {@code config_options}/{@code impact_scope}
     * 两列，而 dev PG 实查 {@code ecos_control.sys_config} 只有 13 列
     * （id/config_group/config_key/config_value/config_type/config_label/config_label_en/
     * description/sort_order/status/created_at/updated_at/edition）——两列在 DDL 单源里
     * <b>零 CREATE 载体</b>，导致 {@code GET /api/v1/system/config} 与 {@code /metadata}
     * 在活体上恒 500「查询配置失败」。现只选实存列；两个缺口键<b>保留键位但置 null</b>
     * （前端按 {@code || undefined}/{@code || ''} 消费，null 与「列不存在」同义，禁补空数组
     * 冒充「已配置但为空」）。</p>
     *
     * @param group   配置分组，可为空
     * @param edition 版本过滤，可为空
     * @param currentEdition 当前版本标识（来自 Maven profile）
     */
    public List<ConfigGroupRow> listByGroup(String group, String edition, String currentEdition) {
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT id, config_group, config_key, config_value, config_type, " +
            "config_label, config_label_en, description, sort_order, edition " +
            "FROM sys_config WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" AND config_group=?");
            params.add(group);
        }

        // edition 过滤: 如果指定了 edition，返回 edition='all' 或 edition=<指定的>
        String effectiveEdition = !edition.isBlank() ? edition : currentEdition;
        if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
            sqlBuilder.append(" AND (edition='all' OR edition=?)");
            params.add(effectiveEdition);
        }

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" ORDER BY sort_order");
        } else {
            sqlBuilder.append(" ORDER BY config_group, sort_order");
        }

        List<ConfigGroupRow> result = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sqlBuilder.toString(), params.toArray())) {
            result.add(new ConfigGroupRow(
                    asString(row.get("id")),
                    asString(row.get("config_group")),
                    asString(row.get("config_key")),
                    asString(row.get("config_value")),
                    asString(row.get("config_type")),
                    asString(row.get("config_label")),
                    asString(row.get("config_label_en")),
                    asString(row.get("description")),
                    asInt(row.get("sort_order")),
                    null,
                    null,
                    asString(row.get("edition"))));
        }
        return result;
    }

    /** 更新单个配置值；返回受影响行数 */
    public int updateConfigValue(String configKey, String val) {
        return dao.update(
                "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                val, configKey);
    }

    /** 查询配置元数据（带定义、影响范围） */
    public List<ConfigMetaRow> queryMetadata() {
        // #147CC：impact_scope 在 DDL 单源零载体（活体 13 列无此列），禁再选；键位保留为 null。
        String sql = "SELECT id, config_group, config_key, config_value, config_type, " +
                     "config_label, config_label_en, description, edition " +
                     "FROM sys_config ORDER BY config_group, sort_order";
        List<ConfigMetaRow> result = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sql)) {
            result.add(new ConfigMetaRow(
                    asString(row.get("id")),
                    asString(row.get("config_group")),
                    asString(row.get("config_key")),
                    asString(row.get("config_value")),
                    asString(row.get("config_type")),
                    asString(row.get("config_label")),
                    asString(row.get("config_label_en")),
                    asString(row.get("description")),
                    null,
                    asString(row.get("edition"))));
        }
        return result;
    }

    /**
     * 契约面（详设接口总表 {@code GET /api/v1/system/config/group} → getConfigByGroup）按分组取配置项。
     *
     * <p><b>列面口径</b>：只选 DDL 单源实存列（{@code V13__ecos_sys_config.sql} 建表 +
     * {@code V162} §5 ALTER 的 config_group/description/config_type/config_label/sort_order/edition/status）。
     * 本方法刻意<b>不</b>复用 {@link #listByGroup} 的列清单——那条腿选的
     * {@code config_options}/{@code impact_scope} 在 DDL 单源里零 CREATE 载体（2026-10-28 实测 live
     * {@code ecos_control.sys_config} 13 列亦无此二者），照抄会把新端点一起拖进幻影列失败。</p>
     *
     * <p><b>版本面</b>：{@code sys_config} 无 {@code version_no} 列（后端口径＝DB 只存在用版本、
     * 历史版本一律进 Git，《后端开发规范》§十一.1），故查询不合成版本号；需要时由调用方按
     * {@code updated_at} 如实呈现。</p>
     *
     * @param configGroup     配置分组（必给，调用方已裁决非空）
     * @param keys            可选配置键白名单；空/未给＝该分组全部在用项
     * @param edition         调用方指定的版本档；空＝用 {@code currentEdition}
     * @param currentEdition  当前部署档位（Maven profile）
     */
    public List<ConfigContractRow> listByConfigGroupForContract(String configGroup, List<String> keys,
                                                               String edition, String currentEdition) {
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT config_key, config_value, config_type, config_group, edition, updated_at " +
            "FROM sys_config WHERE status='active' AND config_group=?");
        List<Object> params = new ArrayList<>();
        params.add(configGroup);

        String effectiveEdition = (edition == null || edition.isBlank()) ? currentEdition : edition;
        if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
            sqlBuilder.append(" AND (edition='all' OR edition=?)");
            params.add(effectiveEdition);
        }

        if (keys != null && !keys.isEmpty()) {
            sqlBuilder.append(" AND config_key IN (")
                     .append(String.join(",", java.util.Collections.nCopies(keys.size(), "?")))
                     .append(")");
            params.addAll(keys);
        }

        sqlBuilder.append(" ORDER BY sort_order, config_key");
        List<ConfigContractRow> result = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sqlBuilder.toString(), params.toArray())) {
            result.add(new ConfigContractRow(
                    asString(row.get("config_key")),
                    asString(row.get("config_value")),
                    asString(row.get("config_type")),
                    asString(row.get("config_group")),
                    asString(row.get("edition")),
                    isoTime(row.get("updated_at"))));
        }
        return result;
    }

    /** 查询用于消费审计的配置项基本信息 */
    public List<ConfigAuditRow> queryForAudit() {
        String sql = "SELECT config_key, config_group, config_label, description FROM sys_config ORDER BY config_group, sort_order";
        List<ConfigAuditRow> result = new ArrayList<>();
        for (Map<String, Object> row : dao.queryForList(sql)) {
            result.add(new ConfigAuditRow(
                    asString(row.get("config_key")),
                    asString(row.get("config_group")),
                    asString(row.get("config_label")),
                    asString(row.get("description"))));
        }
        return result;
    }

    // ── 行值归一（守住旧 Controller 的取值口径） ──

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /** 时间列 → ISO-8601 文本；空值保持 null（不写 1970 之类的占位值）。 */
    private static String isoTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toString();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        return String.valueOf(value);
    }
}
