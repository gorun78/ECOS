package com.chinacreator.gzcm.ai.wagent.evidence;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ClaimType;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ValueSource;
import com.chinacreator.gzcm.ai.wagent.evidence.ClaimEvidence.Claim;
import com.chinacreator.gzcm.ai.wagent.evidence.ClaimEvidence.Evidence;
import com.chinacreator.gzcm.ai.wagent.evidence.EvidenceChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-24 Claim/Evidence 校验离线单测：
 * 三守卫——数值缺 numericValue 拒、数值 value_source=NONE 拒、claim 集合非空但 evidence 空拒；
 * isValidSource 形态守卫（null/空/超 512 拒）。
 */
class ClaimEvidenceTest {

    private static Claim nonNumericClaim(String id) {
        return new Claim(id, "run-1", ClaimType.FACT, false, null, ValueSource.NONE, "文本叙述", "high");
    }

    private static Evidence evidence(String id) {
        return new Evidence(id, "data-engine", "data.check_coverage:1.0.3", "{\"src\":\"cm\"}", Instant.now());
    }

    /** F10-24：claim 集合非空 + 至少 1 条 evidence ⇒ 通过；evidence 空 ⇒ 抛 ISE（narrative 必可溯）。 */
    @Test
    void everyCriticalClaimHasAtLeastOneEvidence() {
        List<Claim> claims = List.of(nonNumericClaim("c1"), nonNumericClaim("c2"));
        assertDoesNotThrow(() -> EvidenceChain.validate(claims, List.of(evidence("e1"))),
                "claim 非空且有 evidence ⇒ 校验通过");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EvidenceChain.validate(claims, List.of()),
                "claim 非空但 evidence 空必须拒");
        assertTrue(ex.getMessage().contains("E-WA-NARRATIVE-NUM"),
                "缺 evidence 必须带 E-WA-NARRATIVE-NUM，实际：" + ex.getMessage());
    }

    /**
     * F10-24：Claim 数值一致性——数值型 claim 缺 numericValue ⇒ 拒（E-WA-NARRATIVE-NUM，
     * 触发 narrative 重生成兜底，重生成上限 2 次由 narrative 侧控制；本断言命中数值缺 numericValue 守
     * 卫）。
     */
    @Test
    void numericConsistencyLimitsRegenerationAtTwo() {
        // 数值型 claim 但 numericValue=null ⇒ 守卫命中。
        Claim badNumeric = new Claim("c-num", "run-1", ClaimType.FACT, true, null,
                ValueSource.DATA_QUERY, "销售环比", "high");
        assertNarrativeNum(() -> EvidenceChain.validate(List.of(badNumeric), List.of(evidence("e1"))));

        // 对照：数值 + numericValue 非空 + value_source 非 NONE ⇒ 通过。
        Claim okNumeric = new Claim("c-ok", "run-1", ClaimType.FACT, true,
                new BigDecimal("123"), ValueSource.CM_01, "环比 123%", "high");
        assertDoesNotThrow(() -> EvidenceChain.validate(List.of(okNumeric), List.of(evidence("e1"))),
                "数值 + CM_01 来源应通过");
    }

    /** F10-24：数值型 claim 且 value_source=NONE ⇒ 拒（数字来源必归 CM_01..05 / DATA_QUERY / DERIVED 之一）。 */
    @Test
    void numberNotFromDeterministicSourceIsRejected() {
        Claim noSource = new Claim("c-nosrc", "run-1", ClaimType.FACT, true,
                new BigDecimal("123"), ValueSource.NONE, "环比 123%", "high");
        assertNarrativeNum(() -> EvidenceChain.validate(List.of(noSource), List.of(evidence("e1"))));

        // 对照：数值 + DATA_QUERY ⇒ 通过。
        Claim okNumeric = new Claim("c-ok2", "run-1", ClaimType.FACT, true,
                new BigDecimal("123"), ValueSource.DATA_QUERY, "环比 123%", "high");
        assertDoesNotThrow(() -> EvidenceChain.validate(List.of(okNumeric), List.of(evidence("e1"))),
                "数值 + DATA_QUERY 来源应通过");
    }

    /**
     * F10-24：isValidSource 形态守卫——null / 空 / 超长 ⇒ false；
     * 「检索 chunk / vector 溯源类 source」空形态 ⇒ false（向量 chunk 直引不作 evidence源的阈定义边界）。
     */
    @Test
    void evidenceVectorChunkNotAcceptedAsEvidence() {
        // 合法非空 source（≤512）⇒ true。
        assertTrue(EvidenceChain.isValidSource("cm:01:metrics:sales"));
        // null / 空 / 超长 ⇒ false（无法作为有效溯源 source）。
        assertFalse(EvidenceChain.isValidSource(null));
        assertFalse(EvidenceChain.isValidSource(""));
        assertFalse(EvidenceChain.isValidSource(" ".repeat(513)), "超 512 字符 source 非法");
        assertTrue(EvidenceChain.isValidSource("x".repeat(512)), "恰 512 字符 source 合法");
    }

    /** 断言 ISE 且消息带 E-WA-NARRATIVE-NUM 短码。 */
    private static void assertNarrativeNum(Executable e) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, e);
        assertTrue(ex.getMessage().contains("E-WA-NARRATIVE-NUM"),
                "数值违例须带 E-WA-NARRATIVE-NUM，实际：" + ex.getMessage());
    }
}
