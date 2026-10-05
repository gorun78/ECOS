package com.chinacreator.gzcm.engine.cognitive2.fc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §九 · DSL 四关静态校验验收（F09-02 / W216 / C198）。
 *
 * <p>验收标识：{@code mvn -Dtest=CaliberDslValidationTest#rejectsUnknownSymbolAndDimensionMismatch}。</p>
 *
 * <p>白名单：SUM/ROUND/MIN/MAX/IF + 变量引用；禁脚本引擎（铁律 :14）。四关：
 * V1 语法 / V2 引用闭合 / V3 量纲 / V4 无环（SELF() 占位符约定）。</p>
 */
class CaliberDslValidationTest {

    @Test
    @DisplayName("§九 · 未知符号 AND 量纲失配 都应被拦")
    void rejectsUnknownSymbolAndDimensionMismatch() {
        // V2: 未登记符号
        CaliberDsl.ValidationResult v2 = CaliberDsl.validate(
                "SUM(base_amt)", Set.of("other"), Map.of("base_amt", "YUAN"), "YUAN");
        assertFalse(v2.passed(), "V2 应拦未知符号: " + v2.violations());
        assertTrue(v2.violations().stream().anyMatch(v -> v.startsWith("V2")), "V2 前缀缺失: " + v2.violations());

        // V3: 量纲失配
        CaliberDsl.ValidationResult v3 = CaliberDsl.validate(
                "SUM(base_amt)", Set.of("base_amt"), Map.of("base_amt", "USDT"), "YUAN");
        assertFalse(v3.passed(), "V3 应拦量纲失配: " + v3.violations());
        assertTrue(v3.violations().stream().anyMatch(v -> v.startsWith("V3")), "V3 前缀缺失: " + v3.violations());
    }

    @Nested
    class V1_SyntaxGate {
        @Test
        void whitelistFunctionsPass() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("SUM(x, y)", Set.of("x", "y"), null, null);
            assertTrue(r.passed(), "合法表达式被误拦: " + r.violations());
        }

        @Test
        void unknownFunctionRejected() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("SCRIPT(x)", Set.of("x"), null, null);
            assertFalse(r.passed());
            assertTrue(r.violations().stream().anyMatch(v -> v.startsWith("V1")), "V1 前缀缺失: " + r.violations());
            assertTrue(r.violations().stream().anyMatch(v -> v.contains("whitelist")), "应含白名单提示: " + r.violations());
        }

        @Test
        void unbalancedParenRejected() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("SUM(x", Set.of("x"), null, null);
            assertFalse(r.passed());
            assertTrue(r.violations().stream().anyMatch(v -> v.startsWith("V1")));
        }
    }

    @Nested
    class V2_ReferenceClosure {
        @Test
        void knownSymbolPasses() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("x + y", Set.of("x", "y"), null, null);
            // 顶层加减乘除不进入 DSL 白名单（+ 会被视为非法 token）— 走单变量形态
            assertTrue(r.passed() || r.violations().isEmpty(), "变量引用不应自动拦: " + r.violations());
        }

        @Test
        void emptyExpressionRejected() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("  ", null, null, null);
            assertFalse(r.passed(), "空串应拦");
            assertTrue(r.violations().stream().anyMatch(v -> v.startsWith("V1")));
        }
    }

    @Nested
    class V3_DimensionGate {
        @Test
        void mixedDimensionRejected() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate(
                    "SUM(revenue, cost_amt)",
                    Set.of("revenue", "cost_amt"),
                    Map.of("revenue", "YUAN", "cost_amt", "USD"),
                    "YUAN");
            assertFalse(r.passed());
            assertTrue(r.violations().stream().anyMatch(v -> v.contains("cost_amt")),
                    "应指出失配符号: " + r.violations());
        }
    }

    @Nested
    class V4_NoSelfReference {
        @Test
        void selfMarkerRejected() {
            CaliberDsl.ValidationResult r = CaliberDsl.validate("SELF()+1", null, null, null);
            // 独立 V2/V3 关闭时，V1 语法先过（SELF 视为变量），随后 V4 命中
            assertFalse(r.passed(), "V4 应拦 SELF 占位符: " + r.violations());
            assertTrue(r.violations().stream().anyMatch(v -> v.startsWith("V4")),
                    "V4 前缀缺失: " + r.violations());
        }
    }

    @Nested
    class Evaluation {
        @Test
        void sumEvaluatesHalfUp2() {
            BigDecimal r = CaliberDsl.evaluate("SUM(a, b)",
                    Map.of("a", new BigDecimal("1.005"), "b", new BigDecimal("2")));
            assertEquals(0, r.compareTo(new BigDecimal("3.01")),
                    "HALF_UP 2 位: 1.005+2=3.005→3.01, 实际 " + r);
        }

        @Test
        void roundHonorScale() {
            BigDecimal r = CaliberDsl.evaluate("ROUND(x, 1)",
                    Map.of("x", new BigDecimal("2.567")));
            assertEquals(0, r.compareTo(new BigDecimal("2.6")), "实际 " + r);
        }

        @Test
        void ifSelectsBranch() {
            BigDecimal r1 = CaliberDsl.evaluate("IF(flag, a, b)",
                    Map.of("flag", new BigDecimal("1"), "a", new BigDecimal("10"), "b", new BigDecimal("20")));
            assertEquals(0, r1.compareTo(new BigDecimal("10")));
            BigDecimal r2 = CaliberDsl.evaluate("IF(flag, a, b)",
                    Map.of("flag", new BigDecimal("0"), "a", new BigDecimal("10"), "b", new BigDecimal("20")));
            assertEquals(0, r2.compareTo(new BigDecimal("20")));
        }

        @Test
        void unknownFunctionThrows() {
            // 求值阶段再兜底：不接受白名单外函数
            assertNotNull(assertThrows(IllegalArgumentException.class,
                    () -> CaliberDsl.evaluate("SCRIPT(a)", Map.of("a", new BigDecimal("1")))));
        }

        @Test
        void unresolvedSymbolThrows() {
            assertNotNull(assertThrows(IllegalArgumentException.class,
                    () -> CaliberDsl.evaluate("a", Map.of())));
        }
    }
}
