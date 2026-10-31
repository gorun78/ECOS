package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体版本 Git 归档历史 VO — F6-07 规范前缀 {@code /api/v1/ontology/git} 只读读面（#146av additive）。
 *
 * <p>数据来源 = {@code ecos_ontology.ecos_ontology_version} 表
 * （发布时经 runtime Git 归档回填的 {@code git_ref} 即历史真值），
 * 仅含已发布且 {@code git_ref} 非空的版本行——不伪造 JGit commit log。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyGitHistoryVO {

    /** 版本 ID（ver 前缀） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 版本号（如 1.0.0） */
    private String versionNo;

    /** 状态（只读读面恒为 Published） */
    private String status;

    /** Git 归档引用（commit id 或 tag，由发布归档链路回填） */
    private String gitRef;

    /** 发布人 */
    private String publisher;

    /** 发布时间 ISO 字符串 */
    private String publishedAt;

    /** 创建时间 ISO 字符串 */
    private String createdAt;
}
