package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B4a 数据访问收口（R2-B4a）：{@code MetadataCollectGitArchive}（..metadata..）
 * 内联 raw-SQL（sys_config 读取）的 DAO 承载点。
 *
 * <p>架构铁律 §2.5#1 / IR06：非 dao 层禁私持 {@code JdbcTemplate}，
 * SQL 一律下沉 {@code ..dao..}；本 DAO 只搬不改，SQL 文本与
 * {@code MetadataCollectGitArchive.resolveHistoryLimit()} 原实现逐字节一致
 * （配置键 dw.metadata.history_versions 保持内联字面量，不做参数化改写）。</p>
 */
@Repository
public class MetadataGitArchiveConfigDao extends JdbcTemplateDaoSupport {

    /**
     * 读取"历史版本保留份数"配置值（sys_config，键 dw.metadata.history_versions）。
     * 查不到配置行 / 表缺失时由调用方 try/catch 后回退默认值。
     */
    public String findHistoryVersionsConfig() {
        return jdbcTemplate.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'dw.metadata.history_versions'",
                String.class);
    }
}
