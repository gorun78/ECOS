package com.chinacreator.gzcm.common.security.http;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 「服务端解析的机构」请求属性单源（§2.9.9 G8；#147G8）。
 *
 * <p><b>为什么单独建类</b>：机构值必须在**服务端**解析后沿请求传递（sysman
 * {@code ClearanceInterceptor} 写入、gateway 属主守卫读取），且两侧不得各自拼字面量——
 * 常量漂移会让守卫静默失效。客户端 {@code X-Org-Id} 头已在 {@code EcosTrustHeaderFilter}
 * 剥离，<b>本属性只接受服务端写入值</b>，任何从参数/头回填的写法都属越权面回潮。</p>
 *
 * <p><b>为什么落在 {@code security.http}</b>（#147Z-6）：本类是信任边界的载体（服务端解析值 +
 * 客户端头已剥离），按 {@code ArchitectureGuardTest}「Controller 外禁依赖 servlet API、security
 * 包除外」的分层口径，原 {@code common.context} 落位会被判违例；迁入 security 包是**改落位**
 * 而非**放宽规则**。</p>
 *
 * @author ECOS Common #147G8
 */
public final class ServerOrgAttribute {

    /** 请求属性名（写入=准入门面，读取=仓库属主守卫等）。 */
    public static final String NAME = "ecos.serverOrgId";

    private ServerOrgAttribute() {
    }

    /** 服务端解析成功后写入；空值不写（"拿不到机构"与"全局"必须可区分）。 */
    public static void set(HttpServletRequest request, String orgId) {
        if (request != null && orgId != null && !orgId.isBlank()) {
            request.setAttribute(NAME, orgId.trim());
        }
    }

    /** 读取服务端解析的机构；未解析返回 {@code null}（调用方按 fail-closed 处置）。 */
    public static String get(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object v = request.getAttribute(NAME);
        return v == null ? null : v.toString();
    }
}
