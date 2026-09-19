package com.chinacreator.gzcm.engine.data.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PipelineNodeTypesCatalogTest — 三版本/前端枚举边界契约（Wave 4 T1）。
 *
 * <p>架构铁律 §4.8.2「枚举边界一致」：前端 NodePalette（节点面板）、
 * 后端 PipelineServiceImpl.VALID_NODE_TYPES、以及前端
 * pipelineValidation.ts REQUIRED_FIELDS_BY_TYPE 三方必须同源；
 * 本测试锁定后端目录 = 前端 5 + PMO-36 4 + SINK_MINIO 1 + SOURCE_MINIO 1（共 11 类），防漂移。
 */
class PipelineNodeTypesCatalogTest {

    @Test
    @DisplayName("目录覆盖前端 REQUIRED_CONFIG_FIELDS 的 5 个基础类型")
    void catalogCoversFrontendRequiredTypes() {
        Set<String> frontend = Set.of(
                "SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "TRANSFORM_SQL", "OUTPUT_OBJECT");
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.containsAll(frontend),
                "后端目录必须覆盖前端基础 5 类");
    }

    @Test
    @DisplayName("目录含 PMO-36 新增 JOIN / SINK / TRANSFORM_UDF")
    void catalogCoversPmo36NewTypes() {
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains("JOIN"));
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains("SINK"));
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains("TRANSFORM_UDF"));
    }

    @Test
    @DisplayName("目录含数据湖节点 SINK_MINIO / SOURCE_MINIO（B6-1）")
    void catalogCoversLakeNodeTypes() {
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains("SINK_MINIO"));
        assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains("SOURCE_MINIO"));
    }

    @Test
    @DisplayName("目录总量锁定为 11（与前端枚举下边界对齐）")
    void catalogSizeLocked() {
        assertEquals(11, PipelineNodeTypesCatalog.SUPPORTED.size());
    }
}
