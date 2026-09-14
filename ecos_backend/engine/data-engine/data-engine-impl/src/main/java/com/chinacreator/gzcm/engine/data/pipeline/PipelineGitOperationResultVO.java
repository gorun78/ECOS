package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Git 操作通用响应（pull / load / branch）。
 *
 * <p>字段全量覆盖三个场景：pull（yamlUpdated=true）、load（pipelineId+gitUrl）、
 * branch（localPath+branchName），各场景按实际语义只填相关字段。
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

    /** 本地仓库路径回显（branch 场景） */
    private String localPath;

    /** 切换后的分支名（branch 场景） */
    private String branchName;
}
