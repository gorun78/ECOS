package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.nav.model.AssetStatusItemVO;
import com.chinacreator.gzcm.engine.kb.nav.model.AssetStatusVO;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * F10 后端 — GET /api/v1/knowledge/assets/status 端点测试（3 case）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>多 ids 命中 — Mock 双引擎返回部分命中</li>
 *   <li>边界 100 — 恰好 100 ids 不拒绝</li>
 *   <li>&gt; 100 拒绝 — 101 ids 返回 400</li>
 * </ol>
 *
 * @since PMO-D 2026-09-24
 */
@ExtendWith(MockitoExtension.class)
class AssetStatusControllerTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private KnowledgeNodeMapper nodeMapper;

    @Mock
    private KnowledgeEmbeddingMapper embeddingMapper;

    @Mock
    private PgVectorSupport pgVectorSupport;

    @InjectMocks
    private KnowledgeArticleController controller;

    @BeforeEach
    void setUp() {
        // 默认 pgvector 可用
        lenient().when(pgVectorSupport.isAvailable()).thenReturn(true);
    }

    @Test
    @DisplayName("Case 1: 多 ids 命中 — 3 个 id 中 2 入图 + 1 入向量")
    void multiIdsPartialHit() {
        when(nodeMapper.batchExists(anyList())).thenReturn(List.of("a1", "a2"));
        when(embeddingMapper.batchExists(anyList())).thenReturn(List.of("a1"));

        ApiResponse<AssetStatusVO> resp = controller.assetStatus("a1, a2, a3");

        assertTrue(resp.isSuccess());
        AssetStatusVO vo = resp.getData();
        assertNotNull(vo);
        assertEquals(3, vo.getItems().size());
        for (AssetStatusItemVO item : vo.getItems()) {
            if ("a1".equals(item.getArticleId())) {
                assertTrue(item.isGraph());
                assertTrue(item.isVector());
            } else if ("a2".equals(item.getArticleId())) {
                assertTrue(item.isGraph());
                assertFalse(item.isVector());
            } else if ("a3".equals(item.getArticleId())) {
                assertFalse(item.isGraph());
                assertFalse(item.isVector());
            }
        }
    }

    @Test
    @DisplayName("Case 2: 边界 100 — 恰好 100 ids 不拒绝")
    void boundary100IdsOk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(',');
            sb.append("id-").append(i);
        }
        when(nodeMapper.batchExists(anyList())).thenReturn(List.of());
        when(embeddingMapper.batchExists(anyList())).thenReturn(List.of());

        ApiResponse<AssetStatusVO> resp = controller.assetStatus(sb.toString());

        assertTrue(resp.isSuccess(), "100 ids 应成功，不应拒绝");
        assertEquals(100, resp.getData().getItems().size());
    }

    @Test
    @DisplayName("Case 3: > 100 拒绝 — 101 ids 抛 ValidationException（→ 400）")
    void over100Rejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) sb.append(',');
            sb.append("id-").append(i);
        }

        assertThrows(ValidationException.class, () -> controller.assetStatus(sb.toString()),
                "101 ids 应抛 ValidationException（→ 400）");
    }
}
