package com.chinacreator.gzcm.engine.ontology.git;

import java.io.Serializable;

/**
 * 本体版本 Git 归档请求（强类型契约，VG-7）。
 *
 * <p>安全约束（P0 红线，架构铁律 §2.5 / 后端开发规范 §十一 VG-2）：
 * <ul>
 *   <li>仅接受服务端 {@code repositoryId}（仓库注册表的逻辑标识）定位仓库，
 *       {@code localPath}/{@code remoteUrl} 一律由 runtime 注册表解析，<b>禁止</b>客户端直接传入；</li>
 *   <li>{@code assetType}/{@code assetId}/{@code versionNo} 写入工作树前做 path-traversal 校验；</li>
 *   <li>Git 凭据由服务端经 security-engine 加密注入，本请求不携带任何凭据字段。</li>
 * </ul>
 *
 * <p>本类为 {@link OntologyGitArchiveResult} 的入参，与旧 {@code Map} 契约并存
 * （API 只增不改）：旧 {@code OntologyGitService.commit/pull/load} 委托至本契约。
 */
public class OntologyGitArchiveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务端仓库逻辑标识（runtime 注册表 repositoryId）；禁止客户端传 localPath/gitUrl。 */
    private String repositoryId;

    /** 资产类型（本体引擎固定 {@code ontology}），构成 tag 首段。 */
    private String assetType;

    /** 资产 ID（本体 ontologyId），构成 tag 中段，写入工作树前做安全段校验。 */
    private String assetId;

    /** 版本号（如 {@code 1.0.2}），构成 tag 末段，写入工作树前做安全段校验。 */
    private String versionNo;

    /** 已序列化的版本内容（本体版本快照 JSON，来自 {@code OntologyVersion.snapshot}）。 */
    private String content;

    /** 提交信息（可选，缺省自动生成）。 */
    private String commitMessage;

    /** 提交作者名（可选，缺省 system）。 */
    private String authorName;

    /** 提交作者邮箱（可选，缺省 system@ecos.local）。 */
    private String authorEmail;

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }
}
