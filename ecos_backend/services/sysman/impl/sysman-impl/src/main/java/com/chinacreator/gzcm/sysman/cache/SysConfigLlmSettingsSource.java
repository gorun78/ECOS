package com.chinacreator.gzcm.sysman.cache;

import com.chinacreator.gzcm.runtime.llm.config.LlmSettings;
import com.chinacreator.gzcm.runtime.llm.config.LlmSettingsSource;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 运行期配置的唯一读取面：{@code sys_config} 的 {@code config_group=llm}（G-2 整改；V354 播种）。
 *
 * <p><b>为什么放在 sysman-impl</b>：铁律「配置与字典只经 sysman api 门面」；runtime 底座
 * （llm-gateway）不得直读配置表。本类作为 {@link LlmSettingsSource} 适配 Bean 供
 * llm-gateway 门面消费。包位置与 {@code SysConfigCacheSettingsSource} 同理是承流条件——
 * gateway 的 {@code @ComponentScan} 只圈 {@code com.chinacreator.gzcm.sysman}。</p>
 *
 * <p>键：{@code llm.default-model} / {@code llm.default-temperature} /
 * {@code llm.agent-timeout-ms} / {@code llm.max-context-tokens}；
 * 读取失败一律返回 {@link LlmSettings#DEFAULTS}（= yml 现行缺省，行为不变，never fail-open）。</p>
 *
 * <p>provider 选择、api-key-ref、模型目录清单（catalog）属部署形态，<b>不经本读取面</b>，
 * 仍由 {@code LLMGatewayProperties}（yml）承载——V264「模型目录留 yml」口径仅对 catalog 保留。</p>
 */
@Component
public class SysConfigLlmSettingsSource implements LlmSettingsSource {

    private static final Logger log = LoggerFactory.getLogger(SysConfigLlmSettingsSource.class);

    static final String GROUP_LLM = "llm";
    static final String KEY_MODEL = "llm.default-model";
    static final String KEY_TEMPERATURE = "llm.default-temperature";
    static final String KEY_AGENT_TIMEOUT = "llm.agent-timeout-ms";
    static final String KEY_MAX_CONTEXT = "llm.max-context-tokens";

    private final SysConfigService sysConfigService;

    public SysConfigLlmSettingsSource(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 每次经 {@link SysConfigService} 的进程内配置缓存取值（同 {@code SysConfigCacheSettingsSource}
     * 口径：不做二次快照，「改配置 + refreshCache()」即时生效，无需重启）。
     */
    @Override
    public LlmSettings settings() {
        try {
            return new LlmSettings(
                    str(KEY_MODEL, LlmSettings.DEFAULTS.defaultModel()),
                    dbl(KEY_TEMPERATURE, LlmSettings.DEFAULTS.defaultTemperature()),
                    num(KEY_AGENT_TIMEOUT, LlmSettings.DEFAULTS.agentTimeoutMs()),
                    (int) num(KEY_MAX_CONTEXT, LlmSettings.DEFAULTS.maxContextTokens()));
        } catch (RuntimeException e) {
            log.warn("读取 LLM 配置失败，按 yml 缺省处理（行为不变，never fail-open）: {}", e.getMessage());
            return LlmSettings.DEFAULTS;
        }
    }

    private String str(String key, String fallback) {
        String v = sysConfigService.getString(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private long num(String key, long fallback) {
        String v = sysConfigService.getString(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double dbl(String key, double fallback) {
        String v = sysConfigService.getString(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
