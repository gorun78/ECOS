package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交到 Git 请求（POST /api/v1/engine/data/pipeline/tasks/{id}/git/commit）。
 *
 * <p>字段名与既有前端契约对齐（GitCommitDialog: message/branch/author），
 * localPath/username/password 可选，缺省时走服务端默认。
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

    /** 本地仓库路径（可选，缺省 ${java.io.tmpdir}/ecos-git/{id}） */
    private String localPath;

    /** Git 用户名（可选） */
    private String username;

    /** Git 密码（可选） */
    private String password;
}
