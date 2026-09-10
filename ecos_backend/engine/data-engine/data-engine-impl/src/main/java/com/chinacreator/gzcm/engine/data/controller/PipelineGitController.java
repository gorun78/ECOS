package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.PipelineGitService;
import com.chinacreator.gzcm.runtime.access.git.GitService.GitException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/data/pipeline")
public class PipelineGitController {

    private final PipelineGitService gitService;

    public PipelineGitController(PipelineGitService gitService) {
        this.gitService = gitService;
    }

    @PostMapping("/tasks/{id}/git/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("提交到 Git 成功", gitService.commit(id, body));
        } catch (GitException e) {
            return ApiResponse.internalError("Git 提交失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("Git 提交失败: " + e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/git/pull")
    public ApiResponse<Map<String, Object>> pull(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("从 Git 拉取成功", gitService.pull(id, body));
        } catch (GitException e) {
            return ApiResponse.internalError("Git 拉取失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("Git 拉取失败: " + e.getMessage());
        }
    }

    @PostMapping("/git/load")
    public ApiResponse<Map<String, Object>> loadFromGit(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("从 Git 加载成功", gitService.loadFromGit(body));
        } catch (GitException e) {
            return ApiResponse.internalError("从 Git 加载失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("从 Git 加载失败: " + e.getMessage());
        }
    }

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

    @PostMapping("/git/branch")
    public ApiResponse<Map<String, Object>> switchBranch(@RequestBody Map<String, Object> body) {
        try {
            String localPath = (String) body.get("localPath");
            String branchName = (String) body.get("branchName");
            String taskId = (String) body.get("taskId");

            if (localPath == null || branchName == null) {
                return ApiResponse.badRequest("localPath 和 branchName 不能为空");
            }

            return ApiResponse.success("分支切换成功", gitService.switchBranch(localPath, branchName, taskId));
        } catch (GitException e) {
            return ApiResponse.internalError("切换分支失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.internalError("切换分支失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/engine/data/pipeline/git/versions/{id}
     *
     * <p>返回 Pipeline 定义的 Git 版本列表（commit SHAs，7 位 short，最新在上）。
     * Pipeline 首次 commit 之前返回 {@code []}（空数组，不抛）；本地仓库不存在同样返回空。
     *
     * <p>与 {@code /tasks/{id}/git/commit} 调用同一 per-pipeline 子目录
     * （{@code pipelines/{id}/pipeline.yaml}），保证版本与提交点 1:1 对齐。
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
