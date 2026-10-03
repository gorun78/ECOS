package com.chinacreator.gzcm.engine.kb.profile;

import java.math.BigDecimal;

/**
 * 画像数值统计 VO（resolve 返回体 stats + D.3 示例字段）。
 *
 * @author ECOS KB Team
 */
public class ProfileStatsVO {

    private Long sampleCount;
    private BigDecimal missingRate;
    private BigDecimal p10;
    private BigDecimal p50;
    private BigDecimal p90;
    private BigDecimal mean;
    private BigDecimal ciLow;
    private BigDecimal ciHigh;
    /** 统计方法：T_APPROX / BOOTSTRAP。 */
    private String statsMethod;

    public ProfileStatsVO() {
    }

    public ProfileStatsVO(Long sampleCount, BigDecimal missingRate, BigDecimal p10, BigDecimal p50,
                          BigDecimal p90, BigDecimal mean, BigDecimal ciLow, BigDecimal ciHigh,
                          String statsMethod) {
        this.sampleCount = sampleCount;
        this.missingRate = missingRate;
        this.p10 = p10;
        this.p50 = p50;
        this.p90 = p90;
        this.mean = mean;
        this.ciLow = ciLow;
        this.ciHigh = ciHigh;
        this.statsMethod = statsMethod;
    }

    public Long getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Long sampleCount) {
        this.sampleCount = sampleCount;
    }

    public BigDecimal getMissingRate() {
        return missingRate;
    }

    public void setMissingRate(BigDecimal missingRate) {
        this.missingRate = missingRate;
    }

    public BigDecimal getP10() {
        return p10;
    }

    public void setP10(BigDecimal p10) {
        this.p10 = p10;
    }

    public BigDecimal getP50() {
        return p50;
    }

    public void setP50(BigDecimal p50) {
        this.p50 = p50;
    }

    public BigDecimal getP90() {
        return p90;
    }

    public void setP90(BigDecimal p90) {
        this.p90 = p90;
    }

    public BigDecimal getMean() {
        return mean;
    }

    public void setMean(BigDecimal mean) {
        this.mean = mean;
    }

    public BigDecimal getCiLow() {
        return ciLow;
    }

    public void setCiLow(BigDecimal ciLow) {
        this.ciLow = ciLow;
    }

    public BigDecimal getCiHigh() {
        return ciHigh;
    }

    public void setCiHigh(BigDecimal ciHigh) {
        this.ciHigh = ciHigh;
    }

    public String getStatsMethod() {
        return statsMethod;
    }

    public void setStatsMethod(String statsMethod) {
        this.statsMethod = statsMethod;
    }
}
