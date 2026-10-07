package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

/**
 * 系统配置 · 值访问门面（D-10 收敛，2026-10-07 B7a）。
 *
 * <p>铁律 §2.1 / §4：配置与字典只经 sysman api 门面暴露；引擎侧禁止
 * {@code import com.chinacreator.gzcm.sysman.**.impl.**}。</p>
 *
 * <p>本接口对齐 {@code sysman-impl.SysConfigService} 的<b>值访问面</b>
 * （getString/getInt/getLong/getBoolean + 组查询 + 缓存刷新 + 批量更新）。
 * 与既有 {@link IConfigService} 的 {@code Config} CRUD 语义不同，二者并存：
 * <ul>
 *   <li>{@code IConfigService} — Config 实体 CRUD（业务配置对象）</li>
 *   <li>{@code ISysConfigService} — sys_config 表键值缓存 + 分组/批量（引擎消费面）</li>
 * </ul>
 *
 * <p>实现：{@code sysman-impl.SysConfigService}（同一 Bean，网关经
 * {@code excludeFilters} 唯一装载，各 engine 仅依赖本门面）。</p>
 */
public interface ISysConfigService {

    /** 获取字符串值（缺 → null）。 */
    String getString(String key);

    /** 获取字符串值（带默认）。 */
    String getString(String key, String defaultValue);

    /** 获取整数值（缺 → 0）。 */
    int getInt(String key);

    /** 获取整数值（带默认）。 */
    int getInt(String key, int defaultValue);

    /** 获取长整数值（缺 → 0L）。 */
    long getLong(String key);

    /** 获取长整数值（带默认）。 */
    long getLong(String key, long defaultValue);

    /** 获取布尔值（缺 → false）。 */
    boolean getBoolean(String key);

    /** 获取布尔值（带默认）。 */
    boolean getBoolean(String key, boolean defaultValue);

    /** 按 config_group 分组查询配置行。 */
    List<Map<String, Object>> getByGroup(String group);

    /** 按 config_group 分组查询（getByGroup 别名，兼容 DataEngineConfigController）。 */
    List<Map<String, Object>> listByGroup(String group);

    /** 获取全部（缓存快照）。 */
    Map<String, String> getAll();

    /** 按 key 获取完整配置行（未命中 → null）。 */
    Map<String, Object> getByKey(String key);

    /** 更新配置值并刷新缓存。 */
    boolean updateValue(String key, String value);

    /** 插入/更新配置值并刷新缓存。 */
    void upsertValue(String configKey, String configValue, String configGroup, String configType, String description);

    /** 批量更新配置值并刷新缓存。 */
    int updateBatch(List<Map<String, String>> updates);

    /** 刷新缓存：从 sys_config 表重取 active。 */
    void refreshCache();

    /** 当前缓存条目数（诊断）。 */
    int cacheSize();
}
