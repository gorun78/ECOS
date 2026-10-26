package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 安全档案连接自检载体（#142/B4-3e-87 收口 D-B4-101 后仅剩职责）。
 *
 * <p>{@code td_user_security_profile} / {@code td_role_security_profile} 是 sysman 属主表
 * （铁律 #5）。本类原先承载七组读面（已迁出：#142/B4-3e-83 / 3e-84，D-B4-99 d) 与 D-B4-100）
 * 与九腿写面（upsert×3／clearAllDefaults／activate／delete×2／updateProfileFields／clone，
 * 其中 {@code upsertProfile} 以 {@code String.format} 拼表名列名），收口前两存。
 * <b>写面九腿已整腿删除</b>：写语句单源移到 sysman {@code SecurityProfileQueryDao}，
 * 安全引擎只经属主 api 门面 {@code ISecurityProfileQueryService} 的写方法
 * （upsertUserProfile／upsertRoleProfile／upsertCoalescing／clearAllDefaults／activateDefault／
 * deleteUserProfile／deleteRoleProfile／updateProfileFields／cloneUserProfile）落写，
 * 引擎不再拼装任何指向这两张属主表的语句。</p>
 *
 * <p>本类只留 {@link #ping()}（{@code SELECT 1} 连接自检）：它不读属主表任何事实列，
 * 是引擎自身数据源的存活探针（供 {@code SecurityEngineImpl} 状态面消费），不构成属主语句两存。</p>
 */
@Repository
public class SecurityConfigDao extends JdbcTemplateDaoSupport {

    /** 连接自检（SELECT 1）。 */
    public boolean ping() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
