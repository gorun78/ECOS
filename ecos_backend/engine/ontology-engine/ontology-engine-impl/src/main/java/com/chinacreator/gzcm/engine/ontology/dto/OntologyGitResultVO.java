package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Git 版本操作（commit / pull / load）结果 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code OntologyGitServiceImpl.commit / pull / load} 既有 Map 输出契约：
 * 三个端点共享兼容结构 {@code ontologyId / url / commitMessage / status / timestamp}，
 * NON_NULL 保证各端点只输出实际填充的字段（commit 输出 commitMessage；
 * pull 无 url；load 无 ontologyId/commitMessage）。
 *
 * <p>T16-4: Git 提交记录动态结构 — 当前 impl 仅返回上述 5 个标量字段，
 * 未来若扩展 commit hash / author / files 等动态 Git 元数据，可在本 VO 追加
 * Object 豁免字段，无需改 API 契约。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyGitResultVO {

    /** 所属本体 ID（commit / pull 填充；load 不填） */
    private String ontologyId;

    /** Git 远程 URL（load 填充） */
    private String url;

    /** 提交信息（commit 填充） */
    private String commitMessage;

    /** 操作状态（committed / pulled / loaded） */
    private String status;

    /** 时间戳（epoch millis） */
    private Long timestamp;
}
