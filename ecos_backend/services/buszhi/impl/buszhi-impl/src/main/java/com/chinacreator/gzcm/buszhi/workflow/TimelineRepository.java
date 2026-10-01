package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.TimelineDao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * 结构化 Timeline 门面 — 替代旧 td_audit_log 的 LIKE 查询。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link TimelineDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / 分页构造 / JSON 序列化 helper / 异常兜底语义
 * 原样由 {@code dao} 层承担，门面原样委托。
 *
 * <p>所有 timeline 事件写入 ecos_object_timeline 表，
 * 提供按对象/实体/事件类型查询的结构化能力。
 */
@Repository
public class TimelineRepository {

    private final TimelineDao dao;

    public TimelineRepository(TimelineDao dao) {
        this.dao = dao;
    }

    /**
     * 记录一条结构化时间线事件（委托 {@code dao}）。
     *
     * @param objectId    对象ID
     * @param entityCode  实体代码
     * @param eventType   事件类型 (ObjectCreated/ObjectUpdated/StatusChanged/ActionExecuted)
     * @param summary     事件摘要
     * @param actor       操作人
     * @param details     事件详情 (JSON)
     */
    public void record(String objectId, String entityCode, String eventType,
                       String summary, String actor, Map<String, Object> details) {
        dao.record(objectId, entityCode, eventType, summary, actor, details);
    }

    /**
     * 查询对象的结构化时间线（分页，按时间倒序）。
     */
    public Map<String, Object> findByObject(String entityCode, String objectId, int page, int size) {
        return dao.findByObject(entityCode, objectId, page, size);
    }

    /**
     * 按实体 + 事件类型查询 timeline（用于审计/统计）。
     */
    public List<Map<String, Object>> findByEntityAndType(String entityCode, String eventType, int limit) {
        return dao.findByEntityAndType(entityCode, eventType, limit);
    }

    /**
     * 获取对象最后一条 timeline 事件（用于获取当前状态等）。
     */
    public Map<String, Object> findLatestByObject(String objectId) {
        return dao.findLatestByObject(objectId);
    }
}
