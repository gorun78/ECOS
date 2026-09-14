package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitLoadRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitOperationResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitPullRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitSwitchBranchRequest;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.GitService.GitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * Pipeline Git 版本管理服务。
 *
 * <p>P2#1: 本地仓库路径不再硬编码 /tmp/ecos-git —— 走
 * {@code @Value("${ecos.pipeline.git.local-path:${java.io.tmpdir}/ecos-git")"}，
 * Windows 下默认落到 %TMP%/ecos-git，生产可按档位覆盖。
 * P2#2: 查库 SQL 改为显式字段列表（禁 SELECT *）。
 * P2#3: 入参改强类型 DTO 重载、返回强类型 VO；Map 旧入口保留（API 只增不改），
 *       Controller 层已切换 DTO 入口。
 */
@Service
public class PipelineGitService {

    private static final Logger log = LoggerFactory.getLogger(PipelineGitService.class);
    private final GitService gitService;
    private final JdbcTemplate jdbc;

    /** git 本地仓库根路径（缺省 ${java.io.tmpdir}/ecos-git，跨平台有效） */
    @Value("${ecos.pipeline.git.local-path:${java.io.tmpdir}/ecos-git}")
    private String gitLocalRoot;

    public PipelineGitService(GitService gitService, JdbcTemplate jdbc) {
        this.gitService = gitService;
        this.jdbc = jdbc;
    }

    /** 仅测试注入用（@Value 字段反射注入等价物）。 */
    public void injectGitLocalRootForTest(String path) {
        this.gitLocalRoot = path;
    }

    /** P2#1 统一入口：commit / pull / versions 共用的 per-pipeline 本地仓库路径。 */
    private String defaultLocalPath(String id) {
        return gitLocalRoot + File.separator + id;
    }

    // ==================== 1. commit ====================

    /**
     * 提交到 Git（Map 入参版，保留兼容；实际由 doCommit 走显式 SQL 字段）。
     */
    public Map<String, Object> commit(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        String localPath = (String) b.getOrDefault("localPath", defaultLocalPath(id));
        String message = (String) b.getOrDefault("message", "Update pipeline " + id);
        String username = (String) b.get("username");
        String password = (String) b.get("password");

        PipelineGitCommitResultVO vo = doCommit(id, localPath, message, username, password);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("branch", vo.getBranch());
        result.put("commitId", vo.getCommitId());
        result.put("message", vo.getMessage());
        return result;
    }

    /** 提交到 Git（DTO 入参版，Controller 切换后默认调用，P2#3）。 */
    public PipelineGitCommitResultVO commit(String id, PipelineGitCommitRequest req) throws GitException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("commit id 不能为空");
        }
        if (req == null) {
            throw new IllegalArgumentException("commit 请求体不能为空");
        }
        String localPath = req.getLocalPath() != null ? req.getLocalPath() : defaultLocalPath(id);
        String message = req.getMessage() != null ? req.getMessage() : "Update pipeline " + id;
        return doCommit(id, localPath, message, req.getUsername(), req.getPassword());
    }

    /** 提交核心逻辑（DTO/Map 两入口共用；P2#2 显式字段列表，禁 SELECT *）。 */
    private PipelineGitCommitResultVO doCommit(String id, String localPath, String message,
                                               String username, String password) throws GitException {
        Map<String, Object> task = jdbc.queryForMap(
                "SELECT id, git_url, git_branch, yaml_content FROM ecos_pipeline_task WHERE id = ?", id);
        String gitUrl = (String) task.get("git_url");
        String gitBranch = (String) task.getOrDefault("git_branch", "main");
        String yamlContent = (String) task.get("yaml_content");

        if (gitUrl != null && !gitUrl.isEmpty()) {
            try {
                gitService.clone(gitUrl, localPath, username, password);
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
            gitService.push(localPath, username, password);
        }

        log.info("Pipeline 已提交到 Git: taskId={}", id);
        PipelineGitCommitResultVO vo = new PipelineGitCommitResultVO();
        vo.setTaskId(id);
        vo.setBranch(gitBranch);
        vo.setMessage(message);
        return vo;
    }

    // ==================== 2. pull ====================

    /** 从 Git 拉取（Map 入参版，保留兼容）。 */
    public Map<String, Object> pull(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        String localPath = (String) b.getOrDefault("localPath", defaultLocalPath(id));

        PipelineGitOperationResultVO vo = doPull(id, localPath);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("yamlUpdated", vo.getYamlUpdated());
        return result;
    }

    /** 从 Git 拉取（DTO 入参版，P2#3）。 */
    public PipelineGitOperationResultVO pull(String id, PipelineGitPullRequest req) throws GitException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("pull id 不能为空");
        }
        String localPath = (req != null && req.getLocalPath() != null)
                ? req.getLocalPath() : defaultLocalPath(id);
        return doPull(id, localPath);
    }

    /** 拉取核心逻辑（P2#2 显式字段列表）。 */
    private PipelineGitOperationResultVO doPull(String id, String localPath) throws GitException {
        jdbc.queryForMap(
                "SELECT id, yaml_content FROM ecos_pipeline_task WHERE id = ?", id);
        gitService.pull(localPath, "origin", "main");

        String yamlContent = loadPipelineYaml(localPath, id);
        jdbc.update(
                "UPDATE ecos_pipeline_task SET yaml_content = ?, updated_at = NOW() WHERE id = ?",
                yamlContent, id);

        log.info("从 Git 拉取成功: taskId={}", id);
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        vo.setTaskId(id);
        vo.setYamlUpdated(true);
        return vo;
    }

    // ==================== 3. loadFromGit ====================

    /** 从 Git 加载（Map 入参版，保留兼容）。 */
    public Map<String, Object> loadFromGit(Map<String, Object> body) throws GitException {
        Map<String, Object> b = body != null ? body : Map.of();
        String gitUrl = (String) b.get("gitUrl");
        String pipelineId = (String) b.get("pipelineId");
        String branch = (String) b.getOrDefault("branch", "main");
        String username = (String) b.get("username");
        String password = (String) b.get("password");
        String localPath = (String) b.getOrDefault("localPath",
                gitLocalRoot + File.separator + "load-" + UUID.randomUUID().toString().substring(0, 8));
        String taskId = (String) b.get("taskId");

        PipelineGitOperationResultVO vo = doLoadFromGit(gitUrl, pipelineId, branch, username, password, localPath, taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", vo.getTaskId());
        result.put("pipelineId", vo.getPipelineId());
        result.put("gitUrl", vo.getGitUrl());
        return result;
    }

    /** 从 Git 加载（DTO 入参版，P2#3）。 */
    public PipelineGitOperationResultVO loadFromGit(PipelineGitLoadRequest req) throws GitException {
        if (req == null) {
            throw new IllegalArgumentException("load 请求体不能为空");
        }
        String localPath = req.getPath() != null ? req.getPath()
                : gitLocalRoot + File.separator + "load-" + UUID.randomUUID().toString().substring(0, 8);
        return doLoadFromGit(req.getGitUrl(), req.getPipelineId(),
                req.getBranch(), null, null, localPath, req.getTaskId());
    }

    private PipelineGitOperationResultVO doLoadFromGit(String gitUrl, String pipelineId, String branch,
                                                       String username, String password, String localPath,
                                                       String taskId) throws GitException {
        if (gitUrl == null && (localPath == null || localPath.isEmpty())) {
            throw new IllegalArgumentException("gitUrl 和 localPath 至少需要一项");
        }
        if (pipelineId == null && taskId == null) {
            throw new IllegalArgumentException("pipelineId 和 taskId 至少需要一项");
        }

        if (gitUrl != null && !gitUrl.isEmpty()) {
            gitService.clone(gitUrl, localPath, username, password);
        }
        if (branch != null && !branch.isEmpty() && !gitUrl.isEmpty()) {
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
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        vo.setTaskId(newTaskId);
        vo.setPipelineId(pid);
        vo.setGitUrl(gitUrl);
        return vo;
    }

    // ==================== 4. listBranches ====================

    public List<String> listBranches(String localPath) throws Exception {
        File gitDir = new File(localPath);
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("本地仓库不存在: " + localPath);
        }
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(gitDir)) {
            return git.branchList().call().stream()
                .map(ref -> ref.getName().replace("refs/heads/", ""))
                .toList();
        }
    }

    // ==================== 5. switchBranch ====================

    /** 切换分支（Map 入参版，保留兼容）。 */
    public Map<String, Object> switchBranch(String localPath, String branchName, String taskId) throws GitException {
        gitService.checkoutBranch(localPath, branchName);
        if (taskId != null) {
            jdbc.update(
                "UPDATE ecos_pipeline_task SET git_branch = ?, updated_at = NOW() WHERE id = ?",
                branchName, taskId);
        }
        log.info("Git 分支切换: {} → {}", localPath, branchName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("localPath", localPath);
        result.put("branchName", branchName);
        return result;
    }

    /** 切换分支（DTO 入参版，P2#3）。 */
    public PipelineGitOperationResultVO switchBranch(PipelineGitSwitchBranchRequest req) throws GitException {
        if (req == null || req.getLocalPath() == null || req.getBranchName() == null) {
            throw new IllegalArgumentException("localPath 和 branchName 不能为空");
        }
        gitService.checkoutBranch(req.getLocalPath(), req.getBranchName());
        if (req.getTaskId() != null) {
            jdbc.update(
                    "UPDATE ecos_pipeline_task SET git_branch = ?, updated_at = NOW() WHERE id = ?",
                    req.getBranchName(), req.getTaskId());
        }
        log.info("Git 分支切换: {} → {}", req.getLocalPath(), req.getBranchName());
        PipelineGitOperationResultVO vo = new PipelineGitOperationResultVO();
        vo.setLocalPath(req.getLocalPath());
        vo.setBranchName(req.getBranchName());
        return vo;
    }

    // ==================== 6. versions（P2#1 跨平台路径） ====================

    /**
     * Pipeline 定义的 Git 版本列表（最新在上）。
     *
     * <p>与 {@link #commit(String, Map)} 共用同一 git 工作树（{@link #defaultLocalPath(String)}）
     * 和同一 per-pipeline 子目录（{@code pipelines/{id}/pipeline.yaml}）。
     *
     * <ul>
     *   <li>git 仓库不存在 → 返回空列表（首次 commit 之前属正常态）</li>
     *   <li>仓库存在但该 pipeline 子目录还没 commit 历史 → 返回空列表</li>
     *   <li>仓库存在且有 commit 历史 → 返回 7 位 short SHA（最新在上）</li>
     * </ul>
     */
    public List<String> versions(String id) throws GitException {
        // P2#1：与 commit() 同源 defaultLocalPath(id)，跨平台可配
        File gitDir = new File(defaultLocalPath(id));

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

    // ==================== 私有 util ====================

    private void writePipelineYaml(String localPath, String pipelineId, String yamlContent) throws GitException {
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
        try {
            return java.nio.file.Files.readString(
                java.nio.file.Paths.get(localPath, "pipelines", pipelineId, "pipeline.yaml"));
        } catch (java.io.IOException e) {
            throw new GitException("读取 pipeline.yaml 失败: " + pipelineId, e);
        }
    }
}
