package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * 预测点 — 单个未来时点的预测值 + 置信区间（PMO-51 T1）。
 */
public class ForecastPoint {

    /** 步序号（1-based，相对最后一帧历史） */
    private int step;
    /** 时间标识（由历史末点 t 按步长外推得出） */
    private long t;
    /** 预测值 */
    private double value;
    /** 置信下界 */
    private double lowerBound;
    /** 置信上界 */
    private double upperBound;

    public ForecastPoint() {
    }

    public ForecastPoint(int step, long t, double value, double lowerBound, double upperBound) {
        this.step = step;
        this.t = t;
        this.value = value;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public long getT() { return t; }
    public void setT(long t) { this.t = t; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public double getLowerBound() { return lowerBound; }
    public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
    public double getUpperBound() { return upperBound; }
    public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
}
