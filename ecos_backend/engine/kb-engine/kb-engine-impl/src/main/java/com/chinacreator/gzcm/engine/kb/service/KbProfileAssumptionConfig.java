package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.KbAssumptionStoreDao;
import com.chinacreator.gzcm.engine.kb.dao.KbProfileStoreDao;
import com.chinacreator.gzcm.engine.kb.service.assumption.JdbcAssumptionStore;
import com.chinacreator.gzcm.engine.kb.service.assumption.KbAssumptionService;
import com.chinacreator.gzcm.engine.kb.service.profile.CuratedFactSourceRestImpl;
import com.chinacreator.gzcm.engine.kb.service.profile.JdbcProfileStore;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * F04-06/07 bean 装配（画像服务 + 假设服务），保持 Service 纯构造器（单测直接 new）。
 *
 * <p>store 经 DAO 走 schema 限定表；画像供数走 datanet REST 只读 CURATED（S-1）。
 * 生成一律经 runtime-task（KbProfileController），本层不含线程/调度（铁律 §2.5-3）。
 */
@Configuration
public class KbProfileAssumptionConfig {

    @Bean
    public JdbcProfileStore kbProfileStore(KbProfileStoreDao dao) {
        return new JdbcProfileStore(dao);
    }

    @Bean
    public KbProfileService kbProfileService(JdbcProfileStore store,
                                             CuratedFactSourceRestImpl facts) {
        return new KbProfileService(store, facts);
    }

    @Bean
    public JdbcAssumptionStore kbAssumptionStore(KbAssumptionStoreDao dao) {
        return new JdbcAssumptionStore(dao);
    }

    @Bean
    public KbAssumptionService kbAssumptionService(JdbcAssumptionStore store) {
        return new KbAssumptionService(store);
    }

    /** 保留 RestTemplate 依赖约束（供 CuratedFactSourceRestImpl 复用同一 bean，避免 new RestTemplate — K-36）。 */
    @Bean
    public CuratedFactSourceRestImpl kbCuratedFactSource(RestTemplate restTemplate,
                                                         @org.springframework.beans.factory.annotation.Value(
                                                                 "${ecos.route.datanet-base-url:}")
                                                         String datanetOverride) {
        return new CuratedFactSourceRestImpl(restTemplate, datanetOverride);
    }
}
