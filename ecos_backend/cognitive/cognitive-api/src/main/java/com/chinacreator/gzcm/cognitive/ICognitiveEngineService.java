package com.chinacreator.gzcm.cognitive;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.cognitive.model.*;

/**
 * ECOS 认知引擎服务接口。
 *
 * <p>提供六层蓝图健康度、规则推理/因果分析、帕累托优化、执行计划管理等核心能力。
 * 三个推理器（RuleEngine、CausalReasoner、NsgaIIOptimizer）为模块内部组件，
 * 本接口是唯一的对外服务契约。
 *
 * <p>所有方法返回统一的 {@link ApiResponse} 封装。
 */
public interface ICognitiveEngineService {

    /**
     * 获取六层蓝图健康度。
     *
     * @param layer 可选，过滤指定层 L1~L6，传 null 或空字符串返回全部六层
     * @return 蓝图健康度汇总数据
     */
    ApiResponse<BlueprintResponse> getBlueprint(String layer);

    /**
     * 规则推理 / 因果分析。
     *
     * <p>通过 mode 字段选择推理器：{@code "rule"} 使用规则引擎，{@code "causal"} 使用因果推理器。
     *
     * @param request 推理请求（含模式、事实、上下文、选项）
     * @return 推理结果（规则匹配或因果路径）
     */
    ApiResponse<ReasonResponse> reason(ReasonRequest request);

    /**
     * 帕累托多目标优化 (NSGA-II)。
     *
     * @param request 优化请求（含问题定义和算法参数）
     * @return 帕累托前沿解集
     */
    ApiResponse<OptimizeResponse> optimize(OptimizeRequest request);

    /**
     * 创建执行计划。
     *
     * <p>将推理/优化产出编排为可下发给 Hermes 执行的 ExecutionPlan。
     *
     * @param request 计划创建请求（含来源、优先级、目标）
     * @return 创建成功的执行计划
     */
    ApiResponse<ExecutionPlan> createPlan(CreatePlanRequest request);

    /**
     * 查询执行计划详情及当前执行状态。
     *
     * @param planId 执行计划 ID
     * @return 执行计划详情
     */
    ApiResponse<ExecutionPlan> getPlan(String planId);

    /**
     * 认知引擎健康检查。
     *
     * <p>返回三推理器就绪状态，供 Gateway / Prometheus 使用。
     *
     * @return 健康状态
     */
    ApiResponse<HealthResponse> health();
}
