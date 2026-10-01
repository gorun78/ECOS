package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对象 Action Service — 从 ObjectActionController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：Action 定义查询、对象存在性校验、可用 Action 列表查询。
 * 保持与原 Controller 完全一致的 SQL 语义。</p>
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL 下沉 {@link WorkspaceDataDao}，
 * SQL 文本与原实现逐字节一致；本层保留方法签名 / 异常语义不变。</p>
 */
@Service
public class ObjectActionService {

    private static final Logger log = LoggerFactory.getLogger(ObjectActionService.class);

    private final WorkspaceDataDao dao;

    public ObjectActionService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    /**
     * 验证对象是否存在（按表名 + id 查询）。
     * 返回查询结果行（非空即存在），异常时调用方按 debug 忽略。
     */
    public List<Map<String, Object>> findObjectById(String table, String id) {
        return dao.findObjectById(table, id);
    }

    /**
     * 按 entity code 查询可用 Action 列表（status = 'ACTIVE'）。
     */
    public List<Map<String, Object>> listAvailableActions(String entityCode) {
        return dao.listAvailableActions(entityCode);
    }

    /**
     * 按 action code 查找 Action 定义（name 或 id 匹配，LIMIT 1）。
     * 异常时记录日志并返回空列表。
     */
    public List<Map<String, Object>> findActionByCode(String actionCode) {
        try {
            return dao.findActionByCode(actionCode);
        } catch (Exception e) {
            log.warn("Failed to find action {}: {}", actionCode, e.getMessage());
            return List.of();
        }
    }

    // ── PMO-E2 通用委托方法（B6a 收口至 DAO） ──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }
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
