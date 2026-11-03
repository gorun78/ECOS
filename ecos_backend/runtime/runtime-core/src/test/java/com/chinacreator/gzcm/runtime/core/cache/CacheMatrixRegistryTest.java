package com.chinacreator.gzcm.runtime.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 14 类矩阵逐类定案门（§2.9.8①；#147AL）。
 *
 * <p>四条：① 恰好 14 类且序号 1~14 不重不漏；② 每类都有权威源与依据；
 * ③ WIRED 必须有缓存域与接线位置，且域不重复；④ NOT_CACHED 必须无域且理由以"不缓存"开头
 * ——禁把"没接线"伪装成"不需要缓存"。</p>
 *
 * @author ECOS Runtime #147AL
 */
class CacheMatrixRegistryTest {

    private static final List<CacheMatrixRegistry.Entry> ENTRIES = CacheMatrixRegistry.entries();

    @Test
    @DisplayName("恰好 14 类，序号 1~14 不重不漏")
    void coversAllFourteenClasses() {
        assertEquals(14, ENTRIES.size());
        Set<Integer> indexes = ENTRIES.stream().map(CacheMatrixRegistry.Entry::index)
                .collect(Collectors.toSet());
        assertEquals(IntStream.rangeClosed(1, 14).boxed().collect(Collectors.toSet()), indexes);
    }

    @Test
    @DisplayName("每类都有权威源与依据")
    void everyEntryHasAuthorityAndRationale() {
        for (CacheMatrixRegistry.Entry e : ENTRIES) {
            assertTrue(nonBlank(e.object()), "对象名缺失: " + e.index());
            assertTrue(nonBlank(e.authority()), "权威源缺失: " + e.object());
            assertTrue(nonBlank(e.rationale()), "依据缺失: " + e.object());
        }
    }

    @Test
    @DisplayName("WIRED 必须有域与接线位置（同域多对象由 objectType 区分）")
    void wiredEntriesHaveDomainAndWiring() {
        Set<String> composedKeys = new HashSet<>();
        for (CacheMatrixRegistry.Entry e : ENTRIES) {
            if (e.status() != CacheMatrixRegistry.Status.WIRED) {
                continue;
            }
            assertTrue(nonBlank(e.domain()), "WIRED 缺缓存域: " + e.object());
            assertTrue(nonBlank(e.wiring()), "WIRED 缺接线位置: " + e.object());
            assertTrue(composedKeys.add(e.domain() + "/" + e.object()),
                    "同一 (域, 对象) 重复登记: " + e.domain() + "/" + e.object());
        }
    }

    @Test
    @DisplayName("NOT_CACHED 无域且理由以'不缓存'开头")
    void notCachedEntriesAreExplicit() {
        for (CacheMatrixRegistry.Entry e : ENTRIES) {
            if (e.status() != CacheMatrixRegistry.Status.NOT_CACHED) {
                continue;
            }
            assertNull(e.domain(), "NOT_CACHED 不应有缓存域: " + e.object());
            assertNull(e.wiring(), "NOT_CACHED 不应有接线位置: " + e.object());
            assertTrue(e.rationale().startsWith("不缓存"), "NOT_CACHED 理由须显式: " + e.object());
        }
    }

    @Test
    @DisplayName("结论可枚举：接线与不缓存合计 14 类")
    void statusesPartitionMatrix() {
        long wired = ENTRIES.stream()
                .filter(e -> e.status() == CacheMatrixRegistry.Status.WIRED).count();
        long notCached = ENTRIES.stream()
                .filter(e -> e.status() == CacheMatrixRegistry.Status.NOT_CACHED).count();
        assertEquals(14, wired + notCached);
        assertEquals(10, wired, "WIRED 数变化须同步文档回写");
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
