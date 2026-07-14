package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 决策变量定义。
 */
public class Variable implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变量名 */
    private String name;
    /** 类型：INTEGER / REAL / DISCRETE */
    private String type;
    /** 最小值 */
    private Object min;
    /** 最大值 */
    private Object max;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getMin() { return min; }
    public void setMin(Object min) { this.min = min; }
    public Object getMax() { return max; }
    public void setMax(Object max) { this.max = max; }
}
