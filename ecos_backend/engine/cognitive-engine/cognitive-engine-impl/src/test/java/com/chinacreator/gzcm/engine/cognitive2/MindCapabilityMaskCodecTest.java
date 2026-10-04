package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chinacreator.gzcm.engine.cognitive2.mind.MindCapabilityMask;
import com.chinacreator.gzcm.engine.cognitive2.mind.MindCapabilityMaskCodec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 F05-11 / REQ-COG-04 —— {@link MindCapabilityMaskCodec} TEXT JSON 数组 round-trip 护栏
 * （验收标识 {@code MindCapabilityMaskCodecTest#roundTripThroughTextJsonArray}）。
 *
 * <p><b>为什么离线留这个</b>：能力掩码的 DB 值形态（{@code []} / {@code ["DETECT",…]}）是三处
 * 契约的交汇点（DDL {@code capability_mask_json} TEXT 列 V187 / E1 detect 出参 / E4
 * capability 投影）。序列化只经本 codec，禁 Mapper/Service 散点手写 JSON；本护栏锁
 * "枚举序 ↔ JSON 数组 ↔ 枚举序" 三向确定性，任一处散点拼串会立刻红。
 */
class MindCapabilityMaskCodecTest {

    @Test
    @DisplayName("roundTripThroughTextJsonArray: 空=「[]」/ 全集=「[DETECT,FORECAST,SIMULATE,PLAN,REVIEW]」逐字对齐 + round-trip")
    void roundTripThroughTextJsonArray() {
        // 空
        assertEquals("[]", MindCapabilityMaskCodec.toJsonArray(List.of()));
        assertEquals(List.of(), MindCapabilityMaskCodec.fromJsonArray("[]"));

        // 全集（声明序）
        List<MindCapabilityMask> all = List.of(
            MindCapabilityMask.DETECT, MindCapabilityMask.FORECAST, MindCapabilityMask.SIMULATE,
            MindCapabilityMask.PLAN, MindCapabilityMask.REVIEW);
        String json = MindCapabilityMaskCodec.toJsonArray(all);
        assertEquals("[\"DETECT\",\"FORECAST\",\"SIMULATE\",\"PLAN\",\"REVIEW\"]", json,
            "全集序列化形态必须紧凑 + 枚举序逐字对齐");
        assertEquals(all, MindCapabilityMaskCodec.fromJsonArray(json), "round-trip 必须保序保值");

        // 子集
        assertEquals("[\"FORECAST\",\"REVIEW\"]",
            MindCapabilityMaskCodec.toJsonArray(List.of(MindCapabilityMask.FORECAST, MindCapabilityMask.REVIEW)));
    }

    @Test
    @DisplayName("读侧容错 + 违例拒收：空白→空列表；非法 JSON / 未知成员 → CodecException（不静默）")
    void blankIsEmptyAndMalformedThrows() {
        assertEquals(List.of(), MindCapabilityMaskCodec.fromJsonArray("   "));
        assertEquals(List.of(), MindCapabilityMaskCodec.fromJsonArray(null));
        assertThrows(MindCapabilityMaskCodec.CodecException.class,
            () -> MindCapabilityMaskCodec.fromJsonArray("not-json"));
        assertThrows(MindCapabilityMaskCodec.CodecException.class,
            () -> MindCapabilityMaskCodec.fromJsonArray("[\"DETECT\",\"TYPO\"]"),
            "未知成员必须拒收：读侧由 Service 决定记 issues[] 或回空，codec 层禁吞");
    }
}
