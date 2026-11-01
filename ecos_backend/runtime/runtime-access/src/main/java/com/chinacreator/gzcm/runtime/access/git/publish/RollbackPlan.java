package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 回滚计划——§2.9.6②「回滚 → 优先发布已知历史版本（git ref 检出重应用），非任意反向 SQL」
 * 的类型面表达（验收 §3.1.4 DF-3；#147D）。
 *
 * <p><b>本类存在的意义是把"反向 SQL"这条路写成不可表达</b>：可执行的回滚计划只有
 * {@link Mode#REPUBLISH_GIT_REF} 一种，它必须携带一个<b>已发布过的历史版本的 git ref</b>
 * 与该版本的 {@code version_no}；没有 ref 就没有计划（{@link Mode#UNAVAILABLE}），
 * 消费侧只能报「待人工」，不许现场拼一段 {@code DELETE}/{@code UPDATE} 把数据"抹回去"——
 * 那种写法在双写非原子的前提下会把「回滚」变成第二次不可审计的写入。</p>
 *
 * <p><b>与 {@code gitRef} 缺位的现实关系</b>：#147C 后本体侧归档成功即写回
 * {@code ecos_ontology_version.git_ref}，故已发布的历史版本确实持有可检出的 ref；
 * 但「按 ref 读回该版本内容」的能力在 {@code GitService}/{@code OntologyGitService}
 * 面上尚未提供（read-at-ref 缺失）。因此本批只能<b>产出并校验计划</b>（含 ref 必填门、
 * 模式单一来源、执行前的状态转换），把<b>实际检出重应用</b>留给 #147E 的读写腿切单通道，
 * 台账按此项登记为未覆盖面，禁在本批宣布 DF-3 端到端成立。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public final class RollbackPlan {

    /** 回滚机制唯一种类：把已知历史版本检出后重新应用为新的权威库版本。 */
    public static final String MECHANISM_REPUBLISH_KNOWN_VERSION =
            "REPUBLISH_KNOWN_VERSION_FROM_GIT_REF";

    /** 计划种类——只有 A 档可执行（禁新增"反向 SQL"档，§2.9.6②）。 */
    public enum Mode {

        /** 可执行：发布已知历史版本（git ref 检出重应用）。 */
        REPUBLISH_GIT_REF,

        /** 不可执行：缺少可检出的历史版本引用，须待人工处置。 */
        UNAVAILABLE
    }

    private final Mode mode;
    private final String repositoryId;
    private final String gitRef;
    private final String versionNo;
    private final String reason;

    private RollbackPlan(Mode mode, String repositoryId, String gitRef, String versionNo, String reason) {
        this.mode = mode;
        this.repositoryId = trimToNull(repositoryId);
        this.gitRef = trimToNull(gitRef);
        this.versionNo = trimToNull(versionNo);
        this.reason = trimToNull(reason);
    }

    /**
     * 构造「发布已知历史版本」计划。
     *
     * <p>任一必需引用为空白即降级为 {@link Mode#UNAVAILABLE}，原因照实记录——
     * 这是 §3.1.4 DF-5「缺 version_no/git_ref 拒发布」在回滚侧的同形门，
     * 禁造出一个"看似可执行、实则无处可检"的空壳计划。</p>
     */
    public static RollbackPlan republishKnownVersion(String repositoryId, String gitRef, String versionNo) {
        boolean missingGitRef = trimToNull(gitRef) == null;
        boolean missingVersionNo = trimToNull(versionNo) == null;
        if (missingGitRef && missingVersionNo) {
            return unavailable("缺 git_ref 与 version_no：无已知历史版本可检出（DF-5）");
        }
        if (missingGitRef) {
            return unavailable("缺 git_ref：历史版本未归档，无从检出（DF-5）");
        }
        if (missingVersionNo) {
            return unavailable("缺 version_no：历史版本引用不完整（DF-5）");
        }
        return new RollbackPlan(Mode.REPUBLISH_GIT_REF, repositoryId, gitRef, versionNo,
                "检出历史版本并重新应用为新的权威库版本");
    }

    /** 构造不可执行计划（待人工）。 */
    public static RollbackPlan unavailable(String reason) {
        return new RollbackPlan(Mode.UNAVAILABLE, null, null, null,
                reason == null ? "无可用回滚路径" : reason);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public Mode mode() {
        return mode;
    }

    public String repositoryId() {
        return repositoryId;
    }

    public String gitRef() {
        return gitRef;
    }

    public String versionNo() {
        return versionNo;
    }

    public String reason() {
        return reason;
    }

    /** 回滚机制单源标识；不可执行计划同样返回该串以外的 {@code null}，禁伪造机制名。 */
    public String mechanism() {
        return mode == Mode.REPUBLISH_GIT_REF ? MECHANISM_REPUBLISH_KNOWN_VERSION : null;
    }

    /** 是否可执行（唯一为真的种类是 {@link Mode#REPUBLISH_GIT_REF}）。 */
    public boolean isExecutable() {
        return mode == Mode.REPUBLISH_GIT_REF;
    }

    /** 供状态机与消费侧日志的单行说明。 */
    public String describe() {
        if (mode == Mode.UNAVAILABLE) {
            return "UNAVAILABLE(" + reason + ")";
        }
        return MECHANISM_REPUBLISH_KNOWN_VERSION
                + " repositoryId=" + repositoryId
                + " gitRef=" + gitRef
                + " versionNo=" + versionNo;
    }

    @Override
    public String toString() {
        return describe();
    }
}
