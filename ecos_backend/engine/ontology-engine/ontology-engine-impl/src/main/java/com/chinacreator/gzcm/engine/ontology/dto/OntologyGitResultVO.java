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

    /** 操作状态（committed/pulled 为真实结果；not_available 表示未接入或失败，禁止伪成功） */
    private String status;

    /** 不可用原因（status=not_available 时必填，禁止伪成功） */
    private String reason;

    /** 时间戳（epoch millis） */
    private Long timestamp;

    /** 服务端仓库逻辑标识（commit/pull 填充） */
    private String repositoryId;

    /** 真实提交号（来自 runtime GitService.getCurrentCommitId，禁止伪造；仅 committed 时填充） */
    private String commitId;

    /** 归档标签，格式 {assetType}/{assetId}/{versionNo}（仅 commit 且提供 versionNo 时填充） */
    private String tag;

    /** 资产类型（commit 填充，缺省 ontology） */
    private String assetType;

    /** 版本号（commit 填充） */
    private String versionNo;

    /** pull 使用的服务端 remote（默认 origin，非客户端入参） */
    private String remote;

    /** pull 使用的分支（来自服务端仓库注册表 defaultBranch） */
    private String branch;
}
