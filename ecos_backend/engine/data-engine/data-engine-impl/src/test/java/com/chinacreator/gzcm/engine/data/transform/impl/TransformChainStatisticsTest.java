package com.chinacreator.gzcm.engine.data.transform.impl;

import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TransformChainStatisticsTest — Wave-4.2 P0-2 集成回归。
 *
 * <p>验证 TransformController L71-169 所用到的 4 个 statistics 变量
 * (inputCount/outputCount/filteredCount/errorCount) 在执行后均被真实赋值。
 * TransformChainImpl.execute 已经填充前三项，errorCount 在
 * 链成功时保持默认 0。
 */
class TransformChainStatisticsTest {

    private TransformChainImpl chain;

    @BeforeEach
    void setUp() {
        this.chain = new TransformChainImpl();
    }

    /**
     * 直通 step：不改动数据。
     */
    private static final class PassthroughStep implements TransformStep {
        @Override public String getName() { return "Passthrough"; }
        @Override public String getType() { return "passthrough"; }
        @Override public DataFrame transform(DataFrame input, Map<String, Object> params) {
            return input;
        }
    }

    @Test
    @DisplayName("直通链 — 3 行输入，4 个统计字段可读且值正确")
    void statisticsPopulatedAfterExecute() throws Exception {
        DataFrame in = new DataFrame(new java.util.ArrayList<>(List.of(
                Map.of("a", 1), Map.of("a", 2), Map.of("a", 3))));
        chain.addStep(new PassthroughStep());

        TransformResult result = chain.execute(in);
        assertNotNull(result.getStatistics());

        assertEquals(3L, result.getStatistics().getInputCount());
        assertEquals(3L, result.getStatistics().getOutputCount());
        assertEquals(0L, result.getStatistics().getFilteredCount());
        assertEquals(0L, result.getStatistics().getErrorCount());
    }

    @Test
    @DisplayName("过滤链 — inputCount - outputCount = filteredCount")
    void filteredCountComputedAfterExecute() throws Exception {
        DataFrame in = new DataFrame(new java.util.ArrayList<>(List.of(
                Map.of("age", 30), Map.of("age", 20), Map.of("age", 15))));
        // 过滤 age >= 18
        chain.addStep(new TransformStep() {
            @Override public String getName() { return "FilterAge"; }
            @Override public String getType() { return "filter"; }
            @Override public DataFrame transform(DataFrame input, Map<String, Object> params) {
                DataFrame out = new DataFrame(new java.util.ArrayList<>());
                out.setColumns(input.getColumns());
                for (Map<String, Object> row : input.getRows()) {
                    Object age = row.get("age");
                    if (age instanceof Number n && n.intValue() >= 18) {
                        out.addRow(row);
                    }
                }
                return out;
            }
        });

        TransformResult result = chain.execute(in);
        assertEquals(3L, result.getStatistics().getInputCount());
        assertEquals(2L, result.getStatistics().getOutputCount());
        assertEquals(1L, result.getStatistics().getFilteredCount());
        assertEquals(0L, result.getStatistics().getErrorCount());
    }
}
