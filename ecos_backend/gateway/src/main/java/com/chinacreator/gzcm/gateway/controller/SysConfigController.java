package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.*;

/**
 * 系统配置 API — 全字段操作 sys_config 表。
 * 网关层 Controller，通过 JdbcTemplate 直连数据库。
 */
@RestController
@RequestMapping({"/api/v1/system/config", "/api/system/config"})
public class SysConfigController {

    private static final Logger log = LoggerFactory.getLogger(SysConfigController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ecos.edition:all}")
    private String currentEdition;

    public SysConfigController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── 已知被代码消费的配置参数映射 (config_key → consumed_by) ──
    private static final Map<String, String> KNOWN_CONSUMED = new LinkedHashMap<>();
    static {
        KNOWN_CONSUMED.put("session_timeout_minutes",   "AuthServiceImpl, TokenServiceImpl");
        KNOWN_CONSUMED.put("password_min_length",        "UserServiceImpl");
        KNOWN_CONSUMED.put("password_expire_days",       "AuthServiceImpl");
        KNOWN_CONSUMED.put("password_history_count",     "UserServiceImpl");
        KNOWN_CONSUMED.put("max_login_attempts",         "AuthServiceImpl");
        KNOWN_CONSUMED.put("lockout_duration_minutes",   "AuthServiceImpl");
        KNOWN_CONSUMED.put("max_concurrent_sessions",    "SessionService");
    }

    // ── GET /api/v1/system/config ──────────────────────────────

    /**
     * 返回所有配置（按 config_group 分组）。
     * 响应: { code:0, data:{ data:[...], total:N } }
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listAll(
            @RequestParam(defaultValue = "") String group,
            @RequestParam(defaultValue = "") String edition) {
        try {
            StringBuilder sqlBuilder = new StringBuilder(
                "SELECT id, config_group, config_key, config_value, config_type, " +
                "config_label, config_label_en, description, sort_order, " +
                "edition, config_options, impact_scope, default_value, " +
                "is_consumed, consumed_by, consumed_at, config_desc_zh " +
                "FROM sys_config WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (group != null && !group.isBlank()) {
                sqlBuilder.append(" AND config_group=?");
                params.add(group);
            }

            String effectiveEdition = !edition.isBlank() ? edition : currentEdition;
            if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
                sqlBuilder.append(" AND (edition='all' OR edition=?)");
                params.add(effectiveEdition);
            }

            sqlBuilder.append(" ORDER BY config_group, sort_order");

            List<Map<String, Object>> rows = jdbc.queryForList(
                    sqlBuilder.toString(), params.toArray());

            // Post-process: parse config_options JSON → List
            for (Map<String, Object> row : rows) {
                Object opts = row.get("config_options");
                if (opts instanceof String s && !s.isBlank()) {
                    try {
                        List<String> parsed = objectMapper.readValue(
                                s, new TypeReference<List<String>>() {});
                        row.put("config_options", parsed);
                    } catch (Exception e) {
                        row.put("config_options", List.of());
                    }
                } else {
                    row.put("config_options", List.of());
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", rows);
            result.put("total", rows.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询配置失败: {}", e.getMessage());
            return ApiResponse.internalError("查询配置失败: " + e.getMessage());
        }
    }

    // ── GET /api/v1/system/config/audit ────────────────────────

    /**
     * 配置消费审计：检查 is_consumed=FALSE 的项，并检测代码引用。
     * 返回: [{ key, label, value, consumed, consumedBy, consumedAt }]
     */
    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> audit() {
        try {
            String sql = "SELECT config_key, config_label, config_value, " +
                         "is_consumed, consumed_by, consumed_at " +
                         "FROM sys_config ORDER BY config_group, sort_order";
            List<Map<String, Object>> rows = jdbc.queryForList(sql);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String key = (String) row.get("config_key");
                Boolean isConsumed = (Boolean) row.get("is_consumed");
                String consumedBy = (String) row.get("consumed_by");
                Timestamp consumedAt = (Timestamp) row.get("consumed_at");

                // 代码级检测：KNOWN_CONSUMED 映射
                boolean codeConsumed = KNOWN_CONSUMED.containsKey(key);
                if (codeConsumed && (isConsumed == null || !isConsumed)) {
                    // 数据库标记未消费但代码中有引用
                    isConsumed = true;
                    consumedBy = KNOWN_CONSUMED.get(key);
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("label", row.get("config_label"));
                item.put("value", row.get("config_value"));
                item.put("consumed", isConsumed != null && isConsumed);
                item.put("consumedBy", consumedBy);
                item.put("consumedAt", consumedAt != null ? consumedAt.toLocalDateTime().toString() : null);

                // 模糊匹配检测
                if (Boolean.FALSE.equals(isConsumed) || isConsumed == null) {
                    String dotKey = key.replace('_', '.');
                    boolean fuzzyMatch = KNOWN_CONSUMED.values().stream()
                            .anyMatch(v -> v.toLowerCase().contains(dotKey.toLowerCase())
                                    || v.toLowerCase().contains(key.toLowerCase()));
                    if (fuzzyMatch) {
                        item.put("suggestion", "可能被消费但键名格式不匹配，请核实");
                    } else {
                        item.put("suggestion", "未检测到代码消费，建议确认是否为冗余配置");
                    }
                }

                result.add(item);
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("配置审计失败: {}", e.getMessage());
            return ApiResponse.internalError("配置审计失败: " + e.getMessage());
        }
    }

    // ── PUT /api/v1/system/config/{configKey} ──────────────────

    /**
     * 更新单个配置值。
     * 请求体: { "value": "new_value" }
     */
    @PutMapping("/{configKey}")
    public ApiResponse<Map<String, Object>> update(
            @PathVariable String configKey,
            @RequestBody Map<String, String> body) {
        try {
            // 兼容多种字段名: value > configValue > config_value
            String val = body.get("value");
            if (val == null) val = body.get("configValue");
            if (val == null) val = body.get("config_value");
            if (val == null) {
                return ApiResponse.badRequest("缺少 value/configValue 字段");
            }

            int rows = jdbc.update(
                    "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                    val, configKey);
            if (rows == 0) {
                return ApiResponse.notFound("配置项不存在: " + configKey);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("config_key", configKey);
            result.put("config_value", val);
            result.put("updated", true);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("更新配置失败: {}", e.getMessage());
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }
}
