package com.chinacreator.gzcm.engine.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * kb-engine REST 客户端配置。
 * <p>为 KnowledgeExtractionService 提供 RestTemplate bean（调用 ai-engine 用）。</p>
 */
@Configuration
public class KbEngineRestConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
