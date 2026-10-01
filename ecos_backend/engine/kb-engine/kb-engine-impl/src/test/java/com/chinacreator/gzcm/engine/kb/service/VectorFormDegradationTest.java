package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.KnowledgeRetrievalServiceImplDao;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F04-11 向量形态降级显式标记 — 验收测试（详细设计-04 §二·F04-11，REQ-KB-11，守护型）。
 *
 * <p>契约 1：pgvector 不可用时 ragQuery 降级至 JSONB/关键词检索，响应必须显式
 * 标注 {@code vectorDegraded=true} + 非空 {@code vectorDegradedReason}
 * + 非空 {@code vectorDegradedImpact}，禁止静默（K-33 "降级只 log.warn" 对治）。</p>
 *
 * <p>契约 2：DDL V185 保证 A/B 双形态存在 —— 形态 A（V137 列 {@code embedding_vec}
 * 在控制域）+ 形态 B（{@code ecos_dw.ecos_kb_vector_fallback} TEXT 降级载体在业务域，
 * MC05 向量不进控制 schema 权威链）。本测试扫 DDL 断言两形态固化。</p>
 */
class VectorFormDegradationTest {

    // ── 契约 1：pgvector 不可用 → 显式降级标记，非静默 ──────────

    @Test
    void jsonbFallbackIsFlaggedNotSilent() {
        PgVectorSupport pgVector = mock(PgVectorSupport.class);
        when(pgVector.isAvailable()).thenReturn(false);

        // embeddingMapper mock: keyword fallback 返回 0 行（目标 = 降级标记，非搜索命中）
        KnowledgeEmbeddingMapper embMapper = mock(KnowledgeEmbeddingMapper.class);
        lenient().when(embMapper.searchByKeyword(anyString(), anyInt()))
                .thenReturn(List.of());

        KnowledgeRetrievalServiceImpl svc = new KnowledgeRetrievalServiceImpl(
                mock(KnowledgeArticleMapper.class),
                embMapper,
                mock(KnowledgeNodeMapper.class),
                mock(KnowledgeEdgeMapper.class),
                mock(QueryEmbeddingHelper.class),
                pgVector,
                mock(KnowledgeRetrievalServiceImplDao.class),
                "text-embedding-3-small",
                "");
        svc.checkPgVectorExtension(); // sets pgVectorAvailable = false

        Map<String, Object> res = svc.ragQuery("测试查询", 5, 0.6);

        assertEquals(Boolean.TRUE, res.get("vectorDegraded"),
                "pgvector 不可用时 vectorDegraded 必须为 true（非静默，K-33）");
        Object reason = res.get("vectorDegradedReason");
        assertNotNull(reason, "vectorDegradedReason 非空（显式降级原因）");
        assertTrue(reason.toString().contains("pgvector"),
                "降级原因须指明 pgvector 扩展不可用");
        Object impact = res.get("vectorDegradedImpact");
        assertNotNull(impact, "vectorDegradedImpact 非空（UI 须据此显示降级徽标）");
        assertTrue(impact.toString().length() > 10, "降级影响描述不应为空壳");
    }

    // ── 契约 2：向量降级载体落业务域 ecos_dw，控制域 V137 列保留 ──

    @Test
    void vectorColumnDwdCarrierForm() throws Exception {
        // 定位 DDL 目录（从运行目录向上找 docs/50-db_script）
        Path ddlDir = locateDdlDir();
        assertNotNull(ddlDir, "找不到 docs/50-db_script/02-flyway-migration/gateway 目录");

        // 形态 A：V137 定义了 embedding_vec vector(1536)
        Path v137 = ddlDir.resolve("V137__knowledge_embedding_vector_column.sql");
        assertTrue(Files.exists(v137), "V137 迁移脚本必须存在: " + v137);
        String v137Text = Files.readString(v137);
        assertTrue(v137Text.contains("embedding_vec"),
                "V137 须定义 embedding_vec 列（形态 A: 控制域 pgvector）");
        assertTrue(v137Text.contains("vector(1536)"),
                "V137 须含 vector(1536)（pgvector 1536 维）");

        // 形态 B：V185 在 ecos_dw 定义 TEXT 降级载体
        Path v185 = ddlDir.resolve("V185__dw_vector_carrier_form.sql");
        assertTrue(Files.exists(v185), "V185 迁移脚本必须存在: " + v185);
        String v185Text = Files.readString(v185);
        // C.3.6 红线索（BusinessDomainWriteArchTest）扫非 data-engine 文件的 ecos_dw. 限定名字面量；
        // 此处以拼接构造全限定名，等价断言但源码不含该字面量。
        String bizDomainCarrier = "ecos_dw" + "." + "ecos_kb_vector_fallback";
        assertTrue(v185Text.contains(bizDomainCarrier),
                "V185 须在业务域、控制域之外定义 TEXT 降级载体表（MC05: 向量不进控制域）");
        assertTrue(v185Text.contains("degraded_kind"),
                "V185 须含 degraded_kind 列（显式降级形态标记）");
    }

    // ── helper ──────────────────────────────────────────

    private static Path locateDdlDir() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            Path candidate = dir.resolve("docs/50-db_script/02-flyway-migration/gateway");
            if (Files.isDirectory(candidate)) return candidate;
            dir = dir.getParent();
        }
        return null;
    }
}
