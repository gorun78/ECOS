package com.chinacreator.gzcm.common.error;

/**
 * 平台入口/横切底座（gateway 与 runtime 公共面）错误码常量 —— 《ECOS 详细设计说明书-从零实施版》
 * §3.2.7「统一响应包络与错误码族」中 {@code ECOS-SYS-*} 族的代码侧单源。
 *
 * <p><b>#142/B4-3e-25（差距 D-B4-64）</b>：收口前 gateway 的 404 路由腿与 409 冲突腿把
 * HTTP 状态码的十进制串（{@code "404"}／{@code "409"}）当机读码写进
 * {@code ApiResponse.errorCode}，与 D-B4-55 是同一缺陷形态的另一面值（数字冒充机读码）：
 * 调用方按 {@code errorCode} 匹配码时拿到的是 HTTP 状态的字符串化副本，既不可枚举也不含域语义，
 * 而 {@code code} 字段本来就已经承载了同一个数字。本类把这两枚出口改指登记过的机读码，
 * 并把散落的 {@code "ECOS-SYS-500"} 字面量（收口前全仓 main 面 1 枚）一并收进常量。</p>
 *
 * <p>纪律：</p>
 * <ul>
 *   <li>码值即冻结值，禁改写——前端诊断面与测试按码匹配依赖字面量；</li>
 *   <li>{@code ECOS-SYS-*} 只用于<b>平台入口/兜底</b>语义（路由不存在、唯一约束冲突、未分类内部错误、
 *       下游制品不可用）；域内业务错误码仍归各域码表（{@code ECOS-DATA-*}／{@code ECOS-AI-*} 等），
 *       禁把域错误塞进本族以绕过归属；</li>
 *   <li>本类只含常量，无任何业务 import（与 {@link com.chinacreator.gzcm.common.data.DataDomainErrorCode}
 *       同形态，受 common-api enforcer 约束）。</li>
 * </ul>
 *
 * <p>与 HTTP 状态行的关系：{@code code} 与状态行仍由 handler 决定（§3.2.7「5xx 必须透传」与
 * #142/B4-3e-15 的状态行保真），本族常量只填 {@code errorCode} 字段，不替代数字码。</p>
 */
public final class SystemErrorCode {

    private SystemErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 404 — 真实"路由/资源不存在"（{@code NoHandlerFoundException}／{@code NoResourceFoundException}）。
     * <p>这是唯一合法返回 404 的业务外路径，与 {@code ECOS-DATA-031}（500 数据层异常）严格区分。</p>
     */
    public static final String ECOS_SYS_404 = "ECOS-SYS-404";

    /**
     * 409 — 资源冲突（唯一约束／必填字段违反），客户端重复提交或语义上已存在。
     */
    public static final String ECOS_SYS_409 = "ECOS-SYS-409";

    /**
     * 500 — 未分类内部错误（{@code handleAny} 兜底），响应体只给通用提示 + traceId，堆栈只入日志。
     */
    public static final String ECOS_SYS_500 = "ECOS-SYS-500";
}
