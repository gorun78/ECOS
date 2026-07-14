package com.chinacreator.gzcm.runtime.hermes.repository;

import com.chinacreator.gzcm.runtime.hermes.model.AgentCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 调用日志 Mapper — 操作 sys_agent_call_log 表
 */
@Mapper
public interface AgentCallLogRepository {

    int insert(AgentCallLog log);

    List<AgentCallLog> findBySubsystem(
            @Param("subsystem") String subsystem,
            @Param("limit") int limit);

    long countBySubsystemAndStatus(
            @Param("subsystem") String subsystem,
            @Param("status") String status);

    long countTokensBySubsystem(@Param("subsystem") String subsystem);
}
