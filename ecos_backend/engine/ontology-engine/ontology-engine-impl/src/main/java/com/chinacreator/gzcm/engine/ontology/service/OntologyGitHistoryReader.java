package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本体版本历史 Git 读面（DS-4 本体域第一批；2026-11-08）。
 *
 * <p><b>真源</b>：已发布版本的内容与提交元数据直接从 {@code ontology-model} 仓库读取——
 * 提交消息 {@code Publish ontology <ontologyId> <versionNo>} 是版本事件的权威记录，
 * 内容在 {@code ontologies/<ontologyId>/<versionNo>.json}。不再依赖
 * {@code ecos_ontology_version.git_ref} 索引才能读历史（DB 只保留草稿/在工作态）。</p>
 *
 * <p><b>诚实语义</b>：仓库未注册/不可读、匹配提交的归档文件缺失 ⇒ 显式异常，
 * 禁返回空历史（"读不到" ≠ "没有版本"）。历史条数受 {@link GitService#MAX_COMMIT_LOG_LIMIT}
 * 约束，超出部分需分页（本轮上限登记为已知边界）。</p>
 *
 * <p><b>id 方案</b>：{@code git-<commitId>}（40 位 sha 前缀为 {@code git-}）。与旧库内
 * {@code ver5001} 型 id 并存：草稿仍走库，已发布版本走 Git；详情端点按前缀分派。</p>
 */
@Component
public class OntologyGitHistoryReader {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitHistoryReader.class);

    static final String REPOSITORY_ID = "ontology-model";
    static final String COMMIT_PREFIX = "Publish ontology ";
    static final String PATH_PREFIX = "ontologies/";
    static final String ID_PREFIX = "git-";

    private final GitService gitService;
    private final GitRepositoryService repositoryService;

    public OntologyGitHistoryReader(GitService gitService, GitRepositoryService repositoryService) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    /** 已发布版本（新→旧，按版本号去重取最新提交）。 */
    public List<OntologyVersion> listPublished(String ontologyId) {
        GitRepository repo = requireRepository();
        String prefix = ontologyId == null || ontologyId.isBlank()
                ? COMMIT_PREFIX : COMMIT_PREFIX + ontologyId + " ";
        Map<String, OntologyVersion> byVersion = new LinkedHashMap<>();
        for (GitCommitView c : commits(repo)) {
            String msg = c.getMessage() == null ? "" : c.getMessage().trim();
            if (!msg.startsWith(prefix)) {
                continue;
            }
            String rest = msg.substring(COMMIT_PREFIX.length()).trim();
            int at = rest.lastIndexOf(' ');
            if (at <= 0 || at == rest.length() - 1) {
                log.warn("Git 提交消息无法解析版本（跳过但留痕）: commit={} msg={}", c.getCommitId(), msg);
                continue;
            }
            String oid = rest.substring(0, at).trim();
            String versionNo = rest.substring(at + 1).trim();
            if (ontologyId != null && !ontologyId.isBlank() && !ontologyId.equals(oid)) {
                continue;
            }
            OntologyVersion v = toVersion(repo, c, oid, versionNo);
            byVersion.putIfAbsent(versionNo, v);
        }
        return new ArrayList<>(byVersion.values());
    }

    /** 按 {@code git-<sha>} 取单版本；非该前缀返回 empty（交由库内草稿读面）。 */
    public Optional<OntologyVersion> findById(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        String sha = id.substring(ID_PREFIX.length()).trim();
        if (sha.isEmpty()) {
            return Optional.empty();
        }
        GitRepository repo = requireRepository();
        for (GitCommitView c : commits(repo)) {
            if (!sha.equals(c.getCommitId())) {
                continue;
            }
            String msg = c.getMessage() == null ? "" : c.getMessage().trim();
            if (!msg.startsWith(COMMIT_PREFIX)) {
                return Optional.empty();
            }
            String rest = msg.substring(COMMIT_PREFIX.length()).trim();
            int at = rest.lastIndexOf(' ');
            if (at <= 0) {
                throw new IllegalStateException("Git 提交消息无法解析版本: " + msg);
            }
            return Optional.of(toVersion(repo, c,
                    rest.substring(0, at).trim(), rest.substring(at + 1).trim()));
        }
        return Optional.empty();
    }

    /** 按本体 + 版本号取已发布版本（内容读自该提交）。 */
    public Optional<OntologyVersion> findPublished(String ontologyId, String versionNo) {
        return listPublished(ontologyId).stream()
                .filter(v -> versionNo != null && versionNo.equals(v.getVersionNo()))
                .findFirst();
    }

    private OntologyVersion toVersion(GitRepository repo, GitCommitView c,
                                      String ontologyId, String versionNo) {
        String path = PATH_PREFIX + ontologyId + "/" + versionNo + ".json";
        Optional<String> content;
        try {
            content = gitService.readFileAtRef(repo, c.getCommitId(), path);
        } catch (GitService.GitException e) {
            throw new IllegalStateException("Git 历史内容读取失败: " + path + "@" + shortSha(c.getCommitId())
                    + " - " + e.getMessage(), e);
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Git 提交存在但归档文件缺失: " + path + "@"
                    + shortSha(c.getCommitId()) + "（历史完整性破坏，禁静默跳过）");
        }
        OntologyVersion v = new OntologyVersion();
        v.setId(ID_PREFIX + c.getCommitId());
        v.setOntologyId(ontologyId);
        v.setVersionNo(versionNo);
        v.setStatus("Published");
        v.setSnapshot(content.get());
        v.setChangeLog(c.getMessage());
        v.setPublisher(c.getAuthorName());
        v.setGitRef(c.getCommitId());
        LocalDateTime at = toLocal(c.getCommittedAt());
        v.setPublishedAt(at);
        v.setCreatedAt(at);
        return v;
    }

    private GitRepository requireRepository() {
        try {
            GitRepository repo = repositoryService.getRepositoryById(REPOSITORY_ID);
            if (repo == null || repo.getLocalPath() == null || repo.getLocalPath().isBlank()) {
                throw new IllegalStateException("Git 仓库未解析 localPath: " + REPOSITORY_ID);
            }
            return repo;
        } catch (GitRepositoryService.GitRepositoryException e) {
            throw new IllegalStateException("Git 仓库未注册: " + REPOSITORY_ID + " - " + e.getMessage(), e);
        }
    }

    private List<GitCommitView> commits(GitRepository repo) {
        try {
            List<GitCommitView> rows = gitService.listCommits(repo, 0, GitService.MAX_COMMIT_LOG_LIMIT);
            return rows == null ? List.of() : rows;
        } catch (GitService.GitException e) {
            throw new IllegalStateException("Git 提交历史读取失败: " + e.getMessage(), e);
        }
    }

    private static LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }

    private static String shortSha(String sha) {
        return sha == null ? "null" : sha.substring(0, Math.min(10, sha.length()));
    }
}
