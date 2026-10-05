package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.AutomationLevel;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分册10 F10-18 · Step 行写入器：应用侧原子 upsert，UNIQUE(run_id, step_key, attempt) 在代码内兜底。
 *
 * <p>无状态 orchestrator 原则：Step 行是唯一持久事实（metrics_json 每步一写）。
 * {@link #write(StepRow)} 幂等——同三元组多次写返回<b>既存</b>行（不覆盖、不重号），
 * 并发写由 ConcurrentHashMap key 原子保证 "先查后插" 的唯一性。</p>
 */
public final class StepWriter {

    private StepWriter() {}

    public record StepRow(String runId, String stepKey, int attempt, StepType type,
                          String inputHash, String outputHash,
                          AutomationLevel level, PolicyEffect effect, String error) {
        public StepRow {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(stepKey, "stepKey");
            Objects.requireNonNull(type, "type");
            if (attempt < 0) throw new IllegalArgumentException("attempt < 0");
        }
    }

    private static final class Triple {
        final String runId; final String stepKey; final int attempt;
        Triple(String r, String k, int a) { runId = r; stepKey = k; attempt = a; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Triple t)) return false;
            return t.runId.equals(runId) && t.stepKey.equals(stepKey) && t.attempt == attempt;
        }
        @Override public int hashCode() { return 31 * (31 * runId.hashCode() + stepKey.hashCode()) + attempt; }
    }

    /** 进程内存储（跨 JVM 一致性由 DB UNIQUE 兜底，此处代码内幂等）。可注入测试自行替换。 */
    private final Map<Triple, StepRow> store = new ConcurrentHashMap<>();

    /** 幂等 upsert：同三元组返回既存行（不覆盖新输入）。 */
    public StepRow write(StepRow r) {
        Objects.requireNonNull(r, "row");
        Triple key = new Triple(r.runId(), r.stepKey(), r.attempt());
        return store.computeIfAbsent(key, k -> r);
    }

    /** 读某 (runId, stepKey) 的最新 attempt 行；无 ⇒ null。 */
    public StepRow readLatest(String runId, String stepKey) {
        StepRow best = null;
        for (StepRow row : store.values()) {
            if (row.runId().equals(runId) && row.stepKey().equals(stepKey)
                    && (best == null || row.attempt() > best.attempt())) {
                best = row;
            }
        }
        return best;
    }

    /** 测试/诊断：当前缓存行总数。 */
    public int size() { return store.size(); }
}
