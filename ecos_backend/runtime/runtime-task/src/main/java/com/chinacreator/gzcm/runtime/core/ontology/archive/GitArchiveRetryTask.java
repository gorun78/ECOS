package com.chinacreator.gzcm.runtime.core.ontology.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * F03-15 (W83/C66) — 本体版本 × Git 归档补偿队列消费侧。
 *
 * <p>铁律 §2.5-4「长运行任务不走各引擎内自建 @Scheduled」——本任务居 runtime-task，
 * 由 gateway fat-JAR 统一调度（monster-state；split-state 由 runtime-task 服务开机启动）。
 *
 * <p>与 {@code AuditRetryTask} 同族同口径：60s 扫一次 pending、指数退避 1/2/4min，
 * 3 次失败 → 标 exhausted + critical 告警，不回滚发布。
 *
 * <p>调用链解耦：本任务不 import {@code OntologyGitService}（避免 runtime 反依赖 engine）；
 * 由 {@link GitArchiveReplayer} lambda 承接实际归档动作。同 JVM 下由 engine
 * 注册的 {@code @Component} 提供实现（gateway fat-JAR 组件扫描覆盖）。
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class GitArchiveRetryTask {

    public static final long INTERVAL_MS = 60_000L;
    public static final int MAX_ATTEMPTS = 3;
    public static final int BATCH = 50;
    private static final String T = "public.ecos_ontology_git_archive_pending";

    private static final Logger log = LoggerFactory.getLogger(GitArchiveRetryTask.class);

    private final JdbcTemplate jdbc;
    private final GitArchiveReplayer replayer;
    private final GitArchiveFailureSink failureSink;

    public GitArchiveRetryTask(JdbcTemplate jdbc) {
        this(jdbc, (repoId, targetVersionNo, commitMessage, authorName, payload) ->
                ReplayerResult.fail("runtime-task 占位 replayer：缺引擎侧注入（monolith 态才有真 OntologyGitService）"),
            (sev, m) -> log.warn("[ontology-git-archive] ALERT [{}] {}", sev, m));
    }

    public GitArchiveRetryTask(JdbcTemplate jdbc,
                                GitArchiveReplayer replayer,
                                GitArchiveFailureSink failureSink) {
        this.jdbc = jdbc;
        this.replayer = replayer == null ? ((r, v, c, a, p) -> ReplayerResult.fail("replayer=null")) : replayer;
        this.failureSink = failureSink == null ? ((s, m) ->
                log.warn("[ontology-git-archive] ALERT [{}] {}", s, m)) : failureSink;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void replayDue() {
        Timestamp now = Timestamp.from(Instant.now());
        List<String> ids = jdbc.queryForList(
            "SELECT id FROM " + T
                + " WHERE is_deleted = 0 AND status = 'pending' AND attempts < " + MAX_ATTEMPTS
                + "   AND next_retry_at <= ? ORDER BY next_retry_at ASC LIMIT " + BATCH,
            String.class, now);
        if (ids.isEmpty()) return;

        int ok = 0, escalated = 0;
        for (String id : ids) {
            Map<String, Object> row = fetch(id);
            if (row == null) continue;

            ReplayerResult res;
            try {
                res = replayer.replay(
                    String.valueOf(row.get("repositoryId")),
                    String.valueOf(row.get("targetVersionNo")),
                    row.get("commitMessage") == null ? "" : String.valueOf(row.get("commitMessage")),
                    row.get("authorName") == null ? "system" : String.valueOf(row.get("authorName")),
                    row.get("payload") == null ? "" : String.valueOf(row.get("payload")));
            } catch (Exception ex) {
                log.warn("[ontology-git-archive] replay 抛异常 id={} err={}", id, ex.getMessage());
                res = ReplayerResult.fail(ex.getMessage());
            }

            if (res.isSuccess()) {
                jdbc.update("UPDATE " + T
                        + " SET status = 'closed', last_commit_id = ?, update_time = NOW() WHERE id = ?",
                    res.getCommitId(), id);
                ok++;
            } else {
                int attempts = currentAttempts(row);
                if (attempts + 1 >= MAX_ATTEMPTS) {
                    markExhausted(id, res.getError());
                    failureSink.alert("critical",
                        "ontology-git-archive " + MAX_ATTEMPTS + " 次仍失败，需人工介入：id=" + id
                        + " ontology_id=" + row.get("ontologyId")
                        + " version=" + row.get("targetVersionNo"));
                    escalated++;
                } else {
                    long delayMinutes = 1L << (attempts + 1); // 1→2, 2→4
                    jdbc.update("UPDATE " + T
                            + " SET attempts = attempts + 1, last_error = ?,"
                            + "   next_retry_at = NOW() + INTERVAL '1 minute' * " + delayMinutes + ","
                            + "   update_time = NOW() WHERE id = ?",
                        clamp(res.getError()), id);
                }
            }
        }
        if (ok > 0 || escalated > 0) {
            log.info("[ontology-git-archive] tick total={} ok={} escalated={}", ids.size(), ok, escalated);
        }
    }

    private Map<String, Object> fetch(String id) {
        try {
            return jdbc.queryForMap(
                "SELECT id, ontology_id AS ontologyId, version_id AS versionId,"
                    + " target_version_no AS targetVersionNo, repository_id AS repositoryId,"
                    + " commit_message AS commitMessage, author_name AS authorName,"
                    + " payload, attempts FROM " + T
                    + " WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            return null;
        }
    }

    private int currentAttempts(Map<String, Object> row) {
        Object a = row.get("attempts");
        if (a instanceof Number n) return n.intValue();
        return 0;
    }

    private void markExhausted(String id, String reason) {
        jdbc.update("UPDATE " + T
                + " SET status = 'exhausted', last_error = ?, update_time = NOW() WHERE id = ?",
            clamp(reason), id);
    }

    private static String clamp(String s) {
        if (s == null) return "replay-failed";
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    /** 单归档重放结果。 */
    public static final class ReplayerResult {
        private final boolean ok;
        private final String commitId;
        private final String ref;
        private final String error;

        private ReplayerResult(boolean ok, String commitId, String ref, String error) {
            this.ok = ok; this.commitId = commitId; this.ref = ref; this.error = error;
        }
        public static ReplayerResult ok(String commitId, String ref) { return new ReplayerResult(true, commitId, ref, null); }
        public static ReplayerResult fail(String error) { return new ReplayerResult(false, null, null, error); }
        public boolean isSuccess() { return ok; }
        public String getCommitId() { return commitId; }
        public String getRef() { return ref; }
        public String getError() { return error; }
    }

    /** 单次归档执行契约：runtime-task 不 import OntologyGitService，隔离 cross-module 反依赖。 */
    @FunctionalInterface
    public interface GitArchiveReplayer {
        ReplayerResult replay(String repositoryId, String targetVersionNo,
                              String commitMessage, String authorName, String payload);
    }

    /** 3 次仍失败的告警下沉（01 册 runtime-monitor 门禁可替换）。 */
    @FunctionalInterface
    public interface GitArchiveFailureSink {
        void alert(String severity, String message);
    }
}
