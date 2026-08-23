package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final JdbcTemplate jdbcTemplate;

    public SecurityAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async
    public void asyncWrite(String id, String userId, String username, String action,
                           String resource, String result, String detail, String ipAddress) {
        try {
            jdbcTemplate.update(
                "INSERT INTO ecos_audit_log (id, user_id, username, action, resource, result, detail, ip_address) VALUES (?,?,?,?,?,?,?,?)",
                id, userId, username, action, resource, result, detail, ipAddress
            );
            log.debug("审计日志写入成功: id={}, action={}", id, action);
        } catch (Exception e) {
            log.error("审计日志异步写入失败: id={}, action={}", id, action, e);
        }
    }
}
