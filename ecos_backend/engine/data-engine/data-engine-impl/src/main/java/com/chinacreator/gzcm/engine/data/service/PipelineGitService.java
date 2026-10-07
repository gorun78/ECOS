package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitLoadRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitOperationResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitPullRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitSwitchBranchRequest;
import com.chinacreator.gzcm.runtime.core.crypto.SecurityCryptoEgress;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.GitService.GitException;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.sysman.config.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.util.*;

/**
 * Pipeline Git 版本管理服务。
 *
 * <p>H4-T1/T2 整改后口径：
 * <ul>
 *   <li>仓库定位只认 repositoryId（runtime 注册表优先，缺省落 sys_config 单源根路径），
 *       仅传已废弃 localPath 的请求返回 200 + status=not_available + reason，拒绝执行（禁伪成功）</li>
 *   <li>请求体不再接收 username/password，凭据由服务端经 security-engine 加密存储解析注入</li>
 *   <li>gitUrl 必须通过允许域注册表（sys_config ecos.git.allowed_hosts）+ 内网/链路本地地址拒绝</li>
 *   <li>仓库根路径单源 sys_config ecos_git_repo_root（{@link GitRepoRootResolver}），
 *       默认 {user.home}/ecos-git-repos，pipeline 工作树落 {repoRoot}/pipeline/{id}</li>
 * </ul>
 */
@Service
public class PipelineGitService {

    private static final Logger log = LoggerFactory.getLogger(PipelineGitService.class);

    public static final String STATUS_NOT_AVAILABLE = "not_available";
    static final String KEY_ALLOWED_HOSTS = "ecos.git.allowed_hosts";
    static final String KEY_GIT_USERNAME = "ecos.git.username";
    static final String KEY_GIT_PASSWORD_ENC = "ecos.git.password_enc";
    private static final String KEY_GIT_PASSWORD = "git_access_password";
    private static final String LOCALPATH_DEPRECATED_REASON =
            "localPath 入参已废弃：请改传 repositoryId（服务端经仓库注册表/sys_config 根路径解析实际路径）";

    private final GitService gitService;
    private final JdbcTemplate jdbc;
    private final ISysConfigService sysConfigService;
    private final GitRepositoryService gitRepositoryService;
    private final GitRepoRootResolver repoRootResolver;
    private final ObjectProvider<SecurityCryptoEgress> cryptoEgressProvider;

    public PipelineGitService(GitService gitService,
                              JdbcTemplate jdbc,
                              ISysConfigService sysConfigService,
                              GitRepositoryService gitRepositoryService,
                              GitRepoRootResolver repoRootResolver,
                              ObjectProvider<SecurityCryptoEgress> cryptoEgressProvider) {
        this.gitService = gitService;
        this.jdbc = jdbc;
        this.sysConfigService = sysConfigService;
        this.gitRepositoryService = gitRepositoryService;
        this.repoRootResolver = repoRootResolver;
        this.cryptoEgressProvider = cryptoEgressProvider;
    }

    /** 仓库定位结果：localPath 为 null 时表示未解析；status 非空表示拒绝执行。 */
    public record LocalPathResolution(String localPath, String status, String reason) {
        boolean unavailable() {
            return status != null;
        }
    }

    /**
     * 解析仓库本地路径：repositoryId 优先（runtime 注册表 → sys_config 单源根路径兜底）；
     * 仅传已废弃 localPath 时拒绝执行并返回指引。
     */
    public LocalPathResolution resolveLocalPath(String taskId, String repositoryId, String legacyLocalPath) {
        if (repositoryId != null && !repositoryId.trim().isEmpty()) {
            String repoId = repositoryId.trim();
            try {
                GitRepository registered = gitRepositoryService.getRepositoryById(repoId);
                if (registered != null && registered.getLocalPath() != null && !registered.getLocalPath().isBlank()) {
                    return new LocalPathResolution(registered.getLocalPath(), null, null);
                }
            } catch (GitRepositoryService.GitRepositoryException e) {
                log.debug("仓库未在 runtime 注册表命中，按单源根路径解析: repoId={}", repoId);
            }
            return new LocalPathResolution(repoRootResolver.resolveRepoPath(repoId), null, null);
        }
        if (legacyLocalPath != null && !legacyLocalPath.trim().isEmpty()) {
            return new LocalPathResolution(null, STATUS_NOT_AVAILABLE, LOCALPATH_DEPRECATED_REASON);
        }
        String fallback = (taskId != null && !taskId.isBlank())
                ? repoRootResolver.resolveUnderRoot("pipeline", taskId)
                : repoRootResolver.resolveRepoRoot();
        return new LocalPathResolution(fallback, null, null);
    }

    // ==================== 1. commit ====================

    public Map<String, Object> commit(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        String repositoryId = (String) b.get("repositoryId");
        String legacyLocalPath = (String) b.get("localPath");
        String message = (String) b.getOrDefault("message", "Update pipeline " + id);

        PipelineGitCommitResultVO vo = commit(id, toCommitRequest(repositoryId, legacyLocalPath, message));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("branch", vo.getBranch());
        result.put("commitId", vo.getCommitId());
        result.put("message", vo.getMessage());
        if (vo.getStatus() != null) {
            result.put("status", vo.getStatus());
            result.put("reason", vo.getReason());
        }
        return result;
    }

    private PipelineGitCommitRequest toCommitRequest(String repositoryId, String localPath, String message) {
        PipelineGitCommitRequest req = new PipelineGitCommitRequest();
        req.setRepositoryId(repositoryId);
        req.setLocalPath(localPath);
        req.setMessage(message);
        return req;
    }

    public PipelineGitCommitResultVO commit(String id, PipelineGitCommitRequest req) throws GitException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("commit id 不能为空");
        }
        if (req == null) {
            throw new IllegalArgumentException("commit 请求体不能为空");
        }
        String message = req.getMessage() != null ? req.getMessage() : "Update pipeline " + id;
        LocalPathResolution resolution = resolveLocalPath(id, req.getRepositoryId(), req.getLocalPath());
        PipelineGitCommitResultVO vo = new PipelineGitCommitResultVO();
        vo.setTaskId(id);
        vo.setMessage(message);
        if (resolution.unavailable()) {
            vo.setStatus(resolution.status());
            vo.setReason(resolution.reason());
            return vo;
        }
        return doCommit(id, resolution.localPath(), message, vo);
    }

    private PipelineGitCommitResultVO doCommit(String id, String localPath, String message,
                                               PipelineGitCommitResultVO vo) throws GitException {
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT id, git_url, git_branch, yaml_content FROM ecos_pipeline_task WHERE id = ?", id);
        String gitUrl = (String) task.get("git_url");
        String gitBranch = (String) task.getOrDefault("git_branch", "main");
        String yamlContent = (String) task.get("yaml_content");

        if (gitUrl != null && !gitUrl.isEmpty()) {
            validateGitUrl(gitUrl);
            String[] credentials = resolveGitCredentials();
            try {
                gitService.clone(gitUrl, localPath, credentials[0], credentials[1]);
            } catch (GitException e) {
                log.info("仓库可能已存在，尝试 pull: {}", e.getMessage());
                gitService.pull(localPath, "origin", "main");
            }
            gitService.checkoutBranch(localPath, gitBranch);
        } else {
            log.info("未配置 gitUrl，使用本地仓库: {}", localPath);
        }

        writePipelineYaml(localPath, id, yamlContent);
        gitService.commit(localPath, message, List.of("pipelines/" + id + "/pipeline.yaml"));
        if (gitUrl != null && !gitUrl.isEmpty()) {
            String[] credentials = resolveGitCredentials();
            gitService.push(localPath, credentials[0], credentials[1]);
        }

        log.info("Pipeline 已提交到 Git: taskId={}", id);
        vo.setTaskId(id);
        vo.setBranch(gitBranch);
        vo.setMessage(message);
        return vo;
    }

    // ==================== 2. pull ====================

    public Map<String, Object> pull(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        PipelineGitPullRequest req = new PipelineGitPullRequest();
        req.setRepositoryId((String) b.get("repositoryId"));
        req.setLocalPath((String) b.get("localPath"));

        PipelineGitOperationResultVO vo = pull(id, req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("yamlUpdated", vo.getYamlUpdated());
        if (vo.getStatus() != null) {
            result.put("status", vo.getStatus());
            result.put("reason", vo.getReason());
        }
        return result;
    }

    public PipelineGitOperationResultVO pull(String id, PipelineGitPullRequest req) throws GitException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("pull id 不能为空");
        }
        LocalPathResolution resolution = resolveLocalPath(
                id, req != null ? req.getRepositoryId() : null, req != null ? req.getLocalPath() : null);
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        vo.setTaskId(id);
        if (resolution.unavailable()) {
            vo.setStatus(resolution.status());
            vo.setReason(resolution.reason());
            return vo;
        }
        return doPull(id, resolution.localPath(), vo);
    }

    private PipelineGitOperationResultVO doPull(String id, String localPath,
                                                PipelineGitOperationResultVO vo) throws GitException {
        jdbc.queryForMap("SELECT id, yaml_content FROM ecos_pipeline_task WHERE id = ?", id);
        gitService.pull(localPath, "origin", "main");

        String yamlContent = loadPipelineYaml(localPath, id);
        jdbc.update(
                "UPDATE ecos_pipeline_task SET yaml_content = ?, updated_at = NOW() WHERE id = ?",
                yamlContent, id);

        log.info("从 Git 拉取成功: taskId={}", id);
        vo.setTaskId(id);
        vo.setYamlUpdated(true);
        return vo;
    }

    // ==================== 3. loadFromGit ====================

    public Map<String, Object> loadFromGit(Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        PipelineGitLoadRequest req = new PipelineGitLoadRequest();
        req.setRepositoryId((String) b.get("repositoryId"));
        req.setPath((String) b.get("localPath"));
        req.setGitUrl((String) b.get("gitUrl"));
        req.setPipelineId((String) b.get("pipelineId"));
        req.setBranch((String) b.get("branch"));
        req.setTaskId((String) b.get("taskId"));

        PipelineGitOperationResultVO vo = loadFromGit(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("pipelineId", vo.getPipelineId());
        result.put("gitUrl", vo.getGitUrl());
        if (vo.getStatus() != null) {
            result.put("status", vo.getStatus());
            result.put("reason", vo.getReason());
        }
        return result;
    }

    public PipelineGitOperationResultVO loadFromGit(PipelineGitLoadRequest req) throws GitException {
        if (req == null) {
            throw new IllegalArgumentException("load 请求体不能为空");
        }
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        if ((req.getGitUrl() == null || req.getGitUrl().isEmpty())
                && (req.getRepositoryId() == null || req.getRepositoryId().isBlank())
                && (req.getPath() == null || req.getPath().isBlank())) {
            throw new IllegalArgumentException("gitUrl 和 repositoryId 至少需要一项");
        }
        LocalPathResolution resolution = resolveLocalPath(req.getTaskId(), req.getRepositoryId(), req.getPath());
        if (resolution.unavailable()) {
            vo.setStatus(resolution.status());
            vo.setReason(resolution.reason());
            return vo;
        }
        String localPath = resolution.localPath();
        if (req.getGitUrl() != null && !req.getGitUrl().isEmpty()) {
            validateGitUrl(req.getGitUrl());
        }
        if (req.getRepositoryId() == null && (req.getTaskId() == null || req.getTaskId().isBlank())) {
            localPath = repoRootResolver.resolveUnderRoot("pipeline",
                    "load-" + UUID.randomUUID().toString().substring(0, 8));
        }
        return doLoadFromGit(req.getGitUrl(), req.getPipelineId(), req.getBranch(), localPath, req.getTaskId(), vo);
    }

    private PipelineGitOperationResultVO doLoadFromGit(String gitUrl, String pipelineId, String branch,
                                                       String localPath, String taskId,
                                                       PipelineGitOperationResultVO vo) throws GitException {
        String[] credentials = resolveGitCredentials();
        if (gitUrl != null && !gitUrl.isEmpty()) {
            gitService.clone(gitUrl, localPath, credentials[0], credentials[1]);
        }
        if (branch != null && !branch.isEmpty() && gitUrl != null && !gitUrl.isEmpty()) {
            gitService.checkoutBranch(localPath, branch);
        }
        String pid = pipelineId != null ? pipelineId : taskId;
        String yamlContent = loadPipelineYaml(localPath, pid);

        String newTaskId = taskId != null ? taskId : UUID.randomUUID().toString();
        String effectiveBranch = branch != null ? branch : "main";
        jdbc.update(
                "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT') ON CONFLICT (id) DO UPDATE SET yaml_content = EXCLUDED.yaml_content, updated_at = NOW()",
                newTaskId, "Git: " + pid, "从 Git 加载", yamlContent, gitUrl, effectiveBranch);

        log.info("从 Git 加载 Pipeline: gitUrl={}, pipelineId={}", gitUrl, pid);
        vo.setTaskId(newTaskId);
        vo.setPipelineId(pid);
        vo.setGitUrl(gitUrl);
        return vo;
    }

    // ==================== 4. listBranches ====================

    public List<String> listBranches(String repositoryId) throws GitException {
        LocalPathResolution resolution = resolveLocalPath(null, repositoryId, null);
        if (resolution.unavailable() || resolution.localPath() == null) {
            throw new IllegalArgumentException(repositoryId == null || repositoryId.isBlank()
                    ? "repositoryId 不能为空（localPath 入参已废弃，请改传 repositoryId）"
                    : resolution.reason());
        }
        return listBranchesAtLocalPath(resolution.localPath());
    }

    /** 仅传已废弃 localPath 的兼容入口：拒绝执行并给出指引（禁伪成功）。 */
    public List<String> listBranchesByLegacyLocalPath(String legacyLocalPath) throws GitException {
        if (legacyLocalPath != null && !legacyLocalPath.trim().isEmpty()) {
            throw new IllegalArgumentException(LOCALPATH_DEPRECATED_REASON);
        }
        throw new IllegalArgumentException("repositoryId 不能为空（localPath 入参已废弃，请改传 repositoryId）");
    }

    private List<String> listBranchesAtLocalPath(String localPath) throws GitException {
        File gitDir = new File(localPath);
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("本地仓库不存在: " + localPath);
        }
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(gitDir)) {
            return git.branchList().call().stream()
                .map(ref -> ref.getName().replace("refs/heads/", ""))
                .toList();
        } catch (Exception e) {
            throw new GitException("读取分支列表失败: " + localPath, e);
        }
    }

    // ==================== 5. switchBranch ====================

    public Map<String, Object> switchBranch(String repositoryId, String branchName, String taskId) throws GitException {
        PipelineGitOperationResultVO vo = switchBranch(
                buildSwitchRequest(repositoryId, null, branchName, taskId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("branchName", vo.getBranchName());
        if (vo.getStatus() != null) {
            result.put("status", vo.getStatus());
            result.put("reason", vo.getReason());
        }
        return result;
    }

    private PipelineGitSwitchBranchRequest buildSwitchRequest(String repositoryId, String localPath,
                                                              String branchName, String taskId) {
        PipelineGitSwitchBranchRequest req = new PipelineGitSwitchBranchRequest();
        req.setRepositoryId(repositoryId);
        req.setLocalPath(localPath);
        req.setBranchName(branchName);
        req.setTaskId(taskId);
        return req;
    }

    public PipelineGitOperationResultVO switchBranch(PipelineGitSwitchBranchRequest req) throws GitException {
        if (req == null || req.getBranchName() == null || req.getBranchName().isBlank()) {
            throw new IllegalArgumentException("branchName 不能为空");
        }
        LocalPathResolution resolution = resolveLocalPath(req.getTaskId(), req.getRepositoryId(), req.getLocalPath());
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        vo.setTaskId(req.getTaskId());
        if (resolution.unavailable()) {
            vo.setStatus(resolution.status());
            vo.setReason(resolution.reason());
            return vo;
        }
        gitService.checkoutBranch(resolution.localPath(), req.getBranchName());
        if (req.getTaskId() != null) {
            jdbc.update(
                    "UPDATE ecos_pipeline_task SET git_branch = ?, updated_at = NOW() WHERE id = ?",
                    req.getBranchName(), req.getTaskId());
        }
        log.info("Git 分支切换: {} → {}", req.getRepositoryId(), req.getBranchName());
        vo.setBranchName(req.getBranchName());
        return vo;
    }

    // ==================== 6. versions ====================

    public List<String> versions(String id) throws GitException {
        File gitDir = new File(repoRootResolver.resolveUnderRoot("pipeline", id));

        if (!gitDir.exists() || !new File(gitDir, ".git").exists()) {
            return new ArrayList<>();
        }

        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(gitDir)) {
            String gitRelative = "pipelines/" + id + "/pipeline.yaml";
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits =
                git.log().addPath(gitRelative).call();
            List<String> shas = new ArrayList<>();
            for (org.eclipse.jgit.revwalk.RevCommit c : commits) {
                shas.add(c.getName().substring(0, 7));
            }
            if (!shas.isEmpty()) {
                return shas;
            }
            return List.of();
        } catch (Exception e) {
            throw new GitException("读取 Git 版本历史失败: " + id, e);
        }
    }

    // ==================== 安全校验 / 凭据解析 ====================

    /** gitUrl 允许域注册表校验：拒内网/回环/链路本地/元数据地址，且必须命中注册表白名单（缺省拒绝）。 */
    void validateGitUrl(String gitUrl) {
        String host = extractHost(gitUrl);
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("非法 gitUrl（无法解析主机）: " + gitUrl);
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (isBlockedHost(lower)) {
            throw new IllegalArgumentException("gitUrl 指向内网/链路本地/元数据地址，已拒绝: " + host);
        }
        String registry = sysConfigService.getString(KEY_ALLOWED_HOSTS, "");
        List<String> allowed = new ArrayList<>();
        if (registry != null) {
            for (String entry : registry.split(",")) {
                if (!entry.trim().isEmpty()) {
                    allowed.add(entry.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException(
                    "gitUrl 允许域注册表（sys_config " + KEY_ALLOWED_HOSTS + "）未配置，缺省拒绝远程 Git 操作: " + host);
        }
        boolean matched = allowed.stream().anyMatch(a -> lower.equals(a) || lower.endsWith("." + a));
        if (!matched) {
            throw new IllegalArgumentException("gitUrl 主机不在允许域注册表内，已拒绝: " + host);
        }
    }

    static String extractHost(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return null;
        }
        String url = gitUrl.trim();
        if (url.toLowerCase(Locale.ROOT).startsWith("file://")) {
            throw new IllegalArgumentException("禁止 file:// 协议的 gitUrl: " + gitUrl);
        }
        try {
            URI uri = new URI(url);
            if (uri.getHost() != null) {
                return uri.getHost();
            }
            String scheme = uri.getScheme();
            if (scheme != null) {
                return null;
            }
        } catch (Exception ignored) {
            // 非 URI 语法（scp 式 git@host:path），走下方兜底解析
        }
        if (url.startsWith("git@") || url.startsWith("ssh://")) {
            String rest = url.startsWith("git@") ? url.substring(4) : URI.create(url).getHost();
            if (url.startsWith("git@")) {
                int colon = rest.indexOf(':');
                int slash = rest.indexOf('/');
                int end = colon >= 0 ? colon : (slash >= 0 ? slash : rest.length());
                return rest.substring(0, end);
            }
            return rest;
        }
        return null;
    }

    static boolean isBlockedHost(String lowerHost) {
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost")
                || lowerHost.equals("metadata.google.internal") || lowerHost.equals("metadata")) {
            return true;
        }
        if (lowerHost.equals("127.0.0.1") || lowerHost.startsWith("127.")) {
            return true;
        }
        if (lowerHost.startsWith("169.254.")) {
            return true;
        }
        if (lowerHost.startsWith("10.")) {
            return true;
        }
        if (lowerHost.startsWith("192.168.")) {
            return true;
        }
        if (lowerHost.startsWith("0.0.0.0")) {
            return true;
        }
        if (lowerHost.startsWith("[") || lowerHost.contains(":")) {
            String v6 = lowerHost.startsWith("[") ? lowerHost.substring(1, lowerHost.lastIndexOf(']')) : lowerHost;
            if (v6.equals("::1") || v6.startsWith("fe80") || v6.startsWith("fc") || v6.startsWith("fd")) {
                return true;
            }
        }
        if (lowerHost.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*")) {
            return true;
        }
        return false;
    }

    /** 凭据单源注入：sys_config ecos.git.username + ecos.git.password_enc（AES 密文，经 security-engine 解密）。 */
    String[] resolveGitCredentials() {
        String username = sysConfigService.getString(KEY_GIT_USERNAME, "");
        String passwordEnc = sysConfigService.getString(KEY_GIT_PASSWORD_ENC, "");
        if (username == null || username.isBlank() || passwordEnc == null || passwordEnc.isBlank()) {
            return new String[]{null, null};
        }
        SecurityCryptoEgress cryptoEgress = cryptoEgressProvider.getIfAvailable();
        if (cryptoEgress == null || !cryptoEgress.available()) {
            log.warn("runtime 安全加解密出口不可用，Git 凭据解密跳过（按匿名访问处理）");
            return new String[]{null, null};
        }
        try {
            return new String[]{username, cryptoEgress.decrypt(passwordEnc, KEY_GIT_PASSWORD)};
        } catch (Exception e) {
            log.warn("Git 凭据解密失败，按匿名访问处理: {}", e.getMessage());
            return new String[]{null, null};
        }
    }

    // ==================== 私有 util ====================

    private void writePipelineYaml(String localPath, String pipelineId, String yamlContent) throws GitException {
        GitRepoRootResolver.requireSafeSegment(pipelineId);
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(localPath, "pipelines", pipelineId);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.createDirectories(dir.resolve("expressions"));
            java.nio.file.Files.createDirectories(dir.resolve("config"));
            java.nio.file.Files.writeString(dir.resolve("pipeline.yaml"), yamlContent);
        } catch (java.io.IOException e) {
            throw new GitException("写入 pipeline.yaml 失败", e);
        }
    }

    private String loadPipelineYaml(String localPath, String pipelineId) throws GitException {
        if (pipelineId != null) {
            GitRepoRootResolver.requireSafeSegment(pipelineId);
        }
        try {
            return java.nio.file.Files.readString(
                java.nio.file.Paths.get(localPath, "pipelines", pipelineId, "pipeline.yaml"));
        } catch (java.io.IOException e) {
            throw new GitException("读取 pipeline.yaml 失败: " + pipelineId, e);
        }
    }
}
