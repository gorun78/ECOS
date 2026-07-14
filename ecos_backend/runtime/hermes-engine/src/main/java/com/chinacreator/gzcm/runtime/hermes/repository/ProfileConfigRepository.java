package com.chinacreator.gzcm.runtime.hermes.repository;

import com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Profile 配置 Mapper — 操作 sys_agent_profile 表
 */
@Mapper
public interface ProfileConfigRepository {

    List<ProfileConfig> findAll();

    List<ProfileConfig> findBySubsystem(@Param("subsystem") String subsystem);

    ProfileConfig findById(@Param("id") String id);

    ProfileConfig findBySubsystemAndProfileName(
            @Param("subsystem") String subsystem,
            @Param("profileName") String profileName);

    List<ProfileConfig> findEnabledBySubsystem(@Param("subsystem") String subsystem);

    int insert(ProfileConfig config);

    int update(ProfileConfig config);

    int deleteById(@Param("id") String id);
}
