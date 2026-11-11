package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * CLI 预检的只读连通探测（SQL 必须住在 DAO 层，controller 内禁止出现 SQL 字面量 —— 6.2.2 分层约束）。
 */
@Repository
public class CliPreflightProbeDao extends JdbcTemplateDaoSupport {

    /**
     * 只读探活：真读一次审批凭证表（与 step 报告里声明的 source 一致，不能用 {@code SELECT 1}
     * 冒充表可达）。异常由调用方按「不可达」处理。
     *
     * <p>{@code COUNT(*)} 在 PG 是 int8，必须用 {@code Long} 承接；{@code queryObject(...Integer)}
     * 走的是 BeanPropertyRowMapper，对标量类型会抛 "No default constructor"（实测 2026-11-11）。</p>
     */
    public boolean ping() {
        Long rows = queryLong("SELECT COUNT(*) FROM ecos_ai.agent_approval");
        return rows != null;
    }
}
