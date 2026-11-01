package com.chinacreator.gzcm.engine.ontology.git;

/**
 * 按 git ref 读回本体版本内容的回执（#147E read-at-ref）。
 *
 * <p>三态语义与 {@link OntologyGitArchiveResult} 对齐：{@link #STATUS_FOUND} 携带真实内容；
 * {@link #STATUS_NOT_AVAILABLE} 表示通道/引用/文件不可用并附原因；禁以空内容冒充"版本为空"。</p>
 *
 * @author ECOS Ontology #147E
 */
public class OntologyGitReadResult {

    /** 已读回内容（content 非空）。 */
    public static final String STATUS_FOUND = "found";

    /** 不可用（通道未装配/引用缺失/文件在该 ref 下不存在），content 为空。 */
    public static final String STATUS_NOT_AVAILABLE = "not_available";

    private String status = STATUS_NOT_AVAILABLE;
    private String repositoryId;
    private String ref;
    private String content;
    private String reason;
    private long timestamp;

    public boolean isFound() {
        return STATUS_FOUND.equals(status) && content != null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
