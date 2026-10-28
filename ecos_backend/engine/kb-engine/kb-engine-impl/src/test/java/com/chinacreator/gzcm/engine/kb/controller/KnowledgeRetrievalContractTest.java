package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalAnswerRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalHitVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchResultVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeRetrievalQueryService;
import com.chinacreator.gzcm.engine.kb.stream.KnowledgeRetrievalSseFrames;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /api/v1/knowledge/retrieval} 两端点契约门（附录 A 第 14 行；#146r）。
 *
 * <p><b>本文件钉三件事</b>：① {@code /search} 成功走 200 + {@code code=0} 信封，data 即编排层出参
 * （控制器不二次包装、不补默认值）；② 编排层的 {@code DomainException}（403/502/503）
 * <b>原样上浮</b>——控制器禁宽 catch 折叠，SSE 端点同样（故障在 {@code SseEmitter} 创建前
 * 抛出，落真实状态行）；③ 帧装配分支：有据 ⇒ {@code meta → delta → citation* → done(ANSWERED)}，
 * 无据 ⇒ {@code meta + done(NO_EVIDENCE)}，引用要素缺失的命中被跳过。</p>
 *
 * <p>harness 与 {@code KnowledgeGraphSyncContractTest} 同族：Mockito + 直调 Controller，
 * 断言取 {@code getStatusCode()} 与机读码三元组，不看文案。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#146r /knowledge/retrieval 契约：状态行保真 + 帧装配分支")
class KnowledgeRetrievalContractTest {

    @Mock private KnowledgeRetrievalQueryService queryService;

    private KnowledgeRetrievalController controller() {
        return new KnowledgeRetrievalController(queryService);
    }

    // ── ① search：200 信封 + data 原样 ───────────────────────────────

    @Test
    @DisplayName("POST /search ⇒ 200，信封 code=0，data 即编排层出参（不二次包装）")
    void searchReturnsSuccessEnvelope() {
        KnowledgeRetrievalSearchRequest request = new KnowledgeRetrievalSearchRequest();
        request.setQuestion("还款如何计算");
        KnowledgeRetrievalSearchResultVO vo = result(List.of(hit("chunk-1", "art-1", "按日计息", 0.9)));
        when(queryService.search(request)).thenReturn(vo);

        var resp = controller().search(request);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(),
                "成功信封 code 取 CODE_SUCCESS（不是 HTTP 状态号）");
        assertSame(vo, resp.getBody().getData(), "控制器不得二次包装或改造编排层出参");
        verify(queryService).search(request);
    }

    // ── ② 状态行保真：编排层异常原样上浮，控制器零折叠 ─────────────────

    @Test
    @DisplayName("POST /search：策略拒绝 ⇒ 403/ECOS-KB-062 原样上浮（禁 catch 折叠成 200）")
    void searchPropagatesDenial() {
        KnowledgeRetrievalSearchRequest request = new KnowledgeRetrievalSearchRequest();
        request.setQuestion("还款如何计算");
        when(queryService.search(any()))
                .thenThrow(KbErrorCodes.PERMISSION_DENIED.exception("被安全策略拒绝"));

        KbErrorCodes.DomainException e = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().search(request));

        assertEquals(KbErrorCodes.PERMISSION_DENIED.httpStatus(), e.getHttpStatus());
        assertEquals(KbErrorCodes.PERMISSION_DENIED.code(), e.getCode());
    }

    @Test
    @DisplayName("POST /answer：安全不可用 ⇒ 503/ECOS-KB-020 上浮（SSE 端点同样在创建 emitter 前失败）")
    void answerPropagatesSecurityUnavailableBeforeEmitter() {
        when(queryService.answer(any()))
                .thenThrow(KbErrorCodes.SECURITY_UNAVAILABLE.exception("security-engine 不可用"));

        KbErrorCodes.DomainException e = assertThrows(KbErrorCodes.DomainException.class,
                () -> controller().answer(new KnowledgeRetrievalAnswerRequest()));

        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.httpStatus(), e.getHttpStatus());
        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.code(), e.getCode());
    }

    @Test
    @DisplayName("POST /answer：编排成功 ⇒ 返回 emitter 且帧已按契约装配（不抛错）")
    void answerAssemblesFramesOnEmitter() {
        when(queryService.answer(any())).thenReturn(new KnowledgeRetrievalQueryService.AnswerOutcome(
                List.of(hit("chunk-1", "art-1", "按日计息", 0.9)), "按日计息，月末结算", false, null));

        assertNotNull(controller().answer(new KnowledgeRetrievalAnswerRequest()));
        verify(queryService).answer(any());
    }

    // ── ③ 帧装配分支（包内直调，绕开 emitter 线路） ─────────────────────

    @Test
    @DisplayName("有据：meta → delta → citation* → done(ANSWERED)；缺 sourceId 的命中跳过引用帧")
    void writeAnswerEmitsCitationsForIdentifiedHitsOnly() throws IOException {
        Recorder recorder = new Recorder();
        KnowledgeRetrievalQueryService.AnswerOutcome outcome =
                new KnowledgeRetrievalQueryService.AnswerOutcome(List.of(
                        hit("chunk-1", "art-1", "按日计息", 0.9),
                        hit("chunk-2", "", "无源命中", 0.8)), "按日计息，月末结算", false, null);

        controller().writeAnswer(recorder.frames(), outcome);

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DELTA,
                SseFrameContract.FRAME_CITATION, SseFrameContract.FRAME_DONE), recorder.events);
        assertEquals(2, recorder.payloads.get(0).get(KnowledgeRetrievalSseFrames.KEY_HITS),
                "meta 报的是召回命中数（含无源命中）");
        assertEquals(2, recorder.payloads.get(3).get(KnowledgeRetrievalSseFrames.KEY_HITS),
                "done 与 meta 的 hits 必须同数");
        assertEquals(KnowledgeRetrievalSseFrames.STATUS_ANSWERED,
                recorder.payloads.get(3).get(SseFrameContract.KEY_STATUS));
    }

    @Test
    @DisplayName("无据：只发 meta + done(NO_EVIDENCE)，hits 与召回明细同数（不冒充 0）")
    void writeAnswerWithoutEvidenceEndsOnNoEvidence() throws IOException {
        Recorder recorder = new Recorder();
        KnowledgeRetrievalQueryService.AnswerOutcome outcome =
                new KnowledgeRetrievalQueryService.AnswerOutcome(
                        List.of(hit("chunk-1", "art-1", "  ", 0.9)), null, false, null);

        controller().writeAnswer(recorder.frames(), outcome);

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DONE), recorder.events);
        assertEquals(KnowledgeRetrievalSseFrames.STATUS_NO_EVIDENCE,
                recorder.payloads.get(1).get(SseFrameContract.KEY_STATUS));
        assertEquals(1, recorder.payloads.get(1).get(KnowledgeRetrievalSseFrames.KEY_HITS),
                "有召回但零可用正文 ⇒ 命中数照实报 1，不得写 0");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static final class Recorder {
        final List<String> events = new ArrayList<>();
        final List<Map<String, Object>> payloads = new ArrayList<>();

        KnowledgeRetrievalSseFrames frames() {
            return new KnowledgeRetrievalSseFrames("trace-kb-7", (event, data) -> {
                events.add(event);
                payloads.add(data);
            });
        }
    }

    private static KnowledgeRetrievalHitVO hit(String id, String sourceId, String chunk, Object score) {
        KnowledgeRetrievalHitVO hit = new KnowledgeRetrievalHitVO();
        hit.setId(id);
        hit.setSourceId(sourceId);
        hit.setChunk(chunk);
        hit.setScore(score);
        hit.setCitations(List.of());
        return hit;
    }

    private static KnowledgeRetrievalSearchResultVO result(List<KnowledgeRetrievalHitVO> hits) {
        KnowledgeRetrievalSearchResultVO vo = new KnowledgeRetrievalSearchResultVO();
        vo.setHits(hits);
        vo.setMaskedColumns(List.of());
        vo.setVectorDegraded(false);
        return vo;
    }
}
