package com.chinacreator.gzcm.engine.data.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PipelineNodeTypesCatalog — 节点类型目录的唯一权威来源（Wave 4 T1 → F02-04 扩展）。
 *
 * <p>与前端 {@code REQUIRED_FIELDS_BY_TYPE}（pipelineValidation.ts）同源，
 * 架构铁律 §4.8.2「枚举边界一致」：
 * <ul>
 *   <li>后端保存校验使用（{@link PipelineServiceImpl#validateSaveDto} 复用本目录）</li>
 *   <li>前端 NodePalette 节点面板枚举同源（前端常量 P2-01 5 + PMO-36 4 = 9 类 + 后续新增 3 类）</li>
 *   <li>{@code GET /api/v1/pipeline/node-types} 复用本目录</li>
 * </ul>
 *
 * <p><b>F02-04（详细设计-02，W57）节点能力一致性</b>：
 * <ul>
 *   <li>每条目增加 {@link NodeTypeInfo#available()} 与 {@link NodeTypeInfo#unavailableReason()}；</li>
 *   <li>{@code available=false} 的节点在<b>创建时即拒</b>（{@code ECOS-DATA-012}，400），
 *       而非执行时才拒；执行器保留原显式拒绝作为第二道保险；</li>
 *   <li>当前 {@code SOURCE_CDC} 执行器显式 {@code BusinessException("未实现")}，
 *       故 {@code available=false}；其余 11 类均可用时 {@code available=true}。</li>
 * </ul>
 *
 * <p>目录版本号 {@link #CATALOG_VERSION} 与 {@code GET /api/v1/pipeline/node-types}
 * 响应同源，前端 NodePalette 用于断点续做/UI 灰显。
 */
public final class PipelineNodeTypesCatalog {

    /** 目录版本号（与 GET /api/v1/pipeline/node-types 响应同源）。 */
    public static final String CATALOG_VERSION = "2.1.0";

    /**
     * 节点类型全集 = 前端 5（SOURCE_JDBC/SOURCE_CSV/SOURCE_REST/TRANSFORM_SQL/OUTPUT_OBJECT）
     * + PMO-36 新增 4（SOURCE_CDC/TRANSFORM_UDF/JOIN/SINK）
     * + 数据采集 1（SINK_MINIO，数据湖近源库写入）
     * + 近源层读取 1（SOURCE_MINIO，「管道基于近源层处理」）
     * + 文档解析 1（TRANSFORM_DOC_PARSE，非结构化 → DW 表）。
     */
    public static final Set<String> SUPPORTED = Set.of(
            "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_CDC", "SOURCE_MINIO",
            "TRANSFORM_SQL", "TRANSFORM_UDF", "TRANSFORM_DOC_PARSE", "JOIN", "SINK",
            "SINK_MINIO", "OUTPUT_OBJECT"
    );

    /**
     * F02-04 元数据词表：按类型返回 {@link NodeTypeInfo}。
     * 未登记的类型（SUPPORTED 内外的擦边值）→ {@link NodeTypeInfo#UNKNOWN()} 兜底
     * （available=false，reason="类型未登记"）。
     */
    public record NodeTypeInfo(String type, boolean available, String unavailableReason) {
        static NodeTypeInfo ofAvailable(String type) {
            return new NodeTypeInfo(type, true, null);
        }
        static NodeTypeInfo ofUnavailable(String type, String reason) {
            return new NodeTypeInfo(type, false, reason);
        }
        static final NodeTypeInfo UNKNOWN = new NodeTypeInfo(null, false, "节点类型未登记于目录");
    }

    private static final Map<String, NodeTypeInfo> INFO = new LinkedHashMap<>();
    static {
        // 全部 12 类逐一登记；SUPPORTED 内容必须同此（若新增类型忘记登记 → NodeCatalogParityTest 会 FAIL）
        INFO.put("SOURCE_JDBC",     NodeTypeInfo.ofAvailable("SOURCE_JDBC"));
        INFO.put("SOURCE_CSV",      NodeTypeInfo.ofAvailable("SOURCE_CSV"));
        INFO.put("SOURCE_REST",     NodeTypeInfo.ofAvailable("SOURCE_REST"));
        // F02-04：SOURCE_CDC 执行器抛 BusinessException（未实现 CDC 语义），创建即拒
        INFO.put("SOURCE_CDC",      NodeTypeInfo.ofUnavailable("SOURCE_CDC",
                "尚未对接 CDC（Debezium/Canal）驱动；可用 SOURCE_JDBC + 增量水位替代"));
        INFO.put("SOURCE_MINIO",    NodeTypeInfo.ofAvailable("SOURCE_MINIO"));
        INFO.put("TRANSFORM_SQL",   NodeTypeInfo.ofAvailable("TRANSFORM_SQL"));
        INFO.put("TRANSFORM_UDF",   NodeTypeInfo.ofAvailable("TRANSFORM_UDF"));
        INFO.put("TRANSFORM_DOC_PARSE", NodeTypeInfo.ofAvailable("TRANSFORM_DOC_PARSE"));
        INFO.put("JOIN",            NodeTypeInfo.ofAvailable("JOIN"));
        INFO.put("SINK",            NodeTypeInfo.ofAvailable("SINK"));
        INFO.put("SINK_MINIO",      NodeTypeInfo.ofAvailable("SINK_MINIO"));
        INFO.put("OUTPUT_OBJECT",   NodeTypeInfo.ofAvailable("OUTPUT_OBJECT"));
    }

    private PipelineNodeTypesCatalog() {
    }

    /** 兼容既有调用点：type 类型名是否在目录内。 */
    public boolean supports(String type) {
        return type != null && SUPPORTED.contains(type);
    }

    /** F02-04：查询单条目录条目；未登记返回 {@link NodeTypeInfo#UNKNOWN}。 */
    public static NodeTypeInfo info(String type) {
        return type == null ? NodeTypeInfo.UNKNOWN : INFO.getOrDefault(type, NodeTypeInfo.UNKNOWN);
    }

    /** F02-04：全目录有序列表（供 GET /api/v1/pipeline/node-types 直接序列化）。 */
    public static List<NodeTypeInfo> all() {
        return List.copyOf(INFO.values());
    }

    /** F02-04：创建/保存前置断言 —— 该类型必须在目录内且 available=true。
     *  违反 → 抛 {@link com.chinacreator.gzcm.common.exception.BusinessException}(400, ECOS-DATA-012)。 */
    public static void assertCreatable(String type) {
        NodeTypeInfo i = info(type);
        if (!i.available()) {
            String reason = i.unavailableReason() == null ? "" : ("；原因: " + i.unavailableReason());
            throw new com.chinacreator.gzcm.common.exception.BusinessException(400,
                    "ECOS-DATA-012: " + (i.type() == null ? "非法节点类型: " + type : "节点类型不可用: " + type) + reason);
        }
    }
}
