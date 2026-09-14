package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 从 Git 拉取请求（POST /api/v1/engine/data/pipeline/tasks/{id}/git/pull）。
 *
 * <p>localPath 可选，缺省时走服务端默认（${java.io.tmpdir}/ecos-git/{id}）。
 */
@Data
public class PipelineGitPullRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本地仓库路径（可选，缺省 ${java.io.tmpdir}/ecos-git/{id}） */
    private String localPath;
}
