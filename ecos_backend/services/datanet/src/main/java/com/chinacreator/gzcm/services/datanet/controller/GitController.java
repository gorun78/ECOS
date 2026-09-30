package com.chinacreator.gzcm.services.datanet.controller;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.GitRepoRootResolver;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.chinacreator.gzcm.services.datanet.service.GitQueryService;

/**
 * ECOS 数据工作台 — Git 版本管理 API (P3-A 从 gateway 迁至 datanet)。
 *
 * <p>所有端点均接收 {@code repoId} 参数定位仓库，通过 {@link GitService} 与
 * {@link GitRepositoryService} 执行实际操作。仓库根路径经 {@link GitRepoRootResolver}
 * 从 sys_config key {@code ecos_git_repo_root} 单源解析（repoId 做 path-traversal 校验，
 * 响应体不回显 localPath）。
 *
 * @author ECOS Data Workbench Phase 2
 * @since PMO-49 P3-A
 */
@RestController
@RequestMapping("/api/v1/ecos/git")
public class GitController {

    private static final Logger log = LoggerFactory.getLogger(GitController.class);

    @Autowired
    private GitService gitService;

    @Autowired
    private GitRepositoryService gitRepositoryService;

    @Autowired
    private GitQueryService gitQueryService;

    @Autowired
    private GitRepoRootResolver repoRootResolver;

    private GitRepository resolveRepo(String repoId) throws GitRepositoryService.GitRepositoryException {
        return gitRepositoryService.getRepositoryById(repoId);
    }

    /** repoId → {repoRoot}/{repoId}，非法 repoId（穿越/绝对路径）直接拒绝 */
    private String repoPath(String repoId) {
        return repoRootResolver.resolveRepoPath(repoId);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestParam String repoId) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String commitId = gitService.getCurrentCommitId(repo);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("repoName", repo.getName());
            data.put("currentCommit", commitId);
            data.put("branch", repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取仓库状态失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "获取仓库状态失败");
        }
    }

    @GetMapping("/commits")
    public ApiResponse<Map<String, Object>> commits(
            @RequestParam String repoId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            resolveRepo(repoId);
            String repoIdPath = repoPath(repoId);
            String countSql = "SELECT count(*) FROM td_git_repository WHERE local_path = ?";
            int total = gitQueryService.queryForObject(countSql, Integer.class, repoIdPath);

            String sql = "SELECT id, commit_id, message, author, branch, "
                       + "committed_at, created_at "
                       + "FROM td_git_repository "
                       + "WHERE local_path = ? "
                       + "ORDER BY committed_at DESC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = gitQueryService.queryForList(sql, repoIdPath, limit, offset);
            List<Map<String, Object>> items = rows.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.get("id"));
                    item.put("commitId", row.get("commit_id"));
                    item.put("message", row.get("message"));
                    item.put("author", row.get("author"));
                    item.put("branch", row.get("branch"));
                    item.put("committedAt", row.get("committed_at"));
                    item.put("createdAt", row.get("created_at"));
                    return item;
                })
                .collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("items", items);
            data.put("total", total);
            data.put("offset", offset);
            data.put("limit", limit);
            return ApiResponse.success(data);
        } catch (GitRepositoryService.GitRepositoryException e) {
            log.warn("仓库不存在: repoId={}", repoId, e);
            return ApiResponse.error(500, "仓库不存在");
        } catch (IllegalArgumentException e) {
            log.warn("非法 repoId（路径校验拒绝）: repoId={}", repoId, e);
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.warn("查询提交历史失败（表可能尚未创建），返回空列表: repoId={}", repoId, e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("items", Collections.emptyList());
            data.put("total", 0);
            data.put("offset", offset);
            data.put("limit", limit);
            return ApiResponse.success(data);
        }
    }

    @GetMapping("/diff")
    public ApiResponse<Map<String, Object>> diff(
            @RequestParam String repoId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String fromRef = (from != null && !from.isEmpty()) ? from : "HEAD~1";
            String toRef = (to != null && !to.isEmpty()) ? to : "HEAD";
            Map<String, Object> diffResult = gitService.diff(repo, fromRef, toRef);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("from", fromRef);
            data.put("to", toRef);
            data.putAll(diffResult);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取差异失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "获取差异失败");
        }
    }

    @PostMapping("/commit")
    public ApiResponse<Map<String, Object>> commit(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String message = (String) body.getOrDefault("message", "");
            String authorName = (String) body.getOrDefault("authorName", "system");
            String authorEmail = (String) body.getOrDefault("authorEmail", "system@ecos.local");
            if (message == null || message.trim().isEmpty()) {
                return ApiResponse.error(500, "提交信息不能为空");
            }
            String commitId = gitService.commitAllChanges(repo, message, authorName, authorEmail);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("commitId", commitId);
            data.put("message", message);
            data.put("committedAt", Instant.now().toString());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("提交失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "提交失败");
        }
    }

    @PostMapping("/tag")
    public ApiResponse<Map<String, Object>> tag(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String tagName = (String) body.getOrDefault("name", "");
            String message = (String) body.getOrDefault("message", "");
            if (tagName == null || tagName.trim().isEmpty()) {
                return ApiResponse.error(500, "标签名不能为空");
            }
            String createdTag = gitService.createTag(repo, tagName,
                (message != null && !message.isEmpty()) ? message : "Tag " + tagName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("tag", createdTag);
            data.put("message", message);
            data.put("createdAt", Instant.now().toString());
            return ApiResponse.success(data);
        } catch (GitService.GitException e) {
            log.error("创建标签失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "创建标签失败");
        } catch (Exception e) {
            log.error("创建标签失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "创建标签失败");
        }
    }

    @PostMapping("/rollback")
    public ApiResponse<Map<String, Object>> rollback(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String targetCommitId = (String) body.getOrDefault("commitId", "");
            if (targetCommitId == null || targetCommitId.trim().isEmpty()) {
                return ApiResponse.error(500, "目标 commitId 不能为空");
            }
            log.info("Rollback requested (not_available): repoId={}, targetCommit={}", repoId, targetCommitId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("rollbackTo", targetCommitId);
            data.put("status", "not_available");
            data.put("reason", "回滚能力未实现（runtime GitService 无 revert 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("回滚失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "回滚失败");
        }
    }

    @GetMapping("/branches")
    public ApiResponse<Map<String, Object>> branches(@RequestParam String repoId) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String defaultBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("current", defaultBranch);
            data.put("branches", Collections.emptyList());
            data.put("status", "not_available");
            data.put("reason", "分支枚举能力未实现（runtime GitService 无 branches 操作）；原 [main,dev,prod] 为硬编码假数据，已按禁伪成功口径移除");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取分支列表失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "获取分支列表失败");
        }
    }

    @PostMapping("/branch")
    public ApiResponse<Map<String, Object>> switchBranch(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String targetBranch = (String) body.getOrDefault("branch", "main");
            log.info("Branch switch requested (not_available): repoId={}, to={}", repoId, targetBranch);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("requestedBranch", targetBranch);
            data.put("status", "not_available");
            data.put("reason", "分支切换能力未实现（runtime GitService 无面向仓库注册的 checkout 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("切换分支失败: repoId={}", repoId, e);
            return ApiResponse.error(500, "切换分支失败");
        }
    }
}
