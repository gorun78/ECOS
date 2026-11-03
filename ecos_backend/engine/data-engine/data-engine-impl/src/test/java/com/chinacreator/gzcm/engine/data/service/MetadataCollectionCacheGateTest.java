package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.engine.data.MetadataService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 14 类矩阵 #4「接入元数据」缓存门（§2.9.8①；#147AH）。
 *
 * <p>四条：① 命中即免库（第二次不再查仓库）；② 写入/采集后失效会回源；
 * ③ 缓存关闭时每次都回源（没有缓存 ≠ 没有数据）；④ 值经 JSON 往返后时间字段不失真。</p>
 *
 * @author ECOS Data #147AH
 */
class MetadataCollectionCacheGateTest {

    private static RuntimeCacheManager manager(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        return new RuntimeCacheManager(new ObjectProvider<>() {
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
    }

    private static MetadataCollectionService service(CacheSettings settings,
                                                     DataResourceRepository resourceRepository,
                                                     DataSourceRepository dataSourceRepository) {
        RuntimeCacheManager cache = manager(settings);
        ObjectProvider<RuntimeCacheManager> provider = new ObjectProvider<>() {
            @Override
            public RuntimeCacheManager getObject(Object... args) {
                return cache;
            }

            @Override
            public RuntimeCacheManager getObject() {
                return cache;
            }

            @Override
            public RuntimeCacheManager getIfAvailable() {
                return cache;
            }

            @Override
            public RuntimeCacheManager getIfUnique() {
                return cache;
            }
        };
        return new MetadataCollectionService(mock(MetadataService.class), dataSourceRepository,
                resourceRepository, mock(ConnectorFactory.class), provider);
    }

    private static DataResource resource() {
        DataResource r = new DataResource();
        r.setResourceId("r-1");
        r.setResourceName("orders");
        r.setDatasourceId("ds-1");
        r.setLastSyncTime(LocalDateTime.of(2026, 11, 3, 10, 30, 0));
        return r;
    }

    private static final CacheSettings ENABLED = new CacheSettings(true, 600L, 60L, false, "dev");

    @Test
    @DisplayName("命中即免库：第二次读不再查仓库")
    void hitAvoidsSecondRead() {
        DataResourceRepository repository = mock(DataResourceRepository.class);
        when(repository.findByDatasource("ds-1")).thenReturn(List.of(resource()));
        MetadataCollectionService service = service(ENABLED, repository, mock(DataSourceRepository.class));

        List<DataResource> first = service.getResources("ds-1");
        List<DataResource> second = service.getResources("ds-1");

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(repository, times(1)).findByDatasource("ds-1");
    }

    @Test
    @DisplayName("失效后回源：invalidateCache 让下一次读重新查仓库")
    void evictionForcesReload() {
        DataResourceRepository repository = mock(DataResourceRepository.class);
        when(repository.findByDatasource("ds-1")).thenReturn(List.of(resource()));
        MetadataCollectionService service = service(ENABLED, repository, mock(DataSourceRepository.class));

        service.getResources("ds-1");
        service.invalidateCache("ds-1");
        service.getResources("ds-1");

        verify(repository, times(2)).findByDatasource("ds-1");
    }

    @Test
    @DisplayName("缓存关闭：每次读都回源")
    void disabledCacheAlwaysReadsDb() {
        DataResourceRepository repository = mock(DataResourceRepository.class);
        when(repository.findByDatasource("ds-1")).thenReturn(List.of(resource()));
        MetadataCollectionService service = service(CacheSettings.DISABLED, repository,
                mock(DataSourceRepository.class));

        service.getResources("ds-1");
        service.getResources("ds-1");

        verify(repository, times(2)).findByDatasource("ds-1");
    }

    @Test
    @DisplayName("时间字段 JSON 往返不失真")
    void timeFieldSurvivesRoundTrip() {
        DataResourceRepository repository = mock(DataResourceRepository.class);
        when(repository.findByDatasource("ds-1")).thenReturn(List.of(resource()));
        MetadataCollectionService service = service(ENABLED, repository, mock(DataSourceRepository.class));

        service.getResources("ds-1");
        List<DataResource> cached = service.getResources("ds-1");

        assertNotNull(cached.get(0).getLastSyncTime());
        assertEquals(LocalDateTime.of(2026, 11, 3, 10, 30, 0), cached.get(0).getLastSyncTime());
    }

    @Test
    @DisplayName("全量目录失效后重建")
    void bulkInvalidationRebuilds() {
        DataSourceRepository dataSourceRepository = mock(DataSourceRepository.class);
        DataSourceEntity ds = new DataSourceEntity();
        ds.setDatasourceId("ds-1");
        ds.setDatasourceName("pg");
        when(dataSourceRepository.findAll()).thenReturn(List.of(ds));
        DataResourceRepository repository = mock(DataResourceRepository.class);
        when(repository.findByDatasource(anyString())).thenReturn(List.of(resource()));
        MetadataCollectionService service = service(ENABLED, repository, dataSourceRepository);

        service.getAllResources();
        service.getAllResources();
        verify(dataSourceRepository, times(1)).findAll();

        service.invalidateAllCaches();
        service.getAllResources();
        verify(dataSourceRepository, times(2)).findAll();
    }
}
