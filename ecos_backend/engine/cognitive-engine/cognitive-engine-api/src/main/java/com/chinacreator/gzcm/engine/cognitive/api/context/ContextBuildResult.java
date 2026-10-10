package com.chinacreator.gzcm.engine.cognitive.api.context;

import lombok.Data;

/**
 * Context Builder 响应 DTO（《认知域与智能域补充详细设计 v1.0》§5.2 响应示例）。
 *
 * <p>装配元数据壳：{@link ContextPack} 本体 + digest/TTL/引用锚点。
 * {@code contextDigest} 仅用于关联、回放与一致性校验，不意味着把完整上下文写入认知数据库
 * （§5.2 重要约束）。返回体经 {@code ApiResponse} 统一承载，traceId 走
 * {@code ApiResponse.withTraceIdFromMdc()}（§12.2 统一 trace 维度）。</p>
 */
@Data
public class ContextBuildResult {

    /** 规范化 Pack 摘要（{@code sha256:<64hex>}；对 {@link ContextPack#normalizedDigestView()} 固定序计算）。 */
    private String contextDigest;

    /** Pack 有效时长（毫秒；null ⇒ 服务缺省 300000）。 */
    private Long ttlMillis;

    /** 场景 ID（§12.2）。 */
    private String scenarioId;

    /** 蓝图引用回显（blueprintId + version；Sup-BP-03 回链锚点）。 */
    private ContextBuildRequest.BlueprintRef blueprintRef;

    /** 范围引用回显（scopeRef：tenantId / projectIds / periods / caliberId / asOfTime）。 */
    private ContextBuildRequest.Scope scopeRef;

    /** 请求追踪 ID（回显请求 requestId，与 body traceId 共同构成回放链）。 */
    private String requestId;

    /** Pack 构建完成时点（ISO-8601 UTC）。 */
    private String createdAt;

    /** Pack 过期时点（createdAt + ttlMillis）。 */
    private String expiresAt;

    /** 组装是否被预算裁剪（回显 pack.truncated）。 */
    private boolean truncated;

    /** 是否就绪（回显 pack.ready；false ⇒ 依赖缺失，Run 侧不得继续依赖步骤）。 */
    private boolean ready;

    /** Pack 本体（全部 §5.3 字段）。 */
    private ContextPack pack;
}
