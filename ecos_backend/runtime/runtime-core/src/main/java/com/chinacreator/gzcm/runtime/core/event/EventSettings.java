package com.chinacreator.gzcm.runtime.core.event;

/**
 * 事件总线运行参数（G-2 快照）。
 *
 * <p>字段与 {@code sys_config config_group=event}（V354 播种）一一对应；
 * 缺省值对齐 {@code @Value} 现行缺省，保证「未播种/未桥接 ⇒ 行为不变」。</p>
 *
 * <p>刻意只收「运行期调整有意义」的两项：{@code bootstrap-servers} 属部署寻址，
 * 留 yml 不入门面。</p>
 *
 * @param autoOffsetReset 无已提交位点时从何处开始消费
 * @param groupPrefix     共享消费组前缀；空串 = 每次订阅独立 fan-out（现行行为）
 */
public record EventSettings(String autoOffsetReset, String groupPrefix) {

    /** 未桥接/读取失败时兜底 = {@code @Value} 现行缺省。 */
    public static final EventSettings DEFAULTS = new EventSettings("latest", "");

    public EventSettings {
        autoOffsetReset = (autoOffsetReset == null || autoOffsetReset.isBlank())
                ? DEFAULTS.autoOffsetReset : autoOffsetReset.trim();
        groupPrefix = groupPrefix == null ? "" : groupPrefix.trim();
    }
}
