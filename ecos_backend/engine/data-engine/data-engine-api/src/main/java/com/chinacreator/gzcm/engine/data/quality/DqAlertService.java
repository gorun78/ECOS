package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqAlertQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 告警分发服务契约（PMO-48-C T12）。
 *
 * <p>核心能力：</p>
 * <ol>
 *   <li>{@link #dispatchOnRuleCheckFailed} — 规则检查未通过（passed=false / 评估失败）时分发：
 *       severity 映射 P0-P3 → 判重合并（同 rule 5min 内合并 notify_count++）→
 *       写 {@code ecos_dq.dq_alert_record} → P0/P1/P2 经 runtime IAlertService 推送（P3 仅落库）
 *       → P0/P1 自动建工单（DqWorkOrderService）并尝试白名单自动修复（DqAutoRepairService）</li>
 *   <li>告警列表/详情/确认/解决 — REST 端点契约</li>
 * </ol>
 *
 * <p>决策记录：</p>
 * <ul>
 *   <li>决策 #2：P0-P3 全量落库；P0/P1/P2 走 runtime 推送，P3 不推送（待 Phase 4 邮件日报消化）</li>
 *   <li>决策 #6/#7：推送统一走 runtime IAlertService（不直连 IM/Kafka/Producer）</li>
 *   <li>决策 #5：仅低危场景（AUTO_REPAIR_WHITELIST 白名单）自动修复，未命中一律 MANUAL 降级</li>
 * </ul>
 *
 * <p>安全卡（铁律 2.4）：payload 参数经 {@code DqSecurityService.maskParameters} 脱敏；
 * 分发/确认/解决动作异步 audit（DqSecurityService#auditWrite）。</p>
 *
 * <p>Bean 名 {@code ecosDqAlertService}（铁律 1.3 防多 Bean 冲突，不 implements 既有接口）。</p>
 *
 * @author PMO-48-C T12
 */
public interface DqAlertService {

    /**
     * 规则检查未通过（或评估失败）时触发告警分发。
     *
     * <p>内部 try/catch 全隔离：单条失败记 log，不中断上游 runRuleBatch。</p>
     *
     * @param ruleId    规则 ID（dq_rule.id）
     * @param checkId   规则检查记录 ID（dq_rule_check.id，追溯用）
     * @param scopeKind 规则目标类型: TABLE / FIELD / PIPELINE / DATASOURCE（null 安全）
     * @param scopeId   规则目标 ID（null 安全）
     */
    void dispatchOnRuleCheckFailed(String ruleId, String checkId, String scopeKind, String scopeId);

    /**
     * 告警分页列表（level/status/keyword 过滤，created_at 倒序）。
     *
     * @param query 查询条件（全部字段可选）
     * @return 分页结果
     */
    PageResult<DqAlertVO> listAlerts(DqAlertQuery query);

    /**
     * 告警详情。
     *
     * @param id 告警 ID
     * @return 告警 VO；不存在或已删除时返回 null
     */
    DqAlertVO getAlert(String id);

    /**
     * 确认告警（status → ACKED，仅 PENDING/NOTIFIED/ESCALATED 可确认）。
     *
     * @param id    告警 ID
     * @param ackBy 确认人
     */
    void ackAlert(String id, String ackBy);

    /**
     * 解决告警（status → RESOLVED，附处理说明）。
     *
     * @param id         告警 ID
     * @param resolvedBy 处理人
     * @param note       处理说明（可空）
     */
    void resolveAlert(String id, String resolvedBy, String note);
}
