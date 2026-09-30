package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 从 Git 拉取请求（POST /api/v1/engine/data/pipeline/tasks/{id}/git/pull）。
 *
 * <p>定位仓库优先 repositoryId；localPath 只读兼容（传入即拒绝执行并返回 not_available 指引）。
 * 缺省时服务端按 sys_config ecos_git_repo_root 单源解析 {repoRoot}/pipeline/{id}。
 */
@Data
public class PipelineGitPullRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库注册 ID（可选，优先；服务端据此解析实际路径） */
    private String repositoryId;

    /** @deprecated 已废弃入参：仅保留反序列化兼容，传入时返回 not_available 指引改用 repositoryId */
    @Deprecated
    private String localPath;
}
