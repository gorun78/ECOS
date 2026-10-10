package com.chinacreator.gzcm.engine.ai.capability;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 能力 ID 词表单源（《ECOS 认知域与智能域补充详细设计 v1.0》§10.2 能力目录 + §7.2/§7.3 治理候选能力，
 * 共 <b>11 项</b>三处一致纪律：本枚举 → 台账 {@code capability_id} 列（V269，无 DDL CHECK，读侧以本类校验）
 * → 工具契约 {@code capability_id} 列（V270，idx_v270_tc_capability 反查）。
 *
 * <p><b>模型与能力分离（§10.1）</b>：业务任务只请求 capabilityId，Model Router 结合约束选择实现；
 * 调用方 <b>禁</b>硬编码 provider/model（SUP-MODEL-01）。</p>
 *
 * <ul>
 *   <li>§10.2 八项小模型/LLM 能力：LLM_TEXT_REASONING / TEXT_EMBEDDING / DOCUMENT_OCR /
 *       DOCUMENT_LAYOUT_PARSE / SPEECH_TO_TEXT / TEXT_TO_SPEECH / IMAGE_UNDERSTANDING /
 *       ENTITY_EXTRACTION / DOCUMENT_CLASSIFY / RERANK / SEMANTIC_SIMILARITY §10.2 表中
 *       ENTITY_EXTRACTION、DOCUMENT_CLASSIFY 同属"分类/抽取"族但为两个独立能力 ID，
 *       §10.2 计 9 项；</li>
 *   <li>§7.2 数据治理首批只读能力：DATA_PROFILE（剖析，只读，Blueprint 依赖清单示例同源）；</li>
 *   <li>§7.3 本体候选首项：ONTOLOGY_GAP_ANALYZE（缺对象/属性/关系识别，只读建议）。
 *       §7.2/§7.3 其余能力 ID 归数据治理/本体候选批次按同一扩展位补入（只增不改）。</li>
 * </ul>
 *
 * <p>每项固定携带目录三要件（AiCapabilityCatalogTest 冻结）：<b>contractVersion</b>（契约版本，
 * 变更只增版本号）、<b>evidenceRequired</b>（是否要求来源引用引用 §7.5）、
 * <b>automationLevelDefault</b>（默认所需自动化等级档 0~3，写入工具契约时过三重上限 min）。
 * 计量口径 costUnit（§10.6 非 Token 计量）与确定性输出标志一并冻结，禁散落字面量。</p>
 */
public enum AiCapabilityId {

    // ── §10.2 文本/多模态族 ────────────────────────────────
    /** 文本生成/推理（通用 LLM；输出结构化文本/假设候选）。 */
    LLM_TEXT_REASONING("MULTIMODAL&TEXT", "1", false, 1, "TOKEN", true),
    /** 文本嵌入向量化（Embedding；向量为模型自报置信）。 */
    TEXT_EMBEDDING("TEXT", "1", false, 1, "TOKEN", true),
    /** 语音识别 ASR（音频引用 → 转写文本+时间戳，§10.5 可追溯）。 */
    SPEECH_TO_TEXT("AUDIO", "1", true, 1, "INPUT_SCALE", true),
    /** 语音合成 TTS（文本 → 音频引用+格式+时长）。 */
    TEXT_TO_SPEECH("TEXT", "1", false, 1, "CALL", true),
    /** 图像理解 VQA（图片引用+问题 → 结构化描述，供 KB 抽取引用证据）。 */
    IMAGE_UNDERSTANDING("IMAGE", "1", true, 1, "CALL", true),

    // ── §10.2 文档族 ───────────────────────────────────────
    /** 文档 OCR（文件/图片引用 → 文本块+页码+区域+置信标识；权威业务决策须字段验证 §10.6）。 */
    DOCUMENT_OCR("DOCUMENT", "1", true, 1, "CALL", true),
    /** 文档结构解析 Layout（PDF/图片 → 标题/段落/表格/阅读顺序+页码）。 */
    DOCUMENT_LAYOUT_PARSE("DOCUMENT", "1", true, 1, "INPUT_SCALE", true),

    // ── §10.2 分类/抽取/检索族 ──────────────────────────────
    /** 实体抽取（文本/文档 → 实体+来源区间）。 */
    ENTITY_EXTRACTION("TEXT", "1", true, 1, "CALL", true),
    /** 文档分类（文本/文档 → 类别+来源区间）。 */
    DOCUMENT_CLASSIFY("DOCUMENT", "1", true, 1, "CALL", true),
    /** 重排序 Rerank（查询+候选引用 → 排序分数与顺序）。 */
    RERANK("TEXT", "1", false, 1, "TOKEN", true),
    /** 语义相似度（两段文本/案例引用 → 相似度+模型/校准说明）。 */
    SEMANTIC_SIMILARITY("TEXT", "1", false, 1, "TOKEN", true),

    // ── §7.2 / §7.3 数据治理与本体候选（只读建议族）─────────
    /** 数据剖析（§7.2 首行；assetRef+范围+采样预算 → 列类型/空值率/基数/分布，只读）。 */
    DATA_PROFILE("DATA_ASSET", "1", true, 1, "CALL", true),
    /** 本体缺口分析（§7.3 首行；Blueprint+字段语义+现有本体 → 缺少对象/属性/关系，只读建议）。 */
    ONTOLOGY_GAP_ANALYZE("ONTOLOGY", "1", true, 2, "CALL", true);

    /** 能力族标签（输入模态/资产族；路由输入维度 §10.3-2）。 */
    private final String family;
    /** 能力契约版本号（§6.2 capabilities[].contractVersion 同源；只增数字）。 */
    private final String contractVersion;
    /** 是否要求来源引用引用（§7.5 evidenceRequired；候选/OCR 族必须可溯源）。 */
    private final boolean evidenceRequired;
    /** 默认所需自动化等级 0~3（§11.1 automationLevel 初值；生效取三重上限 min）。 */
    private final int automationLevelDefault;
    /** 计量口径（§10.6：TOKEN/CALL/INPUT_SCALE…，非 Token 模型禁伪 Token）。 */
    private final String costUnit;
    /** 输出是否模型自报（质量 kind 默认 MODEL_ESTIMATE，禁冒充统计概率 SUP-MODEL-03）。 */
    private final boolean modelReportedOutput;

    AiCapabilityId(String family, String contractVersion, boolean evidenceRequired,
                   int automationLevelDefault, String costUnit, boolean modelReportedOutput) {
        this.family = family;
        this.contractVersion = contractVersion;
        this.evidenceRequired = evidenceRequired;
        this.automationLevelDefault = automationLevelDefault;
        this.costUnit = costUnit;
        this.modelReportedOutput = modelReportedOutput;
    }

    public String family() {
        return family;
    }

    public String contractVersion() {
        return contractVersion;
    }

    public boolean evidenceRequired() {
        return evidenceRequired;
    }

    public int automationLevelDefault() {
        return automationLevelDefault;
    }

    public String costUnit() {
        return costUnit;
    }

    public boolean modelReportedOutput() {
        return modelReportedOutput;
    }

    /** 全目录（冻结 11 项，AiCapabilityCatalogTest 断言）。 */
    public static Set<AiCapabilityId> all() {
        return Collections.unmodifiableSet(EnumSet.allOf(AiCapabilityId.class));
    }

    /** 目录三要件快照（读端点/校验器消费单源，禁各引擎自抄）。 */
    public static Map<AiCapabilityId, CapabilityContract> catalog() {
        Map<AiCapabilityId, CapabilityContract> m = new EnumMap<>(AiCapabilityId.class);
        for (AiCapabilityId id : values()) {
            m.put(id, new CapabilityContract(id.name(), id.contractVersion, id.evidenceRequired,
                    id.automationLevelDefault, id.costUnit));
        }
        return Collections.unmodifiableMap(m);
    }

    /** 能力契约三要件 + 计量口径（只读值对象；缺失三要件不可构造——缺一构造即抛）。 */
    public record CapabilityContract(String capabilityId, String contractVersion,
                                     boolean evidenceRequired, int automationLevel, String costUnit) {
        public CapabilityContract {
            if (capabilityId == null || capabilityId.isBlank()) {
                throw new IllegalArgumentException("能力目录条目缺 capabilityId（V269 capability_id NOT NULL）");
            }
            if (contractVersion == null || contractVersion.isBlank()) {
                throw new IllegalArgumentException("能力目录条目缺 contractVersion（§10 三要件之一，禁缺省放行）");
            }
            if (costUnit == null || costUnit.isBlank()) {
                throw new IllegalArgumentException("能力目录条目缺 costUnit（§10.6 计量口径，非 Token 模型禁缺）");
            }
        }
    }
}
