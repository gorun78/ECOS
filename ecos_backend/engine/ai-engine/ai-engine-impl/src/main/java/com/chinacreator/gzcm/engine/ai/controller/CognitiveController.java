package com.chinacreator.gzcm.engine.ai.controller;

import org.springframework.web.bind.annotation.RestController;

/**
 * M0-P0 stub (2026-09-01)
 *
 * <p>原 CognitiveController 引用 pre-existing 已删除的
 * {@code com.chinacreator.gzcm.cognitive.impl.* / .model.*}，gateway 加载
 * 触发 UnsatisfiedDependencyException（已 delete 4 模块后, 真认知在 cognitive-engine）。
 *
 * <p>此前 GatewayApplication excludeFilters 已 exclude 此 Controller Bean。
 * 当前桩版本仅声明 class 不暴露 /api/cognitive/** endpoint,
 * 真实 `/api/cognitive/**` 请求将 404 (按认知引擎 v2 endpoint 路由).
 *
 * <p>相关 C1 清理: 08-产品化重构方案/04-代码与文档清理方案.md → C1-Cognitive-重复-impl
 * 跟踪 Wave-2 ai 重写。
 *
 * @deprecated 临时桩，Wave-2 ai 重写后移除
 */
@Deprecated
@RestController
public class CognitiveController {
    // 当前桩版本无任何字段 / 端点 / Bean 依赖。
}
