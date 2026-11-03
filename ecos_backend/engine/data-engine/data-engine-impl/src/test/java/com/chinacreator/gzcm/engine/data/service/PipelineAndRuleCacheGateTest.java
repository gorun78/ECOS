package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineDefinition;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineRepository;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSaveDTO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.dao.DqRuleLifecycleDao;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;
import com.chinacreator.gzcm.engine.data.quality.service.DqRuleLifecycleServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 14 类矩阵 #5「Pipeline」/#6「质量规则」缓存门（§2.9.8①；#147AI）。
 *
 * <p>四条：① 命中免库；② 写后失效回源；③ 缓存关闭逐次回源；④ 版本清单读面命中后不再查库。</p>
 *
 * @author ECOS Data #147AI
 */
class PipelineAndRuleCacheGateTest {

    private static final CacheSettings ENABLED = new CacheSettings(true, 600L, 60L, false, "dev");

    private static ObjectProvider<RuntimeCacheManager> provider(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        RuntimeCacheManager manager = new RuntimeCacheManager(new ObjectProvider<>() {
            @Override
            public CacheSettingsSource getObject(Object... args) {
                return src;
            }

            @Override
            public CacheSettingsSource getObject() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfAvailable() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfUnique() {
                return src;
            }
        });
        return new ObjectProvider<>() {
            @Override
            public RuntimeCacheManager getObject(Object... args) {
                return manager;
            }

            @Override
            public RuntimeCacheManager getObject() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfAvailable() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfUnique() {
                return manager;
            }
        };
    }

    private static void injectCache(Object target, ObjectProvider<RuntimeCacheManager> provider) {
        try {
            Field field = target.getClass().getDeclaredField("cacheProvider");
            field.setAccessible(true);
            field.set(target, provider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PipelineDefinition definition(String id) {
        PipelineDefinition def = new PipelineDefinition();
        def.setId(id);
        def.setName("pipe-" + id);
        def.setStatus("DRAFT");
        return def;
    }

    @Test
    @DisplayName("Pipeline 定义命中免库，更新后失效回源")
    void pipelineDefinitionIsCachedAndEvictedOnUpdate() {
        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineDefinition def = definition("p1");
        when(repository.findDefinitionById("p1")).thenReturn(def);
        when(repository.updateDefinition(any(), any(), any(), any())).thenReturn(def);
        when(repository.definitionExists("p1")).thenReturn(true);

        PipelineServiceImpl service = new PipelineServiceImpl(repository, mock(PipelineSecurityService.class));
        injectCache(service, provider(ENABLED));

        service.getDefinition("p1");
        service.getDefinition("p1");
        verify(repository, times(1)).findDefinitionById("p1");

        service.updateDefinition("p1", new PipelineSaveDTO());
        service.getDefinition("p1");
        verify(repository, times(2)).findDefinitionById("p1");
    }

    @Test
    @DisplayName("Pipeline 缓存关闭：逐次回源")
    void pipelineDisabledAlwaysReadsDb() {
        PipelineRepository repository = mock(PipelineRepository.class);
        when(repository.findDefinitionById("p1")).thenReturn(definition("p1"));
        PipelineServiceImpl service = new PipelineServiceImpl(repository, mock(PipelineSecurityService.class));
        injectCache(service, provider(CacheSettings.DISABLED));

        service.getDefinition("p1");
        service.getDefinition("p1");
        verify(repository, times(2)).findDefinitionById("p1");
    }

    @Test
    @DisplayName("DQ 版本清单命中免库，getVersion 复用同一份清单")
    void dqVersionsCachedAndSharedWithGetVersion() {
        DqRuleMapper mapper = mock(DqRuleMapper.class);
        DqRuleLifecycleDao dao = mock(DqRuleLifecycleDao.class);
        DqRuleVO rule = new DqRuleVO();
        rule.setId("r1");
        when(dao.findRule(eq("r1"), anyBoolean(), any())).thenReturn(List.of(rule));
        DqRuleVersionVO v1 = new DqRuleVersionVO();
        v1.setVersionNumber(1);
        DqRuleVersionVO v2 = new DqRuleVersionVO();
        v2.setVersionNumber(2);
        when(mapper.listVersionsByRuleId("r1")).thenReturn(List.of(v1, v2));

        DqRuleLifecycleServiceImpl service = new DqRuleLifecycleServiceImpl(mapper,
                mock(DqSecurityService.class), dao);
        injectCache(service, provider(ENABLED));

        assertEquals(2, service.listVersions("r1").size());
        assertEquals(2, service.listVersions("r1").size());
        assertEquals(2, service.getVersion("r1", 2).getVersionNumber());
        verify(mapper, times(1)).listVersionsByRuleId("r1");
    }

    @Test
    @DisplayName("DQ 缓存关闭：逐次回源")
    void dqDisabledAlwaysReadsDb() {
        DqRuleMapper mapper = mock(DqRuleMapper.class);
        DqRuleLifecycleDao dao = mock(DqRuleLifecycleDao.class);
        DqRuleVO rule = new DqRuleVO();
        rule.setId("r1");
        when(dao.findRule(eq("r1"), anyBoolean(), any())).thenReturn(List.of(rule));
        when(mapper.listVersionsByRuleId("r1")).thenReturn(List.of());

        DqRuleLifecycleServiceImpl service = new DqRuleLifecycleServiceImpl(mapper,
                mock(DqSecurityService.class), dao);
        injectCache(service, provider(CacheSettings.DISABLED));

        service.listVersions("r1");
        service.listVersions("r1");
        verify(mapper, times(2)).listVersionsByRuleId("r1");
    }
}
