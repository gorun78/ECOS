package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.common.exception.DataBridgeException;

import java.io.Serial;

/**
 * 知识域（kb-engine）错误码常量与领域异常（详细设计-04 D.5）。
 *
 * <p>对治 W94（C75）三级掩蔽链：kb 侧本地 {@code @RestControllerAdvice} 将结构性故障
 * 透传为带 {@code ECOS-KB-***} 错误码的 4xx/5xx 响应，不再落入网关 {@code Exception→404} 或
 * 前端 {@code catch → 空态} 时被吞。
 *
 * <p>所有码均落在 {@link KbError#code()}，message 由调用方补充业务上下文；HTTP 状态码在
 * {@link KbError#httpStatus()}。
 */
public final class KbErrorCodes {

    private KbErrorCodes() {
    }

    // ── 快照 / 映射 ─────────────────────────────────────────────
    /** C.1 ① 本体快照缺失/版本不可用（拒绝抽取，禁无约束自由抽取）。 */
    public static final KbError SNAPSHOT_MISSING = new KbError(409, "ECOS-KB-011");
    /** C.1 抽取字段映射不满足 C1/C2（硬失败子集）。 */
    public static final KbError MAPPING_INVALID = new KbError(422, "ECOS-KB-012");

    // ── 底座不可用 ───────────────────────────────────────────────
    /** 安全裁决不可用 → 拒绝（DENY），纠正 K-44"warn 后继续"。 */
    public static final KbError SECURITY_UNAVAILABLE = new KbError(503, "ECOS-KB-020");
    /** runtime-task 任务底座不可用（禁降级为本地线程同步计算）。 */
    public static final KbError TASK_BASE_UNAVAILABLE = new KbError(503, "ECOS-KB-021");
    /** 事件底座不可用且本地兜底亦失败（fail-closed 拒绝写）。 */
    public static final KbError EVENT_BASE_UNAVAILABLE = new KbError(500, "ECOS-KB-022");
    /** LLM 底座不可用（非降级路径）。 */
    public static final KbError LLM_BASE_UNAVAILABLE = new KbError(503, "ECOS-KB-023");
    /**
     * 本体寻址底座不可用（{@code ServiceEndpointResolver} 未接入 ⇒ 本体集成硬依赖缺失）。
     *
     * <p><b>#142/B4-3e-42 补登</b>（差距 D-B4-71 属主侧第三批）：收口前
     * {@code KbEntityInstanceExtractionService.ontologyBase()} 抛
     * {@code IllegalStateException}，消息里写 {@code ECOS-KB-404} —— 该号既不在本表、
     * 全仓亦零引用，且 kb 局部 advice 无裸 {@code Exception} 兜底 ⇒ 只能落 gateway 入口兜底回 500。
     * 语义是「制品在本部署不可用」（架构铁律 §3.2.7 ⇒ 属主端点显式 503），不是「不变式被破坏」，
     * 故按本块「底座不可用」序列取次空号 <b>024</b>（020 安全／021 任务／022 事件／023 LLM 之后）。
     * 机读码走 {@link KbError#code()} 字段，不再冒充 message 前缀（D-B4-55 同族）。</p>
     */
    public static final KbError ONTOLOGY_BASE_UNAVAILABLE = new KbError(503, "ECOS-KB-024");

    // ── 存储 / 上传 ─────────────────────────────────────────────
    /** 对象存储（MinIO）不可用（上传）。 */
    public static final KbError OBJECT_STORAGE_UNAVAILABLE = new KbError(500, "ECOS-KB-030");
    /** 上传文件类型/大小不合规（415/413 边界校验）。 */
    public static final KbError UPLOAD_INVALID = new KbError(415, "ECOS-KB-031");

    // ── 画像 / 假设 ─────────────────────────────────────────────
    /** CURATED 供数不可达（画像取数）。 */
    public static final KbError CURATED_SUPPLY_UNREACHABLE = new KbError(502, "ECOS-KB-040");
    /** 画像样本不足且退化链穷尽。 */
    public static final KbError PROFILE_SAMPLE_EXHAUSTED = new KbError(422, "ECOS-KB-041");

    // ── 导航 / 路由 ─────────────────────────────────────────────
    /** 知识导航表缺失（kb_nav_*），替代伪 404。 */
    public static final KbError NAV_TABLES_MISSING = new KbError(500, "ECOS-KB-050");
    /** 路由映射缺失（结构性，K-11/K-12/K-14）。 */
    public static final KbError ROUTE_MAPPING_MISSING = new KbError(500, "ECOS-KB-051");

    // ── 状态机 / 不变式 ─────────────────────────────────────────
    /** 画像/假设状态机非法跃迁。 */
    public static final KbError STATUS_TRANSITION_INVALID = new KbError(409, "ECOS-KB-060");
    /** 已发布资产不可变（I-6）。 */
    public static final KbError PUBLISHED_IMMUTABLE = new KbError(409, "ECOS-KB-061");
    /** 权限不足（非知识管理员发布/审批）。 */
    public static final KbError PERMISSION_DENIED = new KbError(403, "ECOS-KB-062");

    // ── 认知契约 / 假设 ─────────────────────────────────────────
    /** 认知契约写请求被拒（无写代理，REQ-KB-04）。 */
    public static final KbError COGNITION_WRITE_FORBIDDEN = new KbError(405, "ECOS-KB-070");
    /** 尝试修改 APPROVED(ACTIVE) 假设。 */
    public static final KbError APPROVED_ASSUMPTION_MODIFICATION = new KbError(409, "ECOS-KB-071");
    /** 假设已过期不可引用。 */
    public static final KbError ASSUMPTION_EXPIRED = new KbError(410, "ECOS-KB-072");
    /** 认知供给侧（F04-08 只读端点）后端不可用 → fail-loud 拒绝，禁空列表伪装"无数据"。 */
    public static final KbError COGNITION_SUPPLY_UNAVAILABLE = new KbError(503, "ECOS-KB-073");

    // ── 图谱形态 ────────────────────────────────────────────────
    /** 图谱形态不一致（PG↔Neo4j 对账差异，F04-10）。 */
    public static final KbError GRAPH_FORM_MISMATCH = new KbError(500, "ECOS-KB-080");

    /** {@code ECOS-KB-***} 错误码定义（http 状态 + 字符串码）。 */
    public record KbError(int httpStatus, String code) {
        /** 构造带错误码的领域异常（message 附业务上下文）。 */
        public DomainException exception(String message) {
            return new DomainException(httpStatus, this.code, message);
        }
    }

    /** 知识域领域异常：携带字符串错误码 {@code ECOS-KB-***}，供 kb 局部 advice 透出。 */
    public static class DomainException extends DataBridgeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String code;

        public DomainException(int httpStatus, String code, String message) {
            super(httpStatus, httpStatus, message);
            this.code = code;
        }

        public DomainException(int httpStatus, String code, String message, Throwable cause) {
            super(httpStatus, httpStatus, message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
