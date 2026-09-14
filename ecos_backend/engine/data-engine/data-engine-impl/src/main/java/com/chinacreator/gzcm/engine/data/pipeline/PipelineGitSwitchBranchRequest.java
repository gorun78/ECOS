package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 切换 Git 分支请求（POST /api/v1/engine/data/pipeline/git/branch）。
 *
 * <p>字段名与既有客户端契约对齐：localPath/branchName/taskId。
 */
@Data
public class PipelineGitSwitchBranchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本地仓库路径（必填） */
    private String localPath;

    /** 目标分支名（必填） */
    private String branchName;

    /** 任务 ID（可选，存在时回写 ecos_pipeline_task.git_branch） */
    private String taskId;
}
