package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交到 Git 响应（POST /tasks/{id}/git/commit）。
 *
 * <p>status="not_available" + reason 表示服务端拒绝执行（如仅传已废弃的 localPath），
 * 属显式失败而非伪成功。
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

    /** 执行状态：null=成功；not_available=未开放/被拒绝 */
    private String status;

    /** status=not_available 时的指引原因 */
    private String reason;
}
