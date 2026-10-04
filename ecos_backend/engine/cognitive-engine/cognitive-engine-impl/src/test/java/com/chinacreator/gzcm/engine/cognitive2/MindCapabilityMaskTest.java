package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.engine.cognitive2.mind.MindCapabilityMask;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 F05-11 / REQ-COG-04 —— {@link MindCapabilityMask} 唯一权威枚举契约护栏
 * （验收标识 {@code MindCapabilityMaskTest#enumMembersAreExactlyFive}）。
 *
 * <p><b>为什么是护栏而不是实现</b>：E1 detect 出参 {@code capabilityMask[]} 与 E4
 * {@code belief.domain ↔ capability} 投影都必须共用同一份枚举真源；本册既有实现零
 * {@code MindCapabilityMask}（X-13 双 capabilityMask 类型并存），先把枚举落到 api 包并
 * 用本护栏锁住"就是这 5 个、值形态全大写"，防止后续 dispersion（impl 私有 enum、
 * 前端硬编码字符串常量库外的变量名漂移）。
 *
 * <p><b>校订 XX/XX（按用户约定：结论先实测，不复用既有陈述）</b>：REQ-COG-04 §2.2 表
 * 五个成员 = DETECT/FORECAST/SIMULATE/PLAN/REVIEW，本护栏以值形态逐字对齐（防拼写/大小写漂移）。
 */
class MindCapabilityMaskTest {

    @Test
    @DisplayName("enumMembersAreExactlyFive: 恰好 5 个成员且值形态为 DETECT/FORECAST/SIMULATE/PLAN/REVIEW（大写）")
    void enumMembersAreExactlyFive() {
        MindCapabilityMask[] values = MindCapabilityMask.values();
        assertEquals(5, values.length, "能力掩码枚举恰 5 成员");
        List<String> wires = Arrays.stream(values).map(MindCapabilityMask::wire).collect(Collectors.toList());
        assertEquals(List.of("DETECT", "FORECAST", "SIMULATE", "PLAN", "REVIEW"),
            wires, "枚举声明序与 REQ-COG-04 §2.2 表逐字对齐");
    }

    @Test
    @DisplayName("fromWire: 大小写不敏感解析 + 未识别→empty")
    void fromWireIsCaseInsensitiveAndUnknownReturnsEmpty() {
        assertEquals("DETECT", MindCapabilityMask.fromWire("detect").stream().map(MindCapabilityMask::name).findFirst().orElseThrow());
        assertEquals("REVIEW", MindCapabilityMask.fromWire(" REVIEW ").stream().map(MindCapabilityMask::name).findFirst().orElseThrow());
        assertTrue(MindCapabilityMask.fromWire("NOT_A_CAPABILITY").isEmpty(), "未识别成员必须 empty（读侧决定 issue/400，本层不吞）");
        assertTrue(MindCapabilityMask.fromWire(null).isEmpty());
    }
}
