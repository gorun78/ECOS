package com.chinacreator.gzcm.workspace.scenario;

import java.util.Map;

/**
 * 可用资源选项 VO — 跨服务联查返回项（PMO-60 v2.0 P1）。
 * <p>六类真 ID 选项池统一返回结构。</p>
 */
public class AvailableItemVO {

    /** 真实资源 ID（PG 主键） */
    private String id;
    /** 资源名称/显示名 */
    private String name;
    /** 附加属性（任意 Map，如 domain、status 等） */
    private Map<String, Object> extra;

    public AvailableItemVO() {}

    public AvailableItemVO(String id, String name, Map<String, Object> extra) {
        this.id = id;
        this.name = name;
        this.extra = extra;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
