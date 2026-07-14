package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 约束定义。
 */
public class Constraint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 约束表达式 */
    private String expression;
    /** 约束说明 */
    private String description;

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
