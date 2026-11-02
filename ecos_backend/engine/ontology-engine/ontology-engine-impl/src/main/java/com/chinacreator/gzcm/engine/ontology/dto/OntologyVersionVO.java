package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体版本（Ontology Version）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologyVersionService.toMap(OntologyVersion)} 输出。
 * {@code snapshot} 为动态快照对象（JSON），保持 Object 类型
 * （与既有 Map 内 {@code safeParseJson} 行为一致）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyVersionVO {

    /** 版本 ID（ver 前缀） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 版本号（如 1.0.0） */
    private String versionNo;

    /** 状态（Draft / Published / Deprecated） */
    private String status;

    /**
     * 快照（JSON 对象）— 动态内容保持 Object（与既有 safeParseJson 输出对齐）。
     */
    private Object snapshot;

    /** 变更日志 */
    private String changeLog;

    /** Git 归档引用（#147R 读切 Git 的判定键；未归档为 null）。 */
    private String gitRef;

    /**
     * 快照内容来源（#147T 只增键）。
     *
     * <p>单行读面（详情/diff）= <b>本次返回内容的实际来源</b>；
     * 列表读面（{@code /{ontologyId}/versions}、{@code GET /api/v1/ecos/versions}）=
     * <b>内容所在地指针</b>，列表按裁决不内联内容（见 {@code toIndexVO} javadoc），
     * 消费方要内容须走单行读面。</p>
     */
    private VersionContentSource contentSource;

    /**
     * {@code contentSource = DB_FALLBACK} 时的失败原因（#147T）。
     * <p>禁静默退回：Git 读回失败必须把原因带出契约面，供对账与人工处置定位。</p>
     */
    private String contentReason;


    /** 发布人 */
    private String publisher;

    /** 发布时间 ISO 字符串 */
    private String publishedAt;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    // ── 以下两个字段仅 diffVO 端点填充 ────────────────────────

    /**
     * 版本对比 — 前一版本号（仅 diffVO 返回）。
     * <p>用于 {@code GET /{ontologyId}/versions/{v1}/diff/{v2}}，
     * 与既有 Map 行为 {@code result.put("version1", ver1.getVersionNo())} 对齐。
     */
    private String version1;

    /** 版本对比 — 第二版本号（仅 diffVO 返回）。 */
    private String version2;

    /** 版本对比 — 前一个版本快照（仅 diffVO 返回；动态 JSON 子集，保持 Object 与既有契约一致）。 */
    private Object snapshot1;

    /** 版本对比 — 第二版本快照（仅 diffVO 返回；动态 JSON 子集，保持 Object 与既有契约一致）。 */
    private Object snapshot2;

    // ── #147D：发布 / 双写状态面（additive，与 status 列两套口径）────────

    /**
     * 发布执行态（§2.9.6① 的 16 态词表名，单源在 runtime-access 的 {@code PublishState}）。
     *
     * <p><b>与 {@link #status} 的区别（不可混用）</b>：{@code status} 是权威库行的存储态
     * （Draft/Published/Deprecated），本字段是"这次发布三腿跑到哪、对账成不成"的执行态。
     * 二者允许不一致，且不一致正是要暴露的形态——行已 Published 而归档腿失败，
     * 本字段为 {@code PARTIAL_FAILURE}（§2.9.6① 红线：禁把部分成功报完全成功）。
     *
     * <p><b>只增不改</b>：{@code NON_NULL} 序列化，读端点（list/get/diff）不填即不出键，
     * 既有消费方零感知；仅发布/回滚路径填充。
     */
    private String publishState;

    /** 发布态原因（三腿摘要 + 对账判定；{@code publishState} 的可读佐证，禁为空谈）。 */
    private String publishStateReason;

    /**
     * 审计对账结论（§2.9.4④ L95 读回；#147AD）：{@code NOT_AVAILABLE} / {@code MISSING} /
     * {@code PERSISTED} / {@code UNKNOWN}。
     *
     * <p>发布侧投递审计只拿到「总线已受理」，故 {@code publishState} 只能记「待对账」；
     * 本字段是对那条悬空记录的**读回结论**。只增不改：仅在版本详情读面且该版本已走发布/归档时填充，
     * 其余读面不出现该键（{@code NON_NULL}），既有消费方零感知。</p>
     */
    private String auditReconciliation;

    /** 对账结论的可读原因（为何不是 {@code PERSISTED}，或落库行的 apply/verification 值）。 */
    private String auditReconciliationReason;

    public String getAuditReconciliation() {
        return auditReconciliation;
    }

    public void setAuditReconciliation(String auditReconciliation) {
        this.auditReconciliation = auditReconciliation;
    }

    public String getAuditReconciliationReason() {
        return auditReconciliationReason;
    }

    public void setAuditReconciliationReason(String auditReconciliationReason) {
        this.auditReconciliationReason = auditReconciliationReason;
    }
}
