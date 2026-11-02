package com.chinacreator.gzcm.engine.ontology.dto;

/**
 * 版本快照内容的<b>实际来源</b>单源词表（#147T；§2.9.5② 四版本概念 + §2.9.6④「历史只从 Git 取」）。
 *
 * <p>存在意义：#147R 的读切只在<b>日志</b>里区分「取自 Git」与「退回 DB」，契约面看不出来——
 * 消费方无法证伪「内容到底来自哪儿」，对账与验收只能听服务自述。自本批起来源进入 VO，
 * 来源可被外部读出即等于「DB 快照列仍在被历史读消费」这件事可被外部检出。</p>
 *
 * <ul>
 *   <li>{@link #GIT} — 版本已归档（{@code git_ref} 非空）且对象库按 ref 读回成功；</li>
 *   <li>{@link #DB_IN_USE} — 版本未归档（{@code git_ref} 空，Draft/存量），内容权威仍在 DB 快照列，
 *       属过渡态而非终态（终态 = 归档后 {@link #GIT}）；</li>
 *   <li>{@link #DB_FALLBACK} — 版本已归档但按 ref 读回失败，退回 DB 快照列。
 *       <b>禁静默</b>：必须同时带 {@code contentReason} 说明失败原因，且不得据此宣称历史已切 Git。</li>
 * </ul>
 */
public enum VersionContentSource {

    /** 历史内容取自 Git 对象库（按 {@code git_ref} 读回）。 */
    GIT,

    /** 未归档版本，内容取自 DB 快照列（过渡态，待归档）。 */
    DB_IN_USE,

    /** 已归档但 Git 读回失败，退回 DB 快照列（异常态，须带原因）。 */
    DB_FALLBACK
}
