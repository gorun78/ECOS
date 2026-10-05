package com.chinacreator.gzcm.ai.wagent.evidence;

import com.chinacreator.gzcm.ai.wagent.evidence.ClaimEvidence.Claim;
import com.chinacreator.gzcm.ai.wagent.evidence.ClaimEvidence.Evidence;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册10 F10-24 · ClaimVerifier + EvidenceChain：LLM 叙事数字落 body 前的必校守卫。
 *
 * <p>guard 断言仅 3 类（避免过度拦截）：</p>
 * <ol>
 *   <li>Claim.isNumeric 且 numericValue == null → 拒（E-WA-NARRATIVE-NUM）；</li>
 *   <li>Claim.isNumeric 且 valueSource == NONE → 拒（数值来源必在其中之一）；</li>
 *   <li>Claim 集合非空但 evidence 空 → 拒（narrative 必可溯）。</li>
 * </ol>
 */
public final class EvidenceChain {

    private EvidenceChain() {}

    /** 校验 claim + evidence 集合 → 违规则抛 IllegalStateException。 */
    public static void validate(List<Claim> claims, List<Evidence> evidences) {
        if (claims == null) throw new NullPointerException("claims null");
        List<String> evIds = (evidences == null) ? List.of()
                : evidences.stream().map(Evidence::evidenceId).toList();
        for (Claim c : claims) {
            if (c.isNumeric() && c.numericValue() == null) {
                throw new IllegalStateException("E-WA-NARRATIVE-NUM: numeric claim 缺 numericValue " + c.claimId());
            }
            if (c.isNumeric() && c.valueSource() == ValueSource.NONE) {
                throw new IllegalStateException("E-WA-NARRATIVE-NUM: numeric claim value_source=NONE " + c.claimId());
            }
        }
        if (!claims.isEmpty() && evIds.isEmpty()) {
            throw new IllegalStateException("E-WA-NARRATIVE-NUM: claim 无对应 evidence（narrative 必可溯）");
        }
    }

    /** 端点 22 输出：evidence → 引擎端追溯指向（engine→path 映射，String JSON 语义）。 */
    public static Map<String, String> endpointMap(List<Evidence> evidences) {
        Map<String, String> out = new LinkedHashMap<>();
        if (evidences == null) return out;
        for (Evidence e : evidences) {
            out.put(e.engineRefPath(), e.engine());
        }
        return Map.copyOf(out);
    }

    /** 校验 agent 输入的 source 合法性（对应 ClaimValidation 消费方）。 */
    public static boolean isValidSource(String source) {
        return source != null && !source.isBlank() && source.length() <= 512;
    }
}
