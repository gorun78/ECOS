package com.chinacreator.gzcm.engine.data.pipeline;

import java.util.Set;

/**
 * PipelineNodeTypesCatalog — 节点类型目录的唯一权威来源（Wave 4 T1）。
 *
 * <p>与前端 {@code REQUIRED_FIELDS_BY_TYPE}（pipelineValidation.ts）同源，
 * 架构铁律 §4.8.2「枚举边界一致」：
 * <ul>
 *   <li>后端保存校验使用（{@link PipelineServiceImpl#VALID_NODE_TYPES} 复用此目录）</li>
 *   <li>前端 NodePalette 节点面板枚举同源（前端常量 P2-01 5 + PMO-36 4 = 9 类）</li>
 *   <li>（可选）后续 Controller 追加 {@code GET /api/v1/pipeline/node-types} 端点时直接复用</li>
 * </ul>
 */
public final class PipelineNodeTypesCatalog {

    /**
     * 节点类型全集 = 前端 5（SOURCE_JDBC/SOURCE_CSV/SOURCE_REST/TRANSFORM_SQL/OUTPUT_OBJECT）
     * + PMO-36 新增 4（SOURCE_CDC/TRANSFORM_UDF/JOIN/SINK，Wave 2 交付）
     * + 数据采集 1（SINK_MINIO，数据湖近源库写入，Wave 新增）。
     */
    public static final Set<String> SUPPORTED = Set.of(
            "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_CDC",
            "TRANSFORM_SQL", "OUTPUT_OBJECT", "TRANSFORM_UDF", "JOIN", "SINK",
            "SINK_MINIO"
    );

    private PipelineNodeTypesCatalog() {
    }

    /** 兼容旧调用点：直接 offer 一个静态实例。 */
    public boolean supports(String type) {
        return type != null && SUPPORTED.contains(type);
    }
}
