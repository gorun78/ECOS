package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.common.service.IObjectStorageService;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A3 PostgreSQL BYTEA 对象存储实现（标准版）。
 * <p>
 * 操作 {@code ecos_files} 表的 BYTEA 字段进行文件存取（raw-SQL 下沉
 * {@link WorkspaceDataDao}）。<br>
 * 激活条件：Spring profile {@code standard}。
 *
 * <h3>目标表</h3>
 * {@code ecos_files}（列：id / content BYTEA / content_type / created_at）。
 * 该表建表脚本由 db/migration 单一来源维护，运行时不再内嵌建表语句（H8-T1）。
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：SQL 由 DAO 承载，语义与调用点日志等级不变。</p>
 */
@Service
@Profile("standard")
public class PgObjectStorageService implements IObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(PgObjectStorageService.class);

    private final WorkspaceDataDao dao;

    public PgObjectStorageService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    @Override
    public String putObject(String key, byte[] data, String contentType) {
        String id = (key != null && !key.isBlank()) ? key : UUID.randomUUID().toString().replace("-", "");
        dao.upsertFile(id, data, contentType);
        log.info("A3 Pg putObject: id={}, size={}, type={}", id, data.length, contentType);
        return id;
    }

    @Override
    public byte[] getObject(String key) {
        byte[] result = dao.getFileContent(key);
        if (result == null) {
            log.warn("A3 Pg getObject: key '{}' not found", key);
        } else {
            log.debug("A3 Pg getObject: key={}, size={}", key, result.length);
        }
        return result;
    }

    @Override
    public void deleteObject(String key) {
        int rows = dao.deleteFile(key);
        log.info("A3 Pg deleteObject: key={}, deleted_rows={}", key, rows);
    }
}
