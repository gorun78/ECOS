package com.chinacreator.gzcm.engine.kb.cognition;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-08 场景认知契约守护 — 验收测试（详细设计-04 二·F04-08，A 章验收标识）。
 * <p>纯单测（无 Spring 上下文、无 DB）：契约名映射、枚举冻结、写代理陷阱。
 */
class CognitionContractGuardTest {

    private final CognitionContractGuard guard = new CognitionContractGuard();

    /** 契约名（文档/前端别名 + 库内名）→ 库内真身表名，未知名 → null。 */
    @Test
    void guardTablesResolveToRealRelation() {
        assertEquals("public.ecos_cognitive_hypothesis", guard.resolveReal("kb_cognitive_hypothesis"));
        assertEquals("public.ecos_cognitive_hypothesis", guard.resolveReal("ecos_cognitive_hypothesis"));
        assertEquals("public.ecos_cognitive_belief", guard.resolveReal("kb_cognitive_belief"));
        assertEquals("public.ecos_cognitive_mind", guard.resolveReal("kb_cognitive_mind"));

        assertTrue(guard.isGuardedContract("kb_cognitive_hypothesis"));
        assertFalse(guard.isGuardedContract("kb_cognitive_something_else"));
        assertNull(guard.resolveReal("nonsense"));
        assertNull(guard.resolveReal(null));
        assertNull(guard.resolveReal(""));
    }

    /** status 枚举恰为冻结 4 值 + 类型 final + 白名单解析唯一入口。 */
    @Test
    void hypothesisStatusEnumIsFrozen() {
        assertEquals(4, HypothesisStatus.values().length);
        assertArrayEquals(new String[] {"PROPOSED", "EVIDENCED", "BELIEVED", "REFUTED"},
                new String[] {
                        HypothesisStatus.values()[0].name(),
                        HypothesisStatus.values()[1].name(),
                        HypothesisStatus.values()[2].name(),
                        HypothesisStatus.values()[3].name()});

        // 类型冻结：非 final 就能被扩值/篡改
        assertTrue(Modifier.isFinal(HypothesisStatus.class.getModifiers()),
                "HypothesisStatus 必须 final 以冻结枚举形态");

        assertEquals(HypothesisStatus.PROPOSED, HypothesisStatus.fromValue("PROPOSED"));
        assertEquals(HypothesisStatus.EVIDENCED, HypothesisStatus.fromValue("evidenced"));
        assertEquals(HypothesisStatus.BELIEVED, HypothesisStatus.fromValue("BELIEVED"));
        assertEquals(HypothesisStatus.REFUTED, HypothesisStatus.fromValue("REFUTED"));

        assertTrue(HypothesisStatus.isValidStatus("PROPOSED"));
        assertFalse(HypothesisStatus.isValidStatus("JUST_A_STRING"));
        assertFalse(HypothesisStatus.isValidStatus(null));
        assertFalse(HypothesisStatus.isValidStatus(""));
        assertNull(HypothesisStatus.fromValue("  "));
    }

    /** 写代理陷阱：任何写尝试必被拒（405 ECOS-KB-070），fail-loud 不吞。 */
    @Test
    void writeProxyIsForbidden() {
        KbErrorCodes.DomainException ex =
                assertThrows(KbErrorCodes.DomainException.class, () -> guard.assertReadOnly());
        assertEquals("ECOS-KB-070", ex.getCode());
        assertEquals(405, ex.getHttpStatus());
        assertNotNull(ex.getMessage());
    }
}
