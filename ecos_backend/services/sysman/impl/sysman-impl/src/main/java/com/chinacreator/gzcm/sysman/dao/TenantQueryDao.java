package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 租户查询 DAO — 承接 TenantService（原 TenantController 下沉）中的 JdbcTemplate 操作。
 *
 * <p>命名注记（2026-10-11 全栈拉起实测修复）：类名必须区别于 MyBatis mapper 接口
 * {@code com.chinacreator.gzcm.sysman.iam.dao.TenantDao}。两者同简单名时，
 * {@code @MapperScan("com.chinacreator.gzcm.sysman.**.dao")} 与组件扫描会各自注册
 * bean 名 {@code tenantDao}，触发 ConflictingBeanDefinitionException（sysman/dccheng 启动即崩）。
 * 本类是 JdbcTemplate DAO 而非 mapper，按同包 {@code UserQueryDao}/{@code UserLookupDao} 惯例改名。</p>
 */
@Repository
public class TenantQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbcTemplate.queryForMap(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
