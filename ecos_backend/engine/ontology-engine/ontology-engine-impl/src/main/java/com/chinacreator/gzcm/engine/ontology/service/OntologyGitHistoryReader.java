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
 * 本体版本事件读面（DS-4 本体域第一/二层；2026-11-08）。
 *
 * <p><b>事件源</b>（{@code ontology-model} 仓库，全部是普通 Git 提交）：</p>
 * <ul>
 *   <li>{@code Publish ontology <id> <ver>} → 内容 {@code ontologies/<id>/<ver>.json}，状态 Published；</li>
 *   <li>{@code Draft ontology <id> <ver>} → 内容 {@code ontologies/<id>/drafts/<ver>.json}，状态 Draft；</li>
 *   <li>{@code Deprecate ontology <id> <ver>} → 无内容，状态事件（覆盖为 Deprecated）。</li>
 * </ul>
 *
 * <p>同一版本多次事件取最新一条；Deprecate 晚于内容事件时状态为 Deprecated。
 * 仓库未注册、匹配提交缺文件 ⇒ 显式异常（禁空历史假绿）。</p>
 */
@Component
public class OntologyGitHistoryReader {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitHistoryReader.class);

    static final String REPOSITORY_ID = "ontology-model";
    static final String COMMIT_PREFIX = "Publish ontology ";
    static final String DRAFT_PREFIX = "Draft ontology ";
    static final String DEPRECATE_PREFIX = "Deprecate ontology ";
    static final String ID_PREFIX = "git-";
    static final String CANONICAL_DIR = "ontologies/";

    private final GitService gitService;
    private final GitRepositoryService repositoryService;

    public OntologyGitHistoryReader(GitService gitService, GitRepositoryService repositoryService) {
        this.gitService = gitService;
        this.repositoryService = repositoryService;
    }

    /** 当前全部版本（已发布 + 草稿），按提交时间新→旧；含 Deprecate 覆盖后的状态。 */
    public List<OntologyVersion> listAll(String ontologyId) {
        GitRepository repo = requireRepository();
        Map<String, OntologyVersion> byVersion = new LinkedHashMap<>();
        Map<String, Boolean> deprecated = new LinkedHashMap<>();
        for (GitCommitView c : commits(repo)) {
            String msg = subject(c);
            Event e = parse(msg);
            if (e == null) {
                continue;
            }
            if (ontologyId != null && !ontologyId.isBlank() && !ontologyId.equals(e.ontologyId)) {
                continue;
            }
            if (DEPRECATE_PREFIX.equals(e.prefix)) {
                deprecated.put(e.ontologyId + "@" + e.versionNo, true);
                continue;
            }
            String key = e.ontologyId + "@" + e.versionNo;
            if (byVersion.containsKey(e.versionNo) && ontologyId != null && !ontologyId.isBlank()) {
                // 同版本已有更新提交（提交按新→旧遍历），状态与内容均以更新者为准
                continue;
            }
            if (byVersion.containsKey(key)) {
                continue;
            }
            OntologyVersion v = toVersion(repo, c, e);
            byVersion.put(ontologyId == null || ontologyId.isBlank() ? key : e.versionNo, v);
        }
        List<OntologyVersion> out = new ArrayList<>(byVersion.values());
        for (OntologyVersion v : out) {
            if (deprecated.containsKey(v.getOntologyId() + "@" + v.getVersionNo())) {
                v.setStatus("Deprecated");
            }
        }
        return out;
    }

    /** 已发布版本（不含草稿/弃用过滤；弃用状态保留在 status 上）。 */
    public List<OntologyVersion> listPublished(String ontologyId) {
        return listAll(ontologyId).stream()
                .filter(v -> !"Draft".equals(v.getStatus()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 草稿版本（未被发布/弃用覆盖的最新内容事件为 Draft）。 */
    public List<OntologyVersion> listDrafts(String ontologyId) {
        return listAll(ontologyId).stream()
                .filter(v -> "Draft".equals(v.getStatus()))
                .collect(java.util.stream.Collectors.toList());
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
            Event e = parse(subject(c));
            if (e == null || DEPRECATE_PREFIX.equals(e.prefix)) {
                return Optional.empty();
            }
            OntologyVersion v = toVersion(repo, c, e);
            return Optional.of(v);
        }
        return Optional.empty();
    }

    /** 按本体 + 版本号取最新内容事件（Draft 或 Published）。 */
    public Optional<OntologyVersion> findVersion(String ontologyId, String versionNo) {
        return listAll(ontologyId).stream()
                .filter(v -> versionNo != null && versionNo.equals(v.getVersionNo()))
                .findFirst();
    }

    // ── 事件解析 ─────────────────────────────────────────

    private record Event(String prefix, String ontologyId, String versionNo) {
    }

    private static Event parse(String msg) {
        String prefix;
        if (msg.startsWith(COMMIT_PREFIX)) {
            prefix = COMMIT_PREFIX;
        } else if (msg.startsWith(DRAFT_PREFIX)) {
            prefix = DRAFT_PREFIX;
        } else if (msg.startsWith(DEPRECATE_PREFIX)) {
            prefix = DEPRECATE_PREFIX;
        } else {
            return null;
        }
        String rest = msg.substring(prefix.length()).trim();
        int at = rest.lastIndexOf(' ');
        if (at <= 0 || at == rest.length() - 1) {
            return null;
        }
        return new Event(prefix, rest.substring(0, at).trim(), rest.substring(at + 1).trim());
    }

    private static String subject(GitCommitView c) {
        return c.getMessage() == null ? "" : c.getMessage().trim();
    }

    private OntologyVersion toVersion(GitRepository repo, GitCommitView c, Event e) {
        boolean draft = DRAFT_PREFIX.equals(e.prefix);
        String path = CANONICAL_DIR + e.ontologyId + (draft ? "/drafts/" : "/") + e.versionNo + ".json";
        Optional<String> content;
        try {
            content = gitService.readFileAtRef(repo, c.getCommitId(), path);
        } catch (GitService.GitException ex) {
            throw new IllegalStateException("Git 历史内容读取失败: " + path + "@"
                    + shortSha(c.getCommitId()) + " - " + ex.getMessage(), ex);
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Git 提交存在但归档文件缺失: " + path + "@"
                    + shortSha(c.getCommitId()) + "（历史完整性破坏，禁静默跳过）");
        }
        OntologyVersion v = new OntologyVersion();
        v.setId(ID_PREFIX + c.getCommitId());
        v.setOntologyId(e.ontologyId);
        v.setVersionNo(e.versionNo);
        v.setStatus(draft ? "Draft" : "Published");
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
