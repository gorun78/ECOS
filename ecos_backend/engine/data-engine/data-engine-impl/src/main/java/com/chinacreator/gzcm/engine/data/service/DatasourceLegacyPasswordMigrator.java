package com.chinacreator.gzcm.engine.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * F02-01 数据源存量明文凭据迁移器（W52 数据域 P0 安全）。
 *
 * <p>背景：{@code td_datasource.connection_config}（承载 JDBC 连接参数的 TEXT 列）在早期
 * 存量行中内嵌明文 {@code password} 键（D-11：5 行中 2 行含明文）。V164.1 只做了
 * <b>列就位 + 哨兵标记</b>（{@code credential_encrypted = '__PENDING_REENCRYPT__'}），
 * 并明确"真正的加密写路径属业务改造批次（F02-01），本批不做"。SQL 无法做 AES-256-GCM，
 * 故本批改为<b>启动期迁移器</b>承接行级重加密。</p>
 *
 * <p>语义（幂等，可重复执行）：
 * <ol>
 *   <li>SELECT 命中 {@code connection_config LIKE '%"password"%' && connection_config 含真实明文}
 *       的存量行（不含掩码占位 {@code ********}）；</li>
 *   <li>逐行经<b>与写路径同一密文 facade</b>（{@link DataSourceServiceImpl#encryptPassword}）
 *       加密 → 写入 {@code password_enc}（本模块读写路径实际消费的凭据列，见下注）→
 *       {@code connection_config} 剥离 password 键；</li>
 *   <li>记 count 日志。第二次运行命中的存量行 {@code password_enc} 已置位，
 *       选择集为空 → 0 写（幂等）。</li>
 * </ol></p>
 *
 * <p><b>凭据列口径说明</b>：本模块运行态读写路径（{@code register}/{@code updateDataSource}/
 * {@code mapRow}/{@code resolvePassword}/{@code maskProtected}）消费的是 {@code password_enc} 列，
 * V164.1 新增的 {@code credential_encrypted} 列为占位哨兵（业务层未消费）。故迁移器写入
 * {@code password_enc} 才能被读路径真实解密回填 —— 这是对任务书"credential column"在
 * ECOS 现网等价落点的收敛，而非另起一列导致读路径读不到。</p>
 *
 * <p>开关：{@code ecos.data.source.legacy-password-migration.enabled}（默认 {@code true}，
 * 运维可置 {@code false} 关闭；关闭时本 Runner 不装配，启动不迁移）。</p>
 */
@Component
@ConditionalOnProperty(
        name = "ecos.data.source.legacy-password-migration.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DatasourceLegacyPasswordMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasourceLegacyPasswordMigrator.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 与 V164.1 明文凭据检出口径一致：JSON 内含 "password" 键。 */
    static final String SELECT_PENDING_SQL =
            "SELECT datasource_id, connection_config FROM td_datasource "
          + "WHERE (password_enc IS NULL OR password_enc = '') "
          + "AND connection_config IS NOT NULL "
          + "AND connection_config LIKE '%\"password\"%'";

    static final String UPDATE_SQL =
            "UPDATE td_datasource SET password_enc = ?, connection_config = ?, update_time = ? "
          + "WHERE datasource_id = ?";

    /** 掩码占位（与 DataSourceServiceImpl.MASK_VALUE 口径一致）：不算真实明文，跳过。 */
    private static final String MASK_VALUE = "********";

    private final JdbcTemplate jdbc;
    private final DataSourceServiceImpl dataSourceService;

    public DatasourceLegacyPasswordMigrator(JdbcTemplate jdbc,
                                            DataSourceServiceImpl dataSourceService) {
        this.jdbc = jdbc;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int migrated = migrate();
            log.info("[F02-01] 数据源存量明文凭据迁移完成：重加密 {} 行。", migrated);
        } catch (Exception e) {
            // fail-open 于启动：迁移失败不阻塞应用启动，仅记 ERROR（该批存量可在修复后重跑，幂等）
            log.error("[F02-01] 数据源存量明文凭据迁移失败（不阻塞启动，可人工重跑，幂等）: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * 执行迁移。返回实际重加密（写库）的行数。
     * <p>包级可见便于单测直接调用（无需 Spring 上下文）。</p>
     */
    int migrate() {
        List<Map<String, Object>> pending = jdbc.queryForList(SELECT_PENDING_SQL);
        int count = 0;
        for (Map<String, Object> row : pending) {
            String id = row.get("datasource_id") == null ? null : row.get("datasource_id").toString();
            String cfg = row.get("connection_config") == null ? null : row.get("connection_config").toString();
            if (id == null || id.isBlank() || cfg == null || cfg.isBlank()) {
                continue;
            }
            String plain = extractPlainPassword(cfg);
            if (plain == null) {
                continue; // 仅有掩码占位或无有效密码键 → 跳过
            }
            // 与写路径同一 facade：密钥缺失/加密失败 → 抛异常，run() 捕获记 ERROR（整批失败不半写）
            String enc = dataSourceService.encryptPassword(plain);
            String stripped = dataSourceService.stripSensitiveFields(cfg);
            jdbc.update(UPDATE_SQL, enc, stripped, new java.sql.Timestamp(System.currentTimeMillis()), id);
            count++;
        }
        return count;
    }

    /**
     * 从 config JSON 提取真实明文密码（null = 无可用明文）。
     * 键名收敛 password/secret/secretkey/secretaccesskey/passwd/apitoken；掩码占位值跳过。
     */
    private String extractPlainPassword(String cfgJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(cfgJson);
            if (!root.isObject()) {
                // 非常规 JSON 对象：无法安全剥离 password 键 → 跳过（保守，不掩盖非法 JSON）
                return null;
            }
            JsonNode first = null;
            for (String k : passwordKeys()) {
                JsonNode v = root.get(k);
                if (v != null && v.isTextual()) {
                    first = v;
                    break;
                }
            }
            if (first == null) {
                return null;
            }
            String val = first.asText();
            if (val == null || val.isBlank() || MASK_VALUE.equals(val)) {
                return null;
            }
            return val;
        } catch (Exception e) {
            log.debug("[F02-01] connection_config 非合法 JSON 对象，跳过该行: {}", e.getMessage());
            return null;
        }
    }

    private static List<String> passwordKeys() {
        // 与 DataSourceServiceImpl.PASSWORD_KEYS 口径一致
        return List.of("password", "secret", "secretkey", "secretaccesskey", "passwd", "apitoken");
    }
}
