package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
import com.chinacreator.gzcm.engine.data.metadata.MetadataStrategyConfig;
import com.chinacreator.gzcm.engine.security.crypto.IDataEncryptionService;
import com.chinacreator.gzcm.engine.security.crypto.IKeyManagementService;
import com.chinacreator.gzcm.engine.security.crypto.impl.DataEncryptionServiceImpl;
import com.chinacreator.gzcm.engine.security.crypto.service.impl.KeyManagementServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * DataSourceService JdbcTemplate 实现（PMO-E3: 替换启动兜底 stub）。
 * 表: td_datasource
 *
 * PMO-37 增强：
 * - register / updateDataSource 写入 metadata_config (JSONB) + last_collect_time
 * - mapRow 读取新列（向后兼容：旧行 NULL → PROPERTY NULL）
 * - 注册/更新后按策略异步触发明细采集（MetadataAsyncTrigger）
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceServiceImpl.class);
    private static final String TABLE = "td_datasource";

    /** PMO-49: KMS 数据源密码专用 keyId（全局唯一可绑数据源场景，与 sysman 身份凭证 kek 隔离）。 */
    private static final String KEY_DATA_SOURCE_PASSWORD = "dataSourcePwd";
    /** PMO-46: connectionConfig 中敏感键（覆盖平台常见拼写）。 */
    private static final Set<String> PASSWORD_KEYS = Set.of(
            "password", "secret", "secretkey", "secretaccesskey", "passwd", "apitoken");
    /** PMO-46: 掩码值（与 security-engine SecurityService.mask() 口径一致）。 */
    private static final String MASK_VALUE = "********";

    /** PMO-45: 支持的全部数据源类型（与数据模型 7 种基础类型对齐） */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "POSTGRESQL", "MYSQL", "ORACLE", "MSSQL",
            "DM", "KINGBASE", "GAUSS", "MINIO", "FILESYSTEM"
    );

    /** PMO-45: MINIO 对象存储 — enterprise/ultimate 版本才可用 */
    public static final String MINIO_TYPE = "MINIO";

    /** 共享 ObjectMapper：解析/序列化 JSON 配置串（connectionConfig / metadataConfig）。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final JdbcTemplate jdbc;
    private final com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory connectorFactory;
    /** 可选依赖: 当前系统版本（Spring profile），用于 MINIO/FILESYSTEM 版本校验 */
    private String activeProfiles;

    /** 可选依赖（@Autowired(required=false) 语义：避免测试环境无 Bean 时启动失败） */
    private MetadataAsyncTrigger asyncTrigger;

    /** PMO-45: 注入 Environment 以获取 Spring active profile（standard/enterprise/ultimate） */
    private Environment springEnv;

    public DataSourceServiceImpl(JdbcTemplate jdbc,
                                 com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory connectorFactory) {
        this.jdbc = jdbc;
        this.connectorFactory = connectorFactory;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAsyncTrigger(MetadataAsyncTrigger trigger) {
        this.asyncTrigger = trigger;
    }

    /** PMO-46: 注入 Spring Environment（always available） */
    @Autowired
    public void setSpringEnv(Environment env) {
        this.springEnv = env;
    }

    // ──────────────────────────────────────────────────────────────────
    // PMO-49: 数据源密码加密（security-engine，Authentication 直接 new 模式）
    //   - 写前 encryptPassword：明文 → 密文 → password_enc 列；config 中密码字段剥离
    //   - 读后 resolvePassword：密文解密回填 connectionConfig（内存回填，读路径不写库）
    //   - maskSensitiveFields：响应层密码字段回显 "********"
    //   - 降级策略：加密/解密异常 → 明文落库/跳过回填 + WARN（降密不降级服务）
    //   - key 管理：IKeyManagementService 按 keyId 自管 key，首次写入前 ensureKey（幂等）
    // ──────────────────────────────────────────────────────────────────

    /**
     * PMO-49: 数据源密码加密服务（data-engine 自持实例）。
     * 直接 new 实例化 security-engine 提供的 KeyManagementServiceImpl +
     * DataEncryptionServiceImpl，避免跨模块 Bean 循环依赖。
     * 同一进程内 data-engine 所有 DataSourceService 实例共享同一 keyId 因此
     * 解密数据源间可互访（密文互通 = 同一密钥体系）。
     */
    private final IKeyManagementService kms = new KeyManagementServiceImpl();
    private final IDataEncryptionService dataEncryptionService = new DataEncryptionServiceImpl(kms);
    private volatile boolean keyReady;

    /** PMO-49: 确保 KMS 中数据源密码 key 存在（幂等，首次调用创建）。 */
    private synchronized void ensureKey() {
        if (keyReady) return;
        try {
            byte[] kb = kms.getKeyBytes(KEY_DATA_SOURCE_PASSWORD);
            if (kb == null || kb.length == 0) {
                kms.createKey(KEY_DATA_SOURCE_PASSWORD, "AES", 128);
                log.info("PMO-49: KMS key created for datasource passwords (keyId={})", KEY_DATA_SOURCE_PASSWORD);
            }
            keyReady = true;
        } catch (Exception e) {
            log.warn("PMO-49: KMS key ensure 失败（encrypt/decrypt 时重试）: {}", e.getMessage(), e);
        }
    }

    /**
     * PMO-49: 写入前加密密码。
     * @param password 明文密码（null/空白返回 null）
     * @return 密文（Base64 AES）；加密失败时降级返回原明文并告警
     */
    String encryptPassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        ensureKey();
        try {
            return dataEncryptionService.encrypt(password, KEY_DATA_SOURCE_PASSWORD);
        } catch (Exception e) {
            log.warn("PMO-49: 密码加密失败，降级明文存储: {}", e.getMessage(), e);
            return password;
        }
    }

    /**
     * PMO-49: 读取后解密回填 connectionConfig（读路径无库写）。
     *   1. password_enc 有值且能解密 → 写入 config 中第一个密码字段（无则加 "password"）
     *   2. password_enc 空 → config 原样返回（兼容老数据/未迁移）
     *   3. 解密失败 → config 原样返回（不泄露、不阻断）
     * 掩码值（********）视为无密码，不回填。
     */
    String resolvePassword(String cfgJson, String passwordEnc) {
        if (cfgJson == null || cfgJson.isBlank()) {
            return cfgJson;
        }
        if (passwordEnc == null || passwordEnc.isBlank()) {
            return cfgJson;
        }
        String plain = decryptPassword(passwordEnc);
        if (plain == null) {
            return cfgJson; // 解密失败：保持原 config（不泄露、不阻断）
        }
        try {
            Map<String, Object> cfg = parseCfgObj(cfgJson);
            boolean injected = false;
            for (Map.Entry<String, Object> e : cfg.entrySet()) {
                if (PASSWORD_KEYS.contains(e.getKey().toLowerCase())) {
                    // 已有密码字段 → 覆盖（空/掩码都替换为真实解密值）
                    e.setValue(plain);
                    injected = true;
                    break;
                }
            }
            if (!injected) {
                cfg.put("password", plain);
            }
            return OBJECT_MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("PMO-49: connectionConfig 密码回填异常（保持原 JSON）: {}", e.getMessage(), e);
            return cfgJson;
        }
    }

    /** 解密密码；KMS 不可用或解密失败返回 null（不抛——读路径永不因解密失败阻断）。 */
    String decryptPassword(String encCipher) {
        if (encCipher == null || encCipher.isBlank()) {
            return null;
        }
        try {
            String plain = dataEncryptionService.decrypt(encCipher, KEY_DATA_SOURCE_PASSWORD);
            return (plain == null || plain.isBlank()) ? null : plain;
        } catch (Exception e) {
            log.warn("PMO-49: 数据源密码解密失败（key 可能丢失/轮换）: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * PMO-49: 落库前脱敏——剥离 config 中所有密码字段（密码只走 password_enc）。
     * 无密码字段时原样返回入参字符串（避免无谓 JSON 改写）。
     */
    String stripSensitiveFields(String cfgJson) {
        if (cfgJson == null || cfgJson.isBlank()) {
            return cfgJson;
        }
        try {
            Map<String, Object> cfg = parseCfgObj(cfgJson);
            int before = cfg.size();
            cfg.keySet().removeIf(k -> PASSWORD_KEYS.contains(k.toLowerCase()));
            return cfg.size() == before ? cfgJson : OBJECT_MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            // config 非合法 JSON 对象时原样落库（连接测试会暴露错误，不掩盖）
            return cfgJson;
        }
    }

    /**
     * PMO-46: 响应脱敏——config 密码字段回显为 ********（存在但不可用）。
     * 用于 DataSourceController/DataWorkbenchController 的详情/列表回显。
     */
    String maskSensitiveFields(String cfgJson) {
        if (cfgJson == null || cfgJson.isBlank()) {
            return cfgJson;
        }
        try {
            Map<String, Object> cfg = parseCfgObj(cfgJson);
            for (Map.Entry<String, Object> e : cfg.entrySet()) {
                if (PASSWORD_KEYS.contains(e.getKey().toLowerCase())) {
                    e.setValue(MASK_VALUE);
                }
            }
            return OBJECT_MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            return cfgJson;
        }
    }

    /** 从 config JSON 中提取密码候选值（register 落 password_enc 前的抽取）。 */
    String extractPassword(String cfgJson) {
        if (cfgJson == null || cfgJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> cfg = parseCfgObj(cfgJson);
            for (Map.Entry<String, Object> e : cfg.entrySet()) {
                if (PASSWORD_KEYS.contains(e.getKey().toLowerCase())
                        && e.getValue() != null
                        && !e.getValue().toString().isBlank()
                        && !MASK_VALUE.equals(e.getValue())) {
                    return e.getValue().toString();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseCfgObj(String json) throws Exception {
        return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Override
    public DataSourceEntity register(DataSourceDTO dto) {
        // PMO-45: 类型合法性校验
        validateType(dto.getDatasourceType());
        String id = UUID.randomUUID().toString().replace("-", "");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String metadataConfigJson = buildMetadataConfigJson(dto, "MANUAL");
        // PMO-49: 密码提取 → 加密 → password_enc；config 中密码字段剥离（只存密文）
        ensureKey();
        String passwordEnc = encryptPassword(extractPassword(dto.getConnectionConfig()));
        String storedConfig = stripSensitiveFields(dto.getConnectionConfig());
        List<String> cols = List.of(
                "datasource_id", "datasource_name", "datasource_type", "org_id",
                "description", "connection_config", "status", "tags", "metadata_config",
                "create_time", "update_time", "password_enc");
        Object[] vals = {
                id, dto.getDatasourceName(), dto.getDatasourceType(), dto.getOrgId(),
                dto.getDescription(), storedConfig, "active", dto.getTags(),
                metadataConfigJson, now, now, passwordEnc
        };
        jdbc.update(
            "INSERT INTO " + TABLE + " (" + String.join(", ", cols) + ") " +
            "VALUES (" + String.join(", ", java.util.Collections.nCopies(vals.length, "?")) + ")",
            vals
        );
        log.info("Registered datasource: id={}, name={}, strategy={}",
                id, dto.getDatasourceName(),
                dto.getMetadataStrategy() != null ? dto.getMetadataStrategy() : "MANUAL");
        DataSourceEntity saved = getById(id);
        if (saved != null && asyncTrigger != null) {
            asyncTrigger.afterRegister(saved);
        }
        return maskProtected(saved);
    }

    @Override
    public boolean testConnection(String datasourceId) {
        DataSourceEntity ds = getInternal(datasourceId);
        if (ds == null) return false;
        String cfg = resolvePassword(ds.getConnectionConfig(), ds.getPasswordEncrypted());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean ok = false;
        String message;
        try {
            // 真实连通性测试：走 runtime-access Connector（POSTGRESQL 等别名内部归一化为 JDBC）
            com.chinacreator.gzcm.runtime.access.connector.Connector connector =
                    connectorFactory.getConnector(ds.getDatasourceType());
            ok = connector.testConnection(cfg);
            message = ok ? "连接成功" : "连接失败: 数据源不可达或认证被拒绝";
        } catch (Exception e) {
            message = "连接失败: " + e.getMessage();
            log.warn("testConnection failed datasource={}: {}", datasourceId, e.getMessage());
        }
        String finalMessage = message;
        jdbc.update(
            "UPDATE " + TABLE + " SET last_test_time = ?, last_test_result = ?, " +
            "last_test_message = ?, update_time = ? WHERE datasource_id = ?",
            now, ok ? "true" : "false", finalMessage, now, datasourceId
        );
        return ok;
    }

    @Override
    public List<DataSourceEntity> listAll() {
        return jdbc.query(
            "SELECT * FROM " + TABLE + " ORDER BY create_time DESC",
            (rs, i) -> mapRow(rs)
        );
    }

    @Override
    public DataSourceEntity getById(String datasourceId) {
        List<DataSourceEntity> list = jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE datasource_id = ?",
            (rs, i) -> mapRow(rs), datasourceId
        );
        return list.isEmpty() ? null : maskProtected(list.get(0));
    }

    /**
     * PMO-49: 内部读取（不脱敏）— 供 testConnection / testConnectionById /
     * previewSchema / afterUpdate 等需要真实密文回填密码的路径调用。
     * 公开 getById() 强制脱敏，防止 Controller 误把密文回显给前端。
     */
    DataSourceEntity getInternal(String datasourceId) {
        List<DataSourceEntity> list = jdbc.query(
            "SELECT * FROM " + TABLE + " WHERE datasource_id = ?",
            (rs, i) -> mapRow(rs), datasourceId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * PMO-49: 响应脱敏 — 密文字段 passwordEncrypted 置 null，
     * connectionConfig 内密码字段掩码为 ********（GET/PUT/list 回显安全兜底）。
     */
    private DataSourceEntity maskProtected(DataSourceEntity entity) {
        if (entity == null) {
            return null;
        }
        entity.setPasswordEncrypted(null);
        entity.setConnectionConfig(maskSensitiveFields(entity.getConnectionConfig()));
        return entity;
    }

    @Override
    public DataSourceEntity updateDataSource(String datasourceId, DataSourceDTO dto) {
        // PMO-45: 类型合法性校验
        validateType(dto.getDatasourceType());
        DataSourceEntity existing = getInternal(datasourceId);
        if (existing == null) {
            return null;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String oldConnectionConfig = existing.getConnectionConfig();
        String metadataConfigJson = buildMetadataConfigJson(dto, null /* fallback 用 existing */);
        // 保留 existing 的 metadataConfig 作为 fallback，避免 dto 缺策略时把已有配置清成 MANUAL
        String fallback = existing.getMetadataConfig();
        if (dto.getMetadataStrategy() == null && fallback != null && !fallback.isBlank()
                && !fallback.equals("{}")) {
            // 策略字段回退：取 existing 的策略值
            MetadataStrategyConfig old = MetadataStrategyConfig.fromJson(fallback);
            if (old.getStrategy() != null) {
                dto.setMetadataStrategy(old.getStrategy());
            }
            metadataConfigJson = buildMetadataConfigJson(dto, null);
        }
        // PMO-49: 密码更新逻辑 — 新明文 → 重新加密替换；mask/空白 → 保留原密文（密码不变更）
        String oldEnc = existing.getPasswordEncrypted();
        String newEnc = oldEnc;
        String incomingPwd = extractPassword(dto.getConnectionConfig());
        if (incomingPwd != null && !MASK_VALUE.equals(incomingPwd)) {
            ensureKey();
            String candidate = encryptPassword(incomingPwd);
            newEnc = (candidate != null) ? candidate : oldEnc;
        }
        String storedConfig = stripSensitiveFields(dto.getConnectionConfig());
        jdbc.update(
            "UPDATE " + TABLE + " SET datasource_name = ?, datasource_type = ?, org_id = ?, " +
            "description = ?, connection_config = ?, tags = ?, metadata_config = ?, " +
            "password_enc = ?, update_time = ? WHERE datasource_id = ?",
            dto.getDatasourceName(), dto.getDatasourceType(), dto.getOrgId(),
            dto.getDescription(), storedConfig, dto.getTags(),
            metadataConfigJson, newEnc, now, datasourceId
        );
        log.info("Updated datasource: id={}, name={}, strategy={}", datasourceId,
            dto.getDatasourceName(),
            dto.getMetadataStrategy() != null ? dto.getMetadataStrategy() : "(保留)");
        DataSourceEntity updated = getById(datasourceId);
        if (updated != null && asyncTrigger != null) {
            asyncTrigger.afterUpdate(datasourceId, updated.getMetadataConfig(),
                    oldConnectionConfig, dto);
        }
        return maskProtected(updated);
    }

    @Override
    public void remove(String datasourceId) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE datasource_id = ?", datasourceId);
        log.info("Removed datasource: {}", datasourceId);
    }

    @Override
    public void updateMetadataConfig(String datasourceId, String json) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        int n = jdbc.update(
            "UPDATE " + TABLE + " SET metadata_config = ?, update_time = ? WHERE datasource_id = ?",
            json, now, datasourceId
        );
        if (n == 0) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        log.info("Updated metadataConfig: datasource={}, json={}", datasourceId, json);
    }

    private String buildMetadataConfigJson(DataSourceDTO dto, String fallbackStrategy) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> cfg =
                    (java.util.Map<String, Object>) MetadataAsyncTrigger.metadataConfigMap(dto, fallbackStrategy);
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("metadataConfigJson 序列化失败，落默认值: {}", e.getMessage());
            return "{\"strategy\":\"MANUAL\",\"includeRowCount\":true,\"countMethod\":\"ESTIMATE\",\"cacheTtlMinutes\":5,\"onSourceEdit\":true}";
        }
    }

    private DataSourceEntity mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        DataSourceEntity e = new DataSourceEntity();
        e.setDatasourceId(rs.getString("datasource_id"));
        e.setDatasourceName(rs.getString("datasource_name"));
        e.setDatasourceType(rs.getString("datasource_type"));
        e.setOrgId(rs.getString("org_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setDescription(rs.getString("description"));
        e.setConnectionConfig(rs.getString("connection_config"));
        e.setStatus(rs.getString("status"));
        e.setIsDefault(rs.getString("is_default"));
        e.setLastTestTime(rs.getTimestamp("last_test_time"));
        e.setLastTestResult(rs.getString("last_test_result"));
        e.setLastTestMessage(rs.getString("last_test_message"));
        e.setCreateBy(rs.getString("create_by"));
        e.setCreateTime(rs.getTimestamp("create_time"));
        e.setUpdateBy(rs.getString("update_by"));
        e.setUpdateTime(rs.getTimestamp("update_time"));
        e.setTags(rs.getString("tags"));
        e.setRemark(rs.getString("remark"));
        // PMO-37 新增列（缺列时回退 NULL）
        e.setMetadataConfig(readMetadataConfig(rs));
        e.setLastCollectTime(readLastCollectTime(rs));
        // PMO-49: 密码密文列（decryption 回填跑 connectionConfig 用；公开 getById 前脱敏）
        e.setPasswordEncrypted(readPasswordEnc(rs));
        return e;
    }

    private String readPasswordEnc(java.sql.ResultSet rs) {
        try {
            return rs.getString("password_enc");
        } catch (java.sql.SQLException sqlEx) {
            return null; // 缺列回退（兼容未迁移库）
        }
    }

    private String readMetadataConfig(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            Object v = rs.getObject("metadata_config");
            if (v == null) return null;
            String s = v.toString();
            if (s == null || s.isBlank()) return null;
            // PMO-37 修正: "{}" 是合法的空策略对象, 原实现把它转 null 导致
            // FE 列表接口 items[].metadataConfig 永远空, 现按原样透传.
            return s;
        } catch (java.sql.SQLException sqlEx) {
            return null; // 缺列回退
        }
    }

    private Timestamp readLastCollectTime(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            return rs.getTimestamp("last_collect_time");
        } catch (java.sql.SQLException sqlEx) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // PMO-45: 类型校验 + 新公开方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * PMO-45: 校验数据源类型合法性 + 版本约束。
     * MINIO 对象存储仅 enterprise/ultimate 版本支持，standard 版本返回业务错误码 "invalid_type"。
     *
     * @throws IllegalArgumentException 类型不支持或版本不满足
     */
    private void validateType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("数据源类型 datasourceType 不能为空");
        }
        String upper = type.toUpperCase();
        if (!SUPPORTED_TYPES.contains(upper)) {
            throw new IllegalArgumentException(
                "不支持的数据源类型: " + type + "。支持: " + SUPPORTED_TYPES);
        }
        // MINIO 版本约束
        if (MINIO_TYPE.equals(upper)) {
            String profile = resolveProfile();
            boolean ok = profile != null &&
                    (profile.contains("enterprise") || profile.contains("ultimate"));
            if (!ok) {
                throw new IllegalArgumentException(
                    "MINIO 对象存储需要 enterprise 或 ultimate 版本，当前版本: "
                    + (profile != null ? profile : "standard(default)"));
            }
        }
    }

    /**
     * 解析当前 Spring active profile。
     * 多 profile 时取第一个（Maven 构建时用 -Pstandard/-Penterprise/-Pultimate 控制）。
     * 无法获取时返回 null（由调用方做保守判断）。
     */
    private String resolveProfile() {
        if (springEnv != null) {
            String[] profiles = springEnv.getActiveProfiles();
            if (profiles.length > 0) return profiles[0];
        }
        return null;
    }

    /**
     * PMO-45: 对已保存数据源执行连通性测试（Controller /test-connection/{id} 调用）。
     *
     * 对 MINIO / FILESYSTEM 类型: 不走 Connector，直接验证配置合法性（s3:// 或 file:// 前缀）。
     * 对关系型数据库类型: 走 ConnectorFactory → JdbcConnector。
     *
     * @return Map 包含 success(boolean), message(String)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> testConnectionById(String datasourceId) {
        DataSourceEntity ds = getInternal(datasourceId);
        if (ds == null) {
            String err = "数据源不存在: " + datasourceId;
            return Map.of("success", false, "error", err, "message", err);
        }
        // PMO-49: 解密回填 password_enc → 内存中 config（不落库、不回显）
        String cfg = resolvePassword(ds.getConnectionConfig(), ds.getPasswordEncrypted());
        String type = ds.getDatasourceType() != null ? ds.getDatasourceType().toUpperCase() : "";
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Map<String, Object> result;
        if (MINIO_TYPE.equals(type)) {
            // 企业/ultimate MinIO 走 MinioClient 真实连通
            result = testMinio(cfg);
        } else if ("FILESYSTEM".equals(type)) {
            result = testFilesystem(cfg);
        } else {
            // 关系型: 走 JdbcConnector 详细测试（驱动未加载 / 网络不可达 / 认证失败分类化 error）
            Map<String, Object> det;
            try {
                com.chinacreator.gzcm.runtime.access.connector.JdbcConnector jdbc =
                        (com.chinacreator.gzcm.runtime.access.connector.JdbcConnector)
                                connectorFactory.getConnector(type);
                det = jdbc.testConnectionDetailed(cfg, type);
            } catch (Exception e) {
                String err = "找不到对应连接器 (type=" + type + "): " + e.getMessage();
                det = new LinkedHashMap<>();
                det.put("success", false);
                det.put("error", err);
                det.put("message", err);
                det.put("type", type);
            }
            result = det;
        }
        persistLastTest(datasourceId, now, result);
        return result;
    }

    /**
     * 真实 MinIO 连通测试（仅企业/ultimate 生效 — 标准版在 register 已拦下 MINIO）。
     * 通过 listBuckets 校验 endpoint/凭证可达；不可达时返回可读 error。
     */
    private Map<String, Object> testMinio(String connectionConfig) {
        if (isStandardProfile()) {
            String err = "MINIO 仅在企业版/旗舰版可用（当前运行 standard profile）";
            return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
        }
        try {
            Map<String, String> cfg = parseCfg(connectionConfig);
            String endpoint = firstNonBlank(cfg.get("endpoint"), cfg.get("url"), cfg.get("host"));
            if (endpoint == null || endpoint.isBlank()) {
                String err = "MinIO 配置缺少 endpoint（如 http://127.0.0.1:9000）";
                return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
            }
            if (endpoint.contains(":9001")) {
                String err = "MinIO endpoint 指向了控制台端口 9001，请使用 API 端口 9000";
                return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
            }
            String accessKey = firstNonBlank(cfg.get("accessKey"), cfg.get("accessKeyId"), cfg.get("username"));
            String secretKey = firstNonBlank(cfg.get("secretKey"), cfg.get("secretAccessKey"), cfg.get("password"));
            io.minio.MinioClient.Builder builder = io.minio.MinioClient.builder().endpoint(endpoint);
            if (accessKey != null && secretKey != null) {
                builder.credentials(accessKey, secretKey);
            }
            io.minio.MinioClient client = builder.build();
            try {
                client.listBuckets();
                return Map.of("success", true, "message", "MinIO 连通成功", "type", MINIO_TYPE);
            } catch (Exception e) {
                String err = "MinIO 连通失败: " + sanitizeEx(e);
                log.warn("MinIO 连通测试失败 endpoint={}: {}", endpoint, e.getMessage());
                return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
            }
        } catch (IllegalArgumentException e) {
            String err = "MinIO 配置 JSON 解析失败: " + e.getMessage();
            return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
        } catch (Exception e) {
            String err = "MinIO 连通测试异常: " + sanitizeEx(e);
            return Map.of("success", false, "error", err, "message", err, "type", MINIO_TYPE);
        }
    }

    /**
     * 真实 FILESYSTEM 检查：basePath 存在 + 读写权限。
     */
    private Map<String, Object> testFilesystem(String connectionConfig) {
        try {
            Map<String, String> cfg = parseCfg(connectionConfig);
            String basePath = firstNonBlank(cfg.get("basePath"), cfg.get("path"), cfg.get("root"));
            if (basePath == null || basePath.isBlank()) {
                String err = "FILESYSTEM 配置缺少 basePath";
                return Map.of("success", false, "error", err, "message", err, "type", "FILESYSTEM");
            }
            java.nio.file.Path p = java.nio.file.Path.of(basePath);
            if (!java.nio.file.Files.exists(p)) {
                String err = "文件路径不存在: " + basePath;
                return Map.of("success", false, "error", err, "message", err, "type", "FILESYSTEM");
            }
            boolean readable = java.nio.file.Files.isReadable(p);
            boolean writable = java.nio.file.Files.isWritable(p);
            if (!readable) {
                String err = "文件路径不可读: " + basePath;
                return Map.of("success", false, "error", err, "message", err, "type", "FILESYSTEM");
            }
            String msg = "文件系统可达: " + basePath + (writable ? " (可读可写)" : " (只读)");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.put("message", msg);
            r.put("type", "FILESYSTEM");
            r.put("writable", writable);
            return r;
        } catch (IllegalArgumentException e) {
            String err = "FILESYSTEM 配置 JSON 解析失败: " + e.getMessage();
            return Map.of("success", false, "error", err, "message", err, "type", "FILESYSTEM");
        } catch (Exception e) {
            String err = "FILESYSTEM 检查异常: " + e.getMessage();
            return Map.of("success", false, "error", err, "message", err, "type", "FILESYSTEM");
        }
    }

    /** 回写 last_test_time / last_test_result / last_test_message / update_time，error 入 message 列。 */
    private void persistLastTest(String datasourceId, Timestamp now, Map<String, Object> result) {
        Object s = result.get("success");
        boolean success = Boolean.TRUE.equals(s);
        String status = success ? "true" : "false";
        Object errObj = result.containsKey("error") ? result.get("error") : result.get("message");
        String message = errObj != null ? errObj.toString() : (success ? "连接成功" : "连接失败");
        if (message.length() > 900) {
            message = message.substring(0, 900) + "…";
        }
        try {
            jdbc.update(
                "UPDATE " + TABLE + " SET last_test_time=?, last_test_result=?, last_test_message=?, update_time=? WHERE datasource_id=?",
                now, status, message, now, datasourceId);
        } catch (Exception e) {
            log.warn("更新 last_test_* 失败 dsId={}: {}", datasourceId, e.getMessage());
        }
    }

    private boolean isStandardProfile() {
        String p = resolveProfile();
        return p == null || p.equalsIgnoreCase("standard");
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** 异常消息清洗：保留首行，截断长消息，避免堆栈/凭证泄漏进 last_test_message。 */
    private static String sanitizeEx(Throwable e) {
        String m = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        if (nl > 0) m = m.substring(0, nl);
        if (m.length() > 200) m = m.substring(0, 200) + "…";
        return m;
    }

    private Map<String, String> parseCfg(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> r = new LinkedHashMap<>();
            raw.forEach((k, v) -> { if (v != null) r.put(k, v.toString()); });
            return r;
        } catch (Exception e) {
            throw new IllegalArgumentException("连接配置 JSON 解析失败（应为对象）: " + e.getMessage());
        }
    }

    /**
     * PMO-45: 预览数据源代码结构（Controller /preview-schema 调用）。
     *
     * 对关系型数据库: 走 Connector.listResources / queryPreview 获取 schema 摘要。
     * 对 MINIO / FILESYSTEM: 返回占位符（真实枚举待后续任务）。
     *
     * @param datasourceId 数据源 ID
     * @return Map 包含 type, objects(List<String>), columnPreview(List<Map>)
     */
    public Map<String, Object> previewSchema(String datasourceId) {
        DataSourceEntity ds = getInternal(datasourceId);
        if (ds == null) {
            throw new RuntimeException("数据源不存在: " + datasourceId);
        }
        // PMO-49: 解密回填（与 testConnectionById 同路由）
        String cfgResolved = resolvePassword(ds.getConnectionConfig(), ds.getPasswordEncrypted());
        String type = ds.getDatasourceType() != null ? ds.getDatasourceType().toUpperCase() : "";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);

        if (MINIO_TYPE.equals(type) || "FILESYSTEM".equals(type)) {
            // 占位: 待后续任务实现 MinIO bucket / filesystem 目录枚举
            result.put("objects", List.of());
            result.put("columnPreview", List.of());
            result.put("note", "对象存储/文件路径枚举待实现 (PMO-45 后续任务)");
            return result;
        }

        // 关系型: 通过 Connector 获取资源列表
        try {
            com.chinacreator.gzcm.runtime.access.connector.Connector connector =
                    connectorFactory.getConnector(type);
            String cfg = cfgResolved; // PMO-49: 已解密回填的 config
            java.util.List<com.chinacreator.gzcm.common.data.model.DataResource> objects =
                    connector.listResources(cfg, null, null);
            result.put("objects", objects);

            // 取第一个资源预览列
            java.util.List<Map<String, Object>> columnPreview = java.util.List.of();
            if (objects != null && !objects.isEmpty()) {
                String firstObj = objects.get(0).getSourcePath();
                if (firstObj == null || firstObj.isBlank()) {
                    firstObj = objects.get(0).getResourceName();
                }
                if (firstObj != null) {
                    try {
                        List<Map<String, Object>> rows = connector.queryPreview(cfg, firstObj, 5);
                        columnPreview = rows;
                    } catch (Exception ignored) { }
                }
            }
            result.put("columnPreview", columnPreview);
        } catch (Exception e) {
            log.warn("previewSchema failed for datasource={}: {}", datasourceId, e.getMessage());
            result.put("objects", List.of());
            result.put("columnPreview", List.of());
            result.put("error", "预览失败: " + e.getMessage());
        }
        return result;
    }
}
