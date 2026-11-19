package com.chinacreator.gzcm.sysman.cache;

import com.chinacreator.gzcm.runtime.core.event.EventSettings;
import com.chinacreator.gzcm.runtime.core.event.EventSettingsSource;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 事件总线配置的唯一读取面：{@code sys_config} 的 {@code config_group=event}（G-2 整改；V354 播种）。
 *
 * <p>形态与 {@link SysConfigLlmSettingsSource} 完全一致：runtime 底座只声明 SPI，
 * 读表实现在 sysman-impl（铁律「配置只经 sysman 门面」）。包位置是承流条件——
 * gateway 的 {@code @ComponentScan} 只圈 {@code com.chinacreator.gzcm.sysman}。</p>
 *
 * <p>键：{@code event.kafka.auto-offset-reset} / {@code event.kafka.group-prefix}；
 * 读取失败一律返回 {@link EventSettings#DEFAULTS}（= {@code @Value} 现行缺省，行为不变）。</p>
 */
@Component
public class SysConfigEventSettingsSource implements EventSettingsSource {

    private static final Logger log = LoggerFactory.getLogger(SysConfigEventSettingsSource.class);

    static final String KEY_AUTO_OFFSET_RESET = "event.kafka.auto-offset-reset";
    static final String KEY_GROUP_PREFIX = "event.kafka.group-prefix";

    private final SysConfigService sysConfigService;

    public SysConfigEventSettingsSource(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /** 每次经 {@link SysConfigService} 的进程内配置缓存取值（改配置 + refreshCache() 即生效）。 */
    @Override
    public EventSettings settings() {
        try {
            return new EventSettings(
                    str(KEY_AUTO_OFFSET_RESET, EventSettings.DEFAULTS.autoOffsetReset()),
                    str(KEY_GROUP_PREFIX, EventSettings.DEFAULTS.groupPrefix()));
        } catch (RuntimeException e) {
            log.warn("读取事件总线配置失败，按 @Value 缺省处理（行为不变）: {}", e.getMessage());
            return EventSettings.DEFAULTS;
        }
    }

    private String str(String key, String fallback) {
        String v = sysConfigService.getString(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }
}
