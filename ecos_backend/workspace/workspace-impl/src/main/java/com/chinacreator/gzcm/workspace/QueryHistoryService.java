package com.chinacreator.gzcm.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 查询历史服务 — 内存存储（MVP 阶段）。
 * ObjectQL / NLQ 执行成功后自动调用 {@link #save}。
 */
@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);
    private static final int MAX_HISTORY = 200;

    private final List<QueryRecord> records = new CopyOnWriteArrayList<>();

    /**
     * 保存一条查询记录。
     */
    public QueryRecord save(String question, String queryJson, int resultCount) {
        QueryRecord r = new QueryRecord(question, queryJson, resultCount);
        records.add(0, r); // 新记录插到最前面
        // 超过上限移除最旧的
        while (records.size() > MAX_HISTORY) {
            records.remove(records.size() - 1);
        }
        log.debug("QueryHistory: saved record id={}, total={}", r.getId(), records.size());
        return r;
    }

    /**
     * 获取最近 N 条历史记录。
     */
    public List<QueryRecord> getHistory(int limit) {
        int end = Math.min(limit, records.size());
        return new ArrayList<>(records.subList(0, end));
    }

    /**
     * 删除指定记录，成功返回 true。
     */
    public boolean delete(String id) {
        boolean removed = records.removeIf(r -> r.getId().equals(id));
        if (removed) {
            log.debug("QueryHistory: deleted record id={}, remaining={}", id, records.size());
        }
        return removed;
    }

    /**
     * 清空所有历史。
     */
    public void clear() {
        records.clear();
    }
}
