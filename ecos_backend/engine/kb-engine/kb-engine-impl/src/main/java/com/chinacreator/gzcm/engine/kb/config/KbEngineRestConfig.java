package com.chinacreator.gzcm.engine.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * kb-engine REST 客户端配置。
 * <p>为 KnowledgeExtractionService 提供 RestTemplate bean（调用 ai-engine 用）。</p>
 *
 * <p><b>KB-legacy 标记（F04-13）</b>：本 RestTemplate bean 为<b>跨引擎 REST 调用</b>
 * （ai-engine Agent Loop / ontology proposal / data-engine 登记）共享，<b>不由</b>
 * F04-13 LLM 收敛改造的两个 helper（QueryEmbeddingHelper / AnswerGenerationHelper）消费——
 * 二者已改走 {@code LLMGateway}。本 bean 因仍被 KnowledgeExtractionService 等
 * 跨引擎 REST 客户端复用而保留，禁止误删；与 {@code /api/v1/llm/*} 直连无关。</p>
 */
@Configuration
public class KbEngineRestConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
