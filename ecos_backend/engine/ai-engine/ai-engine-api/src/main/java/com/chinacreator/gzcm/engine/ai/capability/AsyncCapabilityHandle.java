package com.chinacreator.gzcm.engine.ai.capability;

/**
 * 异步能力句柄（《补充设计 v1.0》§10.4 {@code AsyncCapabilityHandle} 冻结形态）。
 *
 * <p>异步任务一律经 runtime-task 提交（铁律 §2.5：引擎禁自建调度器/裸线程，
 * SUP-GOV-02），本句柄只是 taskId 与受理信息的<b>回执</b>：
 * <ul>
 *   <li>{@code taskId} = runtime-task 返回的任务 ID（后续 {@code getStatus} 的寻址键）；</li>
 *   <li>{@code traceId} 受理时即回填（§12.2：受理即可追踪，禁跑完才补）；</li>
 *   <li>初始状态恒 {@link CapabilityState#SUBMITTED}（受理≠执行完成，禁提前标 SUCCEEDED）。</li>
 * </ul>
 */
public final class AsyncCapabilityHandle {

    private final String taskId;
    private final String traceId;
    private final CapabilityState initialStatus;

    public AsyncCapabilityHandle(String taskId, String traceId, CapabilityState initialStatus) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("异步能力句柄缺 taskId（runtime-task 回显键，SUP-GOV-02）");
        }
        this.taskId = taskId;
        this.traceId = traceId;
        this.initialStatus = initialStatus == null ? CapabilityState.SUBMITTED : initialStatus;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTraceId() {
        return traceId;
    }

    public CapabilityState getInitialStatus() {
        return initialStatus;
    }
}
