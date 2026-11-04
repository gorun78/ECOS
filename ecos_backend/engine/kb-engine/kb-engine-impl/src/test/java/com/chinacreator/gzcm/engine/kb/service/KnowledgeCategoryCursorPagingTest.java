package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticlePage;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;

/**
 * 七分类游标分页的口径门（#147BA；补 §7.1.2「列表游标分页未做」）。
 *
 * <p>钉六件事：① 首次调用（游标空）走 {@code findByCategory}，不误带游标条件；
 * ② 满页才给 {@code nextCursor}，末页必须 {@code null}（否则调用方会多翻一次空页）；
 * ③ 回传游标时按解码出的 {@code (epochMillis, id)} 走 keyset 查询，不用 OFFSET；
 * ④ 非法游标抛 {@code ValidationException}（400），<b>禁</b>静默回第一页；
 * ⑤ 未知分类同样 400、且不触库（禁「非法值回落全量」）；
 * ⑥ {@code limit} 夹紧到 [1,100]。</p>
 *
 * @author ECOS KB #147BA
 */
class KnowledgeCategoryCursorPagingTest {

    private static final long LAST_TS = 1_700_000_000_000L;
    private static final String LAST_ID = "a-article-1";

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

    private static KnowledgeArticle article(String id, long createdAt) {
        KnowledgeArticle a = new KnowledgeArticle();
        a.setId(id);
        a.setCreatedAt(createdAt);
        return a;
    }

    @Test
    @DisplayName("首屏：不带游标走 findByCategory；未满页 ⇒ nextCursor 为 null")
    void firstPageWithoutCursorAndNotFull() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(eq("Fact"), eq(20))).thenReturn(List.of(article("a1", 1L)));
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        KnowledgeArticlePage page = svc.listByCategoryPage("Fact", 20, null);

        assertEquals(1, page.getItems().size());
        assertNull(page.getNextCursor(), "未满页不该给游标——给了会诱导调用方多翻一次空页");
        verify(mapper, never()).findByCategoryAfter(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("满页 ⇒ 给游标；回传游标时按解码出的 (epochMillis,id) 走 keyset 查询")
    void fullPageEmitsCursorAndCursorIsUsedAsKeyset() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(eq("Fact"), eq(2)))
                .thenReturn(List.of(article("a2", LAST_TS + 1), article(LAST_ID, LAST_TS)));
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        KnowledgeArticlePage first = svc.listByCategoryPage("Fact", 2, null);
        assertNotNull(first.getNextCursor(), "满页必须给游标，否则下一页取不到");
        assertTrue(!first.getNextCursor().contains(":"), "游标必须是不透明串（不得把内部格式漏给调用方）");

        svc.listByCategoryPage("Fact", 2, first.getNextCursor());
        verify(mapper).findByCategoryAfter(eq("Fact"), eq(LAST_TS), eq(LAST_ID), eq(2));
    }

    @Test
    @DisplayName("非法游标 ⇒ ValidationException（禁静默回第一页）")
    void invalidCursorIsRejected() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        assertThrows(ValidationException.class, () -> svc.listByCategoryPage("Fact", 20, "%%not-base64%%"));
        verify(mapper, never()).findByCategory(anyString(), anyInt());
        verify(mapper, never()).findByCategoryAfter(anyString(), anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("未知分类 ⇒ ValidationException 且不触库")
    void unknownCategoryIsRejectedWithoutQuery() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        assertThrows(ValidationException.class, () -> svc.listByCategoryPage("不存在的分类", 20, null));
        verify(mapper, never()).findByCategory(anyString(), anyInt());
    }

    @Test
    @DisplayName("limit 夹紧到 [1,100]")
    void limitIsBounded() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.findByCategory(anyString(), anyInt())).thenReturn(List.of());
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        svc.listByCategoryPage("Fact", 0, null);
        svc.listByCategoryPage("Fact", 999, null);

        verify(mapper).findByCategory("Fact", 1);
        verify(mapper).findByCategory("Fact", 100);
    }
}
