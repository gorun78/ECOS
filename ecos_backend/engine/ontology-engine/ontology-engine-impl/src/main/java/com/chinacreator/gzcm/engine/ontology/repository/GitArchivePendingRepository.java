package com.chinacreator.gzcm.engine.ontology.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.dao.GitArchivePendingDao;
import org.springframework.stereotype.Repository;

/**
 * 本体版本 Git 归档补偿队列（W83/C66 / F03-15）—
 * 生产侧（本体引擎）只做「未 COMMITTED → 入队」；消费侧在 runtime/runtime-task。
 *
 * <p>表 {@code ecos_ontology.ecos_ontology_git_archive_pending}（V170.2）。
 * 本体控制面 schema（与 V244 {@code ecos_runtime_audit_retry} 同族，同由 runtime-task 统一驱动）。
 *
 * <p>红线：本仓库不做 {@code @Scheduled} 重试（铁律 §2.5-4）；
 * 唯一动作是 enqueue 一次、查自己列入队计数、markClosed/markExhausted 三方法。
 *
 * <p>R2/B3c：JDBC 全量下沉至 {@link GitArchivePendingDao}，本类仅保留 schema 语义说明与公开 API。
 */
@Repository
public class GitArchivePendingRepository {

    private final GitArchivePendingDao dao;

    public GitArchivePendingRepository(GitArchivePendingDao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> listPendingDue(Timestamp now, int limit) {
        return dao.listPendingDue(now, limit);
    }

    public Optional<Map<String, Object>> findById(String id) {
        return dao.findById(id);
    }

    /**
     * 发布成功但 Git 归档非 COMMITTED 时入队。
     *
     * <p>MC01：id 由应用侧生成。
     * DR07：版本行固定 1.0.0；本行不升。
     */
    public String enqueue(String ontologyId, String versionId, String targetVersionNo,
                          String repositoryId, String commitMessage, String authorName,
                          String payload, String traceId) {
        return dao.enqueue(ontologyId, versionId, targetVersionNo, repositoryId,
            commitMessage, authorName, payload, traceId);
    }

    /** attempts+1、指数退避 1min&lt;&lt;attempts。返回 false 表示 attempts 已>=3，应 markExhausted。 */
    public boolean bumpAttempt(String id, long delayMinutes) {
        return dao.bumpAttempt(id, delayMinutes);
    }

    public int markClosed(String id, String commitId) {
        return dao.markClosed(id, commitId);
    }

    public int markExhausted(String id, String lastError) {
        return dao.markExhausted(id, lastError);
    }
}
