package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 切换 Git 分支请求（POST /api/v1/engine/data/pipeline/git/branch）。
 *
 * <p>定位仓库优先 repositoryId；localPath 只读兼容（传入即拒绝执行并返回 not_available 指引）。
 */
@Data
public class PipelineGitSwitchBranchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库注册 ID（必填；服务端据此解析实际路径） */
    private String repositoryId;

    /** 目标分支名（必填） */
    private String branchName;

    /** 任务 ID（可选，存在时回写 ecos_pipeline_task.git_branch） */
    private String taskId;

    /** @deprecated 已废弃入参：仅保留反序列化兼容，传入时返回 not_available 指引改用 repositoryId */
    @Deprecated
    private String localPath;
}
