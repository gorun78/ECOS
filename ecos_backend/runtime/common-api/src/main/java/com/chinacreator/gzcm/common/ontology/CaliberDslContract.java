package com.chinacreator.gzcm.common.ontology;

import java.util.List;
import java.util.Map;

/**
 * 口径公式 DSL 四关（V1~V4）的闸名与机读码 <b>跨模块契约单源</b>（#146ah，收口 #146af 余项 r5）。
 *
 * <p><b>为什么住 common-api</b>：四关词表同时被 ontology 引擎属主面（写出面
 * {@code engine/ontology-engine/ontology-engine-impl .../service/CaliberDslValidator}，
 * PRD-09 §1.2 命名与 {@code ECOS-ONTO-07x} 号段的原始出处）与 workspace 场景层
 * （{@code workspace/workspace-impl .../caliber/CaliberAddressing} 的属主机读码→闸名解码、
 * {@code CaliberFormulaInvalidException} 与 {@code CaliberReadService} 的契约四关词表）消费——
 * workspace-impl 已依赖 common-api、ontology-engine-impl 经 runtime-core 传递可达 common-api
 * ⇒ <b>零新增 Maven 依赖</b>即可真单源（#146af 原裁决「至多两枚属主」的前提「跨模块 import
 * 须新挂依赖」已不成立，本类即收敛路径）。pom 双向可达性由门禁腿
 * {@code CaliberDslContractCrossModuleOwnerTest} 钉住。</p>
 *
 * <p><b>与 {@link com.chinacreator.gzcm.common.error.OntologyErrorCode} 的分工</b>：
 * 那边只登记《从零实施版》§6.3.3 在册十三枚；本类管的是码位→闸名的<b>结构性映射</b>与闸名
 * 词表本身——其中 {@code ECOS-ONTO-070} 的在册语义（术语悬空，F6-11）被 DSL V1 借用的
 * 冲突属 r2 尾件，留两侧原属主处按枚登记，本类不裁决语义、只承载现行 wire 面值的映射。
 * 码值即 wire 契约：workspace 侧异常信封（{@code rejectedGate}/{@code ownerErrorCode}）
 * 与前端按 {@code gate} 分组的契约测试都按字面量绑定，禁改值。</p>
 *
 * <p>纪律（common-api enforcer 白名单内）：本类只含常量与纯函数查阅，<b>无任何业务 import</b>。</p>
 */
public final class CaliberDslContract {

    private CaliberDslContract() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    // ── 四关闸名（PRD-09 §1.2；顺序即契约顺序：workspace 门 detail 面 gates 列按此序承运）──

    /** V1 — 语法关（受限递归下降文法）。 */
    public static final String GATE_V1_SYNTAX = "DSL_V1_SYNTAX";

    /** V2 — 引用闭合关（IDENT 须解到指标注册表/事实字段/声明符号）。 */
    public static final String GATE_V2_REFERENCE = "DSL_V2_REFERENCE";

    /** V3 — 量纲一致关（单位推导冲突 ⇒ 违例）。 */
    public static final String GATE_V3_DIMENSION = "DSL_V3_DIMENSION";

    /** V4 — 循环依赖关（公式图拓扑排序无环）。 */
    public static final String GATE_V4_CYCLE = "DSL_V4_CYCLE";

    /** 四关闸名有序词表（contract {@code gates} 列表列与前端分组渲染的键面，序即契约序）。 */
    public static final List<String> GATE_NAMES = List.of(
            GATE_V1_SYNTAX, GATE_V2_REFERENCE, GATE_V3_DIMENSION, GATE_V4_CYCLE);

    // ── 机读码（ECOS-ONTO-07x 号段，原始出处＝ontology 域 CaliberDslValidator）──

    /** V1 语法关机读码。⚠ 070 在 §6.3.3 在册语义＝术语悬空，DSL 借用为 r2 尾件（不翻判断）。 */
    public static final String CODE_V1_SYNTAX = "ECOS-ONTO-070";

    /** V2 引用闭合关机读码（§6.3.3 表外码位，r1 尾件：文档侧冻结表欠缺，勿入 OntologyErrorCode）。 */
    public static final String CODE_V2_REFERENCE = "ECOS-ONTO-071";

    /** V3 量纲一致关机读码（§6.3.3 表外码位，r1 尾件）。 */
    public static final String CODE_V3_DIMENSION = "ECOS-ONTO-072";

    /** V4 循环依赖关机读码（§6.3.3 表外码位，r1 尾件）。 */
    public static final String CODE_V4_CYCLE = "ECOS-ONTO-073";

    /** 机读码 → 闸名（正查；写/审批咽喉 400 信封无 data 段时委托侧唯一可复验的结构化映射）。 */
    private static final Map<String, String> CODE_TO_GATE = Map.of(
            CODE_V1_SYNTAX, GATE_V1_SYNTAX,
            CODE_V2_REFERENCE, GATE_V2_REFERENCE,
            CODE_V3_DIMENSION, GATE_V3_DIMENSION,
            CODE_V4_CYCLE, GATE_V4_CYCLE);

    /** 闸名 → 机读码（反查；写出面按闸名出码，与 {@code CODE_TO_GATE} 恒为互逆）。 */
    private static final Map<String, String> GATE_TO_CODE = Map.of(
            GATE_V1_SYNTAX, CODE_V1_SYNTAX,
            GATE_V2_REFERENCE, CODE_V2_REFERENCE,
            GATE_V3_DIMENSION, CODE_V3_DIMENSION,
            GATE_V4_CYCLE, CODE_V4_CYCLE);

    /** 机读码 → 闸名；映射不上返回 {@code null}（<b>不猜一关顶替</b>，由调用方按缺位承接）。 */
    public static String gateNameByCode(String machineCode) {
        return machineCode == null ? null : CODE_TO_GATE.get(machineCode);
    }

    /** 闸名 → 机读码；映射不上返回 {@code null}。 */
    public static String codeByGate(String gateName) {
        return gateName == null ? null : GATE_TO_CODE.get(gateName);
    }
}
