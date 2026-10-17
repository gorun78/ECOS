package com.chinacreator.gzcm.common.security.decision;

import java.util.List;

/**
 * 三通道裁决入口（{@code POST /api/v1/security/decision}）<b>通道词表单源</b>
 * （#142/B4-3d-2，差距 D-B4-27）。
 *
 * <p><b>为什么必须有这个类（缺陷本质）</b>：{@code purpose} 是裁决的<b>第一入参</b> ——
 * 它决定 OPA 走哪条角色×通道矩阵（§4.7.2）、导出水印义务是否适用、AI 通道的
 * {@code resource.dataScope} 是否生效。而现网的这套词是分散内联的：</p>
 * <ul>
 *   <li>生产侧 {@code DecisionBundle.Purpose} 枚举名（大写）＋
 *       {@code SecurityDecisionService#safeOpa} 里 {@code purpose.name().toLowerCase()}；</li>
 *   <li>消费侧 workspace {@code ScenarioReadGuard.PURPOSE_EXPORT = "export"} 字面量；</li>
 *   <li>入口侧 {@code DecisionController#parsePurpose} 的
 *       {@code valueOf(s.trim().toUpperCase())}。</li>
 * </ul>
 *
 * <p>比"字面量重复"更严重的是 {@code parsePurpose} 的<b>静默降级</b>：任何它认不出的字符串
 * （拼错的 {@code "exprt"}、被改名的 {@code "download"}、大小写外的空格串）一律返回
 * {@code PAGE}。于是"导出通道"的调用一旦词面失配，就会<b>以页面通道的语义被裁决并正常返回
 * 200</b> —— 没有错误、没有日志、消费方无从察觉。护栏矩阵按页面档评估、水印义务不适用，
 * 而调用方以为自己走的是导出。这是 fail-open，且是伪装成成功的那一类
 * （与 D-B4-14「OPA 从未被咨询」同一形态族）。</p>
 *
 * <p>本类把词表收敛到 runtime 底座（与
 * {@code com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract}、
 * {@code com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract} 同一分层口径），
 * 并把"认不出"变成显式结果：{@link #normalize(String)} 对未知名返回 {@code null}，
 * 由入口转 400（{@link #REASON_UNRECOGNIZED}）；<b>缺失/空白</b>仍按既约定默认
 * {@code PAGE}（API 只增不改：现有不传 purpose 的调用方行为不变）。</p>
 *
 * <p>本类不含判定逻辑，只定义"通道有哪三个、规范写法是什么、认不出怎么报错"。</p>
 */
public final class DecisionChannelContract {

    /** 页面通道 —— 查询结果展示（详设-01 C.1 Purpose.PAGE）。 */
    public static final String PURPOSE_PAGE = "page";

    /** 导出通道 —— 文件/报表产出（详设-01 C.1 Purpose.EXPORT）。 */
    public static final String PURPOSE_EXPORT = "export";

    /** AI 通道 —— LLM 上下文供给（详设-01 C.1 Purpose.AI）。 */
    public static final String PURPOSE_AI = "ai";

    /**
     * 未识别的 {@code purpose} 入参 ⇒ 400 的机读理由前缀。
     *
     * <p>写进 message 是为了让消费方日志能区分"我传错词"与"security 内部失败（503）"；
     * 禁消费方把这个 400 当成"默认页面通道"重试 —— 那等于恢复缺陷。</p>
     */
    public static final String REASON_UNRECOGNIZED = "DECISION_PURPOSE_UNRECOGNIZED";

    // ── #142/B4-3d-6 只增：裁决请求面 + 应答段键的单源（差距 D-B4-28 / D-B4-30） ──
    //
    // 动机（与 D-B4-25 同一病灶，只是换了一张面孔）：{@code /decision} 的请求体
    // {@code assetRef.{table,columns}} / {@code purpose} / {@code subject.subjectId}
    // 与应答体 {@code data.columns} 此前各有两份手抄字面量（{@code DecisionController}
    // 读侧 + workspace 客户端写侧），第三份写侧（AI 列面腿）一接线就是四套真值。
    // 键名漂移在跨 Maven 模块、编译期零耦合的两侧不会红 ——
    // 本批把两侧都改成引用这里的常量，并由
    // {@code ClsColumnSurfaceContractGateTest} 的跨模块清单钉住"不得再有裸字面量"。

    /** 请求体：受保护对象段。 */
    public static final String KEY_ASSET_REF = "assetRef";

    /** 请求体：{@code assetRef} 内的表定位符（与 {@code AssetRef.table} 同义）。 */
    public static final String KEY_ASSET_TABLE = "table";

    /** 请求体：{@code assetRef} 内的列全集（空/缺失＝未给宇宙，列面按未判定处置）。 */
    public static final String KEY_ASSET_COLUMNS = "columns";

    /** 请求体：通道词。 */
    public static final String KEY_PURPOSE = "purpose";

    /** 请求体：主体段（只用于跨主体演练；生产以信任链为准）。 */
    public static final String KEY_SUBJECT = "subject";

    /** 请求体：{@code subject} 内的主体标识。 */
    public static final String KEY_SUBJECT_ID = "subjectId";

    /** 应答体 {@code data} 里的列面段（其内部键面见 {@code ColumnSurfaceContract}）。 */
    public static final String KEY_BUNDLE_COLUMNS = "columns";

    private DecisionChannelContract() {
    }

    /** 已知通道词表（顺序即 {@code DecisionBundle.Purpose} 声明顺序，用于错误 message 与门禁）。 */
    public static List<String> known() {
        return List.of(PURPOSE_PAGE, PURPOSE_EXPORT, PURPOSE_AI);
    }

    /**
     * 归一化通道词：{@code trim} + 小写后必须命中 {@link #known()}。
     *
     * @return 规范小写词；{@code raw} 为 {@code null}/空白 ⇒ {@code null}（调用方按默认通道处理）；
     *         非空白但未知名 ⇒ {@code null}（调用方必须拒绝，<b>禁</b>回落页面通道）
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String candidate = raw.trim().toLowerCase();
        if (candidate.isEmpty()) return null;
        return known().contains(candidate) ? candidate : null;
    }

    /** 是否为已知的规范词（{@link #normalize(String)} 的幂等判据，供门禁与日志使用）。 */
    public static boolean isKnown(String candidate) {
        return candidate != null && known().contains(candidate);
    }

    /**
     * 未识别理由串（400 message 用）：带出实际收到的词与合法词表。
     *
     * <p>回显入参是有意为之 —— 静默拒绝会让调用方永远猜不到是拼写问题；这里只回显
     * 去空白后的原值，不含任何敏感上下文。</p>
     */
    public static String unrecognizedReason(String raw) {
        return REASON_UNRECOGNIZED + " purpose=" + (raw == null ? "" : raw.trim())
                + "，合法取值=" + known() + "（未识别时拒绝裁决，禁静默降级为 " + PURPOSE_PAGE + "）";
    }
}
