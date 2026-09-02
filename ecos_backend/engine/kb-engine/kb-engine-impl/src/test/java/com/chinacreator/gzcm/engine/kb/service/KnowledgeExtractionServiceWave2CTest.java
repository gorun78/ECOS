package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-2C (PMO-24) 审批闭环单元测试 — kb-engine-impl。
 *
 * <p>覆盖 KnowledgeExtractionService.approve/reject 的状态机行为，
 * 使用 mock JdbcTemplate 验证 SQL 交互。</p>
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02
 */
class KnowledgeExtractionServiceWave2CTest {

    private KnowledgeExtractionService service;
    private JdbcTemplate mockJdbc;
    private ComplianceRuleMapper mockRuleMapper;
    private KGWriterService mockKgWriter;
    private DocumentParserService mockDocParser;
    private EntityLinkerService mockEntityLinker;

    @BeforeEach
    void setUp() {
        mockJdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        mockRuleMapper = org.mockito.Mockito.mock(ComplianceRuleMapper.class);
        mockKgWriter = org.mockito.Mockito.mock(KGWriterService.class);
        mockDocParser = new DocumentParserService(new MinerUHttpParser());
        mockEntityLinker = org.mockito.Mockito.mock(EntityLinkerService.class);

        service = new KnowledgeExtractionService(
            mockJdbc, mockRuleMapper, mockKgWriter, mockDocParser, mockEntityLinker);
    }

    // ── UT-6: reject 带 reason → 数据库写入 rejected_reason ──

    @Test
    @DisplayName("UT-6: reject 带 reason → status=REJECTED + rejected_reason 写入 DB")
    void rejectWithReasonShouldUpdateStatusAndReason() {
        // mock: 查询 status 返回 PENDING_REVIEW
        org.mockito.Mockito.when(mockJdbc.queryForMap(
            "SELECT status FROM extraction_drafts WHERE id = ?", "test-id"))
            .thenReturn(Map.of("status", "PENDING_REVIEW"));

        org.mockito.Mockito.when(mockJdbc.update(
            org.mockito.Mockito.eq("UPDATE extraction_drafts SET status = 'REJECTED', rejected_reason = ?, updated_at = NOW() WHERE id = ?"),
            org.mockito.Mockito.eq("quality issue"), org.mockito.Mockito.eq("test-id")))
            .thenReturn(1);

        Map<String, Object> result = service.reject("test-id", "quality issue");

        assertEquals("test-id", result.get("id"));
        assertEquals("REJECTED", result.get("status"));
        assertEquals("quality issue", result.get("rejectedReason"));
    }

    // ── UT-7: reject 非 PENDING_REVIEW 状态 → IllegalStateException ──

    @Test
    @DisplayName("UT-7: reject 在 APPROVED 状态 → IllegalStateException (状态机保护)")
    void rejectApprovedShouldThrowIllegalState() {
        org.mockito.Mockito.when(mockJdbc.queryForMap(
            "SELECT status FROM extraction_drafts WHERE id = ?", "test-id"))
            .thenReturn(Map.of("status", "APPROVED"));

        assertThrows(IllegalStateException.class,
            () -> service.reject("test-id", "too late"),
            "reject on APPROVED draft must throw IllegalStateException");
    }

    // ── UT-8: approve 草稿不在 PENDING_REVIEW → IllegalStateException ──

    @Test
    @DisplayName("UT-8: approve 在 UPLOADED 状态 → IllegalStateException (状态机保护)")
    void approveUploadedShouldThrowIllegalState() {
        Map<String, Object> draftRow = new LinkedHashMap<>();
        draftRow.put("id", "test-id");
        draftRow.put("status", "UPLOADED");
        draftRow.put("extracted_rules_json", null);

        org.mockito.Mockito.when(mockJdbc.queryForMap(
            "SELECT * FROM extraction_drafts WHERE id = ?", "test-id"))
            .thenReturn(draftRow);

        assertThrows(IllegalStateException.class,
            () -> service.approve("test-id"),
            "approve on UPLOADED draft must throw IllegalStateException");
    }

    // ── UT-9: MinerUHttpParser 文件大小校验 ──

    @Test
    @DisplayName("UT-9: MinerUHttpParser 文件过大 → RuntimeException (50MB 限制)")
    void mineruParserOversizedFileShouldThrow() {
        MinerUHttpParser parser = new MinerUHttpParser();
        // 反射设置 mineruBaseUrl
        ReflectionTestUtils.setField(parser, "mineruBaseUrl", "http://localhost:8002");

        // 创建一个超过 50MB 的临时文件
        try {
            java.nio.file.Path bigFile = java.nio.file.Files.createTempFile("mineru-test", ".bin");
            byte[] bigData = new byte[51 * 1024 * 1024]; // 51MB
            java.nio.file.Files.write(bigFile, bigData);

            assertThrows(RuntimeException.class,
                () -> parser.parse(bigFile),
                "MinerU parser must reject files > 50MB");

            java.nio.file.Files.delete(bigFile);
        } catch (java.io.IOException e) {
            fail("test file setup failed: " + e.getMessage());
        }
    }

    // ── UT-10: 3 类抽取 systemPrompt 包含 entity/link/rule ──

    @Test
    @DisplayName("UT-10: 抽取 systemPrompt 必须包含 3 类 (entity/link/rule) 契约")
    void extractionPromptShouldContainThreeCategories() {
        // 反射获取 private static final String 常量
        try {
            java.lang.reflect.Field field =
                KnowledgeExtractionService.class.getDeclaredField("EXTRACTION_SYSTEM_PROMPT");
            field.setAccessible(true);
            String prompt = (String) field.get(null);

            assertNotNull(prompt);
            assertTrue(prompt.contains("entities"), "prompt must contain 'entities'");
            assertTrue(prompt.contains("links"), "prompt must contain 'links'");
            assertTrue(prompt.contains("rules"), "prompt must contain 'rules'");
            assertTrue(prompt.contains("CAUSES"), "prompt must contain link type CAUSES");
            assertTrue(prompt.contains("PART_OF"), "prompt must contain link type PART_OF");
            assertTrue(prompt.contains("severity"), "prompt must contain severity field");
        } catch (Exception e) {
            fail("reflection failed: " + e.getMessage());
        }
    }
}
