package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

/**
 * 节点类型元数据 — 描述单个节点类型的图标、类别、执行器说明等。
 * <p>供前端节点面板/属性面板渲染与元数据展示使用。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineNodeTypeMetaVO {

    /** lucide-react 图标名（前端按名映射组件） */
    private String icon;

    /** 节点类别: SOURCE / TRANSFORM / JOIN / SINK / OUTPUT */
    private String category;

    /**
     * 属性 schema 定义（字段 → 类型）。
     * <p>类型属于配置语义（string/number/boolean/array/object/enum），不属于节点配置数据，
     * 故用 Map 承载（Controller 出参本身为强类型 VO）。
     */
    private java.util.Map<String, String> properties;

    /** 必填字段 key 列表 */
    private java.util.List<String> requiredFields;
}
