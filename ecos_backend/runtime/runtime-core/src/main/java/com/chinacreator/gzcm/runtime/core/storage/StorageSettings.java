package com.chinacreator.gzcm.runtime.core.storage;

/**
 * 对象存储 / 文档解析运行参数（G-2 快照）。
 *
 * <p>字段与 {@code sys_config config_group=storage}（V354 播种）一一对应。
 * 刻意只收「运行期调整有意义」的两项：endpoint / access-key / secret-key 属部署凭据，
 * 留 yml 与环境变量，<b>不入门面</b>（凭据不该落配置表明文）。</p>
 *
 * @param minioBucket     默认桶名
 * @param mineruTimeoutSeconds 文档解析服务超时（秒）
 */
public record StorageSettings(String minioBucket, int mineruTimeoutSeconds) {

    /**
     * 未桥接/读取失败时兜底 = runtime-access 现行缺省。
     *
     * <p>{@code mineruTimeoutSeconds = 300}：与 {@code MinerUHttpParser} 的硬编码
     * {@code Duration.ofMinutes(5)} 对齐（OCR 重活放宽超时）。注意 V354 播种值为 120 秒，
     * 与代码真相不符——接线后以库值 120 为准会**缩短**超时，属行为变更，
     * 运维须在启用桥接前自行确认（见 V354 头注订正）。</p>
     */
    public static final StorageSettings DEFAULTS = new StorageSettings("ecos-data", 300);

    public StorageSettings {
        minioBucket = (minioBucket == null || minioBucket.isBlank())
                ? DEFAULTS.minioBucket : minioBucket.trim();
        mineruTimeoutSeconds = mineruTimeoutSeconds <= 0
                ? DEFAULTS.mineruTimeoutSeconds : mineruTimeoutSeconds;
    }
}
