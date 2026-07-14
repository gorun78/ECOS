package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.PipelineGitService;
import com.chinacreator.gzcm.runtime.core.git.GitService.GitException;
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
}
