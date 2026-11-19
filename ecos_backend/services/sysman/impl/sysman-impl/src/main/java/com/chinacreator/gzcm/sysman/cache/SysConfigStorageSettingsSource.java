package com.chinacreator.gzcm.sysman.cache;

import com.chinacreator.gzcm.runtime.core.storage.StorageSettings;
import com.chinacreator.gzcm.runtime.core.storage.StorageSettingsSource;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置的唯一读取面：{@code sys_config} 的 {@code config_group=storage}（G-2 整改；V354 播种）。
 *
 * <p>形态与 {@link SysConfigLlmSettingsSource} 一致。凭据类（endpoint/access-key/secret-key）
 * <b>不在此面</b>——仍由 yml 与环境变量承载，禁落配置表明文。</p>
 *
 * <p>键：{@code storage.minio.bucket} / {@code storage.mineru.timeout-seconds}；
 * 读取失败一律返回 {@link StorageSettings#DEFAULTS}（行为不变）。
 * 注意 DEFAULTS 的 timeout = 300 秒（对齐代码硬编码 5 分钟），
 * 而 V354 播种值为 120 秒 ⇒ 桥接生效后 MinerU 超时会由 300 变 120，
 * 属行为变更，启用前须确认。</p>
 */
@Component
public class SysConfigStorageSettingsSource implements StorageSettingsSource {

    private static final Logger log = LoggerFactory.getLogger(SysConfigStorageSettingsSource.class);

    static final String KEY_MINIO_BUCKET = "storage.minio.bucket";
    static final String KEY_MINERU_TIMEOUT = "storage.mineru.timeout-seconds";

    private final SysConfigService sysConfigService;

    public SysConfigStorageSettingsSource(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public StorageSettings settings() {
        try {
            return new StorageSettings(
                    str(KEY_MINIO_BUCKET, StorageSettings.DEFAULTS.minioBucket()),
                    (int) num(KEY_MINERU_TIMEOUT, StorageSettings.DEFAULTS.mineruTimeoutSeconds()));
        } catch (RuntimeException e) {
            log.warn("读取存储配置失败，按现行缺省处理（行为不变）: {}", e.getMessage());
            return StorageSettings.DEFAULTS;
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
}
