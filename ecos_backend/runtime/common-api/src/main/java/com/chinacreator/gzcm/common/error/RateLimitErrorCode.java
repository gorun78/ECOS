package com.chinacreator.gzcm.common.error;

/**
 * 速率限制与配额域（ECOS-RATE）统一错误码常量 — 详细设计-00 §3.2.7 与附录 C 冻结表单源。
 *
 * <p>设计约束（ECOS 详细设计-从零实施版-2026-10-12 §3.2.7 错误码族表 + 附录 C）：</p>
 * <ul>
 *   <li>所有数据域解除机读码退出面的响应包体内 {@code errorCode} 字段必须取自本类，禁散落字符串字面量；</li>
 *   <li>{@code ECOS-RATE-429}（<b>429</b>）＝请求超频（入口预置流控面触发，{@code RateLimitFilter} 单源）；</li>
 *   <li>{@code ECOS-RATE-430}（<b>429</b>）＝租户日配额耗尽（{@code QuotaFilter} 单源）；</li>
 *   <li>两枚同 HTTP 429 但语义不同：客户端按下述两枚机读码可分派「等待冷却期」与「升级租户/切换额度」两类补救动作，
 *       折叠成一枚将不可区分（差距分析 P1.1「配额耗尽仅裸 429」即此成因）。</li>
 * </ul>
 *
 * <p><b>#142/B4-3e-92 新登</b>（收口差距 <b>P1.1 错误码族系统性漂移</b>中「ECOS-RATE 族在
 * 生产控制面零命中」子项）：改前 {@code RateLimitFilter} 直接 {@code Map.of("code",429,...)}、
 * {@code QuotaFilter} 走 {@code ApiResponse.error(429, msg)} 两枚单参入口，二处均<b>不填</b>
 * {@code errorCode}——与 D-B4-65「机读码干脆没填」同型，而入口预置流控腿在 §3.2.7 归
 * 「限流与配额」族、码表唯一登记 {@code ECOS-RATE-429/430} 但生产面从未引用。本类为
 * §3.2.7 已冻结两枚码的<b>唯一 Java 单源常量持有者</b>，本域其他类禁再复制字面量
 * （字形枷由班车测试锁在 common-api 门禁面，独立 {@code NoMachineCodeFactoryFaceRatchetTest}
 * 的 {@code sysFamilyLiteralsAreSingleSourced} 扩展同行登记）。</p>
 *
 * <p>本类只含常量，无任何业务 import（common-api enforcer 白名单内，与
 * {@link SystemErrorCode}／{@code DataDomainErrorCode}／{@code KbErrorCodes} 同形）。</p>
 */
public final class RateLimitErrorCode {

    private RateLimitErrorCode() {
        throw new UnsupportedOperationException("常量类，禁止实例化");
    }

    /**
     * 429 — 请求超频（入口预置流控面触发）。
     *
     * <p>属主出口＝gateway {@code RateLimitFilter#doFilterInternal}（@Order(5)）内
     * {@code isRateLimited == true} 分支。状态行＝HTTP 429；响应包体 {@code code}
     * 字段沿用 429（HTTP 语义与包体同号，同 {@code SystemErrorCode} 便面规则）；
     * {@code retryAfter=60}s 头沿用既有契约键面（只增 {@code errorCode}，不删改）。</p>
     */
    public static final String ECOS_RATE_429 = "ECOS-RATE-429";

    /**
     * 429 — 租户日配额耗尽。
     *
     * <p>属主出口＝gateway {@code QuotaFilter#sendQuotaExceeded}（@Order(6)）
     * 内原子 UPSERT 阻断或安全网触顶两腿。同 {@link #ECOS_RATE_429} 状态行 429，
     * 但语义是「升级租户档位」而非「等 60s」；包体 data 字段继续携带
     * {@code tenant_id/quota_type/used/limit} 四机读键（既有契约不删），
     * 本批只补 {@code errorCode} 键使客户端可按机读码分派对方补救动作。</p>
     */
    public static final String ECOS_RATE_430 = "ECOS-RATE-430";
}
