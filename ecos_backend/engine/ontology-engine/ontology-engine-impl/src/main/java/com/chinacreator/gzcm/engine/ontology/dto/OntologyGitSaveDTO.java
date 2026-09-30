package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * Git 版本操作入参 DTO — T16-5。
 *
 * <p>对应 {@code OntologyGitService.commit / pull / load} 三个端点的 body 已知字段：
 * commit 消费 {@code message}；load 消费 {@code url}；pull 无 body 字段。
 *
 * <p>T16-5: 收窄 PMO-51 迭代残留的 any-key 透传预留（{@code @JsonAnySetter} extras Map）——
 * 前端契约仅写 message / url 两键（旧 Map 入参的未知 key 时序上仅被
 * {@code service.getOrDefault} 默认值路径消费，无业务意义，丢弃等价）。
 * 未知字段由 Spring MVC 全局 ObjectMapper 忽略（FAIL_ON_UNKNOWN_PROPERTIES=false），
 * 与既有 Map 入参行为一致。
 */
@Data
public class OntologyGitSaveDTO {

    /** 提交信息（commit 端点，可选，缺省 "commit ontology {id}"） */
    private String message;

    /**
     * Git 远程 URL（历史 load 端点字段）。
     * <p><b>安全废弃</b>：H4-T4 起服务端禁止依据客户端 gitUrl 执行 clone/pull（P0 红线），
     * 该字段被忽略；仓库定位仅认 {@link #repositoryId}。为不破坏既有请求契约保留字段。
     */
    private String url;

    /** 服务端仓库逻辑标识（runtime 注册表 repositoryId）；commit/pull/load 定位仓库用。 */
    private String repositoryId;

    /** 资产类型（可选，缺省 ontology），构成 tag 首段。 */
    private String assetType;

    /** 版本号（可选，如 1.0.2），构成 tag 末段并命名归档文件。 */
    private String versionNo;

    /** 已序列化的版本内容（commit 用，通常为版本快照 JSON）。 */
    private String content;
}
