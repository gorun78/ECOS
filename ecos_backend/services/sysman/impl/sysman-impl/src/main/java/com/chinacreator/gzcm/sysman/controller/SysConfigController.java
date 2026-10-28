package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AuthErrorCode;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import com.chinacreator.gzcm.sysman.service.SysConfigQueryService;

/**
 * 系统配置 API — 读写 sys_config 表。
 * 前端 SystemConfigManager 通过此接口按分组管理配置项。
 */
@RestController
@RequestMapping("/api/v1/system/config")
public class SysConfigController {

    private static final Logger log = LoggerFactory.getLogger(SysConfigController.class);

    private final ObjectProvider<SysConfigQueryService> queryProvider;

    /** 当前版本标识，来自 Maven profile (standard / enterprise / ultimate) */
    @Value("${ecos.edition:all}")
    private String currentEdition;

    public SysConfigController(ObjectProvider<SysConfigQueryService> queryProvider) {
        this.queryProvider = queryProvider;
    }

    /**
     * #142/B4-3e-53（D-B4-83）：可选装配缺席时的统一载体＝显式不可用（503＋{@code ECOS-SYS-503}）。
     * <p>原口径把「查询服务没装配」折成 200＋空集（＝谎报无数据）或 500（＝谎报本服务故障），
     * 两侧都违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」。真实空结果不受影响。</p>
     */
    private static DataBridgeException queryUnavailable() {
        return ConfigController.unavailability("系统配置查询服务 SysConfigQueryService");
    }

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

    /**
     * GET /api/v1/system/config/group?configGroup=xxx&subsystem=xxx&keys=a,b&edition=xxx
     * → 引擎经 sysman 门面按分组读配置与字典（契约见《ECOS详细设计说明书-从零实施版》接口总表
     * {@code getConfigByGroup} 行：入参 {@code configGroup、subsystem、keys[]}，
     * 出参 {@code {items[{key,value,valueType,versionNo}]}}，面级 {@code L0 需服务凭证}）。
     *
     * <p><b>additive</b>：既有根 GET（分页/分组列表）、{@code PUT /{configKey}}、{@code /metadata}、
     * {@code /audit} 四腿一字未动，本端点只是补齐契约子路径 {@code /group}。</p>
     *
     * <p><b>四处如实口径（禁造假值，逐条对应实现）</b>：</p>
     * <ol>
     *   <li><b>服务凭证</b>：契约「L0，需服务凭证」＝位于场校验
     *       （缺头或非 {@code serviceId.sig} 形态 ⇒ 403 {@code ECOS-AUTH-011}）。HMAC 验签腿属
     *       {@code HeaderAuthInterceptor}/{@code EcosServiceCredentialVerifier}（H10 服务间凭证面，
     *       现平台默认 {@code TRUST_DEV}），本类<b>不</b>另立第二套密钥校验。契约错误列写的
     *       {@code ECOS-AUTH-410} 在 {@link AuthErrorCode} 属主族（001~006/010/011/020）无此条目，
     *       按「零新增码表条目」复用 011（分态分歧已在差距台账附录C 第 6 条在册）。</li>
     *   <li><b>versionNo</b>：{@code sys_config} 无版本列（DB 只存在用版本，历史版本进 Git，
     *       《后端开发规范》§十一.1）⇒ 该键在位但恒 {@code null}，并附
     *       {@code versionNoApplicable=false} 自证；真实时间面由 {@code updatedAt} 交付，不合成版本号。</li>
     *   <li><b>subsystem</b>：无独立列；《后端开发规范》§十.1 规定 {@code sys_dict.subsystem} 与
     *       {@code config_group} <b>同名对齐</b>。故 {@code subsystem} 只作分组入参别名，
     *       回显时与 {@code configGroup} 同值并标 {@code subsystemSource=config_group_alignment}；
     *       入参二者同时给且不一致 ⇒ 400（禁猜一个执行）。</li>
     *   <li><b>keys[]</b>：真实过滤（{@code config_key IN (...)} 占位符绑定）；请求键中未命中的
     *       逐枚落 {@code missingKeys[]}，<b>不</b>补空值、不静默丢弃。</li>
     * </ol>
     *
     * <p>准入：方法级 {@link MinimumClearance}{@code (0)} 显式声明（方法注解优先于
     * {@code /api/v1/system/} 路径默认 L3）；认证仍由 SecurityConfig 统一要求（monolith 阶段
     * {@code anyRequest().authenticated()}），即本端点＝已认证主体可达、不再叠加准入等级裁决。</p>
     */
    @GetMapping("/group")
    @MinimumClearance(level = 0)
    public ApiResponse<Map<String, Object>> getConfigByGroup(
            @RequestParam(name = "configGroup", defaultValue = "") String configGroup,
            @RequestParam(name = "subsystem", defaultValue = "") String subsystem,
            @RequestParam(name = "keys", required = false) List<String> keys,
            @RequestParam(name = "edition", defaultValue = "") String edition,
            @RequestHeader(name = "X-ECOS-SERVICE", required = false) String serviceCredential) {
        requireServiceCredential(serviceCredential);
        String group = resolveConfigGroup(configGroup, subsystem);

        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，按分组读取配置不可用（fail-closed）: group={}", group);
            throw queryUnavailable();
        }
        try {
            List<String> requestedKeys = normalizedKeys(keys);
            List<Map<String, Object>> rows = sysConfigQueryService
                    .listByConfigGroupForContract(group, requestedKeys, edition, currentEdition);

            List<Map<String, Object>> items = new ArrayList<>();
            Set<String> found = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Object keyObj = row.get("config_key");
                String key = keyObj == null ? null : String.valueOf(keyObj);
                if (key != null) {
                    found.add(key);
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("value", row.get("config_value"));
                item.put("valueType", row.get("config_type"));
                item.put("edition", row.get("edition"));
                // 版本面诚实位：sys_config 无 version_no 列（DB 只存在用版本、历史进 Git），
                // 禁按 updated_at 反推一个「版本号」冒充契约键。
                item.put("versionNo", null);
                item.put("versionNoApplicable", false);
                item.put("updatedAt", isoTime(row.get("updated_at")));
                items.add(item);
            }

            List<String> missingKeys = new ArrayList<>();
            for (String k : requestedKeys) {
                if (!found.contains(k)) {
                    missingKeys.add(k);
                }
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("configGroup", group);
            r.put("subsystem", group);
            r.put("subsystemSource", "config_group_alignment");
            r.put("requestedKeys", requestedKeys);
            r.put("items", items);
            r.put("missingKeys", missingKeys);
            r.put("itemCount", items.size());
            return ApiResponse.success(r);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("按分组读取配置失败: group={}", group, e);
            throw new DataBridgeException(500, 500,
                    SystemErrorCode.ECOS_SYS_500 + ": 按分组读取配置失败");
        }
    }

    /** 契约面服务凭证在场校验（分态口径见 {@link #getConfigByGroup} 第 1 条）。 */
    private static void requireServiceCredential(String serviceCredential) {
        if (serviceCredential == null || serviceCredential.isBlank()) {
            throw new DataBridgeException(403, 403, AuthErrorCode.ECOS_AUTH_011
                    + ": 缺少服务凭证（X-ECOS-SERVICE），该端点按契约只对服务身份开放");
        }
        String value = serviceCredential.trim();
        int dot = value.indexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            throw new DataBridgeException(403, 403, AuthErrorCode.ECOS_AUTH_011
                    + ": 服务凭证格式非法（需 serviceId.sig）");
        }
    }

    /** 分组解析：configGroup 优先、subsystem 作同名别名；二者并存且互异即 400（禁择一执行）。 */
    private static String resolveConfigGroup(String configGroup, String subsystem) {
        String cg = configGroup == null ? "" : configGroup.trim();
        String ss = subsystem == null ? "" : subsystem.trim();
        if (!cg.isEmpty() && !ss.isEmpty() && !cg.equals(ss)) {
            throw new DataBridgeException(400, 400, SystemErrorCode.ECOS_SYS_400
                    + ": configGroup 与 subsystem 取值不一致（§十.1 二者同名对齐）");
        }
        String group = !cg.isEmpty() ? cg : ss;
        if (group.isEmpty()) {
            throw new DataBridgeException(400, 400,
                    SystemErrorCode.ECOS_SYS_400 + ": 缺少 configGroup（或同名的 subsystem）");
        }
        return group;
    }

    /** keys 归一：去空白、去重、保序（同时容忍 Spring 已按逗号拆好的多值形态）。 */
    private static List<String> normalizedKeys(List<String> keys) {
        List<String> out = new ArrayList<>();
        if (keys == null) {
            return out;
        }
        for (String raw : keys) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String k = part.trim();
                if (!k.isEmpty() && !out.contains(k)) {
                    out.add(k);
                }
            }
        }
        return out;
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

    /** GET /api/v1/system/config?group=xxx&edition=xxx → 列出配置；无group则返回全部 */
    @GetMapping
    public ApiResponse<Map<String, Object>> listByGroup(
            @RequestParam(defaultValue = "") String group,
            @RequestParam(defaultValue = "") String edition) {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，系统配置列表不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
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

            List<Map<String, Object>> rows = sysConfigQueryService.queryForList(
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
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询配置失败");
        }
    }

    /** PUT /api/v1/system/config/{configKey} → 更新单个配置值 */
    @PutMapping("/{configKey}")
    public ApiResponse<?> update(@PathVariable String configKey, @RequestBody Map<String, String> body) {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置项 {} 更新不可用（fail-closed）", configKey);
            throw queryUnavailable();
        }
        try {
            // 兼容多种 body 字段名: value > configValue > config_value
            String val = body.get("value");
            if (val == null) val = body.get("configValue");
            if (val == null) val = body.get("config_value");
            if (val == null) return ApiResponse.badRequest("缺少 value/configValue");

            int rows = sysConfigQueryService.update(
                    "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                    val, configKey);
            if (rows == 0) return ApiResponse.notFound("配置项不存在: " + configKey);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("config_key", configKey);
            r.put("config_value", val);
            r.put("updated", true);
            return ApiResponse.success(r);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return ApiResponse.internalError("更新失败");
        }
    }

    // ── 元数据端点 ──────────────────────────────────────────────

    /**
     * GET /api/v1/system/config/metadata → 返回带定义、影响范围的丰富信息
     */
    @GetMapping("/metadata")
    public ApiResponse<List<Map<String, Object>>> metadata() {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置元数据不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
            String sql = "SELECT id, config_group, config_key, config_value, config_type, " +
                         "config_label, config_label_en, description, impact_scope, edition " +
                         "FROM sys_config ORDER BY config_group, sort_order";

            List<Map<String, Object>> rows = sysConfigQueryService.queryForList(sql);
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
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询配置元数据失败", e);
            return ApiResponse.internalError("查询配置元数据失败");
        }
    }

    // ── 消费审计端点 ────────────────────────────────────────────

    /**
     * GET /api/v1/system/config/audit → 检查每个参数是否被代码消费
     * 返回: [{config_key, is_consumed, consumed_by, suggestion}]
     */
    @GetMapping("/audit")
    public ApiResponse<Map<String, Object>> audit() {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置消费审计不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
            String sql = "SELECT config_key, config_group, config_label, description FROM sys_config ORDER BY config_group, sort_order";
            List<Map<String, Object>> rows = sysConfigQueryService.queryForList(sql);
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
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("配置审计失败", e);
            return ApiResponse.internalError("配置审计失败");
        }
    }
}
