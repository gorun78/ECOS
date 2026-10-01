package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * B1 数据访问收口（R2-B1 / 架构铁律 §2.5 #1）：数据源存量明文凭据迁移 raw-SQL 落点。
 *
 * <p>原 {@code service.DatasourceLegacyPasswordMigrator}（ApplicationRunner）直接持
 * {@code jdbcTemplate} 内联 SELECT/UPDATE，位于 {@code ..service..} 包被
 * {@code DataAccessArchTest} 挡回。SQL 下沉至此 DAO，迁移器只保留"选择/加密/剥离"编排
 * 逻辑。SELECT/UPDATE 语义原样搬迁，不改表名/字段/守门条件。</p>
 *
 * <p>凭据列口径说明随迁移器保留：写 {@code password_enc}（读路径真实消费列），
 * 非 V164.1 占位列 {@code credential_encrypted}。</p>
 */
@Repository
public class LegacyPasswordMigrationDao extends JdbcTemplateDaoSupport {

    /** 与 V164.1 明文凭据检出口径一致：JSON 内含 "password" 键且 password_enc 未置位。 */
    public static final String SELECT_PENDING_SQL =
            "SELECT datasource_id, connection_config FROM td_datasource "
          + "WHERE (password_enc IS NULL OR password_enc = '') "
          + "AND connection_config IS NOT NULL "
          + "AND connection_config LIKE '%\"password\"%'";

    public static final String UPDATE_SQL =
            "UPDATE td_datasource SET password_enc = ?, connection_config = ?, update_time = ? "
          + "WHERE datasource_id = ?";

    /** 选择待重加密行（password_enc 空 + config 含 password 键）。 */
    public List<Map<String, Object>> findPending() {
        return jdbcTemplate.queryForList(SELECT_PENDING_SQL);
    }

    /** 写密文 + 剥离后的 config 回该 datasource_id 行（4 占位：enc/encCfg/time/id）。 */
    public int reencrypt(String enc, String strippedConfig, String datasourceId) {
        return jdbcTemplate.update(UPDATE_SQL, enc, strippedConfig,
                new Timestamp(System.currentTimeMillis()), datasourceId);
    }
}
