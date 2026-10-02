package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * AOP 审计拦截层数据访问 DAO — 承接 {@code AuditAspect} 中的 JdbcTemplate 操作。
 * 基础审计走 IAuditLogService，此 DAO 只处理 full 模式下的哈希链双写辅助查询。
 *
 * <p><b>显式 bean name</b>：{@code @Repository("auditLogJdbcDao")}。与
 * {@code com.chinacreator.gzcm.sysman.audit.dao.AuditLogDao}（MyBatis mapper 接口，
 * 基类 bean 名 {@code auditLogDao}，由 {@code @MapperScan com.chinacreator.gzcm.sysman.**.dao}
 * 自动注册）默认同 simple-name 冲突。gateway / sysman / dccheng 三个 fat-JAR 都命中
 * {@code com.chinacreator.gzcm.sysman.**.dao} 扫描，若两同 simple-name Bean 都存活会
 * 触发 {@code ConflictingBeanDefinitionException}（2026-10-02 Gate C 实测）。
 * 本类是 JdbcTemplate 通道（R2 数据访问收口产物），与 MyBatis interface 完全独立；
 * 显式命名后两者可共存（interface 面向 MyBatis XML/mapper 声明，本类面向 raw-SQL 哈希链
 * 双写辅助查询）。
 */
@Repository("auditLogJdbcDao")
public class AuditLogDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /**
     * R1.2: 查询用户的 audit_mode（basic/detailed/full）。
     * <p>查询 td_user_security_profile 表，异常或无配置时由调用方降级为 "basic"。
     */
    public String queryAuditMode(String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT audit_mode FROM td_user_security_profile " +
                "WHERE scope_type = 'user' AND user_id = ? LIMIT 1",
                userId);
        if (!rows.isEmpty() && rows.get(0).get("audit_mode") != null) {
            String mode = rows.get(0).get("audit_mode").toString().trim().toLowerCase();
            if (!mode.isBlank()) {
                return mode;
            }
        }
        return null;
    }

    /**
     * R1.2 full 模式: 向 ecos_audit_log 哈希链表写入行，返回自增 id（bigint）。
     */
    public Long insertHashChainAudit(String username, String operation, String entityType,
                                     String entityId, String changesJson, String ipAddress, String category) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO ecos_audit_log (username, operation, entity_type, entity_id, changes, ip_address, created_at, category) " +
                "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, NOW(), ?) RETURNING id",
                Long.class,
                username,
                operation,
                entityType,
                entityId,
                changesJson,
                ipAddress,
                category);
    }
}
