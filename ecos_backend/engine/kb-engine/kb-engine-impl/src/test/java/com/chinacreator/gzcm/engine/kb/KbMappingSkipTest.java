package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.support.MaterializedSemantics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F03-05 rule 2 — {@code materialized} 裁度语义固化（Q2_SKIP 口径，表驱动锁定）。
 *
 * <p>{@code null} / {@code ""} / {@code "true"} / {@code "1"} → <b>true</b>（加载）；
 * 任意其它字符串 → <b>false</b>（skip）。语义由
 * {@link MaterializedSemantics#shouldMaterialize(String)} 纯函数实现，与 PRD-03 §3.2-2 与
 * 冻结契约 {@code docs/30-设计/contracts/ontology-entity-mappings.schema.json} 的
 * {@code materialized} 字段一致。</p>
 */
class KbMappingSkipTest {

    @ParameterizedTest(name = "shouldMaterialize({0}) == true")
    @NullAndEmptySource
    @CsvSource({
            "true",
            "1",
            "TRUE",      // 大小写不敏感（宽松解析，与设计 base 语义一致）
    })
    @DisplayName("null / \"\" / \"true\" / \"1\" → 加载（true）")
    void loadCases(String value) {
        assertTrue(MaterializedSemantics.shouldMaterialize(value),
                "期望加载（true），输入：" + value);
    }

    @ParameterizedTest(name = "shouldMaterialize({0}) == false")
    @CsvSource({
            "false",
            "0",
            "skip",
            "FALSE",
            "no",
            " materialized ",   // 非 true/1 的其它词 → skip
            "yes",
    })
    @DisplayName("其它取值（false/0/skip/FALSE/no/…）→ skip（false）")
    void skipCases(String value) {
        assertFalse(MaterializedSemantics.shouldMaterialize(value),
                "期望 skip（false），输入：" + value);
    }

    @Test
    @DisplayName("null 与空白/下界语义稳定，不抛异常")
    void stableBoundary() {
        assertTrue(MaterializedSemantics.shouldMaterialize(null));
        assertTrue(MaterializedSemantics.shouldMaterialize(""));
        assertTrue(MaterializedSemantics.shouldMaterialize("  "));
        assertFalse(MaterializedSemantics.shouldMaterialize("NaN"));
    }
}
