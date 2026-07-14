package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A3 PostgreSQL BYTEA 对象存储实现（标准版）。
 * <p>
 * 使用 {@link JdbcTemplate} 操作 {@code ecos_files} 表的 BYTEA 字段进行文件存取。
 * <p>
 * 激活条件：Spring profile {@code standard}。
 *
 * <h3>建表 SQL</h3>
 * <pre>
 * CREATE TABLE IF NOT EXISTS ecos_files (
 *     id           VARCHAR(32) PRIMARY KEY,
 *     content      BYTEA,
 *     content_type VARCHAR(64),
 *     created_at   TIMESTAMP DEFAULT NOW()
 * );
 * </pre>
 */
@Service
@Profile("standard")
public class PgObjectStorageService implements IObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(PgObjectStorageService.class);

    private final JdbcTemplate jdbcTemplate;

    public PgObjectStorageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String putObject(String key, byte[] data, String contentType) {
        String id = (key != null && !key.isBlank()) ? key : UUID.randomUUID().toString().replace("-", "");
        String sql = "INSERT INTO ecos_files (id, content, content_type) VALUES (?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, content_type = EXCLUDED.content_type";
        jdbcTemplate.update(sql, id, data, contentType);
        log.info("A3 Pg putObject: id={}, size={}, type={}", id, data.length, contentType);
        return id;
    }

    @Override
    public byte[] getObject(String key) {
        String sql = "SELECT content FROM ecos_files WHERE id = ?";
        byte[] result = jdbcTemplate.query(sql,
                rs -> rs.next() ? rs.getBytes("content") : null, key);
        if (result == null) {
            log.warn("A3 Pg getObject: key '{}' not found", key);
        } else {
            log.debug("A3 Pg getObject: key={}, size={}", key, result.length);
        }
        return result;
    }

    @Override
    public void deleteObject(String key) {
        String sql = "DELETE FROM ecos_files WHERE id = ?";
        int rows = jdbcTemplate.update(sql, key);
        log.info("A3 Pg deleteObject: key={}, deleted_rows={}", key, rows);
    }
}
