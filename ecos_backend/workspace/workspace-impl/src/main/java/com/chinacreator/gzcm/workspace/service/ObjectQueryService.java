package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ObjectQueryService — Object 查询服务（PMO-E2: 从 ObjectController 下沉的 JdbcTemplate 访问层）。
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL 委托下沉 {@link WorkspaceDataDao}。</p>
 */
@Service
public class ObjectQueryService {

    private final WorkspaceDataDao dao;

    public ObjectQueryService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }
    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
