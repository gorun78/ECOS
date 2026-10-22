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
     * 400 — 入口面请求解析／校验失败（{@code MethodArgumentNotValidException}／
     * {@code NumberFormatException}／{@code HttpMessageNotReadableException} 兜底腿）。
     * <p>#142/B4-3e-27（差距 D-B4-65）按 §3.2.7 表内既有形态<b>追加登记</b>。
     * 只承载<b>平台入口</b>语义：域内参数校验错误仍归各域码表，禁借本码绕过归属。</p>
     */
    public static final String ECOS_SYS_400 = "ECOS-SYS-400";

    /**
     * 401 — 入口兜底"未认证"（Token 缺失／无效／过期，未细分为具体认证缺陷时）。
     * <p>#142/B4-3e-27 追加登记。与 {@code ECOS-AUTH-00x} 序列码的分工：后者是
     * {@code JwtAuthenticationFilter} 已具名的细分缺陷码（缺 Token／过期／验签失败…），
     * 本码只给「已到入口兜底腿、无细分」的场景；编号形态不同属两族既有事实，
     * 族归并在册待裁（见差距台账 D-B4-65 余项），禁在本批把 {@code ECOS-AUTH-00x} 改号。</p>
     */
    public static final String ECOS_SYS_401 = "ECOS-SYS-401";

    /**
     * 403 — 入口兜底"无访问权限"（{@code ForbiddenException}／{@code AccessDeniedException}）。
     * <p>#142/B4-3e-27 追加登记。<b>不</b>用于 ABAC／OPA 的策略拒绝：那类必须带
     * {@code ECOS-SEC-*}（§4.3.3），把策略拒绝写成平台 403 ＝抹掉"谁拒的"这一层归因。</p>
     */
    public static final String ECOS_SYS_403 = "ECOS-SYS-403";

    /**
     * 409 — 资源冲突（唯一约束／必填字段违反），客户端重复提交或语义上已存在。
     */
    public static final String ECOS_SYS_409 = "ECOS-SYS-409";

    /**
     * 500 — 未分类内部错误（{@code handleAny} 兜底），响应体只给通用提示 + traceId，堆栈只入日志。
     */
    public static final String ECOS_SYS_500 = "ECOS-SYS-500";

    /**
     * 503 — 下游制品不可用（§3.2.7 表内既有登记；代码侧此前只存在于
     * {@code SystemEventTopicsV1Controller.ERROR_SYS_UNAVAILABLE} 的一处字面量）。
     * <p>#142/B4-3e-27 把该码登记进本族并让上述副本改指本常量，族码字面量自此在
     * {@code src/main/java} 面只允许本类承载（由 {@code NoMachineCodeFactoryFaceRatchetTest}
     * 的零容忍腿钉住）。§4.7.2「不可用 ≠ 无权限」要求这条腿与 {@link #ECOS_SYS_403}
     * 分形，禁互相折叠。</p>
     */
    public static final String ECOS_SYS_503 = "ECOS-SYS-503";
}
