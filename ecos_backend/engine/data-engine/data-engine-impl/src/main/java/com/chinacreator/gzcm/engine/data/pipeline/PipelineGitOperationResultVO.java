package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Git 操作通用响应（pull / load / branch）。
 *
 * <p>status="not_available" + reason 表示服务端拒绝执行（如仅传已废弃的 localPath），
 * 属显式失败而非伪成功；响应体不回显服务器本地路径。
 */
@Data
public class PipelineGitOperationResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID（commit/pull/load 场景） */
    private String taskId;

    /** yaml 是否已回写（pull 场景） */
    private Boolean yamlUpdated;

    /** 加载的 Pipeline ID（load 场景） */
    private String pipelineId;

    /** 远程仓库地址回显（load 场景） */
    private String gitUrl;

    /** 切换后的分支名（branch 场景） */
    private String branchName;

    /** 执行状态：null=成功；not_available=未开放/被拒绝 */
    private String status;

    /** status=not_available 时的指引原因 */
    private String reason;
}
