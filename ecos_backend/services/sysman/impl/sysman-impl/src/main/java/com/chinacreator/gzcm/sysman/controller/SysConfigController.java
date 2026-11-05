package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AuthErrorCode;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import com.chinacreator.gzcm.sysman.security.RequirePermission;
import com.chinacreator.gzcm.sysman.service.SysConfigQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统配置 API — 读写 sys_config 表。
 * 前端 SystemConfigManager 通过此接口按分组管理配置项。
 *
 * <p><b>#147CA（DoD DI-2 第六批）</b>：本类不再持有弱类型 Map——行面由属主
 * {@link SysConfigQueryService} 以 record 返回，SQL 亦全部收回服务（原 metadata/audit/update
 * 三条腿在本类拼 SQL，违「SQL 下沉 DAO」口径）。**wire 键面逐字不变**，含两处易错点：
 * ① {@code GET /} 的 data 元素仍是「原始列名」形态（id/config_group/…）；② {@code /metadata}
 * 的 {@code consumed_by} 仅在被消费时出现（属性级 NON_NULL，不得连累其它 null 键）。</p>
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
     * <p><b>四处如实口径（禁造假值，逐条对应实现）</b>：服务凭证＝在场校验（缺头或非
     * {@code serviceId.sig} ⇒ 403 {@code ECOS-AUTH-011}；HMAC 验签归
     * {@code HeaderAuthInterceptor}/{@code EcosServiceCredentialVerifier}）；{@code versionNo}
     * 恒 null 并附 {@code versionNoApplicable=false}（{@code sys_config} 无版本列，历史进 Git）；
     * {@code subsystem} 只作 {@code config_group} 同名别名并标 {@code subsystemSource}；
     * 未命中键逐枚落 {@code missingKeys[]}，不补空值。</p>
     *
     * <p>准入：方法级 {@link MinimumClearance}{@code (0)} 显式声明（方法注解优先于
     * {@code /api/v1/system/} 路径默认 L3）；认证仍由 SecurityConfig 统一要求。</p>
     */
    @GetMapping("/group")
    @MinimumClearance(level = 0)
    public ApiResponse<ConfigGroupResultVO> getConfigByGroup(
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
            List<SysConfigQueryService.ConfigContractRow> rows = sysConfigQueryService
                    .listByConfigGroupForContract(group, requestedKeys, edition, currentEdition);

            List<ConfigGroupItemVO> items = new ArrayList<>();
            Set<String> found = new LinkedHashSet<>();
            for (SysConfigQueryService.ConfigContractRow row : rows) {
                if (row.configKey() != null) {
                    found.add(row.configKey());
                }
                items.add(new ConfigGroupItemVO(row.configKey(), row.configValue(), row.configType(),
                        row.edition(),
                        // 版本面诚实位：sys_config 无 version_no 列，禁按 updated_at 反推版本号冒充契约键
                        null, false, row.updatedAt()));
            }

            List<String> missingKeys = new ArrayList<>();
            for (String k : requestedKeys) {
                if (!found.contains(k)) {
                    missingKeys.add(k);
                }
            }

            return ApiResponse.success(new ConfigGroupResultVO(group, group,
                    "config_group_alignment", requestedKeys, items, missingKeys, items.size()));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("按分组读取配置失败: group={}", group, e);
            throw new DataBridgeException(500, 500,
                    SystemErrorCode.ECOS_SYS_500 + ": 按分组读取配置失败");
        }
    }

    /** 契约面服务凭证在场校验（分态口径见 {@link #getConfigByGroup}）。 */
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

    /** GET /api/v1/system/config?group=xxx&edition=xxx → 列出配置；无group则返回全部 */
    @GetMapping
    public ApiResponse<ConfigListVO> listByGroup(
            @RequestParam(defaultValue = "") String group,
            @RequestParam(defaultValue = "") String edition) {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，系统配置列表不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
            List<SysConfigQueryService.ConfigGroupRow> rows =
                    sysConfigQueryService.listByGroup(group, edition, currentEdition);
            return ApiResponse.success(new ConfigListVO(rows, rows.size()));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询配置失败", e);
            return ApiResponse.internalError("查询配置失败");
        }
    }

    /** PUT /api/v1/system/config/{configKey} → 更新单个配置值 */
    @RequirePermission({"config:WRITE"})
    @PutMapping("/{configKey}")
    public ApiResponse<ConfigUpdateResultVO> update(@PathVariable String configKey,
                                                    @RequestBody ConfigValueRequest body) {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置项 {} 更新不可用（fail-closed）", configKey);
            throw queryUnavailable();
        }
        try {
            // 兼容多种 body 字段名: value > configValue > config_value
            String val = body.getValue();
            if (val == null) val = body.getConfigValue();
            if (val == null) val = body.getConfig_value();
            if (val == null) return ApiResponse.badRequest("缺少 value/configValue");

            int rows = sysConfigQueryService.updateConfigValue(configKey, val);
            if (rows == 0) return ApiResponse.notFound("配置项不存在: " + configKey);

            return ApiResponse.success(new ConfigUpdateResultVO(configKey, val, true));
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
    public ApiResponse<List<ConfigMetaVO>> metadata() {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置元数据不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
            List<ConfigMetaVO> result = new ArrayList<>();
            for (SysConfigQueryService.ConfigMetaRow row : sysConfigQueryService.queryMetadata()) {
                boolean consumed = KNOWN_CONSUMED.containsKey(row.configKey());
                result.add(new ConfigMetaVO(row.configKey(), row.configGroup(), row.configLabel(),
                        row.configType(), row.configValue(), row.description(), row.impactScope(),
                        row.edition(), consumed, consumed ? KNOWN_CONSUMED.get(row.configKey()) : null));
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
     * 返回: total/consumed/unconsumed/items[{config_key,is_consumed,consumed_by,suggestion}]
     */
    @GetMapping("/audit")
    public ApiResponse<ConfigAuditResultVO> audit() {
        SysConfigQueryService sysConfigQueryService = queryProvider.getIfAvailable();
        if (sysConfigQueryService == null) {
            log.error("SysConfigQueryService 未装配，配置消费审计不可用（fail-closed）");
            throw queryUnavailable();
        }
        try {
            List<ConfigAuditItemVO> items = new ArrayList<>();
            for (SysConfigQueryService.ConfigAuditRow row : sysConfigQueryService.queryForAudit()) {
                String key = row.configKey();
                boolean consumed = KNOWN_CONSUMED.containsKey(key);
                String consumedBy;
                String suggestion;
                if (consumed) {
                    consumedBy = KNOWN_CONSUMED.get(key);
                    suggestion = null;
                } else {
                    // 尝试模糊匹配：检查是否有代码使用类似key（带点号变体）
                    String dotKey = key == null ? "" : key.replace('_', '.');
                    boolean fuzzyMatch = KNOWN_CONSUMED.values().stream()
                            .anyMatch(v -> v.contains(dotKey) || (key != null && v.contains(key)));
                    consumedBy = null;
                    suggestion = fuzzyMatch
                            ? "可能被消费但键名格式不匹配，请核实"
                            : "未检测到代码消费，建议确认是否为冗余配置";
                }
                items.add(new ConfigAuditItemVO(key, row.configGroup(), row.configLabel(),
                        consumed, consumedBy, suggestion));
            }

            long consumedCount = items.stream().filter(ConfigAuditItemVO::consumed).count();
            return ApiResponse.success(new ConfigAuditResultVO(items.size(), consumedCount,
                    items.size() - consumedCount, items));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("配置审计失败", e);
            return ApiResponse.internalError("配置审计失败");
        }
    }

    // ── 请求 DTO / 响应 VO（#147CA：键集与旧 Map 逐字一致） ─────────

    /** 改值请求（三别名 value > configValue > config_value，与旧 Map 读取口径一致）。 */
    public static final class ConfigValueRequest {
        private String value;
        private String configValue;
        @JsonProperty("config_value")
        private String config_value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }

        public String getConfig_value() {
            return config_value;
        }

        public void setConfig_value(String config_value) {
            this.config_value = config_value;
        }
    }

    /** /group 条目（7 键，含恒 null 的 versionNo 与诚实位 versionNoApplicable）。 */
    public record ConfigGroupItemVO(String key, String value, String valueType, String edition,
                                    String versionNo, boolean versionNoApplicable, String updatedAt) {
    }

    /** /group 响应（7 键）。 */
    public record ConfigGroupResultVO(String configGroup, String subsystem, String subsystemSource,
                                      List<String> requestedKeys, List<ConfigGroupItemVO> items,
                                      List<String> missingKeys, int itemCount) {
    }

    /** 根列表响应（data 元素＝旧原始列名形态）。 */
    public record ConfigListVO(List<SysConfigQueryService.ConfigGroupRow> data, int total) {
    }

    /** 改值回执（键名保持旧 snake_case；显式排序，防 record 注解重排）。 */
    @com.fasterxml.jackson.annotation.JsonPropertyOrder({"config_key", "config_value", "updated"})
    public record ConfigUpdateResultVO(@JsonProperty("config_key") String configKey,
                                       @JsonProperty("config_value") String configValue,
                                       boolean updated) {
    }

    /** 元数据条目（{@code consumed_by} 仅被消费时出现，属性级 NON_NULL）。 */
    public record ConfigMetaVO(@JsonProperty("config_key") String configKey,
                               @JsonProperty("config_group") String configGroup,
                               @JsonProperty("config_label") String configLabel,
                               @JsonProperty("config_type") String configType,
                               @JsonProperty("current_value") String currentValue,
                               @JsonProperty("definition") String definition,
                               @JsonProperty("impact") String impact,
                               @JsonProperty("edition") String edition,
                               @JsonProperty("is_consumed") boolean consumed,
                               @JsonInclude(JsonInclude.Include.NON_NULL)
                               @JsonProperty("consumed_by") String consumedBy) {
    }

    /** 审计条目（6 键，consumed_by/suggestion 互斥为 null 但键都在）。 */
    public record ConfigAuditItemVO(@JsonProperty("config_key") String configKey,
                                    @JsonProperty("config_group") String configGroup,
                                    @JsonProperty("config_label") String configLabel,
                                    @JsonProperty("is_consumed") boolean consumed,
                                    @JsonProperty("consumed_by") String consumedBy,
                                    @JsonProperty("suggestion") String suggestion) {
    }

    /** 审计汇总（4 键）。 */
    public record ConfigAuditResultVO(int total, long consumed, long unconsumed,
                                      List<ConfigAuditItemVO> items) {
    }
}
