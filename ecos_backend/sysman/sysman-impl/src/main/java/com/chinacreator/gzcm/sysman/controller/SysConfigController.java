package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统配置 API — 读写 sys_config 表。
 * 前端 SystemConfigManager 通过此接口按分组管理配置项。
 */
@RestController
@RequestMapping({"/api/v1/system/config", "/api/system/config"})
public class SysConfigController {

    private static final Logger log = LoggerFactory.getLogger(SysConfigController.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /** 当前版本标识，来自 Maven profile (standard / enterprise / ultimate) */
    @Value("${ecos.edition:all}")
    private String currentEdition;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── 已知被代码消费的配置参数映射 (config_key → consumed_by) ──
    private static final Map<String, String> KNOWN_CONSUMED = new LinkedHashMap<>();
    static {
        KNOWN_CONSUMED.put("session_timeout_minutes",   "AuthServiceImpl, TokenServiceImpl (session.timeout_minutes)");
        KNOWN_CONSUMED.put("password_min_length",        "UserServiceImpl (password.min_length)");
        KNOWN_CONSUMED.put("password_expire_days",       "AuthServiceImpl (password.expire_days)");
        KNOWN_CONSUMED.put("password_history_count",     "UserServiceImpl");
        KNOWN_CONSUMED.put("max_login_attempts",         "AuthServiceImpl");
        KNOWN_CONSUMED.put("lockout_duration_minutes",   "AuthServiceImpl");
        KNOWN_CONSUMED.put("max_concurrent_sessions",    "SessionService");
    }

    /** GET /api/v1/system/config?group=xxx&edition=xxx → 列出配置；无group则返回全部 */
    @GetMapping
    public ApiResponse<Map<String, Object>> listByGroup(
            @RequestParam(defaultValue = "") String group,
            @RequestParam(defaultValue = "") String edition) {
        try {
            if (jdbcTemplate == null) {
                return ApiResponse.success(Map.of("data", List.of(), "total", 0));
            }

            // 构建 SQL: 包含 config_options, impact_scope, edition 字段
            StringBuilder sqlBuilder = new StringBuilder(
                "SELECT id, config_group, config_key, config_value, config_type, " +
                "config_label, config_label_en, description, sort_order, " +
                "config_options, impact_scope, edition " +
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

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    sqlBuilder.toString(), params.toArray());

            // Post-process: parse config_options JSONB → List
            for (Map<String, Object> row : rows) {
                Object opts = row.get("config_options");
                if (opts instanceof String && !((String) opts).isBlank()) {
                    try {
                        List<String> parsed = objectMapper.readValue(
                                (String) opts, new TypeReference<List<String>>() {});
                        row.put("config_options", parsed);
                    } catch (Exception e) {
                        row.put("config_options", List.of());
                    }
                } else if (opts == null) {
                    row.put("config_options", List.of());
                }
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("data", rows);
            r.put("total", rows.size());
            return ApiResponse.success(r);
        } catch (Exception e) {
            log.error("查询配置失败: {}", e.getMessage());
            return ApiResponse.internalError("查询配置失败: " + e.getMessage());
        }
    }

    /** PUT /api/v1/system/config/{configKey} → 更新单个配置值 */
    @PutMapping("/{configKey}")
    public ApiResponse<?> update(@PathVariable String configKey, @RequestBody Map<String, String> body) {
        try {
            if (jdbcTemplate == null) return ApiResponse.internalError("数据源不可用");

            // 兼容多种 body 字段名: value > configValue > config_value
            String val = body.get("value");
            if (val == null) val = body.get("configValue");
            if (val == null) val = body.get("config_value");
            if (val == null) return ApiResponse.badRequest("缺少 value/configValue");

            int rows = jdbcTemplate.update(
                    "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                    val, configKey);
            if (rows == 0) return ApiResponse.notFound("配置项不存在: " + configKey);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("config_key", configKey);
            r.put("config_value", val);
            r.put("updated", true);
            return ApiResponse.success(r);
        } catch (Exception e) {
            log.error("更新配置失败: {}", e.getMessage());
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ── 元数据端点 ──────────────────────────────────────────────

    /**
     * GET /api/v1/system/config/metadata → 返回带定义、影响范围的丰富信息
     */
    @GetMapping("/metadata")
    public ApiResponse<List<Map<String, Object>>> metadata() {
        try {
            if (jdbcTemplate == null) {
                return ApiResponse.success(List.of());
            }

            String sql = "SELECT id, config_group, config_key, config_value, config_type, " +
                         "config_label, config_label_en, description, impact_scope, edition " +
                         "FROM sys_config ORDER BY config_group, sort_order";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                String key = (String) row.get("config_key");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("config_key", key);
                item.put("config_group", row.get("config_group"));
                item.put("config_label", row.get("config_label"));
                item.put("config_type", row.get("config_type"));
                item.put("current_value", row.get("config_value"));
                item.put("definition", row.get("description"));
                item.put("impact", row.get("impact_scope"));
                item.put("edition", row.get("edition"));
                item.put("is_consumed", KNOWN_CONSUMED.containsKey(key));
                if (KNOWN_CONSUMED.containsKey(key)) {
                    item.put("consumed_by", KNOWN_CONSUMED.get(key));
                }
                result.add(item);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询配置元数据失败: {}", e.getMessage());
            return ApiResponse.internalError("查询配置元数据失败: " + e.getMessage());
        }
    }

    // ── 消费审计端点 ────────────────────────────────────────────

    /**
     * GET /api/v1/system/config/audit → 检查每个参数是否被代码消费
     * 返回: [{config_key, is_consumed, consumed_by, suggestion}]
     */
    @GetMapping("/audit")
    public ApiResponse<Map<String, Object>> audit() {
        try {
            if (jdbcTemplate == null) {
                return ApiResponse.success(Map.of("total", 0, "consumed", 0, "unconsumed", 0, "items", List.of()));
            }

            String sql = "SELECT config_key, config_group, config_label, description FROM sys_config ORDER BY config_group, sort_order";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                String key = (String) row.get("config_key");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("config_key", key);
                item.put("config_group", row.get("config_group"));
                item.put("config_label", row.get("config_label"));

                boolean consumed = KNOWN_CONSUMED.containsKey(key);
                item.put("is_consumed", consumed);

                if (consumed) {
                    item.put("consumed_by", KNOWN_CONSUMED.get(key));
                    item.put("suggestion", null);
                } else {
                    // 尝试模糊匹配：检查是否有代码使用类似key（带点号变体）
                    String dotKey = key.replace('_', '.');
                    boolean fuzzyMatch = KNOWN_CONSUMED.values().stream()
                            .anyMatch(v -> v.contains(dotKey) || v.contains(key));
                    item.put("consumed_by", null);
                    if (fuzzyMatch) {
                        item.put("suggestion", "可能被消费但键名格式不匹配，请核实");
                    } else {
                        item.put("suggestion", "未检测到代码消费，建议确认是否为冗余配置");
                    }
                }

                result.add(item);
            }

            // 统计
            long consumedCount = result.stream().filter(m -> Boolean.TRUE.equals(m.get("is_consumed"))).count();
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("total", result.size());
            wrapper.put("consumed", consumedCount);
            wrapper.put("unconsumed", result.size() - consumedCount);
            wrapper.put("items", result);

            return ApiResponse.success(wrapper);
        } catch (Exception e) {
            log.error("配置审计失败: {}", e.getMessage());
            return ApiResponse.internalError("配置审计失败: " + e.getMessage());
        }
    }
}
