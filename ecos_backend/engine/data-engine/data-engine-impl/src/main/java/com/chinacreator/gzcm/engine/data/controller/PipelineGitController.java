package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitCommitResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitLoadRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitOperationResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitPullRequest;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineGitSwitchBranchRequest;
import com.chinacreator.gzcm.engine.data.service.PipelineGitService;
import com.chinacreator.gzcm.runtime.access.git.GitService.GitException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pipeline Git 版本管理控制器。
 *
 * <p>P2#3: 所有 6 个端点入参/出参改强类型 DTO/VO（HTTP 契约不变：
 * 路径、方法、JSON 字段名保持向后兼容）。
 *
 * <p>路由：/api/v1/engine/data/pipeline — 沿用 {@link com.chinacreator.gzcm.engine.data.pipeline.PipelineController}
 * 既有前缀，无新增 Controller，不触发三滤波器（VersionPrefixRewriteFilter KEEP）。
 */
@RestController
@RequestMapping("/api/v1/engine/data/pipeline")
public class PipelineGitController {

    private final PipelineGitService gitService;

    public PipelineGitController(PipelineGitService gitService) {
        this.gitService = gitService;
    }

    /**
     * POST /tasks/{id}/git/commit — 提交 Pipeline YAML 到 Git。
     * 入参：{@link PipelineGitCommitRequest}（message/branch/author + 可选 localPath/credentials），
     * 出参：{@link PipelineGitCommitResultVO}。
     */
    @PostMapping("/tasks/{id}/git/commit")
    public ApiResponse<PipelineGitCommitResultVO> commit(@PathVariable String id,
                                                          @RequestBody PipelineGitCommitRequest req) {
        try {
            return ApiResponse.success("提交到 Git 成功", gitService.commit(id, req));
        } catch (GitException e) {
            return ApiResponse.internalError("Git 提交失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("Git 提交失败: " + e.getMessage());
        }
    }

    /**
     * POST /tasks/{id}/git/pull — 从 Git 拉取 Pipeline YAML。
     * 入参：{@link PipelineGitPullRequest}（可选 localPath），出参：{@link PipelineGitOperationResultVO}。
     */
    @PostMapping("/tasks/{id}/git/pull")
    public ApiResponse<PipelineGitOperationResultVO> pull(@PathVariable String id,
                                                           @RequestBody PipelineGitPullRequest req) {
        try {
            return ApiResponse.success("从 Git 拉取成功", gitService.pull(id, req));
        } catch (GitException e) {
            return ApiResponse.internalError("Git 拉取失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("Git 拉取失败: " + e.getMessage());
        }
    }

    /**
     * POST /git/load — 从 Git 仓库加载 Pipeline 到 DB（首次可用）。
     * 入参：{@link PipelineGitLoadRequest}（path/branch/taskId/gitUrl/pipelineId），
     * 出参：{@link PipelineGitOperationResultVO}。
     */
    @PostMapping("/git/load")
    public ApiResponse<PipelineGitOperationResultVO> loadFromGit(@RequestBody PipelineGitLoadRequest req) {
        try {
            return ApiResponse.success("从 Git 加载成功", gitService.loadFromGit(req));
        } catch (GitException e) {
            return ApiResponse.internalError("从 Git 加载失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("从 Git 加载失败: " + e.getMessage());
        }
    }

    /**
     * GET /git/branches?localPath=xxx — 本地仓库分支列表。
     * （无 body，Get 端点路径参数无 Map 入参，本次 P2#3 范围外）
     */
    @GetMapping("/git/branches")
    public ApiResponse<List<String>> listBranches(@RequestParam String localPath) {
        try {
            return ApiResponse.success(gitService.listBranches(localPath));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("获取分支列表失败: " + e.getMessage());
        }
    }

    /**
     * POST /git/branch — 切换分支。
     * 入参：{@link PipelineGitSwitchBranchRequest}（localPath/branchName/taskId），
     * 出参：{@link PipelineGitOperationResultVO}。
     */
    @PostMapping("/git/branch")
    public ApiResponse<PipelineGitOperationResultVO> switchBranch(@RequestBody PipelineGitSwitchBranchRequest req) {
        try {
            return ApiResponse.success("分支切换成功", gitService.switchBranch(req));
        } catch (GitException e) {
            return ApiResponse.internalError("切换分支失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("切换分支失败: " + e.getMessage());
        }
    }

    /**
     * GET /git/versions/{id} — 返回 Pipeline 定义的 Git 版本列表（commit SHAs，7 位 short，最新在上）。
     * Pipeline 首次 commit 之前返回 {@code []}（空数组，不抛）；本地仓库不存在同样返回空。
     * 出参：{@code List<String>}（短路，无 Map 包装）。
     */
    @GetMapping("/git/versions/{id}")
    public ApiResponse<List<String>> listVersions(@PathVariable String id) {
        try {
            return ApiResponse.success(gitService.versions(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (GitException e) {
            return ApiResponse.internalError("读取 Git 版本失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("读取 Git 版本失败: " + e.getMessage());
        }
    }
}
