package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.chinacreator.gzcm.common.exception.BusinessException;

/**
 * W57 / F02-04（详细设计-02 C46）节点能力一致性验收：
 * 执行器不可用（目录 {@code available=false}）的节点在<b>创建/保存时即拒</b>
 * （ECOS-DATA-012，400），而非拖到执行才失败。
 *
 * <p>判定契约落在 {@link PipelineNodeTypesCatalog#assertCreatable(String)}——
 * 由 {@code PipelineServiceImpl#validateSaveDto} 在保存路径前置调用；本测试不触库。</p>
 */
class UnavailableNodeRejectedAtSaveTest {

    @Test
    @DisplayName("SOURCE_CDC 目录 available=false → assertCreatable 抛 400 ECOS-DATA-012（创建即拒）")
    void sourceCdc_rejectedAtSave() {
        PipelineNodeTypesCatalog.NodeTypeInfo info = PipelineNodeTypesCatalog.info("SOURCE_CDC");
        assertFalse(info.available(), "SOURCE_CDC 应登记为不可用");
        assertNotNull(info.unavailableReason(), "不可用类型必须给原因（用户可拖出但保存被拒）");

        BusinessException e = assertThrows(BusinessException.class,
                () -> PipelineNodeTypesCatalog.assertCreatable("SOURCE_CDC"));
        assertEquals(400, e.getErrorCode(), "应为 400 业务异常");
        assertTrue(e.getMessage().contains("ECOS-DATA-012"), "应带 ECOS-DATA-012; 实际 " + e.getMessage());
    }

    @Test
    @DisplayName("未登记类型（擦边值）→ UNKNOWN available=false → 拒")
    void unknownType_rejectedAtSave() {
        PipelineNodeTypesCatalog.NodeTypeInfo info = PipelineNodeTypesCatalog.info("SOURCE_EVIL");
        assertFalse(info.available());
        BusinessException e = assertThrows(BusinessException.class,
                () -> PipelineNodeTypesCatalog.assertCreatable("SOURCE_EVIL"));
        assertTrue(e.getMessage().contains("ECOS-DATA-012"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE_JDBC", "SOURCE_CSV", "SOURCE_REST", "SOURCE_MINIO",
            "TRANSFORM_SQL", "TRANSFORM_UDF", "TRANSFORM_DOC_PARSE", "JOIN", "SINK",
            "SINK_MINIO", "OUTPUT_OBJECT"})
    @DisplayName("11 类可抓/可存节点 → assertCreatable 放行（不误伤）")
    void availableTypes_passSave(String type) {
        assertTrue(PipelineNodeTypesCatalog.info(type).available(), type + " 应可用");
        assertDoesNotThrow(() -> PipelineNodeTypesCatalog.assertCreatable(type),
                "可用类型不得被创建即拒: " + type);
    }
}
