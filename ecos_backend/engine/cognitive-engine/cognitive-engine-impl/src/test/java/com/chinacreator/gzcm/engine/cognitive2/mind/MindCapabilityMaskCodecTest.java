package com.chinacreator.gzcm.engine.cognitive2.mind;

import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMaskCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F05-11：DB 列 TEXT 存 JSON 数组 <-> 内存 List 双向序列化一致性（禁逗号串）。 */
class MindCapabilityMaskCodecTest {

    @Test
    void roundTripThroughTextJsonArray() {
        List<MindCapabilityMask> caps = List.of(
                MindCapabilityMask.DETECT, MindCapabilityMask.FORECAST, MindCapabilityMask.REVIEW);
        String json = MindCapabilityMaskCodec.encode(caps);
        // 必须是 TEXT JSON 数组形态，禁逗号串
        assertEquals("[\"DETECT\",\"FORECAST\",\"REVIEW\"]", json);
        List<MindCapabilityMask> decoded = MindCapabilityMaskCodec.decode(json);
        assertEquals(caps, decoded);
        // 空
        assertEquals("[]", MindCapabilityMaskCodec.encode(List.of()));
        assertTrue(MindCapabilityMaskCodec.decode(null).isEmpty());
        assertTrue(MindCapabilityMaskCodec.decode("[]").isEmpty());
        assertTrue(MindCapabilityMaskCodec.decode("").isEmpty());
        // 未知 token 静默忽略（读侧容错）
        List<MindCapabilityMask> mixed =
                MindCapabilityMaskCodec.decode("[\"DETECT\",\"BOGUS\",\"DETECT\"]");
        assertEquals(List.of(MindCapabilityMask.DETECT), mixed, "去重 + 忽略未知 token");
    }
}
