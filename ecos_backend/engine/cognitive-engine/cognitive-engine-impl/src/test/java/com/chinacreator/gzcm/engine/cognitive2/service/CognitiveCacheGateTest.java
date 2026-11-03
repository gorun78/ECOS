package com.chinacreator.gzcm.engine.cognitive2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

import com.chinacreator.gzcm.common.cognitive.BeliefDistributionVO;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelPublishGateEvaluator;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 14 类矩阵 #9「认知资产模型」/#10「心智模型」缓存门（§2.9.8①；#147AK）。
 *
 * <p>五条：① 模型版本读面命中免库；② 模型写入（哈希冻结）后整域失效；
 * ③ 心智模型读面命中免库；④ 注册写入后失效；⑤ 缓存关闭逐次回源。</p>
 *
 * @author ECOS Cognitive #147AK
 */
class CognitiveCacheGateTest {

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

    private static void injectCache(Object target, Class<?> type, CacheSettings settings) {
        try {
            Field field = type.getDeclaredField("cacheProvider");
            field.setAccessible(true);
            field.set(target, provider(settings));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> modelRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowId", "row-1");
        row.put("model_id", "m1");
        row.put("version", 1);
        row.put("registry_state", "DRAFT");
        return row;
    }

    @Test
    @DisplayName("模型版本读面命中免库，哈希冻结后整域失效")
    void modelVersionsCachedAndEvictedOnWrite() {
        ModelRegistryDao dao = mock(ModelRegistryDao.class);
        when(dao.getModel("m1")).thenReturn(List.of(modelRow()));
        when(dao.selectRowById("row-1")).thenReturn(modelRow());
        when(dao.updateContentHashIfDraft(any(), any())).thenReturn(1);
        ModelRegistryService service = new ModelRegistryService(dao, mock(ModelPublishGateEvaluator.class));
        injectCache(service, ModelRegistryService.class, ENABLED);

        service.getModel("m1");
        service.getModel("m1");
        verify(dao, times(1)).getModel("m1");

        assertNotNull(service.computeAndFreezeContentHash("row-1"));
        service.getModel("m1");
        verify(dao, times(2)).getModel("m1");
    }

    @Test
    @DisplayName("模型缓存关闭：逐次回源")
    void modelDisabledAlwaysReadsDb() {
        ModelRegistryDao dao = mock(ModelRegistryDao.class);
        when(dao.getModel("m1")).thenReturn(List.of(modelRow()));
        ModelRegistryService service = new ModelRegistryService(dao, mock(ModelPublishGateEvaluator.class));
        injectCache(service, ModelRegistryService.class, CacheSettings.DISABLED);

        service.getModel("m1");
        service.getModel("m1");
        verify(dao, times(2)).getModel("m1");
    }

    @Test
    @DisplayName("心智模型读面命中免库，注册写入后失效")
    void beliefLatestCachedAndEvictedOnRegister() {
        BeliefStore store = mock(BeliefStore.class);
        EvidenceStore evidenceStore = mock(EvidenceStore.class);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("id", "b1");
        normalized.put("variable_name", "v1");
        normalized.put("domain", "d1");
        normalized.put("version", 1);
        normalized.put("distribution", List.of());
        when(store.findLatest("v1", "d1")).thenReturn(modelRow());
        when(store.toNormalizedRow(any(), any())).thenReturn(normalized);
        when(store.maxVersion("v1", "d1")).thenReturn(0);
        when(evidenceStore.fieldString(any(), org.mockito.ArgumentMatchers.eq("id"))).thenReturn("b1");

        CognitiveBeliefService service = new CognitiveBeliefService(store, evidenceStore,
                new ObjectMapper(), mock(MentalEventPublisher.class));
        injectCache(service, CognitiveBeliefService.class, ENABLED);

        BeliefDistributionVO first = service.getLatest("v1", "d1");
        assertNotNull(first);
        assertEquals("b1", first.getId());
        service.getLatest("v1", "d1");
        verify(store, times(1)).findLatest("v1", "d1");

        BeliefSaveDTO dto = new BeliefSaveDTO();
        dto.setVariableName("v1");
        dto.setDomain("d1");
        BeliefSaveDTO.OutcomeProb point = new BeliefSaveDTO.OutcomeProb();
        point.setOutcome("yes");
        point.setProb(1.0d);
        dto.setDiscreteDistribution(List.of(point));
        service.register(dto);

        service.getLatest("v1", "d1");
        verify(store, times(2)).findLatest("v1", "d1");
    }
}
