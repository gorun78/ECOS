package com.chinacreator.gzcm.engine.cognitive2.diag;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F05-09 只增不改守护：{@code forecastContext} 块在请求侧是新增可选字段，
 * 既有 {@code POST /api/v1/cognitive/diagnose} 请求 schema 必须继续可解析（缺省行为不变）。
 * <p>本 single-class 验收以 {@link CognitiveApiPaths} 常量与 {@link CognitiveErrorCode}
 * 冻结字面量为断言基准（防路径/码值漂移），实际请求 DTO 演进时须保持这些键名不变。</p>
 */
class DiagnoseContractRegressionTest {

    @Test
    void legacyRequestSchemaUnchanged() {
        // 路径冻结
        assertEquals("/api/v1/cognitive/diagnose", CognitiveApiPaths.COGNITIVE_DIAGNOSE);
        assertEquals("/api/v1/cognitive/diagnose/history", CognitiveApiPaths.COGNITIVE_DIAGNOSE_HISTORY);
        assertEquals("/api/v1/cognitive/simulate", CognitiveApiPaths.COGNITIVE_SIMULATE);
        // 错误码冻结（原有 6 行错误矩阵不变，新增 503 语义仍走同一 ECOS-COG-5xx）
        assertEquals("ECOS-COG-501", CognitiveErrorCode.E501_SCHEMA_NOT_READY);
        assertEquals("ECOS-COG-500", CognitiveErrorCode.E500_ENGINE_DISABLED);
        assertEquals("ECOS-COG-502", CognitiveErrorCode.E502_BALANCE_NOT_OK);
        assertEquals("cognitive schema not ready", CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
        assertEquals("capability not implemented", CognitiveErrorCode.MSG_NOT_IMPLEMENTED);
        assertEquals("scenario attribution not balanced", CognitiveErrorCode.MSG_NOT_BALANCED);
        // D.3 新增可选面的字面量冻结（缺省请求不受影响，见 DiagnoseForecastContractTest 逐键断言）
        assertEquals("DETERMINISTIC_DIFF", CognitiveContractSchema.MODE_DETERMINISTIC_DIFF);
        assertEquals("NON_ACCOUNTING", CognitiveContractSchema.CALIBER_NON_ACCOUNTING);
        assertEquals(0, new java.math.BigDecimal("0.01")
                .compareTo(CognitiveContractSchema.BALANCE_TOLERANCE));
    }
}
