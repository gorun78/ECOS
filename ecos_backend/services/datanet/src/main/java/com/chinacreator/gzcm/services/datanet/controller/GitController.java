package com.chinacreator.gzcm.services.datanet.controller;

import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;

/**
 * ECOS 数据工作台 — Git 版本管理 API (P3-A 从 gateway 迁至 datanet)。
 *
 * <p>#147C 收口后的口径（四条，逐条对应原违例）：</p>
 * <ol>
 *   <li><b>仓库定位单源 = repositoryId 走 runtime 注册表</b>（{@code ecos_control.ecos_git_repo}）。
 *       原 {@code commits} 腿用 {@code GitRepoRootResolver.resolveRepoPath(repoId)} 自行拼路径
 *       再拿它去查库（第二套路径源，DI-1 违例），本批删除该依赖：本地路径只由属主
 *       {@code GitRepoPathResolver} 在 {@link GitRepositoryService} 内解析。</li>
 *   <li><b>提交历史读 Git 对象库</b>（{@link GitService#listCommits}）。原腿 SELECT 的
 *       {@code td_git_repository} 表<b>从不存在</b>（注册表已按 DQ-2 定名 {@code ecos_git_repo}，
 *       且它记的是「仓库」不是「提交」），SQL 必然抛异常，再被泛 catch 折叠成
 *       「items=[] + total=0 + success」＝假绿；现读不到就是显式失败。</li>
 *   <li><b>失败不冒充成功</b>：未注册/停用 ⇒ 404；注册面未装配或读表故障 ⇒ 503；
 *       Git 底座操作失败 ⇒ 500 带原因；参数缺失 ⇒ 400。四类分态由属主服务
 *       {@link GitRepositoryService.GitRepositoryException#getHttpStatus()} 决定，
 *       本层照载不改写（禁「一个 404 包打不可用与停用两态」）。</li>
 *   <li><b>状态行真实（D-B4-59 族）</b>：失败信封一律经 {@link #failure} 写上
 *       {@code ResponseEntity.status(...)}，不再 {@code return ApiResponse.error(...)} 裸回——
 *       裸回形态下 HTTP 状态行恒 200，调用方按状态行判成败即读到假绿。</li>
 * </ol>
 *
 * <p>能力未实现面（rollback / branch 切换）保持既有
 * {@code status=not_available + reason} 显式声明，禁伪成功；该声明是<b>业务体</b>字段，
 * 状态行仍为 200（请求确实被处理并如实回报「不可用」），与「失败冒充成功」不同态。
 * 分支枚举已在 #147C 接上属主 {@link GitService#listBranches}（读真实 ref，不再回硬编码假列表）。
 * 响应键面（{@code repoId}/{@code items}/{@code offset}/{@code limit}）保持不变，
 * 出入参强类型（DI-2）另批收口。</p>
 *
 * @author ECOS Data Workbench Phase 2
 * @since PMO-49 P3-A
 */
@RestController
@RequestMapping("/api/v1/ecos/git")
public class GitController {

    private static final Logger log = LoggerFactory.getLogger(GitController.class);

    /** 仓库未注册/已停用（注册表读面按行显式拒绝，404）。 */
    private static final String CODE_REPO_UNAVAILABLE = "ECOS_GIT_REPO_UNAVAILABLE";

    /** 注册面缺位（未装配 / 读表故障 / 未登记任何启用行）——不可用 ≠ 未注册（503）。 */
    private static final String CODE_GIT_UNAVAILABLE = "ECOS_GIT_UNAVAILABLE";

    /** Git 底座操作失败（仓库已定位，但对象库操作异常）。 */
    private static final String CODE_GIT_OPERATION_FAILED = "ECOS_GIT_OPERATION_FAILED";

    /** 参数缺失类出口机读码（单载体：本文件唯一出口，禁下游各抄一份）。 */
    private static final String CODE_COMMIT_MESSAGE_REQUIRED = "ECOS_GIT_COMMIT_MESSAGE_REQUIRED";
    private static final String CODE_TAG_NAME_REQUIRED = "ECOS_GIT_TAG_NAME_REQUIRED";
    private static final String CODE_ROLLBACK_TARGET_REQUIRED = "ECOS_GIT_ROLLBACK_TARGET_REQUIRED";

    private final GitService gitService;
    private final GitRepositoryService gitRepositoryService;

    public GitController(GitService gitService, GitRepositoryService gitRepositoryService) {
        this.gitService = gitService;
        this.gitRepositoryService = gitRepositoryService;
    }

    /**
     * repoId → 注册表行（含属主解析出的 localPath）。
     * <p>路径穿越守卫在属主侧（{@code GitRepoRootResolver} symlink 感知判定），
     * 本层不再自拼 {@code {root}/{repoId}}。</p>
     */
    private GitRepository resolveRepo(String repoId) throws GitRepositoryService.GitRepositoryException {
        return gitRepositoryService.getRepositoryById(repoId);
    }

    /**
     * 注册面显式拒绝 → 状态行出口（二态分档，判定权在属主异常携带的 httpStatus，本层不猜）。
     *
     * <p>404＝该仓库行不存在/已停用（客户端可换 repoId 重试）；其余（未装配、读表故障、
     * 空注册表）＝503＋{@link #CODE_GIT_UNAVAILABLE}（服务端侧未就绪，换参数无用）。
     * 两态混记成一枚 404 会把「底座没起来」说成「你没注册」，是 D-B4-71 承流门定的错账形态。</p>
     */
    private ResponseEntity<ApiResponse<Map<String, Object>>> repoUnavailable(
            String leg, String repoId, GitRepositoryService.GitRepositoryException e) {
        int status = e.getHttpStatus() == 404 ? 404 : 503;
        String machineCode = status == 404 ? CODE_REPO_UNAVAILABLE : CODE_GIT_UNAVAILABLE;
        log.warn("仓库注册面显式拒绝（{}）: repoId={}, status={}, reason={}",
                leg, repoId, status, e.getMessage());
        return failure(ApiResponse.error(status, machineCode, e.getMessage()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@RequestParam String repoId) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("status", repoId, e);
        }
        try {
            String commitId = gitService.getCurrentCommitId(repo);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("repoName", repo.getName());
            data.put("currentCommit", commitId);
            data.put("branch", repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("获取仓库状态失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "获取仓库状态失败: " + e.getMessage()));
        }
    }

    /**
     * 提交历史——数据源 = Git 仓库对象库（JGit log），不再是数据库表。
     *
     * <p>分页事实来源：{@code hasMore} 由「多取一条」判定（真值，不猜总数）；
     * {@code total} 仅在已读到尽头（{@code hasMore=false}）时给出确切值，
     * 未读到尽头时<b>不伪造</b>总数（原腿的 total 来自不存在的表，恒为假）。</p>
     */
    @GetMapping("/commits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commits(
            @RequestParam String repoId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("commits", repoId, e);
        }
        int safeOffset = Math.max(offset, 0);
        int safeLimit = limit <= 0 ? GitService.DEFAULT_COMMIT_LOG_LIMIT
                : Math.min(limit, GitService.MAX_COMMIT_LOG_LIMIT);
        try {
            // 多取一条只为判 hasMore，属主侧按 MAX 夹顶，不会因 +1 越界
            List<GitCommitView> fetched = gitService.listCommits(repo, safeOffset, safeLimit + 1);
            boolean hasMore = fetched.size() > safeLimit;
            List<GitCommitView> page = hasMore ? fetched.subList(0, safeLimit) : fetched;
            List<Map<String, Object>> items = new ArrayList<>(page.size());
            for (GitCommitView commit : page) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", commit.getCommitId());
                item.put("commitId", commit.getCommitId());
                item.put("message", commit.getMessage());
                item.put("author", commit.getAuthorName());
                item.put("committedAt", commit.getCommittedAt());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("items", items);
            data.put("offset", safeOffset);
            data.put("limit", safeLimit);
            data.put("hasMore", hasMore);
            if (!hasMore) {
                data.put("total", safeOffset + items.size());
            }
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("查询提交历史失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "查询提交历史失败: " + e.getMessage()));
        }
    }

    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diff(
            @RequestParam String repoId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("diff", repoId, e);
        }
        String fromRef = (from != null && !from.isEmpty()) ? from : "HEAD~1";
        String toRef = (to != null && !to.isEmpty()) ? to : "HEAD";
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("from", fromRef);
            data.put("to", toRef);
            data.putAll(gitService.diff(repo, fromRef, toRef));
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("获取差异失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "获取差异失败: " + e.getMessage()));
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commit(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("commit", repoId, e);
        }
        Map<String, Object> b = body != null ? body : Map.of();
        String message = (String) b.getOrDefault("message", "");
        if (message == null || message.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_COMMIT_MESSAGE_REQUIRED, "提交信息不能为空"));
        }
        String authorName = (String) b.getOrDefault("authorName", "system");
        String authorEmail = (String) b.getOrDefault("authorEmail", "system@ecos.local");
        try {
            String commitId = gitService.commitAllChanges(repo, message, authorName, authorEmail);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("commitId", commitId);
            data.put("message", message);
            data.put("committedAt", Instant.now().toString());
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("提交失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "提交失败: " + e.getMessage()));
        }
    }

    @PostMapping("/tag")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tag(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("tag", repoId, e);
        }
        Map<String, Object> b = body != null ? body : Map.of();
        String tagName = (String) b.getOrDefault("name", "");
        String message = (String) b.getOrDefault("message", "");
        if (tagName == null || tagName.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_TAG_NAME_REQUIRED, "标签名不能为空"));
        }
        try {
            String createdTag = gitService.createTag(repo, tagName,
                    (message != null && !message.isEmpty()) ? message : "Tag " + tagName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("tag", createdTag);
            data.put("message", message);
            data.put("createdAt", Instant.now().toString());
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("创建标签失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "创建标签失败: " + e.getMessage()));
        }
    }

    @PostMapping("/rollback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("rollback", repoId, e);
        }
        Map<String, Object> b = body != null ? body : Map.of();
        String targetCommitId = (String) b.getOrDefault("commitId", "");
        if (targetCommitId == null || targetCommitId.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_ROLLBACK_TARGET_REQUIRED, "目标 commitId 不能为空"));
        }
        log.info("Rollback requested (not_available): repoId={}, targetCommit={}", repoId, targetCommitId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repoId", repo.getRepositoryId());
        data.put("rollbackTo", targetCommitId);
        data.put("status", "not_available");
        data.put("reason", "回滚能力未实现（runtime GitService 无 revert 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
        return ok(ApiResponse.success(data));
    }

    /**
     * 分支枚举 —— 数据源 = Git 对象库（#147C 接线属主 {@link GitService#listBranches}）。
     *
     * <p>原腿返回硬编码 {@code [main,dev,prod]} 假数据（禁伪成功口径下已移除）；本批 runtime 底座
     * 补齐 {@code listBranches} 后改读真实 ref 面。属主解析面缺位（{@code localPath} 为 null）
     * 或未注册/停用一律走显式失败，不返回空列表冒充「没有分支」。</p>
     *
     * <p>{@code localPath} 未解析＝<b>服务端侧未就绪</b>（仓根配置 {@code ecos_git_repo_root} 缺位），
     * 原记 404 是把「配置没配」说成「仓库没注册」，本批改 503＋{@link #CODE_GIT_UNAVAILABLE}。</p>
     */
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<Map<String, Object>>> branches(@RequestParam String repoId) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("branches", repoId, e);
        }
        if (repo.getLocalPath() == null || repo.getLocalPath().trim().isEmpty()) {
            log.warn("仓库本地路径未解析，按不可用处理: repoId={}", repoId);
            return failure(ApiResponse.error(503, CODE_GIT_UNAVAILABLE,
                    "仓库已注册但本地路径未解析（属主解析面 ecos_git_repo_root 缺位）: repoId=" + repoId));
        }
        String defaultBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
        try {
            List<String> branches = gitService.listBranches(repo.getLocalPath());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("repoId", repo.getRepositoryId());
            data.put("current", defaultBranch);
            data.put("branches", branches);
            data.put("status", "ok");
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            log.error("枚举分支失败: repoId={}", repoId, e);
            return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED, "枚举分支失败: " + e.getMessage()));
        }
    }

    @PostMapping("/branch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchBranch(
            @RequestParam String repoId,
            @RequestBody Map<String, Object> body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("branch", repoId, e);
        }
        Map<String, Object> b = body != null ? body : Map.of();
        String targetBranch = (String) b.getOrDefault("branch", "main");
        log.info("Branch switch requested (not_available): repoId={}, to={}", repoId, targetBranch);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repoId", repo.getRepositoryId());
        data.put("requestedBranch", targetBranch);
        data.put("status", "not_available");
        data.put("reason", "分支切换能力未实现（runtime GitService 无面向仓库注册的 checkout 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
        return ok(ApiResponse.success(data));
    }

    /** push/pull 入参（强类型，禁裸 Map；当前仅 message 备注，禁 localPath/凭据）。 */
    public static final class GitPushPullRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 推送（#147P）：<b>能力未接，显式 not_available</b>。
     *
     * <p>§2.9.9④ 要求推送受「签名发布清单 + 分支保护」约束，而服务端凭据通道与签名基础设施尚未接入；
     * 前端此前调本路径得网关 404（能力缺失冒充"接口不存在"）。现改为：仓库定位照常分态
     * （未注册 404 / 未装配 503），定位成功后 200 + {@code status=not_available} + 原因，
     * <b>禁把未推送报成推送成功</b>。真实推送落地时换实现、响应键面保持 additive。</p>
     */
    @PostMapping("/push")
    public ResponseEntity<ApiResponse<Map<String, Object>>> push(
            @RequestParam String repoId,
            @RequestBody(required = false) GitPushPullRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("push", repoId, e);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repoId", repo.getRepositoryId());
        data.put("status", "not_available");
        data.put("reason", "推送通道未接：需服务端凭据与签名发布清单（PublishManifest/BranchProtectionPolicy），"
                + "禁止伪成功；真实推送通道落地前本端点只做能力分态回报");
        log.info("Git push requested (not_available): repoId={}", repoId);
        return ok(ApiResponse.success(data));
    }

    /** 拉取（#147P）：同 {@link #push} 的诚实不可用口径（远端与凭据通道未接）。 */
    @PostMapping("/pull")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pull(
            @RequestParam String repoId,
            @RequestBody(required = false) GitPushPullRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("pull", repoId, e);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repoId", repo.getRepositoryId());
        data.put("status", "not_available");
        data.put("reason", "拉取通道未接：远端地址与凭据由服务端统一配置（禁客户端传 remote/token），"
                + "通道落地前如实回报不可用");
        log.info("Git pull requested (not_available): repoId={}", repoId);
        return ok(ApiResponse.success(data));
    }

    /** 成功面：状态行 200（显式承载，禁 {@code return ApiResponse} 裸回形态）。 */
    private static ResponseEntity<ApiResponse<Map<String, Object>>> ok(ApiResponse<Map<String, Object>> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 失败面：状态行取信封 code（400/404/500/503），与 body 同源单值。
     * <p>本文件唯一失败出口；body 已是 error 信封，这里只负责把状态行写实。</p>
     */
    private static ResponseEntity<ApiResponse<Map<String, Object>>> failure(ApiResponse<Map<String, Object>> body) {
        return ResponseEntity.status(body.getCode()).body(body);
    }
}
