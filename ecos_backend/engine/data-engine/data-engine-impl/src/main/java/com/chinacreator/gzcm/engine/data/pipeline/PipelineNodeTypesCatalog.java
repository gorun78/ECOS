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
 *
 * <p><b>F02-04 能力一致性（W57）</b>：{@link #IMPLEMENTED} 登记「执行器
 * {@code PipelineExecutionService.executeNode} switch 中有实现分支」的类型子集，
 * 与 {@link #SUPPORTED} 的差集 = 仅登记尚未实现的类型（当前 {SOURCE_CDC}）。
 * {@code NodeCatalogParityTest} 源码扫描执行器 switch 分支 label 与本集合做等值断言，
 * 漂移即 FAIL — 目录是能力声明，执行器是事实，二者必须同源。
 */
public final class PipelineNodeTypesCatalog {

    /**
     * 节点类型全集 = 前端 5（SOURCE_JDBC/SOURCE_CSV/SOURCE_REST/TRANSFORM_SQL/OUTPUT_OBJECT）
     * + PMO-36 新增 4（SOURCE_CDC/TRANSFORM_UDF/JOIN/SINK，Wave 2 交付）
     * + 数据采集 1（SINK_MINIO，数据湖近源库写入，Wave 新增）
     * + 近源层读取 1（SOURCE_MINIO，B6-1 新增，让「管道基于近源层处理」成立）
     * + 文档解析 1（TRANSFORM_DOC_PARSE，B6-2 新增，A1 目标态「非结构化 → DW 表」）。
     */
    public static final Set<String> SUPPORTED = Set.of(
            "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_CDC", "SOURCE_MINIO",
            "TRANSFORM_SQL", "TRANSFORM_UDF", "TRANSFORM_DOC_PARSE", "JOIN", "SINK",
            "SINK_MINIO", "OUTPUT_OBJECT"
    );

    /**
     * 执行器 switch 有实现分支的类型（F02-04，唯一承流模型）。
     * <p>维护纪律：{@code PipelineExecutionService.executeNode} 新增/删除 case 分支时
     * 必须同步本集合，否则 {@code NodeCatalogParityTest} 源扫描对账 FAIL。
     * <p>注意：existence of a case branch ≠ 能力就绪 —— SOURCE_CDC 也有 case 分支（显式
     * 抛异常拒绝），故「有实现」=「分支内有真实执行逻辑」，以 {@link #availableReasonOf}
     * 的登记为准（available=false 的类型不在本集合内）。
     */
    public static final Set<String> IMPLEMENTED = Set.of(
            "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_MINIO",
            "TRANSFORM_SQL", "TRANSFORM_UDF", "TRANSFORM_DOC_PARSE", "JOIN", "SINK",
            "SINK_MINIO", "OUTPUT_OBJECT"
    );

    /**
     * 类型不可用的对外说明（available=false 时透出，可直接展示）。
     *
     * @param type 节点类型
     * @return 原因文本；可用类型返回 null
     */
    public static String unavailableReasonOf(String type) {
        if (type == null || isAvailable(type)) {
            return null;
        }
        if ("SOURCE_CDC".equals(type)) {
            return "尚未实现，请使用 SOURCE_JDBC/SOURCE_CSV/SOURCE_REST";
        }
        return "尚未实现";
    }

    /**
     * 该类型在保存校验时是否放行（执行器有能力实现）。
     */
    public static boolean isAvailable(String type) {
        return type != null && IMPLEMENTED.contains(type);
    }

    private PipelineNodeTypesCatalog() {
    }

    /** 兼容旧调用点：直接 offer 一个静态实例。 */
    public boolean supports(String type) {
        return type != null && SUPPORTED.contains(type);
    }
}
