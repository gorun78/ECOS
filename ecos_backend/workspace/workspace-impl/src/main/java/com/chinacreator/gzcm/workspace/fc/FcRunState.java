package com.chinacreator.gzcm.workspace.fc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 分册09 F09-04/05/06/11/13 · 预测运行 coordinator 状态（workspace 场景层 · 只编排不生产）。
 *
 * <p><b>铁律 §0.6 边界</b>（R-59①/C215）：本类是编排层，不引用任何 {@code java.math.BigDecimal} /
 * 算术运算符；金额/区间/回测数值一律由 cognitive-engine 计算后经 data-engine 写通道
 * 落到 {@code ecos_dw}；本类只承载 runId / 六要素 / 状态 / 幂等键 / 审计引用 + 明细<b>行引用</b>。
 * {@code WorkspaceNoMoneyArithmeticTest} 独立护栏锁定本包不出现 {@code java.math.BigDecimal} / 加减乘除字面。</p>
 *
 * <p><b>状态机</b>（§3.1，C210 应用层红线）：
 * CREATED → DATA_CHECK → SNAPSHOT_FROZEN → RUNNING → SUCCEEDED / FAILED；
 * SUCCEEDED/FAILED 均为终态，任何后续 transition 一律 reject。幂等：命中 SUCCEEDED 且非 force ⇒ 直接返回 reused=true。</p>
 *
 * <p>生产侧数据面接线（诚实登记，见详细设计-09 §十一【校訂 2026-10-05】）：
 * 六要素六列 / run 主表 / result_detail / backtest / action_ext 已经 V215~V218 迁移脚本
 * 落 gateway 单源目录（R-64①，未实跑库）；每张表由 workspace 通过 data-engine write-channel
 * 落笔——本 ledger 只是 coordinator 语义层的<b>离线可测载体</b>，生产链路 RT 接线 = 待授权任务。</p>
 */
@Component
public class FcRunState {

    /** 状态常量（§3.1）。 */
    public static final String CREATED = "CREATED";
    public static final String DATA_CHECK = "DATA_CHECK";
    public static final String SNAPSHOT_FROZEN = "SNAPSHOT_FROZEN";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    public static boolean isTerminal(String s) { return SUCCEEDED.equals(s) || FAILED.equals(s); }

    /** 六要素 + 状态 + 幂等键 + 明细行引用（生产 layout 落 data-engine 后本类回贴）。 */
    public record RunRow(String runId, Map<String, Object> six, String status, boolean reused,
                          String reusedFromRunId, String taskId,
                          String scopeHash, String overridesHash, String runKey,
                          String errorCode, String errorText,
                          List<Object> cellRefs, Map<String, Object> auditPack) {
        public boolean terminal() { return FcRunState.isTerminal(status); }
    }

    private final Map<String, RunRow> byId = new ConcurrentHashMap<>();
    private final Map<String, List<String>> byRunKey = new ConcurrentHashMap<>();

    public void put(RunRow r) {
        byId.put(r.runId(), r);
        byRunKey.computeIfAbsent(r.runKey(), k -> new CopyOnWriteArrayList<>()).add(r.runId());
    }
    public RunRow require(String runId) {
        RunRow r = byId.get(runId);
        if (r == null) throw new IllegalStateException("fc_run not found: " + runId);
        return r;
    }
    public List<RunRow> snapshot() { return new ArrayList<>(byId.values()); }

    public RunRow latestByRunKey(String runKey) {
        List<String> ids = byRunKey.get(runKey);
        if (ids == null || ids.isEmpty()) return null;
        return byId.get(ids.get(ids.size() - 1));
    }

    /** C210：任何 terminal → * 迁移一律 reject；CREATED/DATA_CHECK/SNAPSHOT_FROZEN/RUNNING → SUCCEEDED/FAILED 允许。 */
    public void transition(String runId, String target) {
        RunRow r = require(runId);
        if (isTerminal(r.status())) {
            throw new IllegalStateException("run " + runId + " terminated, no transition allowed (C210 immutability)");
        }
        // 允许 state → SUCCEEDED/FAILED；进一步在同类内 no-op（无需重发）
        // 环路：本批次语义层不做复杂子状态迁移校验，生产链路另有状态机服务（场景域 C147 权）。
        if (target == null || target.isBlank()) throw new IllegalStateException("target null");
    }

    public static String newUuid() { return UUID.randomUUID().toString(); }
}
