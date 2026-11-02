package com.chinacreator.gzcm.engine.kb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.NotFoundException;
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
 * 改分类写端的口径门（#147U 编辑腿）。
 *
 * <p>五条：① Rule 类带 ruleRef 正常写；② 非 Rule 类写 NULL（防影子规则残留）；
 * ③ Rule 缺 ruleRef / 非 Rule 带 ruleRef / 分类空 一律 400 且不写库；
 * ④ 条目不存在 → 404（禁假成功）；⑤ 回读返回落库后的真实行。</p>
 *
 * @author ECOS KB #147U
 */
class KnowledgeCategoryUpdateTest {

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

    private static KnowledgeArticle row(String category) {
        KnowledgeArticle a = new KnowledgeArticle();
        a.setId("a-1");
        a.setTitle("t");
        a.setKnowledgeCategory(category);
        return a;
    }

    @Test
    @DisplayName("Rule 类：带 ruleRef 写 Rule + ruleRef，并回读真实行")
    void ruleCategoryWritesRuleRef() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.updateCategory("a-1", "Rule", "rule-9")).thenReturn(1);
        when(mapper.findById("a-1")).thenReturn(row("Rule"));

        KnowledgeArticle out = service(mapper).updateArticleCategory("a-1", "rule", " rule-9 ");

        assertEquals("Rule", out.getKnowledgeCategory());
        verify(mapper).updateCategory("a-1", "Rule", "rule-9");
    }

    @Test
    @DisplayName("非 Rule 类：显式写 NULL，防降级后残留影子规则引用")
    void nonRuleCategoryClearsRuleRef() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.updateCategory("a-1", "Fact", null)).thenReturn(1);
        when(mapper.findById("a-1")).thenReturn(row("Fact"));

        service(mapper).updateArticleCategory("a-1", "Fact", null);

        verify(mapper).updateCategory("a-1", "Fact", null);
    }

    @Test
    @DisplayName("非法组合一律 400 且不写库")
    void invalidCombinationsRejectedWithoutWrite() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        KnowledgeRetrievalServiceImpl svc = service(mapper);

        assertThrows(ValidationException.class, () -> svc.updateArticleCategory("a-1", "Rule", "  "));
        assertThrows(ValidationException.class, () -> svc.updateArticleCategory("a-1", "Fact", "rule-1"));
        assertThrows(ValidationException.class, () -> svc.updateArticleCategory("a-1", "  ", null));
        assertThrows(ValidationException.class, () -> svc.updateArticleCategory("a-1", "NotACategory", null));
        assertThrows(ValidationException.class, () -> svc.updateArticleCategory(" ", "Fact", null));

        verify(mapper, never()).updateCategory(anyString(), any(), any());
    }

    @Test
    @DisplayName("条目不存在 → NotFound（禁把 0 行更新当成功）")
    void missingArticleIsNotFound() {
        KnowledgeArticleMapper mapper = mock(KnowledgeArticleMapper.class);
        when(mapper.updateCategory("nope", "Fact", null)).thenReturn(0);

        assertThrows(NotFoundException.class,
                () -> service(mapper).updateArticleCategory("nope", "Fact", null));
    }
}
