package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-08/W172/C154 验收：{@code run_mode} 是与既有 {@code run_type}
 * **正交** 的独立列（R-25 已裁决 ① 增列，禁把 SANDBOX 挤进 run_type），
 * 且入参非法即抛 400（禁静默降级）。
 */
class RunModeTest {

    @Test
    @DisplayName("null/空白 → 默认 FORMAL（A31 向后兼容旧入参 body）")
    void runModeDefaultsToFormal() {
        assertSame(RunMode.FORMAL, RunMode.parseOrDefault(null));
        assertSame(RunMode.FORMAL, RunMode.parseOrDefault(""));
        assertSame(RunMode.FORMAL, RunMode.parseOrDefault("   "));
    }

    @Test
    @DisplayName("未知值 → 抛 InvalidRunModeException(400/INVALID_RUN_MODE)，禁静默通过")
    void runModeRejectsUnknownValue() {
        InvalidRunModeException ex = assertThrows(InvalidRunModeException.class,
                () -> RunMode.parseOrDefault("TIMED"));
        assertEquals(400, ex.getHttpStatus());
        assertEquals("INVALID_RUN_MODE", ex.getCode());
        assertEquals("TIMED", ex.getDetails().get("runMode"));
    }

    @Test
    @DisplayName("FORMAL/SANDBOX 大小写不敏感解析")
    void bothModesParseCaseInsensitive() {
        assertSame(RunMode.FORMAL, RunMode.parseOrDefault("formal"));
        assertSame(RunMode.SANDBOX, RunMode.parseOrDefault("SaNdBoX"));
        assertSame(RunMode.SANDBOX, RunMode.parseOrDefault("  SANDBOX  "));
    }

    @Test
    @DisplayName("枚举恒 2 值 — FORMAL+SANDBOX，破 X-32 三值挤列")
    void enumIsBinary() {
        assertEquals(2, RunMode.values().length);
    }

    @Test
    @DisplayName("fromNullable 与 parseOrDefault 语义区别：前者宽松返 null、后者严格抛")
    void fromNullableVsParseOrDefault() {
        assertNull(RunMode.fromNullable("unknown"));
        assertNull(RunMode.fromNullable(null));
        assertThrows(InvalidRunModeException.class, () -> RunMode.parseOrDefault("unknown"));
    }

    @Test
    @DisplayName("SANDBOX 与 FORMAL 是不同枚举值，DB 列正交不重叠")
    void orthogonalValues() {
        assertTrue(RunMode.FORMAL != RunMode.SANDBOX);
        assertSame(RunMode.FORMAL, RunMode.valueOf("FORMAL"));
        assertSame(RunMode.SANDBOX, RunMode.valueOf("SANDBOX"));
    }

    @Test
    @DisplayName("异常族：InvalidRunMode 是 ScenarioDomainException 子类，Advice 统一映射")
    void invalidRunModeIsDomainException() {
        InvalidRunModeException e = new InvalidRunModeException("X");
        assertTrue(e instanceof ScenarioDomainException);
        assertEquals(Collections.singleton("runMode"), e.getDetails().keySet());
    }
}
