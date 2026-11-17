package com.chinacreator.gzcm.common.security.caller;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 调用方凭证载体的<b>单源</b>（#142/B4-3e-21，收口 <b>D-B4-61</b>「身份载体无单源」）。
 *
 * <p><b>差距形态（实测，不是推断）</b>：合流前「从当前 HTTP 请求里取出调用方凭证、
 * 逐字中继到出站引擎调用」这件事在仓里有 <b>6 个各自成形的实现面</b>——
 * workspace {@code CallerCredentialRelay}、workspace {@code WorkspaceSecurityEngineClient
 * #relayCallerCredentials}、data-engine {@code PipelineSecurityService#relayCallerCredentials}、
 * cognitive {@code SecurityPolicyReviewRoleDecider#currentAuthorization}、
 * ontology {@code DatNetLineageGuard.RestLineageColumnFetcher#currentInboundAuthorization}、
 * agent-service {@code QueryPhysicalTableTool#currentAuthorizationHeader}。
 * 前三支是<b>逐字节同文</b>的复制（{@code Authorization} + 四枚信任头），后三支只取
 * {@code Authorization}。判据分叉的落点是真实的：同一条「无请求上下文」的分支，
 * workspace/data 两支<b>静默不加头</b>（交由属主默认 DENY），cognitive 一支把
 * {@code null} 翻成<b>显式不可用（503）</b>——它明确拒绝把「取不到凭证」读成「策略拒绝」，
 * 而其余三支做不到这个区分。复制体各改各的 ⇒ 任何一支漏了头、或加了兜底主语，
 * 就成了那条调用的实际语义。</p>
 *
 * <p><b>本单源钉的三条纪律</b>：
 * ①<b>不伪造主语</b>：取不到就是 {@link Carrier#EMPTY}／{@code null}，不设任何默认
 * user／tenant／role，不做「anonymous」兜底（§2.4-6 fail-closed）；
 * ②<b>逐字中继、不升权</b>：只把入口请求<b>已带</b>的头原样写进请求头，不拼接、不改写、
 * 空白值按「未携带」处理（发出空值头会让被调方验签器把它当"有凭证但坏了"，
 * 与"无凭证"是两个不同的拒绝态）；
 * ③<b>缺失态可分</b>：{@link Carrier#present()} 让调用方能区分「没有凭证」与
 * 「有凭证但被拒」，从而把传输缺口报成不可用而不是 DENY（§4.7.2「不可用 ≠ 无权限」）。</p>
 *
 * <p><b>载体本身仍是线程绑定的请求上下文</b>：{@link RequestContextHolder} 只在同步请求线程
 * 上有值，异步／{@code @Async}／定时／SSE 执行线程取不到 ⇒ 一律 {@code EMPTY} ⇒ 属主端默认 DENY。
 * 这是<b>有意保留的 fail-closed</b>，不是漏判；要打通异步腿的身份，只有一条路：
 * 请求线程用 {@link #current()} 取一次，工作线程在 {@link #runWith} 的作用域内执行，
 * <b>作用域关闭即清</b>。刻意<b>不</b>做线程继承（{@code InheritableThreadLocal}）：
 * 线程池复用会让上一位调用方的凭证泄给下一位任务，比"异步腿取不到凭证"严重得多。</p>
 *
 * <p><b>面外登记（不在本单源覆盖内，理由逐条）</b>：入站认证器（sysman
 * {@code JwtAuthenticationFilter}）读 {@code Authorization} 是<b>验签入口</b>而非中继，
 * 属主面不同；gateway {@code RateLimitFilter} 读它是取限流键，不是安全主语；
 * 审计拦截器读 {@code X-User-Id} 并以 {@code "anonymous"} 兜底是<b>审计标注</b>面，
 * 与本门的裁决主语无关但已在 {@code CallerCredentialSourceSingleSourceTest} 的余面基线里
 * 量化登记。凭证等级＝单元级，非 live（dev 策略表零行的前置见 D-B4-19）。</p>
 */
public final class CallerCredentialSource {

    private CallerCredentialSource() {
    }

    /**
     * 一次取用的凭证载体。字段为 {@code null} 的含义<b>只有一个</b>：入口请求没带这个头。
     *
     * @param authorization {@code Authorization} 原值（已 trim，未携带为 {@code null}）
     * @param user          {@code X-ECOS-USER} 信任头
     * @param role          {@code X-ECOS-ROLE} 信任头
     * @param tenant        {@code X-ECOS-TENANT} 信任头
     * @param domain        {@code X-ECOS-DOMAIN} 信任头
     */
    public record Carrier(String authorization, String user, String role, String tenant, String domain) {

        /** 无请求上下文／全未携带时的唯一取值——显式空，不是"猜一个"。 */
        public static final Carrier EMPTY = new Carrier(null, null, null, null, null);

        /** 是否至少取到一枚凭证头（{@code false} ＝ 本腿没有主语，调用方须按缺失态分派）。 */
        public boolean present() {
            return authorization != null || user != null || role != null || tenant != null || domain != null;
        }

        /** 按头名取载体字段（供只关心单头的调用方，避免各处再写一遍映射）。 */
        public String valueOf(String header) {
            if (HttpHeaders.AUTHORIZATION.equals(header)) {
                return authorization;
            }
            if (HeaderAuthInterceptor.H_USER.equals(header)) {
                return user;
            }
            if (HeaderAuthInterceptor.H_ROLE.equals(header)) {
                return role;
            }
            if (HeaderAuthInterceptor.H_TENANT.equals(header)) {
                return tenant;
            }
            if (HeaderAuthInterceptor.H_DOMAIN.equals(header)) {
                return domain;
            }
            throw new IllegalArgumentException("非受控凭证头，本单源不承载: " + header);
        }
    }

    /**
     * 工作线程上的显式载体作用域（唯一的跨线程传递通道）。
     *
     * <p>只在 {@link #runWith} 的作用域内非空，且作用域退出时<b>必清</b>
     * （含异常路径）。不用 {@code InheritableThreadLocal}：池线程被复用时上一位调用方的
     * 凭证会泄给下一位任务。</p>
     */
    private static final ThreadLocal<Carrier> SCOPED = new ThreadLocal<>();

    /**
     * 在指定载体的作用域内执行一段无返回值的工作（异步腿的唯一正确用法）。
     *
     * <p><b>用法纪律</b>：载体必须在<b>请求线程</b>用 {@link #current()} 取，
     * 再传进工作线程——工作线程自己取只会得到 {@link Carrier#EMPTY}。
     * 作用域结束即恢复上一层的值（支持嵌套），不给池线程留残留。</p>
     */
    public static void runWith(Carrier carrier, Runnable action) {
        Carrier previous = SCOPED.get();
        SCOPED.set(carrier == null ? Carrier.EMPTY : carrier);
        try {
            action.run();
        } finally {
            if (previous == null) {
                SCOPED.remove();
            } else {
                SCOPED.set(previous);
            }
        }
    }

    /**
     * 从<b>当前线程绑定的</b>请求上下文取调用方凭证；若本线程已被 {@link #runWith}
     * 绑定了显式载体，则以该载体为准（工作线程无请求上下文可读）。
     *
     * @return 载体；无请求上下文（且未绑定作用域）返回 {@link Carrier#EMPTY}，<b>绝不返回 {@code null}</b>
     */
    public static Carrier current() {
        Carrier scoped = SCOPED.get();
        if (scoped != null) {
            return scoped;
        }
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return Carrier.EMPTY;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        Carrier fromHeaders = new Carrier(
                header(request, HttpHeaders.AUTHORIZATION),
                header(request, HeaderAuthInterceptor.H_USER),
                header(request, HeaderAuthInterceptor.H_ROLE),
                header(request, HeaderAuthInterceptor.H_TENANT),
                header(request, HeaderAuthInterceptor.H_DOMAIN));
        // D112（2026-11-15 活体实证）：信任头按字段回落，**不能**整包短路。
        // 原实现 `present()` 为真即原样返回——但认证请求几乎总带 Authorization，
        // 于是 user/tenant 两枚永不走到回落，出站 rls/apply 恒缺信任头（403 ECOS-AUTH-010）。
        // 剥离开销由入口保证：X-ECOS-* 只会被 EcosTrustHeaderFilter 剥离（伪造面），
        // Authorization 不在剥离集合内 ⇒ 它是真实入口凭证，逐字中继安全。
        // 身份写入点见 sysman JwtAuthenticationFilter（认证后、控制器前）；属性名两侧字面量对齐。
        String attrUser = attribute(request, "ECOS_INJECTED_USER");
        String attrTenant = attribute(request, "ECOS_INJECTED_TENANT");
        if (attrUser == null && attrTenant == null) {
            return fromHeaders;
        }
        return new Carrier(
                fromHeaders.authorization(),
                fromHeaders.user() != null ? fromHeaders.user() : attrUser,
                fromHeaders.role(),
                fromHeaders.tenant() != null ? fromHeaders.tenant() : attrTenant,
                fromHeaders.domain());
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object v = request.getAttribute(name);
        return v == null ? null : trimToNull(String.valueOf(v));
    }

    /**
     * 只取 {@code Authorization}（给不需要信任头的调用方，如本体／agent／认知三腿）。
     *
     * @return 原值（trim 后非空白）；未携带或非请求线程返回 {@code null}
     */
    public static String currentAuthorization() {
        return current().authorization();
    }

    /**
     * 把当前请求的凭证与信任头中继到出站请求头。
     *
     * <p>无请求上下文／{@code headers} 为 {@code null} ⇒ <b>原样不加</b>：
     * 由被调方按其默认策略裁决（security-engine 默认 DENY），本方法不发明主语。</p>
     */
    public static void applyTo(HttpHeaders headers) {
        applyTo(headers, current());
    }

    /** 中继指定载体（供已自行取过一次凭证的调用方复用，避免同请求内两次读头）。 */
    public static void applyTo(HttpHeaders headers, Carrier carrier) {
        if (headers == null || carrier == null) {
            return;
        }
        putIfPresent(headers, HttpHeaders.AUTHORIZATION, carrier.authorization());
        putIfPresent(headers, HeaderAuthInterceptor.H_USER, carrier.user());
        putIfPresent(headers, HeaderAuthInterceptor.H_ROLE, carrier.role());
        putIfPresent(headers, HeaderAuthInterceptor.H_TENANT, carrier.tenant());
        putIfPresent(headers, HeaderAuthInterceptor.H_DOMAIN, carrier.domain());
    }

    private static void putIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null) {
            headers.set(name, value);
        }
    }

    private static String header(HttpServletRequest request, String name) {
        return trimToNull(request.getHeader(name));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
