package com.chinacreator.gzcm.engine.data.quality;

/**
 * DQ 白名单自动修复服务契约（PMO-48-C T12）。
 *
 * <p>铁律硬卡（决策 #5：仅低危场景自动修复）：</p>
 * <ul>
 *   <li>仅 {@code AUTO_REPAIR_WHITELIST}（DATASOURCE_DISCONNECT / PIPELINE_NODE_RETRY）
 *       两个 alert_type 允许自动修复；空值/格式/PII/一致性等一律 MANUAL 降级</li>
 *   <li>修复动作经注入的 Service/Connector 执行（DS 健康检查走 IDataSourceService，
 *       Pipeline 重跑走 PipelineExecutionService），禁止 new Driver/Connector</li>
 *   <li>修复成功/失败均回写 dq_work_order.repair_status / repair_action / repair_log
 *       并异步 audit（DQ_AUTO_REPAIR）；失败自动降级转 MANUAL</li>
 * </ul>
 *
 * <p>修复在告警分发链内被同步调用（T12 时序），内部自带重试（间隔 10s/30s/60s，
 * 总上限 &le; 101s），不另建 Thread / ScheduledExecutor（铁律 5.1 #14 约束）。</p>
 *
 * <p>Bean 名 {@code ecosDqAutoRepairService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-C T12
 */
public interface DqAutoRepairService {

    /**
     * 尝试对工单执行白名单自动修复（仅 handling_mode=AUTO_REPAIR 的工单有效）。
     *
     * <p>失败/越权场景：repair_status=FAILED 或 DEGRADED + repair_log 记原因，
     * 工单 handling_mode 自动转回 MANUAL；本方法不抛异常（内部分发链不得被中断）。</p>
     *
     * @param workOrderId 工单 ID（dq_work_order.id）
     */
    void tryRepair(String workOrderId);
}
