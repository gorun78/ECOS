package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交到 Git 响应（POST /tasks/{id}/git/commit）。
 *
 * <p>commitId 当前为 null（JGit commit 不回填 SHA 到响应，前端按 message 提示已提交处理）；
 * branch/message 与既有 Map 响应字段名对齐，保证前后端契约兼容。
 */
@Data
public class PipelineGitCommitResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次提交说明 */
    private String taskId;

    /** 实际使用的分支 */
    private String branch;

    /** git commit SHA（当前为空，后续接入 commit_id 回填） */
    private String commitId;

    /** 提交说明回显 */
    private String message;
}
