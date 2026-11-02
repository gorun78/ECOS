package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dao.KnowledgeRetrievalServiceImplDao;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;

/**
 * 七分类筛选读端的口径门（#147U 后端口；F-KB-18）。
 *
 * <p>钉四件事：① 合法分类（大小写不敏感）按闭集码查询；② 未知/空分类抛
 * {@link ValidationException}（HTTP 400），<b>禁「非法值回落全量」</b>——那会让前端筛选
 * 静默失效却显示成功；③ {@code limit} 夹紧到 [1,100]，不给无界列表；④ 存量行无分类时
 * 返回空集是如实形态（V301 只加列未回填），不是错误。</p>
 *
 * @author ECOS KB #147U
 */
class KnowledgeCategoryFilterTest {

    private static KnowledgeRetrievalServiceImpl service(KnowledgeArticleMapper mapper) {
        return new KnowledgeRetrievalServiceImpl(
                mapper,
                mock(KnowledgeEmbeddingMapper.class),
                mock(KnowledgeNodeMapper.class),
                mock(KnowledgeEdgeMapper.class),
                mock(QueryEmbeddingHelper.class),
                mock(PgVectorSupport.class),
                mock(KnowledgeRetrievalServiceImplDao.class),
                "text-embedding-3-small",
                "");
    }

    @Test
    @DisplayName("合法分类：大小写不敏感，按闭集码 Fact/Rule… 等值查询")
    void validCategoryQueriesByClosedSetCode() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(anyString(), anyInt())).thenReturn(List.of(new KnowledgeArticle()));
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        assertEquals(1, svc.listByCategory("Fact", 20).size());
        verify(mapper).findByCategory("Fact", 20);

        svc.listByCategory("rule", 20);
        verify(mapper).findByCategory("Rule", 20);
    }

    @Test
    @DisplayName("反证：未知分类必 400，且不查库、不回落全量")
    void unknownCategoryIsRejectedNotDegraded() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        assertThrows(ValidationException.class, () -> svc.listByCategory("NotACategory", 20));
        assertThrows(ValidationException.class, () -> svc.listByCategory(null, 20));
        assertThrows(ValidationException.class, () -> svc.listByCategory("  ", 20));
        verify(mapper, never()).findByCategory(anyString(), anyInt());
    }

    @Test
    @DisplayName("limit 夹紧到 [1,100]：0/负数取 1，超限取 100")
    void limitIsClamped() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(anyString(), anyInt())).thenReturn(List.of());
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        svc.listByCategory("Fact", 0);
        svc.listByCategory("Fact", -5);
        verify(mapper, org.mockito.Mockito.times(2)).findByCategory("Fact", 1);

        svc.listByCategory("Fact", 500);
        verify(mapper).findByCategory("Fact", 100);
    }

    @Test
    @DisplayName("存量无分类行：合法分类返回空集（如实形态，不抛错、不虚构数据）")
    void emptyResultForUnbackfilledRows() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(anyString(), anyInt())).thenReturn(List.of());

        assertTrue(service(mapper).listByCategory("Evidence", 20).isEmpty());
    }
}
