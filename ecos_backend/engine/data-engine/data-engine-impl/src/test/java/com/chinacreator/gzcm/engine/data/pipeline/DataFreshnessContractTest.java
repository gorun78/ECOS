package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.engine.data.controller.DataFreshnessController;
import com.chinacreator.gzcm.engine.data.dao.PipelineRunDao;
import com.chinacreator.gzcm.engine.data.service.DataFreshnessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WAgent Readiness data.staleness_days 供数面契约测试（镜像 {@code MetadataColumnsContractTest}
 * 单 JVM 无 live DB 形态）：Controller → Service → DAO 三层 mock 组合 +
 * ApiResponse JSON 结构断言；另附 DAO SQL 形态断言（读 {@code PipelineRunDao}
 * 源码文本，锁定表名/where/limit 不变，避免后续误改）。
 *
 * <p>取值纪律：无任何成功管道运行时返回 {@code lastSucceededFinishedAt = null}
 * （禁止落 0 或估算值），避免下游把「没有数据」误判为「刚刚入湖」。</p>
 */
class DataFreshnessContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PipelineRunDao dao;
    private DataFreshnessService service;
    private DataFreshnessController controller;

    @BeforeEach
    void setUp() {
        dao = mock(PipelineRunDao.class);
        service = new DataFreshnessService(dao);
        controller = new DataFreshnessController(service);
    }

    @Test
    @DisplayName("有 SUCCEEDED run：响应 $.code=0 且 $.data.lastSucceededFinishedAt 为 ISO-8601 + source=ecos_pipeline_run")
    void withSuccessRunReturnsIsoTimestampAndSource() {
        Instant ts = Instant.parse("2026-10-26T12:00:00Z");
        when(dao.lastSucceededFinishedAt()).thenReturn(Optional.of(ts));

        JsonNode root = MAPPER.valueToTree(controller.freshness());

        assertEquals(0, root.path("code").asInt(), "成功族 code 必须为 0");
        assertTrue(root.path("data").isObject(), "$.data 必须是对象: " + root.path("data"));
        assertEquals(ts.toString(), root.path("data").path("lastSucceededFinishedAt").asText(),
                "时间戳必须以 Instant.toString() 规范化（ISO-8601，UTC）");
        assertEquals("ecos_pipeline_run", root.path("data").path("source").asText(),
                "source 必须锁定物理表名（下游取数口径单源）");
        // 响应体键面锁定：data 段恰好 2 个键，禁加/减字段避免下游解析器断裂
        assertEquals(2, root.path("data").size(), "data 段恰好 2 个键: " + root.path("data"));
    }

    @Test
    @DisplayName("无 SUCCEEDED run：lastSucceededFinishedAt=null（禁 0/估算），code 仍为 0")
    void noSuccessRunReturnsNullTimestampNotZero() {
        when(dao.lastSucceededFinishedAt()).thenReturn(Optional.empty());

        JsonNode root = MAPPER.valueToTree(controller.freshness());

        assertEquals(0, root.path("code").asInt(),
                "code 仍为 0（无数据 ≠ 业务错误，明示不伪装依赖故障）");
        assertTrue(root.path("data").path("lastSucceededFinishedAt").isNull(),
                "没有成功行 → 必须返回 null（null 是\"没有数据\"唯一合法表达）");
        assertEquals("ecos_pipeline_run", root.path("data").path("source").asText(),
                "无数据时 source 仍须给出（体征可追溯）");
    }

    @Test
    @DisplayName("DAO 查询形态：SQL 锁定表名 + status='SUCCEEDED' + finished_at IS NOT NULL + ORDER BY DESC LIMIT 1")
    void daoQueryShapeIsLockedBySourceScan() throws IOException {
        String methodSql = readLastSucceededSqlFromSource();

        assertTrue(methodSql.contains("FROM ecos_pipeline_run"),
                "必须从 ecos_pipeline_run 取数: " + methodSql);
        assertTrue(methodSql.contains("status = 'SUCCEEDED'"),
                "必须过滤 status=SUCCEEDED: " + methodSql);
        assertTrue(methodSql.contains("finished_at IS NOT NULL"),
                "必须排除 NULL finished_at: " + methodSql);
        assertTrue(methodSql.contains("ORDER BY finished_at DESC"),
                "必须倒序取最新（禁回退 MAX）: " + methodSql);
        assertTrue(methodSql.contains("LIMIT 1"),
                "必须 LIMIT 1（禁聚合老行 / 禁全表扫描）: " + methodSql);
        // 禁回潮：禁硬编码显式 schema（如 public.ecos_pipeline_run / ecos_data.ecos_pipeline_run）
        // —— search_path 由 JDBC URL currentSchema 统一声明，SQL 层面禁重复
        String normalized = methodSql.replaceAll("\\s+", " ");
        assertTrue(!normalized.matches(".*\\bpublic\\s*\\.\\secos_pipeline_run.*")
                        && !normalized.matches(".*\\becos_data\\s*\\.\\secos_pipeline_run.*"),
                "表名禁携带硬编码 schema 前缀（search_path 单源声明）: " + normalized);
    }

    /** 从 {@code PipelineRunDao.java} 源码里抽出 lastSucceededFinishedAt 方法体用于 SQL 形态断言。 */
    private static String readLastSucceededSqlFromSource() throws IOException {
        Path src = findSrc("PipelineRunDao.java");
        assertNotNull(src, "PipelineRunDao.java 必须可以被本测试定位");
        String text = Files.readString(src, StandardCharsets.UTF_8);
        int start = text.indexOf("public Optional<Instant> lastSucceededFinishedAt");
        assertTrue(start >= 0, "源码必须含 lastSucceededFinishedAt 公开签名");
        int end = text.indexOf("}", start);
        assertTrue(end > start, "方法体大括号定位失败");
        return text.substring(start, end + 1);
    }

    private static Path findSrc(String file) {
        Path here = Path.of("src/main/java/com/chinacreator/gzcm/engine/data/dao/" + file).toAbsolutePath();
        if (Files.exists(here)) return here;
        return null;
    }
}
