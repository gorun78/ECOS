package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.BacktestReport;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.ReviewAdvice;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.SampleRow;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.Thresholds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W226/C208 · 阈值驱动复审任务路由（R-66① 已批准：阈值经 sysman 配置门面，角色经 OPA）。
 *
 * <p>验收标识：{@code mvn -Dtest=BacktestReviewTest#mapeAboveThresholdCreatesReviewTaskWithCorrectRole}。</p>
 *
 * <p>§3.4 · 复审角色路由：画像偏差 → 知识管理员；假设偏差 → 财务+经营负责人（原审批人回避）。
 * 本函数在 cognitive 侧只输出 <b>role 通用标记 PENDING_ROUTING</b>（因子来源属 caller 侧信息），
 * 生产侧接线由 ITaskManagementService 承接。</p>
 */
class BacktestReviewTest {

    @Test
    @DisplayName("§七 W226/C208 · MAPE 超阈值 ⇒ 复审任务 required，role 路由非 NONE")
    void mapeAboveThresholdCreatesReviewTaskWithCorrectRole() {
        // 阈值 MAPE>15%；构造 MAPE=25% 的样本
        List<SampleRow> samples = List.of(
                new SampleRow(new BigDecimal("125"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("125"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("125"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("125"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("125"), new BigDecimal("100")));
        // minSample=5 避免 LOW_SAMPLE
        Thresholds th = new Thresholds(new BigDecimal("15"), new BigDecimal("80"), 5);
        BacktestReport r = FcBacktestCalc.compute("p", "g", null, null, null, samples, th);
        // MAPE = (25+25+25+25+25)/5 /100*100 = 25%（p10/p90 null 时区间记失败样本，coverage=0）
        assertTrue(r.mape().compareTo(new BigDecimal("15")) > 0, "MAPE 应>15%, 实际 " + r.mape());
        ReviewAdvice adv = r.review();
        assertTrue(adv.required(), "复审任务必 required=true");
        assertEquals("PENDING_ROUTING", adv.role(),
                "角色通用标记 PENDING_ROUTING（caller 侧按画像/假设分化）, 实际 " + adv.role());
        assertTrue(adv.reason().contains("mape"), "reason 应含 mape 定位");
    }

    @Test
    @DisplayName("区间覆盖率 < 阈值 ⇒ 复审 required（覆盖率口径敏感）")
    void coverageBelowThresholdCreatesReviewTask() {
        // n=10，intervalBad=9（90% 外）⇒ coverage=0.1 < 0.8
        List<SampleRow> rows = List.of();
        java.util.ArrayList<SampleRow> al = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            al.add(new SampleRow(new BigDecimal("100"), new BigDecimal("150"),
                    new BigDecimal("95"), new BigDecimal("105")));
        }
        Thresholds th = new Thresholds(new BigDecimal("999"), new BigDecimal("80"), 3); // mape 阈值抬高，只测 coverage
        BacktestReport r = FcBacktestCalc.compute("p", "g", null, null, null, al, th);
        assertTrue(r.intervalCoverage().compareTo(new BigDecimal("0.8")) < 0,
                "coverage 应<0.8, 实际 " + r.intervalCoverage());
        assertTrue(r.review().required(), "coverage 低于阈值应触发复审");
        assertEquals("PENDING_ROUTING", r.review().role());
    }

    @Test
    @DisplayName("LOW_SAMPLE 不告警（§3.4：n<minSample ⇒ 无复审任务，仅标记）")
    void lowSampleSuppressesReview() {
        // n=3 < minSample=5 ⇒ LOW_SAMPLE，即便 MAPE 极高也不该告警
        List<SampleRow> samples = List.of(
                new SampleRow(new BigDecimal("500"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("500"), new BigDecimal("100")),
                new SampleRow(new BigDecimal("500"), new BigDecimal("100")));
        Thresholds th = new Thresholds(new BigDecimal("15"), new BigDecimal("80"), 5);
        BacktestReport r = FcBacktestCalc.compute("p", "g", null, null, null, samples, th);
        assertEquals("LOW_SAMPLE", r.sampleFlag());
        assertFalse(r.review().required(), "LOW_SAMPLE 不告警（§3.4）");
        assertEquals("NONE", r.review().role());
    }

    @Test
    @DisplayName("在阈值内 ⇒ 不复审（回归护栏，避免过度告警）")
    void withinThresholdNoReview() {
        // 每样本 p10/p90 涵盖 actual，使 intervalCoverage=1.0；MAPE=0.8% 远低 15% 阈值
        List<SampleRow> samples = List.of(
                new SampleRow(new BigDecimal("101"), new BigDecimal("100"),
                        new BigDecimal("95"), new BigDecimal("105")),
                new SampleRow(new BigDecimal("99"), new BigDecimal("100"),
                        new BigDecimal("95"), new BigDecimal("105")),
                new SampleRow(new BigDecimal("102"), new BigDecimal("100"),
                        new BigDecimal("95"), new BigDecimal("105")),
                new SampleRow(new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("95"), new BigDecimal("105")),
                new SampleRow(new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("95"), new BigDecimal("105")));
        Thresholds th = new Thresholds(new BigDecimal("15"), new BigDecimal("10"), 5);
        BacktestReport r = FcBacktestCalc.compute("p", "g", null, null, null, samples, th);
        assertTrue(r.intervalCoverage().compareTo(new BigDecimal("1.0")) == 0,
                "应全在区间内 coverage=1.0, 实际 " + r.intervalCoverage());
        assertTrue(r.mape().compareTo(new BigDecimal("15")) < 0, "MAPE 应<15, 实际 " + r.mape());
        assertFalse(r.review().required());
        assertEquals("NONE", r.review().role());
    }
}
