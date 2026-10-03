package com.chinacreator.gzcm.engine.kb.vector;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEmbedding;
import com.chinacreator.gzcm.engine.kb.repo.PgVectorSupport;
import com.chinacreator.gzcm.engine.kb.repo.QueryEmbeddingHelper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeArticleMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEmbeddingMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeRetrievalServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F04-11 向量形态显式降级验收测试（MC05/ADR-11/湖规 v2.0"降级须显式标记"，
 * mvn -Dtest=VectorFormDegradationTest）。
 *
 * <p>承载两条不变式：
 * <ol>
 *   <li>{@link #jsonbFallbackIsFlaggedNotSilent()} — pgvector 不可用（或向量路径失败）时，
 *       RAG 响应必须携带 {@code vectorDegraded=true} + {@code vectorDegradedReason} +
 *       {@code vectorDegradedImpact}（对治 K-33/PgVectorSupport 探测失败仅 {@code log.warn}、
 *       JSONB 回退静默降级）；向量形态成功时 {@code vectorDegraded} 必须为 {@code false}，
 *       不带原因/影响字段（无降级即无标记噪声）。</li>
 *   <li>{@link #vectorColumnNeverInControlSchema()} — DDL 单源目录内任何 {@code vector(n)}
 *       类型列声明，其属主表 schema 只能是 R-10 ① 批准的落点 {@code ecos_knowledge}
 *       （V137/V185 的 {@code knowledge_embedding.embedding_vec}）；不得落到其余任何
 *       控制 schema（public/ecos_control/ecos_data/ecos_ontology/ecos_ai/ecos_cognitive）
 *       或业务域（ecos_dw）。降级业务侧载体 V185 用 {@code embedding_text TEXT}，
 *       非 vector 类型，故不受此断言影响。</li>
 * </ol>
 *
 * @author ECOS KB Team
 */
class VectorFormDegradationTest {

    // ── 依赖 mock —— RAG 检索降级路径只需 embeddingMapper / queryEmbeddingHelper / pgVectorSupport ──

    private KnowledgeRetrievalServiceImpl buildService(PgVectorSupport pgVectorSupport,
                                                       QueryEmbeddingHelper embeddingHelper,
                                                       KnowledgeEmbeddingMapper embeddingMapper) {
        return new KnowledgeRetrievalServiceImpl(
                mock(KnowledgeArticleMapper.class),
                embeddingMapper,
                mock(KnowledgeNodeMapper.class),
                mock(KnowledgeEdgeMapper.class),
                embeddingHelper,
                pgVectorSupport,
                mock(JdbcTemplate.class),
                "text-embedding-3-small",
                "http://llm-gateway");
    }

    /**
     * pgvector 不可用 → 关键词回退路径。响应必须显式标记 vectorDegraded=true + 原因 + 影响。
     */
    @Test
    void jsonbFallbackIsFlaggedNotSilent() {
        PgVectorSupport pgVectorSupport = mock(PgVectorSupport.class);
        when(pgVectorSupport.isAvailable()).thenReturn(false);
        KnowledgeEmbeddingMapper embeddingMapper = mock(KnowledgeEmbeddingMapper.class);
        when(embeddingMapper.searchByKeyword(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Collections.emptyList());

        KnowledgeRetrievalServiceImpl service =
                buildService(pgVectorSupport, mock(QueryEmbeddingHelper.class), embeddingMapper);
        service.checkPgVectorExtension();

        Map<String, Object> result = service.ragQuery("如何合规", 3);

        assertTrue((Boolean) result.get("vectorDegraded"),
                "pgvector 不可用时响应必须标记 vectorDegraded=true（禁止静默降级，K-33）");
        assertNotNull(result.get("vectorDegradedReason"), "降级必须携带原因（vectorDegradedReason）");
        String reason = String.valueOf(result.get("vectorDegradedReason"));
        assertTrue(reason.contains("pgvector"), "原因须定位到 pgvector 扩展不可用，实际=" + reason);
        assertNotNull(result.get("vectorDegradedImpact"), "降级必须显式声明影响（vectorDegradedImpact）");
        // 回退仍须出结果（空查询输入仍返回结构完整），向量形态降级不吞字段
        assertNotNull(result.get("sources"));
    }

    /**
     * pgvector 可用但嵌入向量为空（llm-gateway 未返回）→ 仍须显式标记降级，而非伪装成向量检索。
     */
    @Test
    void embedFailureIsAlsoFlaggedNotSilent() {
        PgVectorSupport pgVectorSupport = mock(PgVectorSupport.class);
        when(pgVectorSupport.isAvailable()).thenReturn(true);
        QueryEmbeddingHelper embeddingHelper = mock(QueryEmbeddingHelper.class);
        when(embeddingHelper.embed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
        KnowledgeEmbeddingMapper embeddingMapper = mock(KnowledgeEmbeddingMapper.class);
        when(embeddingMapper.searchByKeyword(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Collections.emptyList());

        KnowledgeRetrievalServiceImpl service =
                buildService(pgVectorSupport, embeddingHelper, embeddingMapper);
        service.checkPgVectorExtension();

        Map<String, Object> result = service.ragQuery("如何合规", 3);

        assertTrue((Boolean) result.get("vectorDegraded"),
                "嵌入向量为空、回退关键词时须显式标记 vectorDegraded=true");
        assertTrue(String.valueOf(result.get("vectorDegradedReason")).contains("嵌入"),
                "原因须定位到嵌入向量获取失败");
    }

    /**
     * 向量形态成功（嵌入 + HNSW 命中）→ 权威形态生效，响应 vectorDegraded 必须为 false，
     * 且不携带原因/影响（无降级即无标记噪声）。
     */
    @Test
    void vectorFormSuccessIsNotMarkedDegraded() {
        PgVectorSupport pgVectorSupport = mock(PgVectorSupport.class);
        when(pgVectorSupport.isAvailable()).thenReturn(true);
        QueryEmbeddingHelper embeddingHelper = mock(QueryEmbeddingHelper.class);
        when(embeddingHelper.embed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("[0.1,0.2,0.3]");
        KnowledgeEmbeddingMapper embeddingMapper = mock(KnowledgeEmbeddingMapper.class);
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", "chunk-1");
        hit.put("articleid", "art-1");
        hit.put("chunktext", "命中内容");
        hit.put("score", 0.9);
        when(embeddingMapper.searchByVector(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(hit));

        KnowledgeRetrievalServiceImpl service =
                buildService(pgVectorSupport, embeddingHelper, embeddingMapper);
        service.checkPgVectorExtension();

        Map<String, Object> result = service.ragQuery("如何合规", 3);

        assertEquals(Boolean.FALSE, result.get("vectorDegraded"),
                "向量形态成功命中时不得标记降级");
        assertFalse(result.containsKey("vectorDegradedReason"), "无降级时不应携带原因字段");
        assertFalse(result.containsKey("vectorDegradedImpact"), "无降级时不应携带影响字段");
        assertEquals(1, result.get("sourcesCount"));
    }

    // ── 向量列 schema 门禁 ──

    /** DDL 单源目录（铁律 v2.0 §3.1；兼容 surefire CWD=模块目录与 IDE CWD=工程根两种形态）。 */
    private static final Path MIGRATION_DIR = resolveMigrationDir();

    /** R-10 ① 批准的向量列唯一落点（V137/V185 的 knowledge_embedding.embedding_vec）。 */
    private static final String APPROVED_VECTOR_SCHEMA = "ecos_knowledge";

    private static Path resolveMigrationDir() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            Path candidate = cur.resolve("gateway/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)
                    && Files.exists(candidate.resolve("V137__knowledge_embedding_vector_column.sql"))) {
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new AssertionError(
                "未找到 DDL 单源目录 gateway/src/main/resources/db/migration（铁律 §3.1）；"
                        + "从 " + Path.of("").toAbsolutePath().normalize() + " 向上递归 10 级均失败");
    }

    /**
     * 全仓 DDL 扫：任何 {@code vector(n)} 类型列声明的属主表 schema 必须是 {@code ecos_knowledge}
     * （R-10 ① 三方口径批准的落点）。禁止落入其余控制 schema 或业务域。
     */
    @Test
    void vectorColumnNeverInControlSchema() throws IOException {
        List<String> violations = new java.util.ArrayList<>();
        int declaredCount = 0;
        for (Path sql : Files.list(MIGRATION_DIR)
                .filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted()
                .toList()) {
            String body = stripComments(Files.readString(sql, StandardCharsets.UTF_8));
            String fileName = sql.getFileName().toString();
            // 只关心落脚点：含 vector(n) 类型声明的语句
            Matcher vec = Pattern.compile("\\bvector\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            if (!vec.find()) {
                continue;
            }
            // 逐语句归因属主表 schema（ALTER TABLE sch.tbl / CREATE TABLE [IF NOT EXISTS] sch.tbl）
            for (String stmt : body.split(";")) {
                if (!vecTypePresent(stmt)) {
                    continue;
                }
                declaredCount++;
                String schema = owningSchema(stmt);
                if (schema == null) {
                    violations.add(fileName + "：vector 类型列声明未落到 schema 限定表（裸名向量=MC05 越界）");
                } else if (!APPROVED_VECTOR_SCHEMA.equals(schema)) {
                    violations.add(fileName + "：vector 类型列落到控制/业务域 schema '" + schema
                            + "'（R-10 ① 批准落点仅为 " + APPROVED_VECTOR_SCHEMA + "）");
                }
            }
        }
        assertTrue(declaredCount > 0,
                "预期至少一处 vector(n) 类型列声明（V137/V185 knowledge_embedding.embedding_vec），"
                        + "0 处说明门禁扫描失效");
        assertTrue(violations.isEmpty(),
                "向量列越出 R-10 ① 批准落点 ecos_knowledge：\n  " + String.join("\n  ", violations));
    }

    /** 归属 schema：优先 ALTER TABLE 目标，其次 CREATE TABLE 目标；无则 null。 */
    private static String owningSchema(String stmt) {
        Matcher alter = Pattern.compile(
                "ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(?<sch>\\w+)\\s*\\.\\s*\\w+",
                Pattern.CASE_INSENSITIVE).matcher(stmt);
        if (alter.find()) {
            return alter.group("sch");
        }
        Matcher create = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?<sch>\\w+)\\s*\\.\\s*\\w+",
                Pattern.CASE_INSENSITIVE).matcher(stmt);
        if (create.find()) {
            return create.group("sch");
        }
        return null;
    }

    private static boolean vecTypePresent(String stmt) {
        return Pattern.compile("\\bvector\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE)
                .matcher(stmt).find();
    }

    /** 剥离注释与字符串字面量（防 COMMENT '...vector...' 等词面误判）。 */
    private static String stripComments(String raw) {
        String s = raw.replaceAll("(?s)/\\*.*?\\*/", " ");   // 块注释
        s = s.replaceAll("'[^']*'", "''");                    // 单引号字符串
        s = s.replaceAll("\"[^\"]*\"", "\"\"");               // 双引号标识符
        s = s.replaceAll("--[^\\n]*", " ");                   // 行尾 --
        s = s.replaceAll("(?m)^\\s*--.*$", " ");              // 整行 --
        return s;
    }
}
