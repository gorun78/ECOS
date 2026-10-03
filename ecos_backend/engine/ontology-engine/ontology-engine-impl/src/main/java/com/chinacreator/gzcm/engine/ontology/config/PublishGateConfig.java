package com.chinacreator.gzcm.engine.ontology.config;

import com.chinacreator.gzcm.engine.ontology.gate.DatanetColumnGuard;
import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 门禁 guard Bean 装配（V1/V2 由 {@code PublishGateService} 内联构造，V3 依赖跨引擎 client 单列在此）。
 * 三 guard 无状态，随 {@code PublishGateService} 同生命周期。
 */
@Configuration
public class PublishGateConfig {

    @Bean
    public DatanetColumnGuard datanetColumnGuard(DataNetResourceClient datanetResourceClient) {
        return new DatanetColumnGuard(datanetResourceClient);
    }
}
