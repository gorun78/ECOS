package com.chinacreator.gzcm.engine.ai.wagent.validate;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateValidationSegment;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateValidationStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateType;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选五段校验执行器（D-WC-1：补齐 {@code generated → validating → validated} 断档）。
 *
 * <p><b>为什么是确定性自检而不是 LLM 判定</b>：五段校验的产出要驱动状态机与发布闸门，
 * 结论必须可复现、可审计、可回放；引入模型判定会让同一候选两次校验给出不同结论。
 * 需要语义判断的口径（如"结论数值与依据是否一致"）属 F10-24 {@code ClaimVerifier}，
 * 在发布链路另判，不在本执行器内。</p>
 *
 * <p><b>五段与判定单源</b>（词表 = {@link CandidateValidationSegment} /
 * {@link CandidateValidationStatus}）：</p>
 * <ol>
 *   <li>{@code structure}：{@code payload_json} 必须是合法 JSON 对象，且候选类型必填
 *       （类型越界在建候选时已拦，此处复核存量行）；</li>
 *   <li>{@code reference}：{@code basis_json} 非空且为合法 JSON ⇒ 依据可追溯，
 *       空/不可解析即 fail（"无依据的候选"不得进入评审）；</li>
 *   <li>{@code conflict}：同租户下同 {@code candidate_type + target_object_ref_json} 不得
 *       并存另一条未终结（generated/validating/validated/review_pending/approved）候选——
 *       有并存即 fail（防同一目标被两份候选分别发布）；</li>
 *   <li>{@code impact}：{@code target_engine} 必须是
 *       {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog} 六代号之一
 *       （发布时按类型映射归档仓；属主不可解析即 fail）；</li>
 *   <li>{@code regression}：无回归基线 ⇒ {@code skipped}（第五道未启用，不阻断）。</li>
 * </ol>
 *
 * <p><b>推进口径</b>：任何一段 {@code fail} ⇒ 停在 {@code validating}（可修复后重跑）；
 * 全非 fail ⇒ {@code validated}。判定只在 {@link #blocksAdvance(Map)} 实现一次。</p>
 */
@Component
public class WAgentCandidateValidationService {

    /** conflict 段判定为"未终结"的状态集（与 CandidateStatus 正向链前段对齐）。 */
    private static final java.util.Set<String> OPEN_STATUSES = java.util.Set.of(
            "generated", "validating", "validated", "review_pending", "approved");

    /** 目标引擎代号集（发布归档仓按类型映射，属主须可解析）。 */
    private static final java.util.Set<String> KNOWN_ENGINES =
            com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog
                    .contractEngineCodesForDdlCheck();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 执行五段校验。
     *
     * @param candidate    待校验候选（已过租户闸）
     * @param conflictPeers 同租户下与 target_object_ref_json 相同的其他候选（由调用方经 DAO 取；
     *                      避免本类直连 DAO 造成校验器与存储耦合）
     * @return 段名 → 结果（段序固定 structure→reference→conflict→impact→regression）
     */
    public Map<CandidateValidationSegment, CandidateValidationStatus> validate(
            WAgentCandidateDO candidate, java.util.List<WAgentCandidateDO> conflictPeers) {
        Map<CandidateValidationSegment, CandidateValidationStatus> out = new LinkedHashMap<>();
        out.put(CandidateValidationSegment.STRUCTURE, checkStructure(candidate));
        out.put(CandidateValidationSegment.REFERENCE, checkReference(candidate));
        out.put(CandidateValidationSegment.CONFLICT, checkConflict(candidate, conflictPeers));
        out.put(CandidateValidationSegment.IMPACT, checkImpact(candidate));
        out.put(CandidateValidationSegment.REGRESSION, CandidateValidationStatus.SKIPPED);
        return out;
    }

    /** 是否有任一段阻断推进（fail）。 */
    public boolean blocksAdvance(Map<CandidateValidationSegment, CandidateValidationStatus> results) {
        return results.values().stream().anyMatch(CandidateValidationStatus::blocksAdvance);
    }

    /**
     * 目标对象引用的**规范形态**（创建链路与冲突比较共用的单源）。
     *
     * <p>为什么必须规范化：conflict 段按 {@code candidate_type + target_object_ref_json} 逐字比对，
     * 若落库保留调用方原始键序，语义相同的两条候选（`{"a":1,"b":2}` 与 `{"b":2,"a":1}`）
     * 会因序列化差异漏判冲突。此处递归按 key 字典序重排后序列化，保证同内容必同串。</p>
     *
     * @param raw 调用方原文（可空）
     * @return 规范形态；空白输入返回 {@code null}（该列可空，conflict 段据此走 skipped）
     * @throws IllegalArgumentException 原文不是合法 JSON（调用方须转 400，禁写入不可解析的引用）
     */
    public static String canonicalTargetRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node == null || node.isNull()) {
                return null;
            }
            return MAPPER.writeValueAsString(sortKeys(node));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "target_object_ref_json 必须是合法 JSON（冲突判定依赖其逐字比对）", e);
        }
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode out = MAPPER.createObjectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            for (String name : names) {
                out.set(name, sortKeys(node.get(name)));
            }
            return out;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                out.add(sortKeys(child));
            }
            return out;
        }
        return node;
    }

    // ── 五段判定 ──────────────────────────────────────────────────

    private CandidateValidationStatus checkStructure(WAgentCandidateDO c) {
        if (c.getCandidateType() == null || !CandidateType.ALL.contains(c.getCandidateType())) {
            return CandidateValidationStatus.FAIL;
        }
        String payload = c.getPayloadJson();
        if (payload == null || payload.isBlank()) {
            return CandidateValidationStatus.FAIL;
        }
        try {
            JsonNode node = MAPPER.readTree(payload);
            return node != null && node.isObject()
                    ? CandidateValidationStatus.PASS : CandidateValidationStatus.FAIL;
        } catch (Exception e) {
            return CandidateValidationStatus.FAIL;
        }
    }

    private CandidateValidationStatus checkReference(WAgentCandidateDO c) {
        String basis = c.getBasisJson();
        if (basis == null || basis.isBlank()) {
            return CandidateValidationStatus.FAIL;
        }
        try {
            MAPPER.readTree(basis);
            return CandidateValidationStatus.PASS;
        } catch (Exception e) {
            return CandidateValidationStatus.FAIL;
        }
    }

    /**
     * 冲突段：同租户、同候选类型、同目标对象引用下是否并存其他未终结候选。
     *
     * <p>无目标对象引用（空）时该段不适用 ⇒ {@code skipped}（如纯知识项候选不挂具体对象）。
     * 比对两侧都过 {@link #canonicalTargetRef}：历史行可能存着调用方原始键序，
     * 只比原文会把同语义引用判成不同目标。</p>
     */
    private CandidateValidationStatus checkConflict(WAgentCandidateDO c,
                                                    java.util.List<WAgentCandidateDO> peers) {
        String target = canonicalOrNull(c.getTargetObjectRefJson());
        if (target == null) {
            return CandidateValidationStatus.SKIPPED;
        }
        if (peers == null || c.getCandidateType() == null) {
            return CandidateValidationStatus.PASS;
        }
        boolean duplicated = peers.stream()
                .filter(java.util.Objects::nonNull)
                .filter(p -> !c.getId().equals(p.getId()))
                .filter(p -> c.getCandidateType().equals(p.getCandidateType()))
                .filter(p -> target.equals(canonicalOrNull(p.getTargetObjectRefJson())))
                .anyMatch(p -> p.getStatus() != null && OPEN_STATUSES.contains(p.getStatus()));
        return duplicated ? CandidateValidationStatus.FAIL : CandidateValidationStatus.PASS;
    }

    /** 规范形态；不可解析的历史脏值按"不参与比对"处理（不因一行脏数据把校验器打成 500）。 */
    private static String canonicalOrNull(String raw) {
        try {
            return canonicalTargetRef(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CandidateValidationStatus checkImpact(WAgentCandidateDO c) {
        String engine = c.getTargetEngine();
        if (engine == null || engine.isBlank()) {
            // 未指定属主：发布时按类型映射归档仓仍可确定（repositoryIdFor 只吃类型）⇒ warn 不阻断
            return CandidateValidationStatus.WARN;
        }
        return KNOWN_ENGINES.contains(engine.trim().toLowerCase(java.util.Locale.ROOT))
                ? CandidateValidationStatus.PASS : CandidateValidationStatus.FAIL;
    }
}
