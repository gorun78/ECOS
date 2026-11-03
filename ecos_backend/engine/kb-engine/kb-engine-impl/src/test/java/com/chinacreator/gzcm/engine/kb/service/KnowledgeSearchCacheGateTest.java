package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.engine.kb.dao.KnowledgeRetrievalServiceImplDao;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 14 类矩阵 #8「知识库索引」检索结果缓存门（§2.9.8①；#147AL）。
 *
 * <p>五条：① 命中免库；② 规范化查询（大小写/首尾空白）命中同一缓存；
 * ③ 不同查询不串键；④ 写入后失效；⑤ 无租户上下文不缓存（权限先于缓存）。</p>
 *
 * @author ECOS KB #147AL
 */
class KnowledgeSearchCacheGateTest {

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

    private static KnowledgeRetrievalServiceImpl service(KnowledgeArticleMapper articleMapper,
                                                        CacheSettings settings) {
        KnowledgeRetrievalServiceImpl service = new KnowledgeRetrievalServiceImpl(
                articleMapper,
                mock(KnowledgeEmbeddingMapper.class),
                mock(KnowledgeNodeMapper.class),
                mock(KnowledgeEdgeMapper.class),
                mock(QueryEmbeddingHelper.class),
                mock(PgVectorSupport.class),
                mock(KnowledgeRetrievalServiceImplDao.class),
                "text-embedding-3-small",
                "");
        try {
            Field field = KnowledgeRetrievalServiceImpl.class.getDeclaredField("cacheProvider");
            field.setAccessible(true);
            field.set(service, provider(settings));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return service;
    }

    private static KnowledgeArticle article() {
        KnowledgeArticle a = new KnowledgeArticle();
        a.setId("a1");
        a.setTitle("t");
        return a;
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("命中免库，且规范化查询共用同一键")
    void canonicalQueryHitsSameEntry() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.search("billing", 10)).thenReturn(List.of(article()));
        KnowledgeRetrievalServiceImpl service = service(mapper, ENABLED);
        TenantContextHolder.setTenantId("t1");

        assertEquals(1, service.searchArticles("billing", 10).size());
        assertEquals(1, service.searchArticles("  Billing  ", 10).size());
        verify(mapper, times(1)).search("billing", 10);
    }

    @Test
    @DisplayName("不同查询（或 limit）不串键")
    void differentQueriesUseDifferentKeys() {
        assertNotEquals(KnowledgeRetrievalServiceImpl.canonicalQueryDigest("a", 10),
                KnowledgeRetrievalServiceImpl.canonicalQueryDigest("b", 10));
        assertNotEquals(KnowledgeRetrievalServiceImpl.canonicalQueryDigest("a", 10),
                KnowledgeRetrievalServiceImpl.canonicalQueryDigest("a", 20));

        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.search("a", 10)).thenReturn(List.of());
        when(mapper.search("ab", 10)).thenReturn(List.of(article()));
        KnowledgeRetrievalServiceImpl service = service(mapper, ENABLED);
        TenantContextHolder.setTenantId("t1");

        service.searchArticles("a", 10);
        service.searchArticles("ab", 10);
        verify(mapper, times(1)).search("a", 10);
        verify(mapper, times(1)).search("ab", 10);
    }

    @Test
    @DisplayName("写入后失效回源")
    void writeEvictsSearchCache() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.search("q", 10)).thenReturn(List.of(article()));
        when(mapper.updateCategory("a1", "Fact", null)).thenReturn(1);
        when(mapper.findById("a1")).thenReturn(article());
        KnowledgeRetrievalServiceImpl service = service(mapper, ENABLED);
        TenantContextHolder.setTenantId("t1");

        service.searchArticles("q", 10);
        service.searchArticles("q", 10);
        verify(mapper, times(1)).search("q", 10);

        service.updateArticleCategory("a1", "Fact", null);
        service.searchArticles("q", 10);
        verify(mapper, times(2)).search("q", 10);
    }

    @Test
    @DisplayName("无租户上下文：不缓存")
    void noTenantMeansNoCaching() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.search("q", 10)).thenReturn(List.of(article()));
        KnowledgeRetrievalServiceImpl service = service(mapper, ENABLED);

        service.searchArticles("q", 10);
        service.searchArticles("q", 10);
        verify(mapper, times(2)).search("q", 10);
    }
}
