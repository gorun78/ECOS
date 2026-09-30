package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体 Git 归档适配层 —— 委托 runtime 唯一 Git 出口 {@link GitService}（架构铁律 §2.5，VG-2）。
 *
 * <p>H4-T4 真实落地：版本资产内容写入工作树 → {@code commitAllChanges} →
 * 按 {@code {assetType}/{assetId}/{versionNo}} 打 tag → {@code getCurrentCommitId} 回真实提交号。
 *
 * <p>安全（P0 红线）：仓库只由服务端 {@code repositoryId}（{@link GitRepositoryService} 注册表）
 * 定位，<b>绝不</b>接受客户端传入 {@code localPath}/{@code remoteUrl}/{@code gitUrl}；资产路径段做
 * path-traversal 校验；不记录任何凭据。
 *
 * <p>诚实空状态：仓库未注册 / runtime 不可用 / 缺必要入参 / git 失败 → {@code not_available} + reason，
 * 禁止 {@code committed:true} 伪成功。
 */
@Service
public class OntologyGitServiceImpl implements OntologyGitService {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitServiceImpl.class);

    static final String STATUS_NOT_AVAILABLE = OntologyGitArchiveResult.STATUS_NOT_AVAILABLE;
    static final String STATUS_COMMITTED = OntologyGitArchiveResult.STATUS_COMMITTED;

    private static final String DEFAULT_ASSET_TYPE = "ontology";
    private static final String DEFAULT_AUTHOR_NAME = "system";
    private static final String DEFAULT_AUTHOR_EMAIL = "system@ecos.local";

    private final GitService gitService;
    private final GitRepositoryService gitRepositoryService;

    public OntologyGitServiceImpl(GitService gitService, GitRepositoryService gitRepositoryService) {
        this.gitService = gitService;
        this.gitRepositoryService = gitRepositoryService;
    }

    // ═══════════════ 强类型真实归档入口 ═══════════════

    @Override
    public OntologyGitArchiveResult archiveVersion(OntologyGitArchiveRequest request) {
        OntologyGitArchiveResult result = new OntologyGitArchiveResult();
        result.setTimestamp(System.currentTimeMillis());
        if (request == null) {
            return notAvailable(result, "归档请求为空，拒绝执行（禁伪成功）");
        }
        String repositoryId = trimToNull(request.getRepositoryId());
        String assetType = orDefault(trimToNull(request.getAssetType()), DEFAULT_ASSET_TYPE);
        String assetId = trimToNull(request.getAssetId());
        String versionNo = trimToNull(request.getVersionNo());
        String content = request.getContent();

        result.setRepositoryId(repositoryId);
        result.setAssetType(assetType);
        result.setAssetId(assetId);
        result.setVersionNo(versionNo);

        // ── 入参校验（禁止客户端 localPath/gitUrl：本请求结构无该字段，天然拒绝）──
        if (repositoryId == null) {
            return notAvailable(result,
                "缺少 repositoryId：仓库仅由服务端注册表定位，禁止客户端传入 localPath/gitUrl");
        }
        if (assetId == null) {
            return notAvailable(result, "缺少 assetId（本体 ontologyId），无法定位归档路径");
        }
        if (content == null || content.isBlank()) {
            return notAvailable(result, "缺少已序列化的版本内容 content（来自 OntologyVersion.snapshot），无法归档");
        }
        try {
            requireSafeSegment(assetType);
            requireSafeSegment(assetId);
            if (versionNo != null) {
                requireSafeSegment(versionNo);
            }
        } catch (IllegalArgumentException e) {
            return notAvailable(result, "非法资产路径段（path-traversal 校验拒绝）: " + e.getMessage());
        }

        // ── 服务端解析仓库（仅 repositoryId）──
        GitRepository repository;
        try {
            repository = gitRepositoryService.getRepositoryById(repositoryId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return notAvailable(result, "仓库未在 runtime 注册表注册: repositoryId=" + repositoryId);
        }
        if (repository == null || isBlank(repository.getLocalPath())) {
            return notAvailable(result, "仓库定位失败或缺少 localPath: repositoryId=" + repositoryId);
        }
        if (!repository.isEnabled()) {
            return notAvailable(result, "仓库已禁用: repositoryId=" + repositoryId);
        }

        String commitMessage = orDefault(trimToNull(request.getCommitMessage()),
            "Archive ontology " + assetId + (versionNo != null ? " " + versionNo : ""));
        String authorName = orDefault(trimToNull(request.getAuthorName()), DEFAULT_AUTHOR_NAME);
        String authorEmail = orDefault(trimToNull(request.getAuthorEmail()), DEFAULT_AUTHOR_EMAIL);

        // ── 内容写入工作树 ──
        try {
            writeAssetContent(repository.getLocalPath(), assetId, versionNo, content);
        } catch (IOException e) {
            log.error("Git 归档写入工作树失败 repositoryId={} assetId={}", repositoryId, assetId, e);
            return notAvailable(result, "写入工作树失败: " + e.getMessage());
        }

        // ── commit ──
        try {
            gitService.commitAllChanges(repository, commitMessage, authorName, authorEmail);
        } catch (GitService.GitException e) {
            log.error("Git commitAllChanges 失败 repositoryId={} assetId={}（工作树可能未初始化 git 或 runtime 不可用）",
                repositoryId, assetId, e);
            return notAvailable(result, "Git 提交失败: " + e.getMessage());
        }

        // ── tag（仅当提供 versionNo）──
        String tag = null;
        if (versionNo != null) {
            tag = assetType + "/" + assetId + "/" + versionNo;
            try {
                gitService.createTag(repository, tag, commitMessage);
            } catch (GitService.GitException e) {
                log.error("Git createTag 失败 repositoryId={} tag={}（commit 已完成，但无法确认标签）",
                    repositoryId, tag, e);
                return notAvailable(result, "Git 打标签失败: " + e.getMessage());
            }
        }

        // ── 真实 commitId（禁止伪造；来源 getCurrentCommitId）──
        String commitId;
        try {
            commitId = gitService.getCurrentCommitId(repository);
        } catch (GitService.GitException e) {
            log.error("Git getCurrentCommitId 失败 repositoryId={}（无法确认提交号，禁伪成功）", repositoryId, e);
            return notAvailable(result, "读取提交号失败: " + e.getMessage());
        }
        if (isBlank(commitId)) {
            return notAvailable(result, "提交号为空，拒绝伪成功");
        }

        result.setStatus(STATUS_COMMITTED);
        result.setCommitId(commitId);
        result.setTag(tag);
        log.info("Git 归档成功 repositoryId={} commitId={} tag={}", repositoryId, commitId, tag);
        return result;
    }

    // ═══════════════ 旧 Map 契约（委托至 archiveVersion；仅透传服务端可解析键）═══════════════

    @Override
    public Map<String, Object> commit(String ontologyId, Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
        req.setRepositoryId(asString(b.get("repositoryId")));
        req.setAssetType(orDefault(asString(b.get("assetType")), DEFAULT_ASSET_TYPE));
        req.setAssetId(ontologyId);
        req.setVersionNo(asString(b.get("versionNo")));
        req.setContent(asString(b.get("content")));
        req.setCommitMessage(asString(b.get("message")));
        req.setAuthorName(asString(b.get("authorName")));
        req.setAuthorEmail(asString(b.get("authorEmail")));

        OntologyGitArchiveResult archived = archiveVersion(req);
        return toCommitMap(ontologyId, req.getCommitMessage(), archived);
    }

    @Override
    public Map<String, Object> pull(String ontologyId, Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        String repositoryId = trimToNull(asString(b.get("repositoryId")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ontologyId", ontologyId);
        result.put("timestamp", System.currentTimeMillis());
        if (repositoryId == null) {
            return pullNotAvailable(result,
                "缺少 repositoryId：pull 仅由服务端注册表定位仓库，禁止客户端传入 remote/gitUrl");
        }
        GitRepository repository;
        try {
            repository = gitRepositoryService.getRepositoryById(repositoryId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return pullNotAvailable(result, "仓库未在 runtime 注册表注册: repositoryId=" + repositoryId);
        }
        if (repository == null || isBlank(repository.getLocalPath()) || !repository.isEnabled()) {
            return pullNotAvailable(result, "仓库定位失败或缺少配置: repositoryId=" + repositoryId);
        }
        String remote = "origin";
        String branch = orDefault(trimToNull(repository.getDefaultBranch()), "main");
        try {
            gitService.pull(repository, remote, branch);
        } catch (GitService.GitException e) {
            log.error("Git pull 失败 repositoryId={}（禁伪成功）", repositoryId, e);
            return pullNotAvailable(result, "Git 拉取失败: " + e.getMessage());
        }
        result.put("status", "pulled");
        result.put("repositoryId", repositoryId);
        result.put("remote", remote);
        result.put("branch", branch);
        return result;
    }

    @Override
    public Map<String, Object> load(Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        // 安全：客户端 url 一律忽略，绝不据此 clone（P0 红线）；load 只认服务端 repositoryId。
        if (b.get("url") != null && !asString(b.get("url")).isBlank()) {
            log.warn("Git load 忽略客户端传入的 url（禁止 remote gitUrl 入参），仅接受 repositoryId");
        }
        String repositoryId = trimToNull(asString(b.get("repositoryId")));
        if (repositoryId == null) {
            result.put("status", STATUS_NOT_AVAILABLE);
            result.put("reason", "load 仅接受服务端 repositoryId，禁止客户端 gitUrl/localPath 入参");
            return result;
        }
        // 语义等价 pull：从服务端注册的 remote 拉取到工作树。
        return pull(repositoryId, Map.of("repositoryId", repositoryId));
    }

    // ═══════════════ 内部工具 ═══════════════

    private Map<String, Object> toCommitMap(String ontologyId, String commitMessage,
                                            OntologyGitArchiveResult r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", r.getStatus());
        map.put("ontologyId", ontologyId);
        if (commitMessage != null) {
            map.put("commitMessage", commitMessage);
        }
        map.put("timestamp", r.getTimestamp() != null ? r.getTimestamp() : System.currentTimeMillis());
        if (r.getRepositoryId() != null) {
            map.put("repositoryId", r.getRepositoryId());
        }
        if (r.getCommitId() != null) {
            map.put("commitId", r.getCommitId());
        }
        if (r.getTag() != null) {
            map.put("tag", r.getTag());
        }
        if (r.getAssetType() != null) {
            map.put("assetType", r.getAssetType());
        }
        if (r.getVersionNo() != null) {
            map.put("versionNo", r.getVersionNo());
        }
        if (r.getReason() != null) {
            map.put("reason", r.getReason());
        }
        return map;
    }

    private OntologyGitArchiveResult notAvailable(OntologyGitArchiveResult result, String reason) {
        result.setStatus(STATUS_NOT_AVAILABLE);
        result.setReason(reason);
        log.warn("Git 归档不可用: {}", reason);
        return result;
    }

    private Map<String, Object> pullNotAvailable(Map<String, Object> result, String reason) {
        result.put("status", STATUS_NOT_AVAILABLE);
        result.put("reason", reason);
        log.warn("Git 拉取不可用: {}", reason);
        return result;
    }

    private void writeAssetContent(String localPath, String assetId, String versionNo, String content)
            throws IOException {
        Path dir = Paths.get(localPath, "ontologies", assetId);
        Files.createDirectories(dir);
        String fileName = (versionNo != null ? versionNo : "latest") + ".json";
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IOException("归档路径越出资产目录，已拒绝");
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** path-traversal 守卫：拒绝 .. / \ 冒号 与空段（与仓库根解析同口径）。 */
    static void requireSafeSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()
                || segment.contains("..") || segment.contains("/") || segment.contains("\\")
                || segment.indexOf(':') >= 0) {
            throw new IllegalArgumentException(segment);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
