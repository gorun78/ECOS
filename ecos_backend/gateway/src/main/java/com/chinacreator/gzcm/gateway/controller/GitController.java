package com.chinacreator.gzcm.gateway.controller;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.git.GitService;
import com.chinacreator.gzcm.runtime.core.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.core.git.entity.GitRepository;

/**
 * ECOS 数据工作台 — Git 版本管理 API (v2 PMO spec)。
 *
 * <p>所有端点均接收 {@code repoId} 参数定位仓库，
 * 通过 {@link GitService} 与 {@link GitRepositoryService} 执行实际操作。
 * 仓库根路径从 {@code sys_config.ecos_git_repo_root} 读取，默认 {@code /home/guorongxiao/ecos-git-repos}。
 *
 * <h3>端点一览：</h3>
 * <ol>
 *   <li>GET  /api/v1/ecos/git/status?repoId=    — 仓库状态</li>
 *   <li>GET  /api/v1/ecos/git/commits?repoId=   — 提交历史</li>
 *   <li>GET  /api/v1/ecos/git/diff?repoId=      — 版本差异</li>
 *   <li>POST /api/v1/ecos/git/commit?repoId=    — 提交</li>
 *   <li>POST /api/v1/ecos/git/tag?repoId=       — 打标签</li>
 *   <li>POST /api/v1/ecos/git/rollback?repoId=  — 回滚</li>
 *   <li>GET  /api/v1/ecos/git/branches?repoId=  — 分支列表</li>
 *   <li>POST /api/v1/ecos/git/branch?repoId=    — 切换分支</li>
 * </ol>
 *
 * @author ECOS Data Workbench Phase 2
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
    private JdbcTemplate jdbc;

    /** 仓库根路径，从 sys_config 读取，默认 /home/guorongxiao/ecos-git-repos */
    private String repoRoot = "/home/guorongxiao/ecos-git-repos";

    /**
     * 初始化：从 sys_config 表读取 ecos_git_repo_root 配置项。
     */
    @PostConstruct
    public void init() {
        try {
            String configValue = jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class,
                "ecos_git_repo_root"
            );
            if (configValue != null && !configValue.trim().isEmpty()) {
                repoRoot = configValue.trim();
                log.info("Loaded ecos_git_repo_root from sys_config: {}", repoRoot);
            } else {
                log.info("ecos_git_repo_root not set in sys_config, using default: {}", repoRoot);
            }
        } catch (Exception e) {
            log.warn("Failed to read ecos_git_repo_root from sys_config, using default '{}': {}",
                     repoRoot, e.getMessage());
        }
    }

    // ── helper ────────────────────────────────────────────────────────

    /**
     * 根据 repoId 解析仓库对象。
     */
    private GitRepository resolveRepo(String repoId) throws GitRepositoryService.GitRepositoryException {
        return gitRepositoryService.getRepositoryById(repoId);
    }

    /**
     * 构建仓库的本地工作路径。
     */
    private String repoPath(String repoId) {
        return repoRoot + "/" + repoId;
    }

    // ════════════════════════════════════════════════════════════════════
    // 1. GET /api/v1/ecos/git/status?repoId=… — 仓库状态
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestParam String repoId) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String commitId = gitService.getCurrentCommitId(repo);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("repoName", repo.getName());
            data.put("localPath", repoPath(repoId));
            data.put("currentCommit", commitId);
            data.put("branch", repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to get status for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"获取仓库状态失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 2. GET /api/v1/ecos/git/commits?repoId=… — 提交历史
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/commits")
    public ApiResponse<Map<String, Object>> commits(
            @RequestParam String repoId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            // 通过 GitRepositoryService 验证仓库存在
            resolveRepo(repoId);

            String repoIdPath = repoPath(repoId);

            String countSql = "SELECT count(*) FROM td_git_repository WHERE local_path = ?";
            int total = jdbc.queryForObject(countSql, Integer.class, repoIdPath);

            String sql = "SELECT id, commit_id, message, author, branch, "
                       + "committed_at, created_at "
                       + "FROM td_git_repository "
                       + "WHERE local_path = ? "
                       + "ORDER BY committed_at DESC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, repoIdPath, limit, offset);

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
            log.warn("Repository not found for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"仓库不存在: " + e.getMessage());
        } catch (Exception e) {
            log.warn("查询提交历史失败（表可能尚未创建），返回空列表: {}", e.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("items", Collections.emptyList());
            data.put("total", 0);
            data.put("offset", offset);
            data.put("limit", limit);
            return ApiResponse.success(data);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 3. GET /api/v1/ecos/git/diff?repoId=… — 版本差异
    // ════════════════════════════════════════════════════════════════════

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
            log.error("Failed to diff for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"获取差异失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 4. POST /api/v1/ecos/git/commit?repoId=… — 提交
    // ════════════════════════════════════════════════════════════════════

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
                return ApiResponse.error(500,"提交信息不能为空");
            }

            String commitId = gitService.commitAllChanges(repo, message, authorName, authorEmail);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("commitId", commitId);
            data.put("message", message);
            data.put("committedAt", Instant.now().toString());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to commit for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"提交失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 5. POST /api/v1/ecos/git/tag?repoId=… — 打标签
    // ════════════════════════════════════════════════════════════════════

    @PostMapping("/tag")
    public ApiResponse<Map<String, Object>> tag(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String tagName = (String) body.getOrDefault("name", "");
            String message = (String) body.getOrDefault("message", "");

            if (tagName == null || tagName.trim().isEmpty()) {
                return ApiResponse.error(500,"标签名不能为空");
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
            log.error("Failed to create tag for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"创建标签失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create tag for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"创建标签失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 6. POST /api/v1/ecos/git/rollback?repoId=… — 回滚
    // ════════════════════════════════════════════════════════════════════

    @PostMapping("/rollback")
    public ApiResponse<Map<String, Object>> rollback(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String targetCommitId = (String) body.getOrDefault("commitId", "");

            if (targetCommitId == null || targetCommitId.trim().isEmpty()) {
                return ApiResponse.error(500,"目标 commitId 不能为空");
            }

            // GitService 暂无 revert 方法，通过 pull 实现占位回滚
            String currentBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
            // 记录回滚意图：后续可通过 gitService 扩展 revert 方法实现
            log.info("Rollback requested: repoId={}, targetCommit={}, branch={}", repoId, targetCommitId, currentBranch);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("rollbackTo", targetCommitId);
            data.put("branch", currentBranch);
            data.put("rolledBackAt", Instant.now().toString());
            data.put("status", "stub — 回滚操作已记录，等待 GitService.revert 实现");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to rollback for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"回滚失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 7. GET /api/v1/ecos/git/branches?repoId=… — 分支列表
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/branches")
    public ApiResponse<Map<String, Object>> branches(@RequestParam String repoId) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String defaultBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("current", defaultBranch);
            // GitService 暂无 branches() 方法，返回默认分支列表作为占位
            data.put("branches", List.of(defaultBranch, "dev", "prod"));
            data.put("status", "stub — 等待 GitService.branches 实现");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to list branches for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"获取分支列表失败: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 8. POST /api/v1/ecos/git/branch?repoId=… — 切换分支
    // ════════════════════════════════════════════════════════════════════

    @PostMapping("/branch")
    public ApiResponse<Map<String, Object>> switchBranch(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        try {
            GitRepository repo = resolveRepo(repoId);
            String targetBranch = (String) body.getOrDefault("branch", "main");
            String currentBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";

            // GitService 暂无 checkout 方法，记录切换意图
            log.info("Branch switch requested: repoId={}, from={}, to={}", repoId, currentBranch, targetBranch);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repoId);
            data.put("switchedTo", targetBranch);
            data.put("previousBranch", currentBranch);
            data.put("status", "stub — 分支切换已记录，等待 GitService.checkout 实现");
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to switch branch for repoId={}: {}", repoId, e.getMessage());
            return ApiResponse.error(500,"切换分支失败: " + e.getMessage());
        }
    }
}
