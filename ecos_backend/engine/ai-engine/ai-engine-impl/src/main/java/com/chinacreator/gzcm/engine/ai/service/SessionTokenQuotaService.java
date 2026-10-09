package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话级 token 配额判定（详细设计-06 F06-07 要点 3；铁律 §2.5-5 配置单源）。
 *
 * <p>职责边界：本类只做<b>判定</b>，不做计量。用量真值一律经
 * {@link LLMGateway#sessionTokenUsage(String)} 向 llm-gateway 的唯一计量点查询
 * （F06-05 收一：禁各引擎自记 token）。上限经 sysman 配置门面读
 * {@value #CONFIG_KEY}，引擎内不硬编码数值。</p>
 *
 * <p><b>0 或未配置 = 不启用配额</b>（与 V264 种子描述同口径），此时
 * {@link QuotaState#enforced()} 为 false，调用方不得据此推断"已放行"以外的任何结论。
 * 读数失败同样不判定，但必须 ERROR 留痕 —— 配额是成本控制而非安全红线，
 * 允许降级，不允许静默。</p>
 */
@Component
public class SessionTokenQuotaService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenQuotaService.class);

    /** 配置键（sysman 配置字典单源，{@code config_group='ai-engine'}，V264 种子建立） */
    public static final String CONFIG_KEY = "ecos.ai.session.token_limit";

    /** 关闭态取值：0 表示不启用会话配额 */
    public static final long LIMIT_DISABLED = 0L;

    private final ObjectProvider<SysConfigService> sysConfigProvider;
    private final LLMGateway llmGateway;

    /** 门面缺失只报一次 WARN（每轮都判，避免刷屏；缺失是部署态而非运行态抖动） */
    private final AtomicBoolean facadeMissingWarned = new AtomicBoolean(false);

    public SessionTokenQuotaService(ObjectProvider<SysConfigService> sysConfigProvider,
                                    LLMGateway llmGateway) {
        this.sysConfigProvider = sysConfigProvider;
        this.llmGateway = llmGateway;
    }

    /**
     * 一次配额判定。
     *
     * @param sessionId 会话 ID；空 ⇒ 无计量作用域，不判定（reason={@code no_session}）
     */
    public QuotaState check(String sessionId) {
        long limit = tokenLimit();
        if (limit <= LIMIT_DISABLED) {
            return new QuotaState(false, false, 0, limit, "limit_disabled");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return new QuotaState(false, false, 0, limit, "no_session");
        }
        long used;
        try {
            used = llmGateway.sessionTokenUsage(sessionId);
        } catch (Exception e) {
            log.error("[F06-07] 会话 token 用量读取失败，本轮不做配额判定（不静默：ERROR 留痕）: "
                    + "session={} err={}", sessionId, e.getMessage(), e);
            return new QuotaState(false, false, 0, limit, "usage_unavailable");
        }
        boolean exceeded = used >= limit;
        return new QuotaState(true, exceeded, used, limit,
                exceeded ? "limit_reached" : "within_limit");
    }

    /** 生效上限（token 数）；{@code 0} = 未启用。 */
    public long tokenLimit() {
        SysConfigService cfg = sysConfigProvider.getIfAvailable();
        if (cfg == null) {
            if (facadeMissingWarned.compareAndSet(false, true)) {
                log.warn("[F06-07] sysman 配置门面不可用（独立 boot 进程未扫 sysman 包），会话配额不生效");
            }
            return LIMIT_DISABLED;
        }
        try {
            int raw = cfg.getInt(CONFIG_KEY, (int) LIMIT_DISABLED);
            if (raw < 0) {
                log.warn("[F06-07] 配额上限配置为负数，按不启用处理: key={} value={}", CONFIG_KEY, raw);
                return LIMIT_DISABLED;
            }
            return raw;
        } catch (Exception e) {
            log.error("[F06-07] 读取配额上限失败，按不启用处理: key={} err={}", CONFIG_KEY, e.getMessage(), e);
            return LIMIT_DISABLED;
        }
    }

    /**
     * 判定结果快照。
     *
     * @param enforced 本次判定是否成立（false = 未启用/无作用域/读数失败）
     * @param exceeded 已用量是否达到上限（仅 enforced=true 有意义）
     * @param used     会话累计 token 用量（真值，来自网关计量点；未判定为 0）
     * @param limit    生效上限；{@code 0} = 未启用
     * @param reason   机器可读判定依据，只入日志与诊断，禁作为面向用户的文案
     */
    public record QuotaState(boolean enforced, boolean exceeded, long used, long limit, String reason) {

        /** 剩余额度（{@code limit - used}，下限 0）；调用方须先读 {@code enforced} 才知道该值有无意义。 */
        public long remaining() {
            return Math.max(0, limit - used);
        }
    }
}
