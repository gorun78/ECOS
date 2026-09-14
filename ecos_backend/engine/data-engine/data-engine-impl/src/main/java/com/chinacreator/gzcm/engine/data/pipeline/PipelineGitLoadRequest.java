package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 从 Git 加载 Pipeline 请求（POST /api/v1/engine/data/pipeline/git/load）。
 *
 * <p>字段名与既有客户端契约对齐：path/branch/taskId（PMO 描述中 path=仓库本地路径）。
 */
@Data
public class PipelineGitLoadRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本地仓库路径（与 pipelineId/gitUrl 配套定位 pipeline.yaml） */
    private String path;

    /** 目标分支（可选，缺省 main） */
    private String branch;

    /** 任务 ID（可选，存在时回写 git_branch） */
    private String taskId;

    /** 远程仓库地址（可选，缺省时直接读本地 path 下的 pipeline.yaml） */
    private String gitUrl;

    /** Pipeline ID（可选，缺省时按 taskId 生成 DRAFT 记录） */
    private String pipelineId;
}
