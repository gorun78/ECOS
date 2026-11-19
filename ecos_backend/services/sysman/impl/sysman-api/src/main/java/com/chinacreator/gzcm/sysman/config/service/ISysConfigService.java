package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

/**
 * sys_config 配置门面契约（CD-3 整改）。
 *
 * <p>后端规范 §十.8 CD-3 登记：6+ 处引擎代码 {@code import sysman.config.service.impl.SysConfigService}
 * （实现层而非契约层），违反「配置与字典只经 sysman api 门面」。本接口在 sysman-api
 * 暴露稳定契约，引擎改依赖本接口 + 本模块，不再 import impl 包。</p>
 *
 * <p>方法签名与 {@code sysman-impl SysConfigService} 公共面一一对应；
 * 实现类 {@code SysConfigService} 实现本接口，Spring 按接口注入即解析到同一 Bean。</p>
 *
 * <p>引擎只应消费本接口的读面 + 受限写面；组管理/缓存运维方法保留给 sysman 内部，
 * 故本接口刻意只暴露引擎真实使用的方法子集（按需增补，不一次铺满）。</p>
 */
public interface ISysConfigService {

    /** 按键取字符串值；未配置返回 null。 */
    String getString(String key);

    /** 按键取字符串值；未配置返回 defaultValue。 */
    String getString(String key, String defaultValue);

    /** 按键取整数值；未配置抛 NumberFormatException（读面契约同 impl）。 */
    int getInt(String key);

    /** 按键取整数值；未配置返回 defaultValue。 */
    int getInt(String key, int defaultValue);

    /** 按键取长整数值；未配置返回 defaultValue。 */
    long getLong(String key, long defaultValue);

    /** 按键取布尔值；未配置返回 defaultValue。 */
    boolean getBoolean(String key, boolean defaultValue);

    /** 拉取指定组的全部配置行（active），按 sort_order、config_key 排序。 */
    List<Map<String, Object>> listByGroup(String group);

    /** 按键取配置行；不存在返回 null。 */
    Map<String, Object> getByKey(String key);

    /** 单值更新；行存在才更新，返回是否命中。 */
    boolean updateValue(String key, String value);

    /** 单条 upsert：不存在则插入（config_type/description 由调用方给定）。 */
    void upsertValue(String configKey, String configValue, String configGroup, String configType, String description);

    /** 按组取配置行（listByGroup 别名，契约兼容既有调用）。 */
    List<Map<String, Object>> getByGroup(String group);

    /** 刷新进程内配置缓存（写后调用即生效）。 */
    void refreshCache();

    /** 进程内配置缓存当前条目数（引擎配置面板运维读数）。 */
    int cacheSize();

    /** 批量更新（元素含 configKey/configValue），返回成功条数。 */
    int updateBatch(List<Map<String, String>> updates);
}
