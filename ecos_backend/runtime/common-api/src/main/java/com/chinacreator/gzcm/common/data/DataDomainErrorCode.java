package com.chinacreator.gzcm.common.data;

/**
 * 数据域（data-engine / datanet）统一错误码常量 — 详细设计-02 §D.1 错误码表单源。
 *
 * <p>设计约束（ECOS 详细设计-02 数据域 §D.1，2026-09-28）：</p>
 * <ul>
 *   <li>所有数据域后端响应的 {@code errorCode} 必须取自本常量，禁止散落字符串字面量；</li>
 *   <li>{@code ECOS_DATA_031}：数据层 SQL/映射异常（500，附 traceId）—— 明令<b>不得再掩蔽为 404</b>；</li>
 *   <li>与 {@code ECOS_DQ_111}（真实 404，治理记录不存在）严格区分；</li>
 *   <li>本类只含常量，无任何业务 import（common-api enforcer 白名单内）。</li>
 * </ul>
 */
public final class DataDomainErrorCode {

    private DataDomainErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    // ── 管道 / YAML task 只读（单模型收敛，F02-03/F02-04）──────────────
    /** 409 — YAML task 模型只读 / legacy 写端点已停用，请改用 pipeline definitions 或 /api/v1/dq/** 治理端点 */
    public static final String ECOS_DATA_011 = "ECOS-DATA-011";
    /** 400 — 节点类型不可用（未实现）/ 必填配置缺失 */
    public static final String ECOS_DATA_012 = "ECOS-DATA-012";
    /** 400 — DAG 非法（环 / 孤立节点 / 必填配置缺失） */
    public static final String ECOS_DATA_013 = "ECOS-DATA-013";

    // ── 业务事实模型（F02-06）─────────────────────────────────────────
    /** 409 — PUBLISHED 后业务字段 UPDATE 禁止（活跃唯一性红线） */
    public static final String ECOS_DATA_021 = "ECOS-DATA-021";
    /** 409 — 关账期写入拒绝（联动 09 册 FC-05 period lock） */
    public static final String ECOS_DATA_022 = "ECOS-DATA-022";
    /** 422 — 导入批次部分行被拒（rowNo/field/ruleId 明细随 data 返回） */
    public static final String ECOS_DATA_023 = "ECOS-DATA-023";

    // ── 数据层异常（F02-08）───────────────────────────────────────────
    /** 500 — 数据层 SQL/映射异常（不再掩蔽为 404），响应体附 traceId */
    public static final String ECOS_DATA_031 = "ECOS-DATA-031";
    /**
     * 404 — 数据源／元数据记录不存在（#142/B4-3e-44 新占位，详设-02 §D.1 同批补行）。
     *
     * <p>登记理由：本域此前<b>没有一枚 404 码</b>，于是 {@code DataSourceServiceImpl#previewSchema}
     * 的「数据源不存在」腿只能抛无码 {@code RuntimeException}，被端点折成
     * 「HTTP 200 ＋ 信封 code 404」（缺陷 <b>D-B4-74／D-B4-75</b>）。与
     * {@code ECOS_DQ_111}（治理记录不存在）严格区分：本码指<b>连接元数据主记录</b>。</p>
     */
    public static final String ECOS_DATA_032 = "ECOS-DATA-032";

    // ── 跨引擎依赖（C.7）──────────────────────────────────────────────
    /** 503 — 跨引擎取数依赖不可用（fail-loud，禁"返回空列表"降级） */
    public static final String ECOS_DATA_041 = "ECOS-DATA-041";

    // ── 业务域唯一写通道守卫段（#142/B4-3e-69，详设-02 §D.1 同批补行）─────────
    /**
     * 400 — 业务域唯一写通道入参非法（缺 layer/carrierRef/traceId/idempotencyKey、
     * 非法 layer、carrierRef 限定名残缺）。
     *
     * <p><b>#142/B4-3e-69（收口 D-B4-73）新登</b>：收口前这枚腿在
     * {@code DwWriteChannelService#parseLayer} 借用 {@code ECOS-DATA-053}
     * （表义＝OLAP 降级）、在 {@code DwSchemaQualifier#resolve} 借用
     * {@code ECOS-DATA-051}（表义＝JDBC 管道失败）——503 段降级码被 400 入参腿
     * 占用，客户端拿到「入参非法」的 message 却看到「基础设施降级」的机读码。</p>
     */
    public static final String ECOS_DATA_061 = "ECOS-DATA-061";
    /**
     * 403 — MC04 OLAP 双例外守卫违规（控制域写 OLAP／层不合法／双 OLAP 并存不一致）。
     *
     * <p><b>#142/B4-3e-69（收口 D-B4-73）新登</b>：收口前 {@code OlapEngineResolver.guard()}
     * 三枚违规腿借用 {@code ECOS-DATA-054}（表义＝Kafka 事件/审计降级）。MC04 违规是
     * <b>不变式违反＝策略拒绝</b>（属主端点折 403），不是基础设施降级——机读码号义
     * 与本段 400/403 语义/状态行三态对齐后，与「OLAP 未启用」腿的 503＋
     * {@code ECOS_DATA_053}（真降级，§4.7.2「不可用 ≠ 无权限」）在状态行与机读码
     * 上可互分派。</p>
     */
    public static final String ECOS_DATA_062 = "ECOS-DATA-062";
    /**
     * 403 — MC02 列类型白名单守卫违规（裸 JSON/JSONB 列或 {@code *_json} 语义列参与 WHERE）。
     *
     * <p><b>#142/B4-3e-69（收口 D-B4-73）新登</b>：收口前 {@code ColumnTypeWhitelistGuard}
     * 两枚违规腿借用 {@code ECOS-DATA-052}（表义＝MinIO RAW 层采集失败）——同 062，
     * 503 段降级码被 403 策略拒绝腿占用。与 062 同段分码：MC02/MC04 是两个独立守卫，
     * 客户端按码分派补救动作时应能区分「改列投影」与「改载体/层/引擎配置」两类违规。</p>
     */
    public static final String ECOS_DATA_063 = "ECOS-DATA-063";

    // ── 基础设施降级矩阵（C.8，503 段）────────────────────────────────
    /** 503 — runtime-access JDBC 管道/SQL 控制台执行失败 */
    public static final String ECOS_DATA_051 = "ECOS-DATA-051";
    /** 503 — MinIO RAW 层采集/SINK 失败（管道整体 FAILED，不留半对象） */
    public static final String ECOS_DATA_052 = "ECOS-DATA-052";
    /** 503 — Doris/ClickHouse OLAP 降级（写禁、读可降级并标 degraded=true） */
    public static final String ECOS_DATA_053 = "ECOS-DATA-053";
    /** 503 — Kafka 事件/审计降级（落库 + 重试，SLA ≤15min） */
    public static final String ECOS_DATA_054 = "ECOS-DATA-054";
    /** 503 — runtime-monitor 降级（DQ 告警仅落库 + 补投） */
    public static final String ECOS_DATA_055 = "ECOS-DATA-055";
    /** 503 — Git 仓库根不可用（归档失败，运行不阻断，明确提示） */
    public static final String ECOS_DATA_056 = "ECOS-DATA-056";
    /** 503 — DuckDB parquet 拒绝（维持 csv） */
    public static final String ECOS_DATA_057 = "ECOS-DATA-057";

    // ── DQ 治理状态机（F02-07）────────────────────────────────────────
    /** 409 — 规则状态机非法迁移（DRAFT→APPROVED 需签核人） */
    public static final String ECOS_DQ_101 = "ECOS-DQ-101";
    /** 400 — 规则表达式含禁用构造（自由 SQL / 裸表名） */
    public static final String ECOS_DQ_102 = "ECOS-DQ-102";
    /** 404 — 治理记录不存在（真实 404，与 ECOS-DATA-031 严格区分） */
    public static final String ECOS_DQ_111 = "ECOS-DQ-111";
}
