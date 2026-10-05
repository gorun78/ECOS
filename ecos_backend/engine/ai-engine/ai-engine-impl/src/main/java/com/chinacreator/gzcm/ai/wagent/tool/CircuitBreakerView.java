package com.chinacreator.gzcm.ai.wagent.tool;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 分册10 F10-24 · 工具级熔断视图：60s 滑动窗口，errorRate > 0.5 且样本 ≥ 20 开合（OPEN）；
 * 30s 后 HALF_OPEN 放<b>单探针</b>调用，探针成功 ⇒ CLOSED，失败 ⇒ 回 OPEN。
 *
 * <p>线程安全：{@link #record(CallRecord)} 同步写入窗口；状态推进同步切。
 * 计数保持 long（禁 double 概率入契约，E-WA-STATE 只按计数判定）。
 * 调用方每次工具调用前后各调一次 {@link #allow(String)} / {@link #record(CallRecord)}。</p>
 */
public final class CircuitBreakerView {

    public enum Phase { CLOSED, OPEN, HALF_OPEN }

    public record CallRecord(long ts, boolean ok) {
        public CallRecord {
            if (ts < 0) throw new IllegalArgumentException("ts 不可负");
        }
    }

    private static final long WINDOW_MS   = 60_000L;
    private static final long OPEN_MS     = 30_000L;
    private static final int   MIN_SAMPLES = 20;

    private final Deque<CallRecord> window = new ArrayDeque<>();
    private volatile Phase phase = Phase.CLOSED;
    private volatile long openedAt = 0L;
    private volatile boolean probeInFlight = false;
    /** 最近一次 record 的时刻；allow() 应基于该时刻而非墙钟，保证离线测可注入假时间戳。 */
    private volatile long lastTs = 0L;

    public CircuitBreakerView() {}

    /** 记录一次调用：窗口滑动 + 状态推进（CLOSED→OPEN→HALF_OPEN→CLOSED 循环）。 */
    public void record(CallRecord r) {
        if (r == null) return;
        long now = r.ts();
        lastTs = now;
        synchronized (window) {
            window.addLast(r);
            while (!window.isEmpty() && now - window.peekFirst().ts() > WINDOW_MS) window.pollFirst();
        }
        if (phase == Phase.OPEN && now - openedAt >= OPEN_MS) {
            phase = Phase.HALF_OPEN;
            probeInFlight = false;
        }
        if (phase == Phase.HALF_OPEN) {
            synchronized (this) {
                if (probeInFlight) return;
                probeInFlight = true;
                if (r.ok()) phase = Phase.CLOSED;
                else { phase = Phase.OPEN; openedAt = now; }
                probeInFlight = false;
            }
        }
        if (phase() == Phase.CLOSED) {
            long total = 0, err = 0;
            synchronized (window) {
                for (CallRecord c : window) { total++; if (!c.ok()) err++; }
            }
            if (total >= MIN_SAMPLES && err * 2L > total) {   // errorRate > 0.5
                phase = Phase.OPEN;
                openedAt = now;
            }
        }
    }

    public Phase phase() { return phase; }

    /** OPEN ⇒ 全拒；HALF_OPEN ⇒ 仅 1 探针；其它放行（fail-closed）。 */
    public boolean allow(String key) {
        long now = lastTs > 0 ? lastTs : System.currentTimeMillis();
        if (phase == Phase.OPEN) {
            if (now - openedAt >= OPEN_MS) phase = Phase.HALF_OPEN;
            else return false;
        }
        if (phase == Phase.HALF_OPEN) {
            synchronized (this) {
                if (probeInFlight) return false;
                probeInFlight = true;
            }
            return true;
        }
        return true;
    }

    /** 兼容旧签名：不带 ts 计入当前时刻（外部代码兼容）。 */
    public void record(boolean ok) {
        record(new CallRecord(System.currentTimeMillis(), ok));
    }

    /** 兼容旧签名：不带 key 仅查 phase（外部代码兼容）。 */
    public boolean allow() {
        return allow("__default__");
    }

    /** 测试/诊断：当前窗口内样本数（long 类，禁 double）。 */
    public long windowSize() {
        synchronized (window) { return window.size(); }
    }
}
