package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import org.springframework.http.HttpHeaders;

/**
 * 出站调用凭证中继（H10 服务凭证收口前的 ADR-7 信任链唯一合法通道，卷07 F07-17 口径）。
 *
 * <p><b>为什么需要它</b>：workspace 独立进程（:18090）向引擎侧发 REST 时若不携带任何凭证，
 * 引擎宿主（gateway/aiming/sysman）的 Spring Security 默认 DENY ⇒ 已登录用户被判 403，
 * 调用方把"plumbing 缺口"误读成"权限拒绝"（2026-10-11 live 实证，见
 * {@code WorkspaceSecurityEngineClient#relayCallerCredentials} 的整改记录）。</p>
 *
 * <p><b>fail-closed 语义保持不变</b>：无入口请求上下文（异步任务/定时线程）⇒ <b>不伪造凭证</b>，
 * 由被调方按其默认策略裁决；只中继入口请求已带的 {@code Authorization} 与
 * {@code X-ECOS-USER/ROLE/TENANT/DOMAIN} 信任头，逐字转发、不升权、不拼接。</p>
 *
 * <p>本类是把 {@code WorkspaceSecurityEngineClient} 内的同义私有方法抽为可复用件，
 * 供新增的认知域 run 适配器使用；<b>#142/B4-3e-21 起</b>取头与中继的判据本身
 * 单源在 {@link CallerCredentialSource}（收口 D-B4-61「身份载体无单源」），
 * 本类只保留 workspace 侧的调用外形，不再自带一套读头逻辑。</p>
 */
public final class CallerCredentialRelay {

    private CallerCredentialRelay() {}

    /** 把当前入口请求的凭证与信任头中继到出站请求头（无请求上下文时原样不加）。 */
    public static void applyTo(HttpHeaders headers) {
        CallerCredentialSource.applyTo(headers);
    }
}
