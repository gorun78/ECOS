package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.engine.ai.oag.dto.OagStepEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAG 承流载荷形状用例（#143/B5-5a，D-B5-7 收口批次）。
 *
 * <p>判据取自前端消费面而非后端自述：{@code pages/aiworkbench/api.ts} 的
 * {@code onBlocked} 读 {@code data.reason}、{@code onDone} 读 {@code traceId}，
 * 因此后端载荷键面必须与消费面同形。本类两条断言在收口前的真实树上都是红的
 * （blocked 只有 {@code message}、traceId 为 null 时整键缺席），属缺陷原形而非凑数。</p>
 */
@DisplayName("B5-5a OAG 载荷形状：键面与前端消费面同形 + 每帧带 traceId")
class OagSsePayloadShapeTest {

    @Test
    @DisplayName("blocked 载荷必须带前端读取的 reason 键（旧形态只出 message ⇒ 拦截原因恒空）")
    void blockedPayloadExposesReasonKeyReadByFrontend() {
        OagStepEvent blocked = OagStepEvent.blocked("安全护栏拒绝：越权查询",
                "trace-oag-1", "sales-query", false);

        Map<String, Object> payload = blocked.toMap();

        assertEquals("安全护栏拒绝：越权查询", payload.get("reason"),
                "前端 onBlocked 读 data.reason，后端只写 message ⇒ blockedReason 恒为空串");
        // message 保留：既有消费面（纯文本兜底腿）不得因新增键而断供
        assertEquals("安全护栏拒绝：越权查询", payload.get("message"));
    }

    @Test
    @DisplayName("traceId 缺失也必须写缺失标记，禁整键缺席（承流帧 traceId 恒在）")
    void everyPayloadCarriesTraceIdEvenWhenUnknown() {
        Map<String, Object> payload = OagStepEvent
                .error(null, "FAILED", 0L, null, "message 不能为空")
                .toMap();

        assertTrue(payload.containsKey("traceId"),
                "traceId 为 null 时整键缺席 ⇒ 前端读成空串，错误态无诊断码可显示");
        assertNotNull(payload.get("traceId"));
    }
}
