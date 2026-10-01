package com.chinacreator.gzcm.workspace.service;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对象状态机 Service — 从 ObjectStateMachineController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：从 demo 实体表查询对象当前状态。保持与原 Controller 完全一致的 SQL 语义。</p>
 *
 * <p>B6a 收口（R2 架构铁律 §2.5 / IR06）：raw-SQL 下沉 {@link WorkspaceDataDao}。</p>
 */
@Service
public class ObjectStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStateMachineService.class);

    private final WorkspaceDataDao dao;

    public ObjectStateMachineService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    /**
     * 从 demo 表中查询对象当前状态。
     *
     * @param table      实体对应表名
     * @param objectId   对象 ID
     * @return 当前状态字符串；对象不存在返回 null；状态为空返回 "Draft"
     */
    public String getObjectStatus(String table, String objectId) {
        try {
            List<Map<String, Object>> rows = dao.findStatusById(table, objectId);
            if (rows.isEmpty()) return null;

            Object status = rows.get(0).get("status");
            if (status == null) return "Draft";
            String s = status.toString().trim();
            return s.isEmpty() ? "Draft" : s;
        } catch (Exception e) {
            log.warn("Failed to get status for table/{}: {}", objectId, e.getMessage());
            return null;
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
