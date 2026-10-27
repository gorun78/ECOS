package com.chinacreator.gzcm.common.error;

/**
 * 认证与准入域（ECOS-AUTH）统一错误码常量 — 详细设计-00 C.2 冻结表（001/002/003/004/005/006/010/011/020）与
 * 详细设计-00 §3.2.7 码表分工约定的 Java 单源。
 *
 * <p>设计约束（详细设计-00-平台入口与横切底座 C.2.5 错误码表 + §3.2.7）：</p>
 * <ul>
 *   <li>{@code ECOS-AUTH-001}（401）＝缺凭证（SecurityConfig 默认 DENY）；</li>
 *   <li>{@code ECOS-AUTH-002/003}（401）＝JWT 签名无效／过期、Token 类型无效；</li>
 *   <li>{@code ECOS-AUTH-004}（401）＝jti 命中黑名单，强制登出；</li>
 *   <li>{@code ECOS-AUTH-005}（503）＝认证依赖不可用（黑名单/权限/租户回查）fail-closed；</li>
 *   <li>{@code ECOS-AUTH-006}（403）＝用户未绑定租户（禁兜底租户）；</li>
 *   <li>{@code ECOS-AUTH-010}（403）＝service 侧缺 {@code X-ECOS-USER/X-ECOS-TENANT} 信任头
 *       （{@code HeaderAuthInterceptor}）；</li>
 *   <li>{@code ECOS-AUTH-011}（403）＝服务凭证格式非法或验签失败（{@code HeaderAuthInterceptor}）；</li>
 *   <li>{@code ECOS-AUTH-020}（403）＝准入等级不足（{@code ClearanceInterceptor}，
 *       文案需含所需等级）。</li>
 * </ul>
 *
 * <p><b>#146m/B4-3e-94 新登</b>（收口差距 <b>P1.1 错误码族系统性漂移</b>中「ECOS-AUTH 序号
 * 002~011 vs 文档状态码口径」子项的字面量副本半）：设计与冻结表早已定档 00x 面（详细设计-00
 * C.2.5 表，历史文档亦在册）；台账差距本句的「文档状态码口径」成指《从零实施版》篇3/4 对
 * {@code ECOS-AUTH-*} 的「401/403/410/503」分态约定——两者并行不悖：00x 是<b>细分语义码</b>，
 * 4xx/5xx 是<b>分态状态行</b>，文档将分态意义归在状态行而不在码号字面重联。而生产面在
 * {@code JwtAuthenticationFilter}（002/003/004/005×3/006 共 7 处）与
 * {@code HeaderAuthInterceptor}（010×1/011×2 共 3 处）与
 * {@code RuntimeAlertV1Controller}（005×3 fail-closed 503 腿）的 13 处出口曾用裸串字面量
 * （与 #146k B4-3e-92 RATE 面／#146l B4-3e-93 DATA/DQ 面「码字面量散落」同型）。本类为
 * 冻结表 9 枚 00x 码的<b>唯一 Java 单源常量持有者</b>，本域其他类禁再复制字面量
 * （判据面由 {@code NoMachineCodeFactoryFaceRatchetTest#authFamilyLiteralsAreSingleSourced}
 * 在 common-api 门禁面零容忍钉死——本批同批扩腿）。本类只含常量，无任何业务 import，
 * 与 {@link RateLimitErrorCode}／{@link SystemErrorCode}／{@code DataDomainErrorCode} 同包同形。</p>
 *
 * <p><b>零新增码表条目</b>：本批只 Java 侧单源化，冻结表 §D.1 一行不动（与 #146k/B4-3e-92 同模）。</p>
 */
public final class AuthErrorCode {

    private AuthErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 401 — 缺凭证。
     *
     * <p>属主出口＝gateway {@code SecurityConfig} 默认 DENY 面（当前生产链路走 4xx 状态行
     * 由 {@code Spring Security} 内建处理，本常量预留给跨 JVM 对账的 errorCode 字段
     * 下游消费方；现无生产字面量 exit 点与之绑定，本枚是冻结表在册码的<b>在册登记半边</b>，
     * 与 {@code RateLimitErrorCode} 处境不同—that 是 429 两枚退出腿先落地后收口，本枚是
     * 退出腿在 SecurityConfig 里未做显式 errorCode 键的「未在出口填充」面（非本批处置面，
     * 与 D-B4-65「机读码干脆没填」同族同类，等后续批次处置 Spring Security 出口）。</p>
     */
    public static final String ECOS_AUTH_001 = "ECOS-AUTH-001";

    /**
     * 401 — JWT 签名无效/过期。
     *
     * <p>属主出口＝sysman-impl {@code JwtAuthenticationFilter#doFilterInternal}
     * {@code validateToken} 异常分支。状态行＝HTTP 401；响应包体 {@code code} 沿用 401
     * （HTTP 语义与包体同号，与 {@code SystemErrorCode} 便面规则保持一致）。</p>
     */
    public static final String ECOS_AUTH_002 = "ECOS-AUTH-002";

    /**
     * 401 — Token 类型无效（需 access）。
     *
     * <p>属主出口＝sysman-impl {@code JwtAuthenticationFilter#doFilterInternal}
     * {@code !"access".equals(tokenType)} 分支（详细设计-00 W06 面，非 Bearer 形态/type 错
     * 均折算 401）。</p>
     */
    public static final String ECOS_AUTH_003 = "ECOS-AUTH-003";

    /**
     * 401 — jti 命中黑名单（强制登出）。
     *
     * <p>属主出口＝sysman-impl {@code JwtAuthenticationFilter#doFilterInternal}
     * {@code isTokenBlacklisted} 真分支。</p>
     */
    public static final String ECOS_AUTH_004 = "ECOS-AUTH-004";

    /**
     * 503 — 认证依赖不可用（fail-closed）。
     *
     * <p>属主出口＝三处：a) {@code JwtAuthenticationFilter} 黑名单查询
     * {@code DataAccessException} 腿；b) {@code JwtAuthenticationFilter}
     * {@code loadUserPermissions} 数据访问异常腿；c) {@code JwtAuthenticationFilter}
     * 租户回查 {@code TenantLookup stub} 异常腿；d) gateway {@code RuntimeAlertV1Controller}
     * list/alerts/{id}/ack/close／dlq/replay 四腿 fail-closed（与 {@code AclEngine}／
     * {@code UserService} 不可用腿同型）。状态行＝HTTP 503。</p>
     */
    public static final String ECOS_AUTH_005 = "ECOS-AUTH-005";

    /**
     * 403 — 用户未绑定租户（禁兜底租户）。
     *
     * <p>属主出口＝sysman-impl {@code JwtAuthenticationFilter#doFilterInternal} 的
     * 回查未命中且非 claims 源分支（详细设计-00 W06：禁用旧 {@code "tenant-a"} 兜底、
     * 显式拒绝，避免「默认租户」越权面）。</p>
     */
    public static final String ECOS_AUTH_006 = "ECOS-AUTH-006";

    /**
     * 403 — service 侧缺 {@code X-ECOS-USER/X-ECOS-TENANT} 信任头。
     *
     * <p>属主出口＝common-api {@code HeaderAuthInterceptor#preHandle} 头校验失败分支
     * 与 {@code DecisionController}「无用户上下文拒绝裁决」腿（详设-02 C.2.2 契约）。</p>
     */
    public static final String ECOS_AUTH_010 = "ECOS-AUTH-010";

    /**
     * 403 — 服务凭证格式非法或验签失败。
     *
     * <p>属主出口＝common-api {@code HeaderAuthInterceptor#preHandle} 的
     * {@code serviceHeader} 格式与 Hmac 验签两分支（详设-02 C.2.2 契约）。</p>
     */
    public static final String ECOS_AUTH_011 = "ECOS-AUTH-011";

    /**
     * 403 — 准入等级不足（文案需含所需等级，详设-00 明文）。
     *
     * <p>属主出口＝澄清拦截链后续批次在别处落地（ClearanceInterceptor 挂 gateway
     * 已在基线成型，字面量面由各自域的 {@code RlsExecutionContract} 常量承载、不重复到本域——
     * 若后续批走闭合 {@code CLEARANCE} 常量化，参考本枚脸色码对齐 {@code 403} 状态行）。
     * 本枚是<b>冻结表在册码的在册登记半边</b>。</p>
     */
    public static final String ECOS_AUTH_020 = "ECOS-AUTH-020";
}
