package com.chinacreator.gzcm.engine.ontology.repository;

import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.LineageEventDao;
import com.chinacreator.gzcm.engine.ontology.model.LineageEvent;
import org.springframework.stereotype.Repository;

/**
 * 血缘事件 JDBC 仓库 — kb_lineage_event 表 CRUD。
 *
 * <p>将 LineageController 的 ConcurrentHashMap 内存存储持久化到 PostgreSQL。
 *
 * <p>逻辑删除：所有查询过滤 {@code is_deleted = 0}，
 * 删除操作设置 {@code is_deleted = 1}（铁律 4.8）。
 *
 * <p>R2/B3c：JDBC 全量下沉至 {@link LineageEventDao}，本类仅保留公开 API 透传。
 */
@Repository
public class LineageEventRepository {

    private final LineageEventDao dao;

    public LineageEventRepository(LineageEventDao dao) {
        this.dao = dao;
    }

    /**
     * 插入一条血缘事件记录。
     *
     * @param eventId   事件业务 ID (UUID)
     * @param query     解析时的查询/输入描述
     * @param format    血缘格式: openlineage/atlas
     * @param nodesJson 节点列表 JSON
     * @param edgesJson 边列表 JSON
     * @return 影响行数
     */
    public int insert(String eventId, String query, String format, String nodesJson, String edgesJson) {
        return dao.insert(eventId, query, format, nodesJson, edgesJson);
    }

    /**
     * 更新一条血缘事件记录。
     *
     * @param eventId   事件业务 ID
     * @param query     查询描述（null 表示不更新）
     * @param format    格式（null 表示不更新）
     * @param nodesJson 节点 JSON（null 表示不更新）
     * @param edgesJson 边 JSON（null 表示不更新）
     * @return 影响行数
     */
    public int update(String eventId, String query, String format, String nodesJson, String edgesJson) {
        return dao.update(eventId, query, format, nodesJson, edgesJson);
    }

    /**
     * 按事件业务 ID 查询（仅未删除）。
     */
    public Optional<LineageEvent> findByEventId(String eventId) {
        return dao.findByEventId(eventId);
    }

    /**
     * 列出所有未删除的血缘事件，按解析时间倒序。
     */
    public List<LineageEvent> findAll() {
        return dao.findAll();
    }

    /**
     * 按格式过滤查询（仅未删除），按解析时间倒序。
     */
    public List<LineageEvent> findByFormat(String format) {
        return dao.findByFormat(format);
    }

    /**
     * 逻辑删除（铁律 4.8）。
     */
    public int logicalDelete(String eventId) {
        return dao.logicalDelete(eventId);
    }
}
