package com.chinacreator.gzcm.engine.ai.wagent.context;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AssumptionSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Situation 情境对象（详细设计-10 §9.2，附件册二 §5.2 的 11 项字段契约，本册采纳为 C 输入唯一形态）。
 *
 * <p>生命周期：运行态对象（依 Run）；正式认知对象只经 cognitive-engine api 门面，
 * <b>不得</b>在 {@code ecos_cognitive_*} 建表；控制面引用落
 * {@code ecos_ai.ecos_wagent_run.situation_snapshot_json TEXT}（V233【2026-09-30 G2 补列】；
 * 受控 JSON = {@code *_json}+TEXT，DR04/MC02，禁 JSONB）。Input Snapshot 三规则（§9.2）：
 * 快照由 data-engine 唯一写/冻结通道产生、不可变、同 Situation+同快照重复运行输出必须相同。</p>
 *
 * <p>类型四值枚举冻结（禁扩展为自由串）；{@code metric.ontology_version} 是口径变更链
 * （F10-07）的锚点；{@code assumptions[].source} 是 CM-07 红线落点——
 * source=inferred 的假设不得直接进 {@code belief.probability}（卷 05 §8.1 同口径）。</p>
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与本包 {@code com.chinacreator.gzcm.engine.ai.wagent} 属同义（F10-01）。</p>
 *
 * @param situationId   ①情境 ID
 * @param questionId    ②业务问题引用（V229 逻辑引用）
 * @param runId         ③运行引用（V233 逻辑引用）
 * @param type          ④类型（四值枚举，禁自由串）
 * @param scope         ⑤范围 {org, objects[]}
 * @param time          ⑥时间 {horizon, as_of}
 * @param metric        ⑦指标 {name, ontologyVersion}（口径链锚点）
 * @param constraints   ⑧约束 {currency, unit, …}
 * @param snapshotRef   ⑨快照引用 {id, hash}（data-engine 唯一冻结通道产物，只引用不复制正文）
 * @param knowledgeRefs ⑩知识画像引用集 [{profileId, version, applicability}]
 * @param assumptions   ⑪假设集 [{id, statement, source∈{user,default,inferred}}]
 */
public record Situation(
        String situationId,
        String questionId,
        String runId,
        SituationType type,
        Scope scope,
        Time time,
        Metric metric,
        Constraints constraints,
        SnapshotRef snapshotRef,
        List<KnowledgeRef> knowledgeRefs,
        List<Assumption> assumptions) {

    public Situation {
        if (situationId == null || situationId.isBlank()) {
            throw new IllegalArgumentException("situation_id 不得为空（§9.2 契约①）");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 必须落四值枚举，禁自由串（§9.2 契约④）");
        }
        knowledgeRefs = knowledgeRefs == null
                ? List.of() : Collections.unmodifiableList(knowledgeRefs);
        assumptions = assumptions == null
                ? List.of() : Collections.unmodifiableList(assumptions);
        for (Assumption a : assumptions) {
            if (a.source() == null) {
                throw new IllegalArgumentException("assumption.source 必填且限 {user,default,inferred}（CM-07 落点）");
            }
        }
    }

    /** 契约④类型四值（小写为 §9.2 冻结字面量，与 cognitive 门面入参同形）。 */
    public enum SituationType {
        FORECAST("forecast"),
        DIAGNOSE("diagnose"),
        SCENARIO("scenario"),
        RANK("rank");

        private final String value;

        SituationType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Set<String> ALL_VALUES = java.util.Arrays.stream(values())
                .map(SituationType::value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        public static boolean isValid(String raw) {
            return raw != null && ALL_VALUES.contains(raw.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** 契约⑤ scope{org, objects[]}。 */
    public record Scope(String org, List<String> objects) {
        public Scope {
            objects = objects == null ? List.of() : Collections.unmodifiableList(objects);
        }
    }

    /** 契约⑥ time{horizon, asOf}（as_of = 数据截止口径，与 Goal.as_of_date 链一致）。 */
    public record Time(String horizon, String asOf) {
    }

    /** 契约⑦ metric{name, ontologyVersion}——口径变更链（F10-07）锚点。 */
    public record Metric(String name, String ontologyVersion) {
    }

    /** 契约⑧ constraints{currency, unit, …}（开放键以受控 JSON 序列化，字段位仅承载常用两项）。 */
    public record Constraints(String currency, String unit) {
    }

    /** 契约⑨ snapshotRef{id, hash}——正文不入库，完整性以哈希锚定（§6.3 引用原则）。 */
    public record SnapshotRef(String id, String hash) {
    }

    /** 契约⑩ knowledgeRefs[]{profileId, version, applicability}。 */
    public record KnowledgeRef(String profileId, String version, String applicability) {
    }

    /** 契约⑪ assumptions[]{id, statement, source}；source 单源 = {@link AssumptionSource}。 */
    public record Assumption(String id, String statement, AssumptionSource source) {
        public Assumption {
            if (source == null) {
                throw new IllegalArgumentException("assumption.source 不得为空（CM-07：inferred 禁入 belief.probability）");
            }
        }

        /** LLM 推断来源判定（CM-07 红线消费点）。 */
        public boolean inferredByAgent() {
            return source == AssumptionSource.INFERRED;
        }
    }
}
