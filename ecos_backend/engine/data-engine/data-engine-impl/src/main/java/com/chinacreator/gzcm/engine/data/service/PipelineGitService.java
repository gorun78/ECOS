package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.runtime.core.git.GitService;
import com.chinacreator.gzcm.runtime.core.git.GitService.GitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
public class PipelineGitService {

    private static final Logger log = LoggerFactory.getLogger(PipelineGitService.class);
    private final GitService gitService;
    private final JdbcTemplate jdbc;

    public PipelineGitService(GitService gitService, JdbcTemplate jdbc) {
        this.gitService = gitService;
        this.jdbc = jdbc;
    }

    public Map<String, Object> commit(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> task = jdbc.queryForMap(
            "SELECT * FROM ecos_pipeline_task WHERE id = ?", id);
        String gitUrl = (String) task.get("git_url");
        String gitBranch = (String) task.getOrDefault("git_branch", "main");
        String yamlContent = (String) task.get("yaml_content");
        String localPath = (String) body.getOrDefault("localPath", "/tmp/ecos-git/" + id);
        String message = (String) body.getOrDefault("message", "Update pipeline " + id);
        String username = (String) body.get("username");
        String password = (String) body.get("password");

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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", id);
        result.put("branch", gitBranch);
        result.put("message", message);
        return result;
    }

    public Map<String, Object> pull(String id, Map<String, Object> body) throws GitException {
        Map<String, Object> task = jdbc.queryForMap(
            "SELECT * FROM ecos_pipeline_task WHERE id = ?", id);
        String localPath = (String) body.getOrDefault("localPath", "/tmp/ecos-git/" + id);

        gitService.pull(localPath, "origin", "main");

        String yamlContent = loadPipelineYaml(localPath, id);
        jdbc.update(
            "UPDATE ecos_pipeline_task SET yaml_content = ?, updated_at = NOW() WHERE id = ?",
            yamlContent, id);

        log.info("从 Git 拉取成功: taskId={}", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", id);
        result.put("yamlUpdated", true);
        return result;
    }

    public Map<String, Object> loadFromGit(Map<String, Object> body) throws GitException {
        String gitUrl = (String) body.get("gitUrl");
        String pipelineId = (String) body.get("pipelineId");
        String branch = (String) body.getOrDefault("branch", "main");
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String localPath = (String) body.getOrDefault("localPath",
            "/tmp/ecos-git/load-" + UUID.randomUUID().toString().substring(0, 8));

        if (gitUrl == null || pipelineId == null) {
            throw new IllegalArgumentException("gitUrl 和 pipelineId 不能为空");
        }

        gitService.clone(gitUrl, localPath, username, password);
        gitService.checkoutBranch(localPath, branch);
        String yamlContent = loadPipelineYaml(localPath, pipelineId);

        String taskId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT') ON CONFLICT (id) DO UPDATE SET yaml_content = EXCLUDED.yaml_content, updated_at = NOW()",
            taskId, "Git: " + pipelineId, "从 Git 加载", yamlContent, gitUrl, branch);

        log.info("从 Git 加载 Pipeline: gitUrl={}, pipelineId={}", gitUrl, pipelineId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("pipelineId", pipelineId);
        result.put("gitUrl", gitUrl);
        return result;
    }

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
