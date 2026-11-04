package com.chinacreator.gzcm.engine.kb.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticle;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeArticlePage;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;

/**
 * 游标分页端点的 <b>wire 契约</b>门（#147BA）。
 *
 * <p>钉两件事：① 路径 {@code GET /api/v1/knowledge/articles/by-category/page} 真的映射到本端点
 * （与既有 {@code /by-category} 不冲突，字面段优先）；② 响应体的键就是 {@code items} /
 * {@code nextCursor}——前端按此取值，多一个字少一个字都算契约破裂。用 standalone MockMvc
 * 只装本控制器，不启上下文。断言走状态行 + body 文本（本仓测试类路径无 Hamcrest，
 * {@code jsonPath} 会编译失败）。</p>
 *
 * @author ECOS KB #147BA
 */
class KnowledgeCategoryPageEndpointTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KnowledgeRetrievalService service = mock(KnowledgeRetrievalService.class);
        KnowledgeArticle a = new KnowledgeArticle();
        a.setId("a-1");
        a.setCreatedAt(1_700_000_000_000L);
        when(service.listByCategoryPage(eq("Fact"), eq(20), isNull()))
                .thenReturn(new KnowledgeArticlePage(List.of(a), "next-token"));
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeArticleController(
                service,
                mock(com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper.class),
                mock(com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper.class),
                mock(PgVectorSupport.class))).build();
    }

    @Test
    @DisplayName("GET by-category/page → 200，wire 键为 items/nextCursor")
    void pageEndpointExposesItemsAndCursor() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/knowledge/articles/by-category/page")
                        .param("category", "Fact")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, result.getResponse().getStatus(), body);
        assertTrue(body.contains("\"items\""), "wire 键必须叫 items: " + body);
        assertTrue(body.contains("\"nextCursor\":\"next-token\""), "游标必须原样回传: " + body);
        assertTrue(body.contains("\"id\":\"a-1\""), "条目字段应保留既有投影: " + body);
    }
}
