package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 工单服务契约（PMO-48-C T12 骨架）。
 *
 * <p>T12 范围（最小集）：</p>
 * <ol>
 *   <li>{@link #createWorkOrder} — 告警（P0/P1）自动建工单：order_no 生成
 *       {@code WO-yyyyMMdd-NNN}（当日序号），handling_mode 由调用方（告警分发器）
 *       按自动修复白名单判定（AUTO_REPAIR / MANUAL），工单创建后不立即分配</li>
 *   <li>{@link #listWorkOrders} / {@link #getWorkOrder} — 列表/详情查询</li>
 * </ol>
 *
 * <p>状态机转换（assign/start/resolve/verify/close）由 T13 在同一实现上扩展，本骨架
 * 不提供状态机方法避免重复实现。</p>
 *
 * <p>安全卡（铁律 2.4 #5）：工单创建/查询走 {@code DqSecurityService} 异步审计。</p>
 *
 * <p>Bean 名 {@code ecosDqWorkOrderService}（铁律 1.3 防多 Bean 冲突，不 implements 既有接口）。</p>
 *
 * @author PMO-48-C T12
 */
public interface DqWorkOrderService {

    /**
     * 由告警创建工单（P0/P1 告警自动触发）。
     *
     * @param alert 告警 VO（含 alertType/alertLevel/message 等）
     * @param rule  触发告警的规则 VO（severity/ruleType/target* 等）
     * @return 新工单 ID（dq_work_order.id，VARCHAR(64)）
     */
    String createWorkOrder(DqAlertVO alert, DqRuleVO rule);

    /**
     * 工单分页列表（status/severity/handlingMode 过滤，created_at 倒序）。
     *
     * @param query 查询条件（全部字段可选）
     * @return 分页结果
     */
    PageResult<DqWorkOrderVO> listWorkOrders(DqWorkOrderQuery query);

    /**
     * 工单详情。
     *
     * @param id 工单 ID
     * @return 工单 VO；不存在或已删除时返回 null
     */
    DqWorkOrderVO getWorkOrder(String id);

    // ==================== T13 状态机扩展（7 态：PENDING → ASSIGNED → IN_WORK → RESOLVED → VERIFIED → CLOSED；任意 → REJECTED 除外） ====================

    /**
     * T13 状态机：指派工单（PENDING → ASSIGNED）。
     *
     * <p>校验：当前状态必须 PENDING；assignedTo 必填。
     * 校验通过后写 assigned_to/assigned_at/status，并异步 audit（DQ_WO_ASSIGN）。</p>
     *
     * @param workOrderId 工单 ID
     * @param assignedTo  指派人（业务账号）
     * @param assigner    指派操作人（审计留痕）
     */
    void assign(String workOrderId, String assignedTo, String assigner);

    /**
     * T13 状态机：开始处理工单（ASSIGNED → IN_WORK）。
     *
     * <p>severity ∈ {CRITICAL, HIGH}（对应 P0/P1）时异步触发自动 RCA：调
     * {@code DqRcaService.runDiagnose}（stub 当前 confidence=0.0）写 rca_result /
     * rca_confidence / rca_analyzed_at，失败不阻塞 IN_WORK 落地。severity ∈ {MEDIUM, LOW}
     * 不触发自动 RCA（手动 RCA 走 {@link #runRca}）。</p>
     *
     * @param workOrderId 工单 ID
     * @param operator    操作人
     */
    void startWork(String workOrderId, String operator);

    /**
     * T13 状态机：完成修复（IN_WORK → RESOLVED）。
     *
     * @param workOrderId  工单 ID
     * @param resolvedBy   处理人
     * @param resolutionNote 处理说明（可空）
     */
    void resolve(String workOrderId, String resolvedBy, String resolutionNote);

    /**
     * T13 状态机：验证修复结果（RESOLVED → VERIFIED 或 RESOLVED → IN_WORK）。
     *
     * <p>pass=true  → status=VERIFIED  + retry_count 不变 + verify_pass=TRUE</p>
     * <p>pass=false → status=IN_WORK   + retry_count++ + verify_pass=FALSE
     *                  + retry_count &gt;= 3 时强制 handling_mode=MANUAL（覆盖 AUTO_REPAIR）</p>
     *
     * @param workOrderId 工单 ID
     * @param verifiedBy  验证人
     * @param pass        验证通过与否
     * @param note        验证备注（失败原因 / 通过说明）
     */
    void verify(String workOrderId, String verifiedBy, boolean pass, String note);

    /**
     * T13 状态机：关闭工单（VERIFIED → CLOSED）。
     *
     * @param workOrderId 工单 ID
     * @param closer      关闭人
     */
    void close(String workOrderId, String closer);

    /**
     * T13：驳回工单。
     *
     * <p>状态白名单：PENDING / ASSIGNED / IN_WORK / RESOLVED（终态 VERIFIED/CLOSED/REJECTED 不允许驳回）。</p>
     *
     * @param workOrderId 工单 ID
     * @param rejector    驳回人
     * @param reason      驳回原因
     */
    void reject(String workOrderId, String rejector, String reason);

    /**
     * T13 升级策略：PENDING 工单 5 分钟未认领自动升级。
     *
     * <p>severity ∈ {MEDIUM, LOW} → severity=HIGH（P1）；severity=HIGH → severity=CRITICAL（P0）；
     * severity=CRITICAL 已是最高级不升级。仅 PENDING 状态生效，提前返回不报错。
     * 自动化失败时留痕 error 日志。</p>
     *
     * @param workOrderId 工单 ID
     * @return 实际发生升级时返回 true；未触发升级（非 PENDING / 已 CRITICAL / 未超 5min）返回 false
     */
    boolean escalateUnack(String workOrderId);

    /**
     * T13 手动 RCA（P2/P3 工单详情页按钮触发）。
     *
     * <p>调 {@code DqRcaService.runDiagnose} 返回结果写入 rca_result JSONB + rca_confidence +
     * rca_analyzed_at。不校验工单 status（任何状态都允许手动 RCA）。
     * 自动 RCA（IN_WORK 触发）失败时同样调用此方法。</p>
     *
     * @param workOrderId 工单 ID
     * @return RCA 结果 JSON（写入工单后的副本，stub 阶段 confidence=0.0）
     */
    Map<String, Object> runRca(String workOrderId);

    /**
     * T13 风险工单排行（统计端点）。
     *
     * @param grade 严重级别（CRITICAL / HIGH / MEDIUM / LOW）；不传则按全部 severity 统计，按 severity 优先级 + 创建时间倒序
     * @param limit 返回上限（&gt;0，缺省 10，上限 100）
     * @return 排行结果：top N 工单摘要列表
     */
    List<Map<String, Object>> riskRanking(String grade, int limit);
}
