package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 从 Git 加载 Pipeline 请求（POST /api/v1/engine/data/pipeline/git/load）。
 *
 * <p>定位仓库优先 repositoryId；path 只读兼容（传入即拒绝执行并返回 not_available 指引）。
 * gitUrl 须通过允许域注册表（sys_config ecos.git.allowed_hosts）校验。
 */
@Data
public class PipelineGitLoadRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库注册 ID（可选，优先；服务端据此解析实际路径） */
    private String repositoryId;

    /** 目标分支（可选，缺省 main） */
    private String branch;

    /** 任务 ID（可选，存在时回写 git_branch） */
    private String taskId;

    /** 远程仓库地址（可选，须经允许域注册表校验） */
    private String gitUrl;

    /** Pipeline ID（可选，缺省时按 taskId 生成 DRAFT 记录） */
    private String pipelineId;

    /** @deprecated 已废弃入参：仅保留反序列化兼容，传入时返回 not_available 指引改用 repositoryId */
    @Deprecated
    private String path;
}
