package com.chinacreator.gzcm.engine.kb.dto;

/**
 * 规则版本行内容来源（#147V，§2.9.5② 四版本概念的读侧落点）。
 *
 * <p>为什么要把来源写进契约而不是只落日志：#147E 起版本快照会归档到 {@code kb-asset-tree}，
 * 但读面始终返回权威库的 {@code snapshot} 列 ⇒「历史版本只进 Git」在外层不可证伪
 * （返回的到底是 Git 内容还是 DB 兜底，消费方无从判断）。本枚举把三态显式化，
 * {@link #DB_FALLBACK} 必须配 {@code content_reason} 说明退回缘由。</p>
 *
 * <p>三态与本体域 {@code VersionContentSource} 同名同义但<b>刻意不共用类型</b>：
 * 跨引擎引用会把 kb-engine 钉到 ontology-engine 的类上（铁律「跨引擎不操作对方表」的同族耦合禁令），
 * 而两域的对外键名本就不同（本体驼峰 {@code contentSource}、规则蛇形 {@code content_source}，
 * 各自受本域响应契约约束），共用 Java 类型并不能统一对外契约。</p>
 *
 * @author ECOS Knowledge Engine #147V
 */
public enum RuleContentSource {

    /** 已归档（{@code git_ref} 非空）且按 ref 从对象库读回成功 ⇒ 内容是 Git 历史版本本体。 */
    GIT,

    /** 未归档（{@code git_ref} 空）或 Git 通道未装配 ⇒ 内容取权威库快照列，属正常在用态。 */
    DB_IN_USE,

    /** 已归档但按 ref 读回失败 ⇒ 退回权威库快照列兜底，须在 {@code content_reason} 说明缘由。 */
    DB_FALLBACK
}
