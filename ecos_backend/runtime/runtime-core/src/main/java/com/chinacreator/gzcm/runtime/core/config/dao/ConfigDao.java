package com.chinacreator.gzcm.runtime.core.config.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.config.entity.ConfigEntity;
import com.chinacreator.gzcm.runtime.core.config.entity.ConfigVersionEntity;

/**
 * 配置数据访问接口
 *
 * @author CDRC Runtime Team
 */
public interface ConfigDao {

    /**
     * 保存配置
     *
     * @param config 配置实体
     */
    void saveConfig(ConfigEntity config);

    /**
     * 根据ID获取配置
     *
     * @param configId 配置ID
     * @return 配置实体
     */
    ConfigEntity getConfigById(String configId);

    /**
     * 根据类型、名称和环境获取配置
     *
     * @param configType 配置类型
     * @param configName 配置名称
     * @param environment 环境
     * @return 配置实体
     */
    ConfigEntity getConfigByTypeNameEnv(String configType, String configName, String environment);

    /**
     * 查询配置列表
     *
     * @param condition 查询条件
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 配置列表
     */
    List<ConfigEntity> queryConfigs(Map<String, Object> condition, int offset, int limit);

    /**
     * 删除配置
     *
     * @param configId 配置ID
     */
    void deleteConfig(String configId);

    /**
     * 保存配置版本
     *
     * @param version 配置版本实体
     */
    void saveConfigVersion(ConfigVersionEntity version);

    /**
     * 获取配置的所有版本
     *
     * @param configId 配置ID
     * @return 版本列表
     */
    List<ConfigVersionEntity> getConfigVersions(String configId);

    /**
     * 根据配置ID和版本号获取配置版本
     *
     * @param configId 配置ID
     * @param version 版本号
     * @return 配置版本实体
     */
    ConfigVersionEntity getConfigVersion(String configId, String version);

    /**
     * 获取配置数量
     *
     * @param condition 查询条件
     * @return 配置数量
     */
    int countConfigs(Map<String, Object> condition);
}