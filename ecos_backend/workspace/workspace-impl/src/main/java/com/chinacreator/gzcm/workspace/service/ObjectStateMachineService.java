package com.chinacreator.gzcm.workspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对象状态机 Service — 从 ObjectStateMachineController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：从 demo 实体表查询对象当前状态。保持与原 Controller 完全一致的 SQL 语义。</p>
 */
@Service
public class ObjectStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStateMachineService.class);

    private final JdbcTemplate jdbc;

    public ObjectStateMachineService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status FROM " + table + " WHERE id = ?", objectId);
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
}
