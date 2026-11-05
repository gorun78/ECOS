package com.chinacreator.gzcm.services.datanet.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.GitService;
import com.chinacreator.gzcm.runtime.access.git.entity.GitCommitView;
import com.chinacreator.gzcm.runtime.access.git.entity.GitRepository;
import com.fasterxml.jackson.annotation.JsonInclude;

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
 * <p>能力未实现面（rollback / branch 切换 / push / pull）保持既有
 * {@code status=not_available + reason} 显式声明，禁伪成功；该声明是<b>业务体</b>字段，
 * 状态行仍为 200（请求确实被处理并如实回报「不可用」），与「失败冒充成功」不同态。
 * 分支枚举已在 #147C 接上属主 {@link GitService#listBranches}（读真实 ref，不再回硬编码假列表）。</p>
 *
 * <p><b>#147BT 出入参强类型化（DoD DI-2 第一批清退 + §2.9.9 G3「datanet GitController 仍收
 * {@code Map}」）</b>：8 个响应体改为 {@code Git*VO}、4 个请求体改为 {@code Git*Request}，
 * 键名/可空性逐字保真（VO 统一 {@code @JsonInclude(NON_NULL)}，与旧 {@code Map} 的
 * 「未 put 即不出现」同形——旧 {@code commits} 腿只在读到尽头时 put {@code total}，现由
 * {@code total==null} 省略同位键）。属主 {@link GitService#diff} 仍返回动态
 * {@code Map}（runtime-access 契约，只增不改），本层按<b>已登记的固定键集</b>
 * （{@code repository/from/to/filesChanged/changedCount}）逐键映射进 VO；
 * 该映射是显式白名单，未来属主加键需同批改 VO（禁静默丢键冒充「diff 无变化」）。</p>
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
    private <T> ResponseEntity<ApiResponse<T>> repoUnavailable(
            String leg, String repoId, GitRepositoryService.GitRepositoryException e) {
        int status = e.getHttpStatus() == 404 ? 404 : 503;
        String machineCode = status == 404 ? CODE_REPO_UNAVAILABLE : CODE_GIT_UNAVAILABLE;
        log.warn("仓库注册面显式拒绝（{}）: repoId={}, status={}, reason={}",
                leg, repoId, status, e.getMessage());
        return failure(ApiResponse.error(status, machineCode, e.getMessage()));
    }

    /**
     * Git 底座失败 → 状态行出口（#147BU · G8 登记缺陷）。
     *
     * <p>分类权在属主异常：{@link GitService.GitException.Kind#NOT_AVAILABLE}（本地目录不在位 /
     * localPath 未解析）＝<b>服务端未就绪</b> ⇒ 503＋{@link #CODE_GIT_UNAVAILABLE}，换参数重试无用；
     * {@link GitService.GitException.Kind#OPERATION_FAILED}（仓库已就位、对象库操作异常）⇒ 500。
     * 此前一律 500，把「目录没准备好」报成「操作失败」，客户端无法区分该重试还是该找运维。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> gitFailure(
            String leg, String repoId, String operation, GitService.GitException e) {
        if (e.getKind() == GitService.GitException.Kind.NOT_AVAILABLE) {
            log.warn("Git 底座未就绪（{}）: repoId={}, reason={}", leg, repoId, e.getMessage());
            return failure(ApiResponse.error(503, CODE_GIT_UNAVAILABLE,
                    operation + "（Git 底座未就绪）: " + e.getMessage()));
        }
        log.error("{}（{}）: repoId={}", operation, leg, repoId, e);
        return failure(ApiResponse.error(500, CODE_GIT_OPERATION_FAILED,
                operation + ": " + e.getMessage()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<GitStatusVO>> status(@RequestParam String repoId) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("status", repoId, e);
        }
        try {
            GitStatusVO data = new GitStatusVO();
            data.setRepoId(repo.getRepositoryId());
            data.setRepoName(repo.getName());
            data.setCurrentCommit(gitService.getCurrentCommitId(repo));
            data.setBranch(repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main");
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("status", repoId, "获取仓库状态失败", e);
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
    public ResponseEntity<ApiResponse<GitCommitsVO>> commits(
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
            List<GitCommitItemVO> items = new ArrayList<>(page.size());
            for (GitCommitView commit : page) {
                GitCommitItemVO item = new GitCommitItemVO();
                item.setId(commit.getCommitId());
                item.setCommitId(commit.getCommitId());
                item.setMessage(commit.getMessage());
                item.setAuthor(commit.getAuthorName());
                item.setCommittedAt(commit.getCommittedAt());
                items.add(item);
            }
            GitCommitsVO data = new GitCommitsVO();
            data.setRepoId(repo.getRepositoryId());
            data.setItems(items);
            data.setOffset(safeOffset);
            data.setLimit(safeLimit);
            data.setHasMore(hasMore);
            if (!hasMore) {
                data.setTotal(safeOffset + items.size());
            }
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("commits", repoId, "查询提交历史失败", e);
        }
    }

    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<GitDiffVO>> diff(
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
            GitDiffVO data = toDiffVO(repo.getRepositoryId(), fromRef, toRef,
                    gitService.diff(repo, fromRef, toRef));
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("diff", repoId, "获取差异失败", e);
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<GitCommitResultVO>> commit(
            @RequestParam String repoId,
            @RequestBody GitCommitRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("commit", repoId, e);
        }
        String message = body == null ? "" : blankToEmpty(body.getMessage());
        if (message.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_COMMIT_MESSAGE_REQUIRED, "提交信息不能为空"));
        }
        String authorName = body != null && body.getAuthorName() != null ? body.getAuthorName() : "system";
        String authorEmail = body != null && body.getAuthorEmail() != null
                ? body.getAuthorEmail() : "system@ecos.local";
        try {
            String commitId = gitService.commitAllChanges(repo, message, authorName, authorEmail);
            GitCommitResultVO data = new GitCommitResultVO();
            data.setRepoId(repo.getRepositoryId());
            data.setCommitId(commitId);
            data.setMessage(message);
            data.setCommittedAt(Instant.now().toString());
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("commit", repoId, "提交失败", e);
        }
    }

    @PostMapping("/tag")
    public ResponseEntity<ApiResponse<GitTagResultVO>> tag(
            @RequestParam String repoId,
            @RequestBody GitTagRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("tag", repoId, e);
        }
        String tagName = body == null ? "" : blankToEmpty(body.getName());
        String message = body == null ? "" : blankToEmpty(body.getMessage());
        if (tagName.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_TAG_NAME_REQUIRED, "标签名不能为空"));
        }
        try {
            String createdTag = gitService.createTag(repo, tagName,
                    message.isEmpty() ? "Tag " + tagName : message);
            GitTagResultVO data = new GitTagResultVO();
            data.setRepoId(repo.getRepositoryId());
            data.setTag(createdTag);
            data.setMessage(message);
            data.setCreatedAt(Instant.now().toString());
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("tag", repoId, "创建标签失败", e);
        }
    }

    @PostMapping("/rollback")
    public ResponseEntity<ApiResponse<GitRollbackResultVO>> rollback(
            @RequestParam String repoId,
            @RequestBody GitRollbackRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("rollback", repoId, e);
        }
        String targetCommitId = body == null ? "" : blankToEmpty(body.getCommitId());
        if (targetCommitId.trim().isEmpty()) {
            return failure(ApiResponse.error(400, CODE_ROLLBACK_TARGET_REQUIRED, "目标 commitId 不能为空"));
        }
        log.info("Rollback requested (not_available): repoId={}, targetCommit={}", repoId, targetCommitId);
        GitRollbackResultVO data = new GitRollbackResultVO();
        data.setRepoId(repo.getRepositoryId());
        data.setRollbackTo(targetCommitId);
        data.setStatus(STATUS_NOT_AVAILABLE);
        data.setReason("回滚能力未实现（runtime GitService 无 revert 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
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
     * 原记 404 是把「配置没配」说成「仓库没注册」，#147C 起改 503＋{@link #CODE_GIT_UNAVAILABLE}。
     * #147BU 起「目录不在位」由属主 {@link GitService#listBranches} 抛
     * {@link GitService.GitException.Kind#NOT_AVAILABLE} 同一分档（本层空路径预检保留，两路同码同态）。</p>
     */
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<GitBranchesVO>> branches(@RequestParam String repoId) {
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
            GitBranchesVO data = new GitBranchesVO();
            data.setRepoId(repo.getRepositoryId());
            data.setCurrent(defaultBranch);
            data.setBranches(gitService.listBranches(repo.getLocalPath()));
            data.setStatus(STATUS_OK);
            return ok(ApiResponse.success(data));
        } catch (GitService.GitException e) {
            return gitFailure("branches", repoId, "枚举分支失败", e);
        }
    }

    @PostMapping("/branch")
    public ResponseEntity<ApiResponse<GitBranchSwitchResultVO>> switchBranch(
            @RequestParam String repoId,
            @RequestBody GitBranchSwitchRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("branch", repoId, e);
        }
        String targetBranch = body != null && body.getBranch() != null ? body.getBranch() : "main";
        log.info("Branch switch requested (not_available): repoId={}, to={}", repoId, targetBranch);
        GitBranchSwitchResultVO data = new GitBranchSwitchResultVO();
        data.setRepoId(repo.getRepositoryId());
        data.setRequestedBranch(targetBranch);
        data.setStatus(STATUS_NOT_AVAILABLE);
        data.setReason("分支切换能力未实现（runtime GitService 无面向仓库注册的 checkout 操作），禁止伪成功；待 Git 归档真实落地批次（H4-T4）开放");
        return ok(ApiResponse.success(data));
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
    public ResponseEntity<ApiResponse<GitSyncResultVO>> push(
            @RequestParam String repoId,
            @RequestBody(required = false) GitPushPullRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("push", repoId, e);
        }
        GitSyncResultVO data = new GitSyncResultVO();
        data.setRepoId(repo.getRepositoryId());
        data.setStatus(STATUS_NOT_AVAILABLE);
        data.setReason("推送通道未接：需服务端凭据与签名发布清单（PublishManifest/BranchProtectionPolicy），"
                + "禁止伪成功；真实推送通道落地前本端点只做能力分态回报");
        log.info("Git push requested (not_available): repoId={}", repoId);
        return ok(ApiResponse.success(data));
    }

    /** 拉取（#147P）：同 {@link #push} 的诚实不可用口径（远端与凭据通道未接）。 */
    @PostMapping("/pull")
    public ResponseEntity<ApiResponse<GitSyncResultVO>> pull(
            @RequestParam String repoId,
            @RequestBody(required = false) GitPushPullRequest body) {
        GitRepository repo;
        try {
            repo = resolveRepo(repoId);
        } catch (GitRepositoryService.GitRepositoryException e) {
            return repoUnavailable("pull", repoId, e);
        }
        GitSyncResultVO data = new GitSyncResultVO();
        data.setRepoId(repo.getRepositoryId());
        data.setStatus(STATUS_NOT_AVAILABLE);
        data.setReason("拉取通道未接：远端地址与凭据由服务端统一配置（禁客户端传 remote/token），"
                + "通道落地前如实回报不可用");
        log.info("Git pull requested (not_available): repoId={}", repoId);
        return ok(ApiResponse.success(data));
    }

    /** 成功面：状态行 200（显式承载，禁 {@code return ApiResponse} 裸回形态）。 */
    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 失败面：状态行取信封 code（400/404/500/503），与 body 同源单值。
     * <p>本文件唯一失败出口；body 已是 error 信封，这里只负责把状态行写实。</p>
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body) {
        return ResponseEntity.status(body.getCode()).body(body);
    }

    /** 属主 diff 的动态 Map → 固定键 VO（白名单映射，禁静默丢键）。 */
    private static GitDiffVO toDiffVO(String repoId, String fromRef, String toRef,
                                      Map<String, Object> raw) {
        GitDiffVO data = new GitDiffVO();
        data.setRepoId(repoId);
        data.setFrom(fromRef);
        data.setTo(toRef);
        if (raw != null) {
            data.setRepository(asString(raw.get("repository")));
            List<GitDiffFileVO> files = new ArrayList<>();
            if (raw.get("filesChanged") instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> cell) {
                        GitDiffFileVO file = new GitDiffFileVO();
                        file.setChangeType(asString(cell.get("changeType")));
                        file.setOldPath(asString(cell.get("oldPath")));
                        file.setNewPath(asString(cell.get("newPath")));
                        files.add(file);
                    }
                }
            }
            data.setFilesChanged(files);
            data.setChangedCount(raw.get("changedCount") instanceof Number n ? n.intValue() : files.size());
        }
        return data;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 能力未实现面的业务体状态值（状态行仍 200，与失败面分档）。 */
    private static final String STATUS_NOT_AVAILABLE = "not_available";
    private static final String STATUS_OK = "ok";

    // ═══════════════════════════ 请求体（强类型，禁裸 Map）═══════════════════════════

    /** 提交请求：仅 message/作者两枚可选键；<b>禁</b> localPath/remote/凭据（铁律 §2.5 第 8 项）。 */
    public static final class GitCommitRequest {
        private String message;
        private String authorName;
        private String authorEmail;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public String getAuthorEmail() {
            return authorEmail;
        }

        public void setAuthorEmail(String authorEmail) {
            this.authorEmail = authorEmail;
        }
    }

    /** 打标签请求（{@code name} 必填，空/空白 ⇒ 400）。 */
    public static final class GitTagRequest {
        private String name;
        private String message;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /** 回滚请求（{@code commitId} 必填；当前能力未接，如实回 not_available）。 */
    public static final class GitRollbackRequest {
        private String commitId;

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }
    }

    /** 分支切换请求（能力未接，只回显请求值）。 */
    public static final class GitBranchSwitchRequest {
        private String branch;

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }
    }

    /** push/pull 入参（当前仅 message 备注，禁 localPath/凭据/remote/token）。 */
    public static final class GitPushPullRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // ═══════════════════════════ 响应体（键面与旧 Map 逐字一致）═══════════════════════════

    /** {@code GET /status}。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitStatusVO {
        private String repoId;
        private String repoName;
        private String currentCommit;
        private String branch;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getRepoName() {
            return repoName;
        }

        public void setRepoName(String repoName) {
            this.repoName = repoName;
        }

        public String getCurrentCommit() {
            return currentCommit;
        }

        public void setCurrentCommit(String currentCommit) {
            this.currentCommit = currentCommit;
        }

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }
    }

    /** {@code GET /commits} 的单条提交（{@code id} 与 {@code commitId} 同值，兼容两枚旧键）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitCommitItemVO {
        private String id;
        private String commitId;
        private String message;
        private String author;
        private Date committedAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public Date getCommittedAt() {
            return committedAt;
        }

        public void setCommittedAt(Date committedAt) {
            this.committedAt = committedAt;
        }
    }

    /** {@code GET /commits}：{@code total} 仅在读到尽头时出现（未读到尽头禁伪造总数）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitCommitsVO {
        private String repoId;
        private List<GitCommitItemVO> items;
        private Integer offset;
        private Integer limit;
        private Boolean hasMore;
        private Integer total;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public List<GitCommitItemVO> getItems() {
            return items;
        }

        public void setItems(List<GitCommitItemVO> items) {
            this.items = items;
        }

        public Integer getOffset() {
            return offset;
        }

        public void setOffset(Integer offset) {
            this.offset = offset;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }
    }

    /** {@code GET /diff} 的单文件变更行（属主 JGit DiffEntry 三键）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitDiffFileVO {
        private String changeType;
        private String oldPath;
        private String newPath;

        public String getChangeType() {
            return changeType;
        }

        public void setChangeType(String changeType) {
            this.changeType = changeType;
        }

        public String getOldPath() {
            return oldPath;
        }

        public void setOldPath(String oldPath) {
            this.oldPath = oldPath;
        }

        public String getNewPath() {
            return newPath;
        }

        public void setNewPath(String newPath) {
            this.newPath = newPath;
        }
    }

    /** {@code GET /diff}：属主动态 Map 的固定键面（映射口径见类注释）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitDiffVO {
        private String repoId;
        private String repository;
        private String from;
        private String to;
        private List<GitDiffFileVO> filesChanged;
        private Integer changedCount;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getRepository() {
            return repository;
        }

        public void setRepository(String repository) {
            this.repository = repository;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public List<GitDiffFileVO> getFilesChanged() {
            return filesChanged;
        }

        public void setFilesChanged(List<GitDiffFileVO> filesChanged) {
            this.filesChanged = filesChanged;
        }

        public Integer getChangedCount() {
            return changedCount;
        }

        public void setChangedCount(Integer changedCount) {
            this.changedCount = changedCount;
        }
    }

    /** {@code POST /commit} 成功回执（commitId 为属主真实返回，禁本地拼造）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitCommitResultVO {
        private String repoId;
        private String commitId;
        private String message;
        private String committedAt;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCommittedAt() {
            return committedAt;
        }

        public void setCommittedAt(String committedAt) {
            this.committedAt = committedAt;
        }
    }

    /** {@code POST /tag} 成功回执。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitTagResultVO {
        private String repoId;
        private String tag;
        private String message;
        private String createdAt;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    /** {@code POST /rollback}：能力未接，恒 {@code status=not_available} + 原因。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitRollbackResultVO {
        private String repoId;
        private String rollbackTo;
        private String status;
        private String reason;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getRollbackTo() {
            return rollbackTo;
        }

        public void setRollbackTo(String rollbackTo) {
            this.rollbackTo = rollbackTo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /** {@code GET /branches}：{@code branches} 为属主真实 ref 列表。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitBranchesVO {
        private String repoId;
        private String current;
        private List<String> branches;
        private String status;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getCurrent() {
            return current;
        }

        public void setCurrent(String current) {
            this.current = current;
        }

        public List<String> getBranches() {
            return branches;
        }

        public void setBranches(List<String> branches) {
            this.branches = branches;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** {@code POST /branch}：能力未接，回显请求分支。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitBranchSwitchResultVO {
        private String repoId;
        private String requestedBranch;
        private String status;
        private String reason;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getRequestedBranch() {
            return requestedBranch;
        }

        public void setRequestedBranch(String requestedBranch) {
            this.requestedBranch = requestedBranch;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /** {@code POST /push}／{@code POST /pull}：能力未接，同形回执。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class GitSyncResultVO {
        private String repoId;
        private String status;
        private String reason;

        public String getRepoId() {
            return repoId;
        }

        public void setRepoId(String repoId) {
            this.repoId = repoId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
