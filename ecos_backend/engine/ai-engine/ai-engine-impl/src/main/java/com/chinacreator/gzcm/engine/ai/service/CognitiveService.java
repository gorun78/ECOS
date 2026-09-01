package com.chinacreator.gzcm.engine.ai.service;

import org.springframework.stereotype.Service;

/**
 * M0-P0 stub (2026-09-01)
 *
 * <p>原 CognitiveService 引用 pre-existing 已删除的
 * {@code com.chinacreator.gzcm.cognitive.impl.RuleEngine / CausalReasoner / NsgaIIOptimizer}
 * 与 {@code cognitive.model.*}（现仅存于 ~/.m2 stale JAR, 13 模块基线已删除）。
 * gateway 加载会触发 UnsatisfiedDependencyException。
 *
 * <p>真实认知能力在 cognitive-engine
 * ({@code com.chinacreator.gzcm.engine.cognitive2.*）：
 * <ul>
 *   <li>入口: {@code cognitive2.api.CausalReasonerService}</li>
 *   <li>请求: {@code cognitive2.model.DiagnosisRequest}</li>
 *   <li>响应: {@code cognitive2.model.ReasoningStep / CausalChainNode / Decision}</li>
 * </ul>
 *
 * <p>相关 C1 清理工作: 08-产品化重构方案/04-代码与文档清理方案.md → C1-Cognitive-重复-impl
 * 跟踪 Wave-2 ai 重写（新建 ai-engine 的 CognitiveController v2,
 * 直接改用 cognitive-engine 接口）。
 *
 * @deprecated 临时桩，Wave-2 ai 重写后移除
 */
@Deprecated
@Service
public class CognitiveService {
    // 当前桩版本无任何字段 / 方法 / Bean 依赖；gateway excludeFilters 已 exclude 此 Bean.
    // 创建此桩是为了让 ai-engine-impl 模块可独立编译 (无 cognitive-impl 依赖)。
}
