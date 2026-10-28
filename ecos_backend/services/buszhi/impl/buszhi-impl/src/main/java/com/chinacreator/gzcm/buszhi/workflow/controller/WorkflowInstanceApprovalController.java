package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.WorkflowErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalRepository;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceRepository;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskEntity;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskRepository;

/**
 * 契约路径审批面 —— 详设《从零实施版》§6.3.3 第 6 篇 / 附录A 第 10 条
 * {@code POST /api/v1/ontology/workflow-instances/{id}/approvals}（#146y，差距分析 v1.0.198）。
 *
 * <p><b>本类存在的理由（偏差型别名收口）</b>：契约把审批动作挂在<b>实例</b>路径上
 * （operationId {@code approveWorkflowTask}），既存实现却挂在<b>任务</b>路径上
 * （{@code WorkflowApprovalController} 的 {@code /api/v1/ecos/approvals/{taskId}/approve}）。
 * 台账附录A 第 10 条原判「偏差」即此。按 API 只增不改红线（AGENTS.md「What Not To Do」+
 * 铁律），既存任务面一字未动，本类以实例路径为<b>新承流面</b>补齐契约字面，
 * 并复用同一份属主业务逻辑（{@link WorkflowApprovalService#approve} 未改一行）。</p>
 *
 * <p><b>契约列逐条落点</b>（§6.3.3 表行：L2｜服务端解析审批人｜审批行｜ECOS-WF-020）：
 * <ol>
 *   <li><b>服务端解析审批人</b>：调用方在 body 里传的 {@code userId}/{@code approver}
 *       <b>一律丢弃</b>（{@link #stripClientSuppliedActor}），审批人由本面解析出的任务
 *       {@code assignee} 决定 —— 属主 {@code WorkflowApprovalService} 在 body 无 {@code userId}
 *       时正是回落 {@code task.getAssignee()}，故「服务端解析」由「不接受客户端自报身份」实现，
 *       不需要新代码。<b>余项（登记不掩盖）</b>：{@code WorkflowActorContext.setActor}
 *       在全仓 main 侧零调用点，即「取当前登录人」尚未接线；本面在任务未指派审批人时
 *       选择 <b>404 fail-loud 拒绝</b>而不是编一个 {@code system} 主语，
 *       接线缺口留待身份链路批次收口。</li>
 *   <li><b>审批行</b>：写入成功后按 {@code approvalId} 读回 {@link WorkflowApprovalEntity}
 *       并整行返回（{@link #toApprovalRow}），不返回「空列表／空对象」。</li>
 *   <li><b>ECOS-WF-020 → 500</b>：审批链任一环（实例读、任务读、审批写、审批读回）抛
 *       Spring {@link DataAccessException} 时，本面改抛携带 {@code 500} 的
 *       {@link DataBridgeException}，message 以 {@code "ECOS-WF-020: 审批表不可达（schema 漂移）"}
 *       <b>开头</b>（{@code MachineErrorCode.orNumeric} 只在串首取码，位置是契约语义的一部分）。
 *       单源常量为 {@link WorkflowErrorCode#ECOS_WF_020}。
 *       <br>为什么在本面 catch：gateway {@code GlobalExceptionHandler#handleSpringDataAccess}
 *       会把裸 {@code DataAccessException} 落成 {@code ECOS-DATA-031}，与契约要求的
 *       {@code ECOS-WF-020} 不同码 ⇒ 不拦截就等于把审批链断裂报成数据域故障。
 *       <br>为什么 catch 覆盖整条链而非只包审批表：W85 根因是同一次 V173 迁移把
 *       workflow 整族表搬进控制域 schema，「实例读不到／审批行读不回」与「审批表不可达」
 *       是同一结构故障的不同表现，§6.5 状态矩阵对它们的统一约束只有一条 ——
 *       <b>审批链断裂绝不折叠成空列表</b>，故一律 500 携码，禁 200 空面。</li>
 * </ol></p>
 *
 * <p><b>实例→任务解析口径</b>：契约路径以 {@code instanceId} 为唯一键，而审批动作的作用对象是任务，
 * 故本面按「该实例<b>最早</b>一条仍可审批（{@code New}/{@code Assigned}）的任务」解析
 * （{@code WorkflowTaskDao#findByInstanceId} 已按 {@code created_at} 升序返回）。
 * 解析不到即 404（沿用既存词汇 {@code WF-009}，不新造码值）；一条实例下存在多枚待审批任务时
 * 本面只消费最早一枚，多次审批须多次调用 —— 这是契约路径（实例级）相对既存路径（任务级）
 * 的<b>信息收窄</b>，登记为余项：若要把并发多审批人也覆盖，须让契约补 {@code taskId} 参数，
 * 属文档侧改动，不在纯代码切片内。</p>
 *
 * <p><b>不主张可达性</b>：本类只做映射字面与语义实装。契约前缀 {@code /api/v1/ontology}
 * 在 {@code route-manifest.json} 里 owner=buszhi 且 {@code route=monolith}（未切流），
 * 故现网实际承流点是 gateway fat-JAR（{@code GlobalExceptionHandler} 在位，状态行保真）；
 * buszhi 独立进程 :18083 无 advice 类，裸抛型异常在该部署形态下只落 Spring 默认 500 —— 
 * 属既存部署面事实，本批不改（登记为余项）。</p>
 */
@RestController
@RequestMapping("/api/v1/ontology/workflow-instances")
public class WorkflowInstanceApprovalController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceApprovalController.class);

    /** 审批表不可达（schema 漂移）—— 契约 §6.3.3 原文文案，机读码必须位于串首。 */
    private static final String APPROVAL_TABLE_UNREACHABLE =
            WorkflowErrorCode.ECOS_WF_020 + ": 审批表不可达（schema 漂移）";

    /** 仍可审批的任务状态（属主词汇：{@code New}/{@code Assigned}；其余为终态或转签态）。 */
    private static final List<String> APPROVABLE_TASK_STATUS = List.of("New", "Assigned");

    /** 客户端自报审批身份的键 —— 契约要求服务端解析，故进本面即丢弃。 */
    private static final List<String> CLIENT_SUPPLIED_ACTOR_KEYS = List.of("userId", "approver");

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTaskRepository taskRepo;
    private final WorkflowApprovalRepository approvalRepo;
    private final WorkflowApprovalService approvalService;

    public WorkflowInstanceApprovalController(WorkflowInstanceRepository instanceRepo,
                                              WorkflowTaskRepository taskRepo,
                                              WorkflowApprovalRepository approvalRepo,
                                              WorkflowApprovalService approvalService) {
        this.instanceRepo = instanceRepo;
        this.taskRepo = taskRepo;
        this.approvalRepo = approvalRepo;
        this.approvalService = approvalService;
    }

    /**
     * 实例级审批动作（operationId {@code approveWorkflowTask}）。
     *
     * @param instanceId 路径变量 {@code {id}}，工作流实例 id
     * @param body       审批意见载荷（可空）；其中 {@code userId}/{@code approver} 一律丢弃
     * @return 审批行（{@code {id,taskId,instanceId,approver,decision,opinion,formData,createdAt}}）
     */
    @PostMapping("/{instanceId}/approvals")
    public ApiResponse<Map<String, Object>> approveWorkflowInstance(
            @PathVariable("instanceId") String instanceId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> sanitized = stripClientSuppliedActor(body);
        String taskId;
        String approvalId;

        try {
            Optional<WorkflowInstanceEntity> instance = instanceRepo.findById(instanceId);
            if (instance.isEmpty()) {
                throw new NotFoundException("WF-009: 实例 " + instanceId + " 不存在");
            }
            WorkflowTaskEntity task = resolveApprovableTask(instanceId);
            if (task.getAssignee() == null || task.getAssignee().isBlank()) {
                throw new NotFoundException("WF-009: 任务 " + task.getId()
                        + " 未指派审批人，服务端无法解析审批主语");
            }
            taskId = task.getId();
            approvalId = String.valueOf(approvalService.approve(taskId, sanitized).get("approvalId"));
        } catch (DataAccessException ex) {
            throw approvalChainUnreachable(ex);
        }

        WorkflowApprovalEntity row;
        try {
            row = approvalRepo.findById(approvalId)
                    .orElseThrow(() -> approvalChainUnreachable(null));
        } catch (DataAccessException ex) {
            throw approvalChainUnreachable(ex);
        }

        log.info("Contract-path approval recorded: instance={} task={} approval={}",
                instanceId, taskId, approvalId);
        return ApiResponse.success(toApprovalRow(row));
    }

    // ── 内部 ───────────────────────────────────────────

    /** 最早一条仍可审批的任务；无则 404（禁把「审批链无对象」写成 200 空面）。 */
    private WorkflowTaskEntity resolveApprovableTask(String instanceId) {
        return taskRepo.findByInstanceId(instanceId).stream()
                .filter(t -> APPROVABLE_TASK_STATUS.contains(t.getStatus()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "WF-009: 实例 " + instanceId + " 无可审批任务（无 New/Assigned 状态任务）"));
    }

    /** 丢弃客户端自报的审批身份键，其余键原样透传给属主服务（只删不改＝additive 语义收窄）。 */
    private static Map<String, Object> stripClientSuppliedActor(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (body != null) {
            body.forEach((k, v) -> {
                if (!CLIENT_SUPPLIED_ACTOR_KEYS.contains(k)) {
                    copy.put(k, v);
                }
            });
        }
        return copy;
    }

    /** 审批链断裂 → 500 携 {@code ECOS-WF-020}；{@code cause} 可为 null（读回空行无底层异常）。 */
    private static DataBridgeException approvalChainUnreachable(DataAccessException cause) {
        return cause == null
                ? new DataBridgeException(500, 500, APPROVAL_TABLE_UNREACHABLE)
                : new DataBridgeException(500, 500, APPROVAL_TABLE_UNREACHABLE, cause);
    }

    /** 审批行 → 响应体（字段名与实体列同名，不额外造词）。 */
    private static Map<String, Object> toApprovalRow(WorkflowApprovalEntity row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("taskId", row.getTaskId());
        out.put("instanceId", row.getInstanceId());
        out.put("approver", row.getApprover());
        out.put("decision", row.getDecision());
        out.put("opinion", row.getOpinion());
        out.put("formData", row.getFormData());
        out.put("createdAt", row.getCreatedAt());
        return out;
    }
}
