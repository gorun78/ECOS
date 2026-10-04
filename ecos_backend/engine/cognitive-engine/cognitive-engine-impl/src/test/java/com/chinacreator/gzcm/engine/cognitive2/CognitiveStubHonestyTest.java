package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 X-17 / F05-13 stub 诚实度护栏：
 *
 * <p>认知侧健康探针返回硬编码 {@code "UP"} 字面量——2026-10-04 实查共 6 处：
 * {@code CognitiveEngineHealthController}×4、{@code CognitiveEngineOpenHealthController:121}
 * (条件式 {@code dbUp ? "UP" : "DOWN"}，属<b>条件诚实</b>)、{@code :155} (硬编码 UP)。本护栏只锁
 * <b>总面只减不增</ b>（≤6）——每个新增的硬编码 UP 字面量都会增加健康探针不诚实面，
 * 由 R-14 组统一切 HeathinessPoller 后趋零。</p>
 */
final class CognitiveStubHonestyTest {

    private static final Pattern UP_LITERAL = Pattern.compile("\"UP\"");

    @Test
    @DisplayName("健康探针 \"UP\" 字面量 只减不增（baseline 6，2026-10-04 实测：Hardcoded 5 + dbUp 条件 1）")
    void noLiteralUpInHealthPayloadRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, UP_LITERAL);
        assertTrue(hits <= 6,
            "\"UP\" 硬编码字面量 %d > 6（超出 2026-10-04 基线；R-14 组切 Heathy poller 前只减不增）"
                .formatted(hits));
    }
}
