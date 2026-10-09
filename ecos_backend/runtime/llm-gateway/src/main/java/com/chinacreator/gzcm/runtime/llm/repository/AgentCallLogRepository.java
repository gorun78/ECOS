package com.chinacreator.gzcm.runtime.llm.repository;

import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
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

    /**
     * 会话累计 token（F06-07 要点 3 配额判定的权威读数面）。
     *
     * @param sessionId 会话标识；空串 = "无会话"占位桶（{@code session_id NOT NULL}），
     *                  真实会话配额查询永不传空串
     * @return tokens_input + tokens_output 累计，无记录时为 0
     */
    long sumTokensBySession(@Param("sessionId") String sessionId);
}
