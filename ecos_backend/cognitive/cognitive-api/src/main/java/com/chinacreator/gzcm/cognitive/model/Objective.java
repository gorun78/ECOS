package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 目标函数定义。
 */
public class Objective implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标名称 */
    private String name;
    /** 优化方向：MIN / MAX */
    private String direction;
    /** 目标描述 */
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
