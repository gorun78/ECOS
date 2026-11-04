package com.chinacreator.gzcm.engine.ontology.git;

import java.io.Serializable;

/**
 * 本体 Git 同步（pull/load）结果（强类型契约，VG-2 收口）。
 *
 * <p>{@code status} 取值：
 * <ul>
 *   <li>{@code pulled} — 已从服务端注册的 remote 拉取到工作树，{@code repositoryId/remote/branch} 有值；</li>
 *   <li>{@code not_available} — 缺 {@code repositoryId} / 仓库未注册或禁用 / git 不可用；
 *       此时 {@code reason} 必填，<b>禁伪成功</b>（与 {@link OntologyGitArchiveResult} 同口径）。</li>
 * </ul>
 *
 * <p>仓库只由服务端 {@code repositoryId}（runtime 注册表）定位；客户端 {@code url}/远端地址一律不入参。</p>
 */
public class OntologyGitSyncResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PULLED = "pulled";
    public static final String STATUS_NOT_AVAILABLE = "not_available";

    /** pulled / not_available。 */
    private String status;

    /** not_available 时的原因（伪成功禁止）。 */
    private String reason;

    /** 本体 ID（load 无本体上下文时为 null，不伪造）。 */
    private String ontologyId;

    /** 定位到的服务端仓库逻辑标识。 */
    private String repositoryId;

    /** 远端名（默认 origin）。 */
    private String remote;

    /** 分支名（仓库缺省分支，缺省 main）。 */
    private String branch;

    /** 结果生成时间戳（epoch millis）。 */
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

    public String getOntologyId() {
        return ontologyId;
    }

    public void setOntologyId(String ontologyId) {
        this.ontologyId = ontologyId;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
