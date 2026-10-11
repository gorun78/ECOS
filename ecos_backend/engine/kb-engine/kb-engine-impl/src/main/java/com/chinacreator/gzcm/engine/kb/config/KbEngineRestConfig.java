package com.chinacreator.gzcm.engine.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 *
 * <p><b>@Primary（2026-10-11）</b>：gateway 聚合宿主下 RestTemplate 候选 Bean 已达 13+
 * （workspace 9 个命名 Bean + ai/cognitive 命名模板），存量「裸注入 RestTemplate」的
 * 引擎客户端（ontology {@code SecurityEngineClient} 等）按类型注入歧义 → 启动崩溃。
 * 本 Bean 是全仓唯一无限定名的默认模板，标 @Primary 恢复「裸注入 = 拿默认模板」语义；
 * 各定向客户端（workspace*Rest/aiRestTemplate/cognitiveRestTemplate）继续按
 * @Qualifier 精确消费，不受影响。</p>
 */
@Configuration
public class KbEngineRestConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
