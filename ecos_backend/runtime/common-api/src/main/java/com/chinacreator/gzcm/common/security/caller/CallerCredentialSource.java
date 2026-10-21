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
 * 这是<b>有意保留的 fail-closed</b>，不是漏判：跨线程传递身份需要一个新的可继承载体
 * （{@code InheritableThreadLocal}／ScopedValue），线程池复用会让上一位调用方的凭证
 * 泄给下一位——那比"异步腿取不到凭证"严重得多。要打通异步腿的身份，属主侧需要显式
 * 任务凭证（服务凭证签名，见 {@code EcosServiceCredentialSigner}），另批处置。</p>
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
     * 从<b>当前线程绑定的</b>请求上下文取调用方凭证。
     *
     * @return 载体；无请求上下文（异步／定时／SSE 线程）返回 {@link Carrier#EMPTY}，<b>绝不返回 {@code null}</b>
     */
    public static Carrier current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return Carrier.EMPTY;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        return new Carrier(
                header(request, HttpHeaders.AUTHORIZATION),
                header(request, HeaderAuthInterceptor.H_USER),
                header(request, HeaderAuthInterceptor.H_ROLE),
                header(request, HeaderAuthInterceptor.H_TENANT),
                header(request, HeaderAuthInterceptor.H_DOMAIN));
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
