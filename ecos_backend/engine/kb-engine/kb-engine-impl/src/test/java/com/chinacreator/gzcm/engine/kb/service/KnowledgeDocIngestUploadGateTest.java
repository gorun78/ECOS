package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.doc.mapper.KbDocMapper;
import com.chinacreator.gzcm.engine.kb.doc.model.KbDoc;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摄取服务单源腿门（#146z）：{@code prepareUpload} 准入闸与 {@code storeAndRegisterQueued}
 * 落库顺序——契约 §7.5.1 状态矩阵把「MinIO 不可用 ⇒ 阻断＝是／不入草稿」写成硬约束，
 * 而该约束只在服务侧可见（控制器只看见抛出的携带异常）。本类钉死顺序本身，
 * 防止后续把它写成「先登记后写对象，失败再回滚」（回滚腿一旦被裁掉就是静默入草稿）。
 *
 * <p>同时钉类型白名单/大小上限的<b>配置驱动</b>形态（详设未给闭集 ⇒ 值来自配置键，
 * 默认值登记台账待裁决）：闸必须按注入值生效，而不是代码里另写一份常量表。</p>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeDocIngestUploadGateTest {

    private static final String ALLOWED = "application/pdf,text/*";

    @Mock private KbDocMapper kbDocMapper;
    @Mock private MinioStorageService minio;
    @Mock private com.chinacreator.gzcm.runtime.access.document.DocumentChunkSplitter chunkSplitter;

    private KnowledgeDocIngestService service(long maxSizeMb) {
        // 分块参数求解走既存 splitter 单源（本类只钉准入闸与落库顺序，不复刻其规则）
        lenient().when(chunkSplitter.resolveChunkSize(any(), any()))
                .thenReturn(512);
        lenient().when(chunkSplitter.resolveChunkOverlap(any(), anyInt(), any()))
                .thenReturn(64);
        return new KnowledgeDocIngestService(kbDocMapper,
                null, null, chunkSplitter, null,
                minio, null, "", 512, 64, ALLOWED, maxSizeMb);
    }

    private static MultipartFile file(String name, String contentType, int bytes) {
        return new MockMultipartFile("file", name, contentType, new byte[bytes]);
    }

    // ── 准入闸（415/413，配置驱动） ────────────────────────────────────────

    @Test
    @DisplayName("白名单外类型 ⇒ 415 ECOS-KB-031 携带，闸值取配置而非代码常量")
    void rejectsTypeOutsideConfiguredWhitelist() {
        KnowledgeDocIngestService svc = service(50);
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> svc.prepareUpload(file("a.exe", "application/octet-stream", 10),
                        "hr", null, null, null));
        assertEquals(415, dex.getHttpStatus());
        assertEquals("ECOS-KB-031", dex.getCode());
        verify(kbDocMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("text/* 前缀项放行 text/csv；带 charset 参数归一后仍放行")
    void wildcardAndParameterizedContentTypeBothPass() {
        KnowledgeDocIngestService svc = service(50);
        KnowledgeDocIngestService.IngestUpload u1 =
                svc.prepareUpload(file("a.csv", "text/csv", 10), "hr", null, null, null);
        assertEquals("text/csv", u1.contentType(), "原样回显声明类型（仅比较用归一值）");
        KnowledgeDocIngestService.IngestUpload u2 =
                svc.prepareUpload(file("a.txt", "text/plain; charset=UTF-8", 10),
                        "hr", null, null, null);
        assertNotNull(u2.objectKey());
    }

    @Test
    @DisplayName("超 ecos.kb.doc.max-size-mb ⇒ 413 + 同一机读码 ECOS-KB-031，禁落行")
    void rejectsOversizeWith413() {
        KnowledgeDocIngestService svc = service(1);   // 1MB
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> svc.prepareUpload(file("big.pdf", "application/pdf", 2 * 1024 * 1024),
                        "hr", null, null, null));
        assertEquals(413, dex.getHttpStatus(), "大小上限是 413，与类型不符的 415 分态（禁折叠）");
        assertEquals("ECOS-KB-031", dex.getCode());
        verify(kbDocMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("空文件 ⇒ ValidationException（400 语义由控制器显式承载）")
    void rejectsEmptyFileWithValidationException() {
        KnowledgeDocIngestService svc = service(50);
        assertThrows(ValidationException.class,
                () -> svc.prepareUpload(file("a.pdf", "application/pdf", 0), null, null, null, null));
    }

    @Test
    @DisplayName("对象 key 形态沿用既存 raw/unstructured/{source}/{docId}/{fileName}")
    void objectKeyKeepsExistingShape() {
        KnowledgeDocIngestService svc = service(50);
        KnowledgeDocIngestService.IngestUpload u =
                svc.prepareUpload(file("a.pdf", "application/pdf", 10), "hr", "d-77", null, null);
        assertEquals("raw/unstructured/hr/d-77/a.pdf", u.objectKey(),
                "与契约 §7.5.1 字面形态的差异登记台账余项，不在本批改数据形态");
        assertEquals(32, svc.newDocId().length(), "缺省 docId = 去横线 UUID（与存量同形）");
    }

    // ── 受理腿顺序：MinIO 在前，失败不落行 ─────────────────────────────────

    @Test
    @DisplayName("MinIO 写抛错 ⇒ 500 ECOS-KB-030 且 kb_doc 零写入（矩阵「不入草稿」）")
    void minioFailureDoesNotRegisterDocRow() {
        when(minio.putObject(anyString(), any(byte[].class), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));
        KnowledgeDocIngestService svc = service(50);
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> svc.storeAndRegisterQueued(
                        svc.prepareUpload(file("a.pdf", "application/pdf", 10), "hr", null, null, null),
                        new byte[]{1}));
        assertEquals(500, dex.getHttpStatus());
        assertEquals("ECOS-KB-030", dex.getCode());
        verify(kbDocMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("MinIO 返回非 success ⇒ 同样 030 且不落行（禁把对象存储软失败当成功）")
    void minioSoftFailureIsBlocked() {
        when(minio.putObject(anyString(), any(byte[].class), anyString()))
                .thenReturn(Map.of("status", "error", "message", "bucket 不存在"));
        KnowledgeDocIngestService svc = service(50);
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> svc.storeAndRegisterQueued(
                        svc.prepareUpload(file("a.pdf", "application/pdf", 10), "hr", null, null, null),
                        new byte[]{1}));
        assertEquals("ECOS-KB-030", dex.getCode());
        assertTrue(dex.getMessage().contains("bucket 不存在"), "上游文案须可溯源");
        verify(kbDocMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("写成功才登记 queued 行（状态值 = F2 状态机起点，禁谎报 parsing）")
    void successRegistersQueuedRow() {
        when(minio.putObject(anyString(), any(byte[].class), anyString()))
                .thenReturn(Map.of("status", "success"));
        KnowledgeDocIngestService svc = service(50);
        var vo = svc.storeAndRegisterQueued(
                svc.prepareUpload(file("a.pdf", "application/pdf", 10), "hr", "d-1", null, null),
                new byte[]{1});
        assertEquals("queued", vo.getParseStatus(), "受理腿终态只能是 queued（执行腿才推 parsing/extracting/done）");
        ArgumentCaptor<KbDoc> captor = ArgumentCaptor.forClass(KbDoc.class);
        verify(kbDocMapper).upsert(captor.capture());
        assertEquals("queued", captor.getValue().getParseStatus());
        assertEquals("raw/unstructured/hr/d-1/a.pdf", captor.getValue().getObjectKey());
    }

    // ── 执行腿回读：缺行 / 缺幂等键 / 空对象 ───────────────────────────────

    @Test
    @DisplayName("执行腿无行 ⇒ IllegalStateException（任务失败承载，不冒充 HTTP 404）")
    void runByDocIdWithoutRowFailsTheTask() {
        when(kbDocMapper.findById("gone")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service(50).runByDocId("gone"));
    }

    @Test
    @DisplayName("执行腿缺 objectKey ⇒ 500 ECOS-KB-030（无幂等键即不可重放）")
    void runByDocIdWithoutObjectKeyIsBlocked() {
        KbDoc doc = new KbDoc();
        doc.setDocId("d-1");
        doc.setSource("hr");
        doc.setParseStatus("queued");
        when(kbDocMapper.findById("d-1")).thenReturn(doc);
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> service(50).runByDocId("d-1"));
        assertEquals("ECOS-KB-030", dex.getCode());
    }

    @Test
    @DisplayName("近源层回读空对象 ⇒ 500 ECOS-KB-030（禁按空文本跑完流水线谎报 done）")
    void runByDocIdWithEmptyObjectIsBlocked() {
        KbDoc doc = new KbDoc();
        doc.setDocId("d-1");
        doc.setSource("hr");
        doc.setObjectKey("raw/unstructured/hr/d-1/a.pdf");
        doc.setParseStatus("queued");
        when(kbDocMapper.findById("d-1")).thenReturn(doc);
        when(minio.getObject("raw/unstructured/hr/d-1/a.pdf")).thenReturn(new byte[]{});
        KbErrorCodes.DomainException dex = assertThrows(KbErrorCodes.DomainException.class,
                () -> service(50).runByDocId("d-1"));
        assertEquals(500, dex.getHttpStatus());
        assertEquals("ECOS-KB-030", dex.getCode());
    }
}
