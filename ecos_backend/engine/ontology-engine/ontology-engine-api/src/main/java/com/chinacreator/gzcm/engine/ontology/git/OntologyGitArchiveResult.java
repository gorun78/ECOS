package com.chinacreator.gzcm.engine.ontology.git;

import java.io.Serializable;

/**
 * 本体版本 Git 归档结果（强类型契约，VG-7）。
 *
 * <p>{@code status} 取值：
 * <ul>
 *   <li>{@code committed} — 内容已写入工作树并 commit，tag 已创建，{@code commitId} 来自
 *       {@code GitService.getCurrentCommitId}（真实值，禁止伪造）；</li>
 *   <li>{@code not_available} — 仓库未注册 / runtime 不可用 / 缺少必要入参 / git 操作失败；
 *       此时 {@code reason} 必填，禁止伪成功。</li>
 * </ul>
 */
public class OntologyGitArchiveResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_COMMITTED = "committed";
    public static final String STATUS_NOT_AVAILABLE = "not_available";

    /** committed / not_available。 */
    private String status;

    /** not_available 时的原因（伪成功禁止）。 */
    private String reason;

    /** 定位到的服务端仓库逻辑标识。 */
    private String repositoryId;

    /** 资产类型。 */
    private String assetType;

    /** 资产 ID。 */
    private String assetId;

    /** 版本号。 */
    private String versionNo;

    /** 真实提交号（来自 getCurrentCommitId）。 */
    private String commitId;

    /** 创建的标签，格式 {assetType}/{assetId}/{versionNo}。 */
    private String tag;

    /** 归档时间戳（epoch millis）。 */
    private Long timestamp;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

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

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
