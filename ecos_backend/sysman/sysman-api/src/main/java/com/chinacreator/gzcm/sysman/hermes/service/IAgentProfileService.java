package com.chinacreator.gzcm.sysman.hermes.service;

import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;

import java.util.List;
import java.util.Map;

/**
 * Agent Profile 配置 CRUD 服务接口
 */
public interface IAgentProfileService {

    /** 查询所有 profile */
    List<ProfileConfig> listAll();

    /** 按子系统查询 profile */
    List<ProfileConfig> listBySubsystem(String subsystem);

    /** 按 ID 查询 */
    ProfileConfig getById(String id);

    /** 创建 profile */
    ProfileConfig create(ProfileConfig config);

    /** 更新 profile */
    ProfileConfig update(ProfileConfig config);

    /** 删除 profile */
    void delete(String id);

    /** 启用/禁用 profile */
    void toggleEnabled(String id, boolean enabled);

    /**
     * 测试连接 — 校验配置能否连通 LLM 后端
     * @param id profile ID
     * @throws RuntimeException 连接失败时抛出异常，描述原因
     */
    void testConnection(String id);

    /** 获取指定子系统的统计信息 */
    Map<String, Object> getSubsystemStats(String subsystem);

    /** 获取全局统计信息 */
    Map<String, Object> getGlobalStats();
}
