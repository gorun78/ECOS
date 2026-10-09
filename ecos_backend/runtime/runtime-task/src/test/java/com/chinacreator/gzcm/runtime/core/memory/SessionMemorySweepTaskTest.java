package com.chinacreator.gzcm.runtime.core.memory;

import com.chinacreator.gzcm.common.service.IMemorySweepable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 详细设计-06 F06-10 要点 1 的<b>驱动侧</b>验证：{@code expireAfterAccess} 的周期动力来自
 * runtime-task 单点（铁律 §2.5-4 / X-25），视图属主只交出 {@link IMemorySweepable} 能力。
 *
 * <p>本用例锁两件事：</p>
 * <ul>
 *   <li>一次周期触发要扫到<b>每一个</b>已登记的有界视图（漏扫 = 该视图仍可无界增长）；</li>
 *   <li>单个视图抛异常只留痕，不得让后续视图失去本轮清扫（整链停摆即门禁失效）。</li>
 * </ul>
 */
@DisplayName("F06-10 底座内存清扫驱动点（runtime-task）")
class SessionMemorySweepTaskTest {

    private static final class Counting implements IMemorySweepable {
        private final int reclaimed;
        private final AtomicInteger sweeps = new AtomicInteger();

        Counting(int reclaimed) {
            this.reclaimed = reclaimed;
        }

        @Override
        public int sweepIdleEntries() {
            sweeps.incrementAndGet();
            return reclaimed;
        }

        @Override
        public int residentEntryCount() {
            return 7;
        }
    }

    private static final class Throwing implements IMemorySweepable {
        private final AtomicInteger sweeps = new AtomicInteger();

        @Override
        public int sweepIdleEntries() {
            sweeps.incrementAndGet();
            throw new IllegalStateException("view owner failed");
        }

        @Override
        public int residentEntryCount() {
            return 0;
        }
    }

    @Test
    @DisplayName("要点1/X-25: 一次触发扫遍所有视图，单点故障不中断整链")
    void sweepsEveryRegisteredViewAndIsolatesFailures() {
        Counting first = new Counting(3);
        Throwing broken = new Throwing();
        Counting last = new Counting(1);
        SessionMemorySweepTask task = new SessionMemorySweepTask(List.of(first, broken, last));

        assertDoesNotThrow(task::sweepAll, "单个视图故障禁抛穿调度线程");

        assertEquals(1, first.sweeps.get());
        assertEquals(1, broken.sweeps.get());
        assertEquals(1, last.sweeps.get(), "故障视图之后的视图仍必须被扫到");
    }

    @Test
    @DisplayName("无视图登记时空转不报错（非 AI 档位的部署形态）")
    void noSweepablesIsNotAnError() {
        SessionMemorySweepTask task = new SessionMemorySweepTask(List.of());
        assertDoesNotThrow(task::sweepAll);
    }
}
