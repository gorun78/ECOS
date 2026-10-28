package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 世界模型只读契约四段（差距分析附录A 第 17 条；契约行 = 《ECOS详细设计说明书-从零实施版》
 * 第 5415 行 {@code getWorldModelEntities / getWorldModelRelations / getWorldModelTraces /
 * getWorldModelRules}，第 10194 行第 8 篇机读码行 {@code ECOS-COG-503（501 + stub=true）}）。
 *
 * <h3>为什么是枚举单源</h3>
 * 段名同时出现在三处——路径常量（{@link CognitiveApiPaths#WORLD_MODEL_ENTITIES_REL} 等）、
 * 501 响应体 {@code data.segment}、门禁断言面。三处各写一遍字面量必然漂移，故段名与
 * 「缺什么载体」的取证串在本枚举一处定版；段名字面量本身仍以
 * {@link CognitiveApiPaths#WORLD_MODEL_SEGMENT_ENTITIES} 等编译期常量为单源（注解值必须是常量）。
 *
 * <h3>本枚举不声明任何能力</h3>
 * 详细设计-05 <b>DG-9</b>／X-43／W136（M0）定口径：世界模型能力<b>未实现</b> ⇒ 四端点回真实
 * HTTP 501 + {@code ECOS-COG-503} + {@code data.stub=true}，<b>禁</b> 200 + 固定值
 * （原伪实现族以固定 confidence 0.75/0.15/0.3 与硬编码因果边对外声明现状，见 X-43）。
 * 因此每段的 {@link #missingCarrier()} 只登记<b>已核实</b>的缺载体取证（DDL 单源脚本号 +
 * 实库 information_schema 盘点 + 全仓 Java 读手 grep），禁凭表名相似反推可得数据。
 *
 * <p>取证基线（2026-10-28 实测）：dev PG {@code sys_man} 内 wm 族实存
 * {@code ecos_cognitive.ecos_wm_goal}／{@code ecos_wm_causal_link}／{@code ecos_wm_scenario}／
 * {@code ecos_wm_goal_log} 四表（{@code V5}／{@code V17} 裸名建表，{@code V194} 收敛登记），
 * 语义分别为目标／目标级因果边／场景／目标字段变更日志；四段各自都不是本契约段名的载体。</p>
 */
public enum WorldModelSegment {

    /** 实体段：世界模型对象集（契约未定载体；认知属主内不存在实体表）。 */
    ENTITIES(CognitiveApiPaths.WORLD_MODEL_SEGMENT_ENTITIES, CognitiveApiPaths.WORLD_MODEL_ENTITIES_REL,
            "缺实体载体：认知属主内无世界模型实体表（铁律 REQ-COG-05 认知不新增 DB 表）；"
                    + "wm 族实存四表（V5/V17 建、V194 登记）分别为目标/目标级因果边/场景/目标变更日志，均非实体口径"),

    /** 关系段：世界模型关系集（因果口径已裁归 ontology 只读 REST，认知侧不自建读腿）。 */
    RELATIONS(CognitiveApiPaths.WORLD_MODEL_SEGMENT_RELATIONS, CognitiveApiPaths.WORLD_MODEL_RELATIONS_REL,
            "缺关系载体：ecos_wm_causal_link 只连 goal_id↔goal_id（V5 目标级因果边），非契约关系集；"
                    + "V194 已按 E.4 裁「本域外因果口径以 ontology REST 只读为准」，禁由目标级边反推世界模型关系"),

    /** 轨迹段：世界模型推演/运行轨迹（轨迹不落盘，F05-12 守护项 1；变更日志非轨迹）。 */
    TRACES(CognitiveApiPaths.WORLD_MODEL_SEGMENT_TRACES, CognitiveApiPaths.WORLD_MODEL_TRACES_REL,
            "缺轨迹载体：ecos_wm_goal_log（V17）列为 goal_id/change_type/old_value/new_value/changed_by/changed_at，"
                    + "是目标字段变更日志而非推演轨迹，且全仓 Java 零读手；轨迹不落盘（F05-12 守护项 1）"),

    /** 规则段：世界模型规则集（认知属主内无规则表，禁由因果边类型字段反推规则）。 */
    RULES(CognitiveApiPaths.WORLD_MODEL_SEGMENT_RULES, CognitiveApiPaths.WORLD_MODEL_RULES_REL,
            "缺规则载体：认知属主内不存在世界模型规则表（实库 wm 族仅目标/因果边/场景/变更日志四形），"
                    + "契约规则段无可读真值面；禁由 relationship_type 反推规则集（推断式关联）");

    private final String pathSegment;
    private final String relativePath;
    private final String missingCarrier;

    WorldModelSegment(String pathSegment, String relativePath, String missingCarrier) {
        this.pathSegment = pathSegment;
        this.relativePath = relativePath;
        this.missingCarrier = missingCarrier;
    }

    /** URI 段名（{@code entities}／{@code relations}／{@code traces}／{@code rules}）。 */
    public String pathSegment() {
        return pathSegment;
    }

    /** 类级前缀 {@code /api/v1/cognitive} 下的相对段（绝对形态见 {@code CognitiveApiPaths.WORLD_MODEL_*}）。 */
    public String relativePath() {
        return relativePath;
    }

    /** 缺载体取证串，进 501 响应体 {@code data.missing} 供运维定位；禁在此声明任何数据值。 */
    public String missingCarrier() {
        return missingCarrier;
    }
}
