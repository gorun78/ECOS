package com.chinacreator.gzcm.workspace.repository;

import java.util.List;
import java.util.Optional;

/**
 * 查询历史存储端口（卷07 F07-21 / W185 · X-55）。
 *
 * <p>X-55 的病灶是 {@code QueryHistoryService} 用
 * {@code CopyOnWriteArrayList + MAX_HISTORY=200} 当"存储"：进程重启即失忆、
 * 上限靠截断伪装有界、且没有主体维度隔离。本端口把存储责任外移，
 * 读取一律<b>分页</b>（{@code limit/offset}），不在服务侧持有集合。</p>
 */
public interface QueryHistoryRepository {

    void save(QueryHistoryEntry entry);

    /** 按主体分页读取，新→旧。 */
    List<QueryHistoryEntry> findPage(String subjectId, int limit, int offset);

    Optional<QueryHistoryEntry> findById(String id);

    /** 返回 true = 确实删掉了一行。 */
    boolean deleteById(String id);

    /** 清空指定主体历史；{@code subjectId == null} = 全量清空（仅运维路径）。 */
    int clearBySubject(String subjectId);
}
