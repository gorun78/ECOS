package com.chinacreator.gzcm.engine.data.transform.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TransformStatisticsTest — Wave-4.2 P0-2 前端 statistics 4 字段可赋值。
 *
 * <p>Wave-5.1 T-06：inputCount/outputCount/filteredCount/errorCount
 * 必须能被 setter 赋值且 getter 读到。
 */
class TransformStatisticsTest {

    @Test
    @DisplayName("4 字段 setter/getter 全链路可赋值")
    void allFourFieldsSettableAndGettable() {
        TransformResult result = new TransformResult();
        TransformResult.TransformStatistics stats = result.getStatistics();
        assertNotNull(stats);

        stats.setInputCount(100L);
        stats.setOutputCount(85L);
        stats.setFilteredCount(12L);
        stats.setErrorCount(3L);

        assertEquals(100L, stats.getInputCount());
        assertEquals(85L, stats.getOutputCount());
        assertEquals(12L, stats.getFilteredCount());
        assertEquals(3L, stats.getErrorCount());
    }

    @Test
    @DisplayName("TransformResult 默认 stats 非 null")
    void defaultStatisticsNotNA() {
        TransformResult result = new TransformResult();
        assertNotNull(result.getStatistics());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("替换 stats 实例后 getStatistics 返回新实例")
    void replaceStatisticsInstance() {
        TransformResult result = new TransformResult();
        TransformResult.TransformStatistics custom = new TransformResult.TransformStatistics();
        custom.setInputCount(1L);
        result.setStatistics(custom);
        assertSame(custom, result.getStatistics());
        assertEquals(1L, result.getStatistics().getInputCount());
    }
}
