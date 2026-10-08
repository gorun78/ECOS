package com.chinacreator.gzcm.engine.cognitive2.mind;

import com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F05-11 验收：枚举恰 5 值 + 以契约文档表为断言基准（防漂移）。
 */
class MindCapabilityMaskTest {

    @Test
    void enumMembersAreExactlyFive() {
        assertEquals(5, MindCapabilityMask.values().length, "capabilityMask 冻结为 5 值");
        assertSameOrder(List.of(
                MindCapabilityMask.DETECT,
                MindCapabilityMask.FORECAST,
                MindCapabilityMask.SIMULATE,
                MindCapabilityMask.PLAN,
                MindCapabilityMask.REVIEW));
    }

    @Test
    void enumMatchesContractDocTable() throws IOException {
        Set<String> doc = readContract();
        Set<String> code = Set.of("DETECT", "FORECAST", "SIMULATE", "PLAN", "REVIEW");
        assertEquals(code, doc, "枚举成员必须与契约文档 §2.2 表逐条一致");
        assertNotNull(MindCapabilityMask.fromName("SIMULATE"));
    }

    private static void assertSameOrder(List<MindCapabilityMask> expected) {
        for (int i = 0; i < MindCapabilityMask.values().length; i++) {
            assertEquals(expected.get(i), MindCapabilityMask.values()[i]);
        }
    }

    private static Set<String> readContract() throws IOException {
        try (InputStream in = MindCapabilityMaskTest.class.getResourceAsStream("/cognitive/capability-mask-contract.txt")) {
            assertNotNull(in, "契约资源 capability-mask-contract.txt 必须存在（F05-11 防漂移）");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }
}
