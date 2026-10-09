package com.chinacreator.gzcm.workspace;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.workspace.repository.QueryHistoryEntry;
import com.chinacreator.gzcm.workspace.repository.QueryHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 查询历史服务 —— 存储责任外移（卷07 F07-21 / W185 · X-55）。
 *
 * <p>X-55 的原实现用 {@code CopyOnWriteArrayList} + {@code MAX_HISTORY=200} 当存储：
 * 进程重启即失忆、"有界"靠截断伪装、无主体隔离、无 traceId 无法关联审计。
 * 现在本类<b>不持有任何集合</b>，写入委托 {@link QueryHistoryRepository}，
 * 读取按主体分页。</p>
 *
 * <p>现状注记：落库表 {@code public.ecos_scenario_query_history} 的 V212 迁移脚本
 * 已落 {@code docs/50-db_script}，但按 §14.4 <b>未实跑库</b>，故 repository bean
 * 默认缺席（见 {@code JdbcQueryHistoryRepository} 的 {@code @ConditionalOnProperty}）。
 * 缺席时本类返回<b>空分页 + 一条 WARN</b>，绝不退化成堆内存列表 ——
 * "看起来能用但重启就没了"正是本项要消灭的假功能。</p>
 */
@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);

    /** 单次读取上限；分页语义由 repository 的 LIMIT/OFFSET 承担，不是堆内截断。 */
    static final int MAX_PAGE_SIZE = 100;

    private final ObjectProvider<QueryHistoryRepository> repositoryProvider;

    public QueryHistoryService(ObjectProvider<QueryHistoryRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    /**
     * 保存一条查询记录。返回体保持既有 {@link QueryRecord} 形状（响应契约只增不改）。
     */
    public QueryRecord save(String question, String queryJson, int resultCount) {
        String id = UUID.randomUUID().toString();
        QueryHistoryEntry entry = new QueryHistoryEntry(
                id, currentSubjectId(), question, digest(queryJson), resultCount,
                EcosTraceContext.currentTraceId(), LocalDateTime.now());

        QueryHistoryRepository repo = repositoryProvider.getIfAvailable();
        if (repo == null) {
            log.warn("QueryHistory 持久化未启用（V212 未实跑或 "
                    + "ecos.workspace.query-history.persistent=false），本次记录丢弃: id={} traceId={}",
                    id, entry.traceId());
        } else {
            repo.save(entry);
            log.debug("QueryHistory: saved id={} subject={} traceId={}",
                    id, entry.subjectId(), entry.traceId());
        }

        QueryRecord r = new QueryRecord(question, queryJson, resultCount);
        r.setId(id);
        r.setTimestamp(entry.createdAt());
        return r;
    }

    /** 读取当前主体最近 {@code limit} 条（分页读，非堆内 subList）。 */
    public List<QueryRecord> getHistory(int limit) {
        return getHistory(limit, 0);
    }

    /** 分页读取（新增能力，供后续 UI 翻页；旧签名仍走 offset=0）。 */
    public List<QueryRecord> getHistory(int limit, int offset) {
        int size = Math.max(0, Math.min(limit, MAX_PAGE_SIZE));
        QueryHistoryRepository repo = repositoryProvider.getIfAvailable();
        if (repo == null) {
            return List.of();
        }
        return repo.findPage(currentSubjectId(), size, Math.max(0, offset)).stream()
                .map(QueryHistoryService::toRecord)
                .toList();
    }

    /** 删除指定记录，成功返回 true。 */
    public boolean delete(String id) {
        QueryHistoryRepository repo = repositoryProvider.getIfAvailable();
        if (repo == null) {
            return false;
        }
        boolean removed = repo.deleteById(id);
        if (removed) {
            log.debug("QueryHistory: deleted record id={}", id);
        }
        return removed;
    }

    /** 清空当前主体历史（不做全量清库，运维路径另议）。 */
    public void clear() {
        QueryHistoryRepository repo = repositoryProvider.getIfAvailable();
        if (repo != null) {
            repo.clearBySubject(currentSubjectId());
        }
    }

    private static QueryRecord toRecord(QueryHistoryEntry e) {
        QueryRecord r = new QueryRecord(e.question(), null, e.rowCount());
        r.setId(e.id());
        r.setTimestamp(e.createdAt());
        return r;
    }

    /** 主体口径与 security-engine 审计一致：SecurityContext 优先，匿名兜底。 */
    private static String currentSubjectId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !auth.getName().isBlank() && !"anonymous".equals(auth.getName())) {
            return auth.getName();
        }
        return "anonymous";
    }

    /** DSL 摘要（不落原文）—— 历史面只关心"查过什么形状"，不复制第二份可执行查询库。 */
    private static String digest(String queryJson) {
        if (queryJson == null) {
            return "";
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(queryJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, h.length); i++) {
                sb.append(String.format("%02x", h[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
