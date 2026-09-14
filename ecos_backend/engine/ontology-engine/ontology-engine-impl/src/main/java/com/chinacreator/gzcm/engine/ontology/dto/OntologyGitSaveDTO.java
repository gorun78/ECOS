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

    /** Git 远程 URL（load 端点，可选） */
    private String url;
}
