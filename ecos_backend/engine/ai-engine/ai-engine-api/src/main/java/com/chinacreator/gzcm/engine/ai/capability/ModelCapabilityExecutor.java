package com.chinacreator.gzcm.engine.ai.capability;

/**
 * Model & Capability Center 统一执行契约（《补充设计 v1.0》§10.4，方法名/签名按原文冻结）。
 *
 * <p><b>逻辑契约</b>（§10.4 末段）：不要求新建 Maven 模块——实现落在
 * {@code ai-engine-impl} 的 capability 包，宿主 aiming/gateway 聚合加载。</p>
 *
 * <ul>
 *   <li>{@link #execute(CapabilityRequest)} — 同步小任务直接执行（60s 档预算内，§4.4 同口径）；</li>
 *   <li>{@link #submit(CapabilityRequest)} — 大文件 OCR / 批量语音转写等<b>异步</b>提交：
 *       一律经 runtime-task（{@code ITaskManagementService}），返回句柄携带 taskId
 *       （SUP-GOV-02：引擎禁自建调度器/裸线程，ArchUnit 守卫）；</li>
 *   <li>{@link #getStatus(String)} — 台账（V269）只读投影回显，终态前质量字段可空，禁伪造。</li>
 * </ul>
 *
 * <p>LLM/Embedding 唯一出口仍是 {@code runtime/llm-gateway}（铁律 §2.5）：本接口只做
 * 能力路由/台账/状态对账，<b>不</b>内嵌第二出口。每次调用必带 traceId +
 * modelRef/modelVersion 进台账（§12.2 / SUP-MODEL-02）。</p>
 */
public interface ModelCapabilityExecutor {

    /**
     * 同步执行指定能力（小任务直执）。
     *
     * @param request 能力请求（capabilityId + inputRef 必填）
     * @return 统一结果结构（§10.5 冻结字段；降级不满足质量门槛必须 DEGRADED/FAILED）
     */
    CapabilityResult execute(CapabilityRequest request);

    /**
     * 异步提交长耗时能力任务（§10.3 末段：经 runtime-task 提交，返回 taskId）。
     *
     * @param request 能力请求
     * @return 异步句柄（taskId + 受理时即载 traceId，初态 SUBMITTED）
     */
    AsyncCapabilityHandle submit(CapabilityRequest request);

    /**
     * 查询异步任务状态（台账 V269 只读投影）。
     *
     * @param taskId {@link #submit} 返回的 runtime-task 任务 ID
     * @return 状态回执；任务不存在时返回 null（调用方映射 404，禁伪空成功）
     */
    CapabilityStatus getStatus(String taskId);
}
