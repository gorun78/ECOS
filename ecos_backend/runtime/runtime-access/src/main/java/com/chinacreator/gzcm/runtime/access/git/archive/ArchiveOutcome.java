package com.chinacreator.gzcm.runtime.access.git.archive;

/**
 * 设计资产归档结果（#147E；禁伪成功三态）。
 *
 * @param status    三态：{@link Status#ARCHIVED} / {@link Status#NOT_AVAILABLE} / {@link Status#FAILED}
 * @param commitId  真实提交号（仅 ARCHIVED 非空）
 * @param tag       标签（可空：无版本号时不打标签；FAILED 时可能已有提交号）
 * @param reason    人读原因（NOT_AVAILABLE/FAILED 必填）
 * @author ECOS Runtime底座 #147E
 */
public record ArchiveOutcome(ArchiveOutcome.Status status, String commitId, String tag, String reason) {

    public enum Status {
        /** 归档成功且有真实 commitId。 */
        ARCHIVED,
        /** 通道/环境未就绪（未注册、禁用、工作树未解析）——合法现状态，不是业务失败。 */
        NOT_AVAILABLE,
        /** 确证失败（写盘/提交/打标签）。 */
        FAILED
    }

    public static ArchiveOutcome archived(String commitId, String tag) {
        return new ArchiveOutcome(Status.ARCHIVED, commitId, tag, null);
    }

    public static ArchiveOutcome notAvailable(String reason) {
        return new ArchiveOutcome(Status.NOT_AVAILABLE, null, null, reason);
    }

    public static ArchiveOutcome failed(String reason) {
        return new ArchiveOutcome(Status.FAILED, null, null, reason);
    }

    public boolean isArchived() {
        return status == Status.ARCHIVED && commitId != null && !commitId.isBlank();
    }
}
