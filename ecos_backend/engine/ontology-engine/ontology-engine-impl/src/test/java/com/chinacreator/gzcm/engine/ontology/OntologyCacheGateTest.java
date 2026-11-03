package com.chinacreator.gzcm.engine.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipGraphVO;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 14 类矩阵 #7「本体映射」缓存门（§2.9.8①；#147AJ）。
 *
 * <p>四条：① 本体清单命中免库；② 本体写入后失效；③ 关系统计/图谱在关系写入后同步失效；
 * ④ 缓存关闭逐次回源。</p>
 *
 * @author ECOS Ontology #147AJ
 */
class OntologyCacheGateTest {

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

    private static OntologyService service(OntologyRepository repository, CacheSettings settings) {
        OntologyService service = new OntologyService(repository, mock(OntologyMappingStore.class));
        try {
            Field field = OntologyService.class.getDeclaredField("cacheProvider");
            field.setAccessible(true);
            field.set(service, provider(settings));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return service;
    }

    private static Map<String, Object> ontologyRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ont-1");
        row.put("code", "demo");
        row.put("name", "Demo");
        row.put("status", "ACTIVE");
        return row;
    }

    @Test
    @DisplayName("本体清单命中免库，写入后失效")
    void ontologyListIsCachedAndEvictedOnWrite() {
        OntologyRepository repository = mock(OntologyRepository.class);
        when(repository.findAllOntologies()).thenReturn(List.of(ontologyRow()));
        when(repository.findOntologyById("ont-1")).thenReturn(ontologyRow());
        OntologyService service = service(repository, ENABLED);

        service.listOntologiesVO();
        service.listOntologiesVO();
        verify(repository, times(1)).findAllOntologies();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Demo2");
        service.updateOntology("ont-1", body);
        service.listOntologiesVO();
        verify(repository, times(2)).findAllOntologies();
    }

    @Test
    @DisplayName("关系清单与图谱命中免库，删除关系后同步失效")
    void relationshipCachesEvictedTogether() {
        OntologyRepository repository = mock(OntologyRepository.class);
        when(repository.findAllRelationships()).thenReturn(List.of());
        when(repository.findAllRelationshipEdges()).thenReturn(List.of());
        when(repository.deleteRelationship("rel-1")).thenReturn(1);
        OntologyService service = service(repository, ENABLED);

        assertNotNull(service.getRelationshipGraphVO());
        service.listAllRelationshipsVO();
        service.listAllRelationshipsVO();
        service.getRelationshipGraphVO();
        verify(repository, times(1)).findAllRelationships();
        verify(repository, times(1)).findAllRelationshipEdges();

        service.deleteRelationship("rel-1");
        service.listAllRelationshipsVO();
        service.getRelationshipGraphVO();
        verify(repository, times(2)).findAllRelationships();
        verify(repository, times(2)).findAllRelationshipEdges();
    }

    @Test
    @DisplayName("缓存关闭：逐次回源")
    void disabledCacheAlwaysReadsDb() {
        OntologyRepository repository = mock(OntologyRepository.class);
        when(repository.findAllOntologies()).thenReturn(List.of(ontologyRow()));
        when(repository.findAllRelationshipEdges()).thenReturn(List.of());
        OntologyService service = service(repository, CacheSettings.DISABLED);

        service.listOntologiesVO();
        service.listOntologiesVO();
        OntologyRelationshipGraphVO graph = service.getRelationshipGraphVO();
        service.getRelationshipGraphVO();

        assertEquals(0, graph.getNodes().size());
        verify(repository, times(2)).findAllOntologies();
        verify(repository, times(2)).findAllRelationshipEdges();
    }
}
