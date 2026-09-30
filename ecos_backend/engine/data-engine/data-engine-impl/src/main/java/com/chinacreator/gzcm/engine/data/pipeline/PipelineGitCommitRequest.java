package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交到 Git 请求（POST /api/v1/engine/data/pipeline/tasks/{id}/git/commit）。
 *
 * <p>字段名与既有前端契约对齐（GitCommitDialog: message/branch/author）。
 * 定位仓库优先 repositoryId；localPath 只读兼容（传入即拒绝执行并返回 not_available 指引）。
 * 凭据不再由请求体携带，改由服务端经 security-engine 加密存储解析注入。
 */
@Data
public class PipelineGitCommitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提交说明（可选，缺省 "Update pipeline {id}"） */
    private String message;

    /** 目标分支（仅透传字段，当前 commit 行为由 PipelineGitService 决定，保留占位不破坏现有客户端） */
    private String branch;

    /** 提交作者（仅透传字段） */
    private String author;

    /** 仓库注册 ID（可选，优先；服务端据此解析实际路径） */
    private String repositoryId;

    /** @deprecated 已废弃入参：仅保留反序列化兼容，传入时返回 not_available 指引改用 repositoryId */
    @Deprecated
    private String localPath;
}
