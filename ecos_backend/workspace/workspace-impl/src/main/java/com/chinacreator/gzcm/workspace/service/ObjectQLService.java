package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ObjectQL 查询 Service — 从 ObjectQLController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：执行 entity 存在性校验与 ObjectQL 查询 SQL，保持与原 Controller 完全一致的 SQL 语义。</p>
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL 下沉 {@link WorkspaceDataDao}。</p>
 */
@Service
public class ObjectQLService {

    private static final Logger log = LoggerFactory.getLogger(ObjectQLService.class);

    private final WorkspaceDataDao dao;

    public ObjectQLService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    /**
     * 校验 link 目标实体是否在 ecos_ontology_entity 表中存在。
     * 返回记录数，调用方判断 ==0 即不存在。
     */
    public Integer countEntityByCode(String entityCode) {
        return dao.countEntityByCode(entityCode);
    }

    /**
     * 无参查询（params 为空时使用）。
     */
    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }

    /**
     * 带参查询（params 非空时使用）。
     */
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }

    // ── PMO-E2 通用委托方法（B6a 收口至 DAO） ──
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return dao.queryForMap(sql, args);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
