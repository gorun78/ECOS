package com.chinacreator.gzcm.runtime.hermes.profile;

import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;

import java.util.List;

/**
 * Profile 管理器接口 — 负责 Profile 配置的加载、缓存、校验
 */
public interface ProfileManager {

    /**
     * 获取指定子系统的指定 profile，若不存在或已禁用则抛异常
     *
     * @param subsystem   子系统标识
     * @param profileName profile 名称
     * @return ProfileConfig
     * @throws IllegalArgumentException 未找到或已禁用
     */
    ProfileConfig getProfile(String subsystem, String profileName);

    /**
     * 列出子系统下所有 profile（含已禁用的）
     */
    List<ProfileConfig> listProfiles(String subsystem);

    /**
     * 列出系统中所有 profile
     */
    List<ProfileConfig> listAllProfiles();

    /**
     * 校验 ProfileConfig 必填字段
     *
     * @throws IllegalArgumentException 校验不通过
     */
    void validateProfile(ProfileConfig config);

    /**
     * 刷新指定子系统的缓存
     */
    void refreshCache(String subsystem);
}
