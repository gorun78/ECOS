package com.chinacreator.gzcm.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Object Runtime 主键生成门禁（D91，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：时间线 / 版本 / 关系三类行的主键原为
 * {@code "tl-" + ID_SEQ.incrementAndGet()}（进程内 {@link AtomicInteger}，起点 500/1000）。
 * 目标表是<b>持久表</b>且 {@code id VARCHAR(50) PRIMARY KEY} 无 DEFAULT ——
 * 进程一重启，计数器归零，必然与上一轮写过的行重号：
 * {@code duplicate key value violates unique constraint "ecos_object_timeline_pkey"}。
 * 该异常又被 {@code recordTimeline} 的 catch 吞成 WARN，于是「对象建成了、时间线一条没有」
 * 成为静默数据缺失（真实库内可见 {@code tl-501}/{@code tl-1001} 两条卡位行）。</p>
 *
 * <p><b>门禁要点</b>：一、主键必须带 UUID 形态（跨进程唯一）；二、连续两次生成不得相同；
 * 三、类里不得再出现「进程内自增计数器当主键」的写法。
 * {@link #probeDetectsSequenceBasedId()} 是反假绿自检。</p>
 */
@DisplayName("D91 — 对象时间线/版本主键必须跨进程唯一（UUID），不得用进程内计数器")
class ObjectRuntimeIdContractTest {

    private static String callNewId(String prefix) throws Exception {
        Method m = ObjectRuntimeService.class.getDeclaredMethod("newId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, prefix);
    }

    @Test
    @DisplayName("生成的主键带前缀且含 UUID 段（跨进程唯一）")
    void idsCarryPrefixAndUuid() throws Exception {
        String tl = callNewId("tl-");
        String ver = callNewId("ver-");
        String rel = callNewId("rel-");
        String ort = callNewId("ort-");

        for (String id : new String[]{tl, ver, rel, ort}) {
            assertTrue(id.matches("^[a-z]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
                    "主键必须是「前缀 + 完整 UUID」，实际: " + id);
            assertTrue(id.length() <= 50, "主键不得超出 VARCHAR(50)，实际长度 " + id.length() + ": " + id);
        }
        assertEquals("tl-", tl.substring(0, 3));
    }

    @Test
    @DisplayName("连续两次生成不得相同（否则仍会撞主键）")
    void idsAreDistinct() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertNotEquals(callNewId("tl-"), callNewId("tl-"),
                    "两次生成的主键相同意味着仍有退化成计数器的风险");
        }
    }

    @Test
    @DisplayName("类中不得再持有进程内自增计数器（id 生成与它解耦）")
    void noProcessLocalSequenceRemains() {
        for (Field f : ObjectRuntimeService.class.getDeclaredFields()) {
            assertFalse(AtomicInteger.class.isAssignableFrom(f.getType()),
                    "静态/实例 AtomicInteger 计数器当主键会在重启后重号，必须移除: " + f.getName());
        }
    }

    @Test
    @DisplayName("探针自检：旧写法（前缀 + 自增数字）确实会被判为不合格")
    void probeDetectsSequenceBasedId() {
        AtomicInteger seq = new AtomicInteger(500);
        String legacy = "tl-" + seq.incrementAndGet();
        assertEquals("tl-501", legacy);
        assertFalse(legacy.matches("^[a-z]+-[0-9a-f]{8}-.*"),
                "旧写法的 id 必须被判为无 UUID 段，否则本门禁恒绿");
        // 重启语义：新实例从同一起点再生成一次 → 与上一轮撞号
        AtomicInteger afterRestart = new AtomicInteger(500);
        assertEquals(legacy, "tl-" + afterRestart.incrementAndGet(),
                "旧写法在重启后必然重复 —— 这正是 D91 的病灶");
    }

    /** 让编译器保留对 AtomicInteger 的引用（反假绿探针需要它）。 */
    @SuppressWarnings("unused")
    private static void keepNullRef() {
        assertNull(null);
    }
}
