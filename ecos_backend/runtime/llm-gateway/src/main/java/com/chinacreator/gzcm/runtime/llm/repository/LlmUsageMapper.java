package com.chinacreator.gzcm.runtime.llm.repository;

import org.apache.ibatis.annotations.Mapper;

/**
 * LLM token 计量 Mapper — {@code public.ecos_runtime_llm_usage}（V244，详细设计-00 §6.2 / F00-07）。
 * <p>
 * 与 sibling {@link AgentCallLogRepository} 同包（宿主 {@code @MapperScan} 已覆盖
 * {@code com.chinacreator.gzcm.runtime.llm.repository}），XML 不在本包（走默认 namespace 绑定
 * 同子包下的 {@code LlmUsageRepository.xml} — 宿主 SqlSessionFactory 的
 * {@code classpath*:mapper/*.xml} / {@code **}.xml 约定加载）。
 *
 * <p>TODO 挂接位（F00-07 蓝本口径）：唯一 LLM 出口
 * {@code runtime.llm.gateway.LLMGatewayImpl}
 * 在 chat 响应解析 {@code usage.prompt_tokens} 处（方法体已有 {@code tokensInput / tokensOutput}
 * 累积，见 {@code streamChat} 收尾与 {@code doEmbed} 的 {@code usage} 读取块），
 * 应在此调 {@code LlmUsageRecorder.record(provider, model, purpose, promptTokens, completionTokens, cost)}。
 * <b>不强行改 LLMGatewayImpl 主路径</b>（任务边界 F00-07 E 章后续批次承接）。
 */
@Mapper
public interface LlmUsageMapper {

    /** V244 全列写入门面 */
    int insert(com.chinacreator.gzcm.runtime.llm.usage.LlmUsageEntity row);
}
