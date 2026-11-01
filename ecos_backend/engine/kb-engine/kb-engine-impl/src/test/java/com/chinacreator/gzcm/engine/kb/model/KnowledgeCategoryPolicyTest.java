package com.chinacreator.gzcm.engine.kb.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** F-KB-18 七分类封闭枚举判据（#147H）。 */
class KnowledgeCategoryPolicyTest {

    @Test
    void sevenCategoriesAreClosedAndCaseInsensitive() {
        assertEquals(7, KnowledgeCategory.values().length);
        assertEquals(KnowledgeCategory.RULE, KnowledgeCategory.fromCode("rule"));
        assertEquals(KnowledgeCategory.FACT, KnowledgeCategory.fromCode("Fact"));
        assertNull(KnowledgeCategory.fromCode("Opinion"), "未知分类必须拒绝，禁自由文本");
        assertNull(KnowledgeCategory.fromCode(" "));
    }

    @Test
    void ruleCategoryRequiresSemanticRootAndOthersForbidIt() {
        assertEquals("Rule", KnowledgeCategoryPolicy.validate("Rule", "rule-42"));
        IllegalArgumentException missingRef = assertThrows(IllegalArgumentException.class,
                () -> KnowledgeCategoryPolicy.validate("Rule", null));
        assertTrue(missingRef.getMessage().contains(KnowledgeCategoryPolicy.CODE_INVALID_CATEGORY));

        IllegalArgumentException shadow = assertThrows(IllegalArgumentException.class,
                () -> KnowledgeCategoryPolicy.validate("Fact", "rule-42"));
        assertTrue(shadow.getMessage().contains("影子规则"));

        IllegalArgumentException orphanRef = assertThrows(IllegalArgumentException.class,
                () -> KnowledgeCategoryPolicy.validate(null, "rule-42"));
        assertTrue(orphanRef.getMessage().contains("rule_ref"));
    }

    @Test
    void nonRuleCategoriesAndEmptyCategoryAreAllowed() {
        assertEquals("Procedure", KnowledgeCategoryPolicy.validate("PROCEDURE", null));
        assertEquals("Evidence", KnowledgeCategoryPolicy.validate("evidence", null));
        assertNull(KnowledgeCategoryPolicy.validate(null, null), "存量无分类数据放行");
    }
}
