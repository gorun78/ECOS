package com.chinacreator.gzcm.engine.data.pipeline;

import lombok.Data;

import java.util.List;

/**
 * 节点类型目录 VO — GET /api/v1/pipeline/node-types 响应载荷。
 * <p>返回当前执行器支持的全部节点类型清单（强类型，DTO 命名 XxxVO）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class PipelineNodeTypesVO {

    /** 当前 schema 版本（SemVer，与 P2-01 规范联动） */
    private String version;

    /** 节点类型清单（type/code/name 见各字段） */
    private List<EntryVO> items;

    /**
     * 单个节点类型条目。
     */
    @Data
    public static class EntryVO {

        /** P2-01 节点类型枚举值（与后端执行器 switch 同源） */
        private String type;

        /** 英文短名（code），供前端非视觉场景使用 */
        private String code;

        /** 展示名称（英文 fallback，前端按 locale i18n 渲染） */
        private String name;

        /** lucide-react 图标名 */
        private String icon;

        /** 节点类别: SOURCE / TRANSFORM / JOIN / SINK / OUTPUT */
        private String category;

        /** 执行器说明（如 JdbcConnector / UdfSandbox 等） */
        private String executor;

        /** 版本（全版本/预留/仅flagship 等） */
        private String version;

        /** 是否可用（false 表示当前档位禁用，如 SOURCE_CDC 仅 flagship） */
        private Boolean enabled;

        /** 禁用原因 i18n key（enabled=false 时） */
        private String disabledReasonKey;

        /** 属性 schema 与必填字段 */
        private PipelineNodeTypeMetaVO meta;
    }
}
