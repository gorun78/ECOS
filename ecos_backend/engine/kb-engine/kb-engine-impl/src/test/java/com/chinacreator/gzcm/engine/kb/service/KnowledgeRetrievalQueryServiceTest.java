package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalAnswerRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalFilters;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalHitVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchResultVO;
import com.chinacreator.gzcm.engine.kb.repo.AnswerGenerationHelper;
import com.chinacreator.gzcm.engine.kb.security.KnowledgeNavSecurityEngineClient;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 附录 A 第 14 行 {@code searchKnowledge} / {@code generateKnowledgeAnswer} 的编排门（#146r）。
 *
 * <p><b>本文件钉五件事</b>：① 入参缺参先于 ABAC 判定（空 {@code question}／非空
 * {@code conversationId}）且不消耗安全裁决、不触达取数；② ABAC 三态不折叠（DENY → 403
 * {@code ECOS-KB-062}，UNEVALUATED → 503 {@code ECOS-KB-020}，两态状态串必异）；③ 出参键面
 * 逐字承 {@code {hits[id,score,sourceId,chunk,citations],vectorDegraded,maskedColumns[]}}，且两个
 * "无载体"字段以显式标志（{@code maskedColumnsApplicable}／{@code citationsApplicable}）
 * 标注而非以空表冒充；④ 供数故障（{@code ECOS-KB-040}）与 LLM 故障（{@code ECOS-KB-023}）
 * 原样上浮，不被编排层折叠；⑤ 零命中/零正文不调 LLM（无据不答），只把有正文的命中送进生成腿。</p>
 *
 * <p><b>为何断言走三元组</b>（异常类型／httpStatus／机读码）而不是文案：文案是渲染面，
 * 状态行与码才是调用方与运维面的判据；文案一改本文件仍应绿。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("#146r 知识检索/问答编排：入参闸 → ABAC 三态 → 严格取数 → 严格生成")
class KnowledgeRetrievalQueryServiceTest {

    @Mock private KnowledgeRetrievalService retrievalService;
    @Mock private AnswerGenerationHelper answerGenerationHelper;
    @Mock private KnowledgeNavSecurityEngineClient securityClient;

    private KnowledgeRetrievalQueryService service() {
        return new KnowledgeRetrievalQueryService(retrievalService, answerGenerationHelper, securityClient);
    }

    // ── ① 入参闸先于裁决：缺参是调用方缺陷，不该消耗一次安全裁决 ──────────

    @Test
    @DisplayName("空 question ⇒ 400，且零安全裁决、零取数")
    void blankQuestionRejectedBeforeVerdictAndQuery() {
        KnowledgeRetrievalSearchRequest request = new KnowledgeRetrievalSearchRequest();
        request.setQuestion("   ");

        ValidationException e = assertThrows(ValidationException.class,
                () -> service().search(request), "空查询无语义，必须 400");
        assertEquals(400, e.getHttpStatus());

        assertThrows(ValidationException.class, () -> service().answer(new KnowledgeRetrievalAnswerRequest()),
                "answer 同口径");
        verifyNoInteractions(securityClient, retrievalService, answerGenerationHelper);
    }

    @Test
    @DisplayName("conversationId 非空 ⇒ 400（载体缺失明示），零安全裁决、零取数")
    void conversationIdWithoutCarrierIsRejectedNotSilentlyDropped() {
        KnowledgeRetrievalAnswerRequest request = new KnowledgeRetrievalAnswerRequest();
        request.setQuestion("上季度营收如何");
        request.setConversationId("conv-1");

        ValidationException e = assertThrows(ValidationException.class,
                () -> service().answer(request), "无会话载体的多轮参数必须显式拒绝");
        assertEquals(400, e.getHttpStatus());
        assertTrue(e.getMessage().contains("conversationId"));
        verifyNoInteractions(securityClient, retrievalService, answerGenerationHelper);
    }

    // ── ② ABAC 三态不折叠 ─────────────────────────────────────────────

    @Test
    @DisplayName("DENY ⇒ 403/ECOS-KB-062；UNEVALUATED ⇒ 503/ECOS-KB-020；两态状态串必异且都不取数")
    void verdictStatesAreNotFolded() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.DENY);
        KbErrorCodes.DomainException denied = assertThrows(KbErrorCodes.DomainException.class,
                () -> service().search(searchRequest("还款策略", null)), "策略拒绝 → 403");

        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.UNEVALUATED);
        KbErrorCodes.DomainException unreachable = assertThrows(KbErrorCodes.DomainException.class,
                () -> service().search(searchRequest("还款策略", null)),
                "护栏没说话 → 不可用态（fail-closed）");

        assertEquals(KbErrorCodes.PERMISSION_DENIED.httpStatus(), denied.getHttpStatus());
        assertEquals(KbErrorCodes.PERMISSION_DENIED.code(), denied.getCode());
        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.httpStatus(), unreachable.getHttpStatus());
        assertEquals(KbErrorCodes.SECURITY_UNAVAILABLE.code(), unreachable.getCode());
        assertNotEquals(denied.getHttpStatus(), unreachable.getHttpStatus(),
                "两态同状态行＝把\"引擎没答\"冒充成\"这人没权限\"（§4.7.2 禁止的折叠）");
        assertNotEquals(denied.getCode(), unreachable.getCode());
        verify(retrievalService, never()).ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any());
    }

    // ── ③ search 出参键面 + 过滤清洗 + attrs 不含检索原文 ────────────────

    @Test
    @DisplayName("search：过滤维清洗去重、topK 缺省 5、出参逐键映射且两个无载体字段显式标注")
    void searchMapsContractShapeAndSanitizesFilters() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(ragResult(List.of(
                        source("chunk-1", "还款方式说明", "art-1", 0.87),
                        source("chunk-2", "逾期处理说明", "art-2", 0.5)), null));

        KnowledgeRetrievalFilters filters = new KnowledgeRetrievalFilters();
        filters.setCategoryIds(List.of(" c1 ", "", "c1"));
        filters.setTags(List.of("t1", "  ", "t1"));
        KnowledgeRetrievalSearchRequest request = searchRequest("  还款怎么算  ", filters);

        KnowledgeRetrievalSearchResultVO vo = service().search(request);

        assertEquals(2, vo.getHits().size());
        KnowledgeRetrievalHitVO first = vo.getHits().get(0);
        assertEquals("chunk-1", first.getId());
        assertEquals("art-1", first.getSourceId());
        assertEquals("还款方式说明", first.getChunk());
        assertEquals(0.87, ((Number) first.getScore()).doubleValue());
        assertTrue(first.getCitations().isEmpty(), "无引用载体 ⇒ 逐条空表");
        assertTrue(vo.getMaskedColumns().isEmpty(), "无列脱敏载体 ⇒ 空表");
        assertFalse(vo.isMaskedColumnsApplicable(), "空表须配 applicable=false（禁把无能力读成已脱敏）");
        assertFalse(vo.isCitationsApplicable());
        assertFalse(vo.isVectorDegraded());
        assertNull(vo.getVectorDegradedReason());

        verify(retrievalService).ragQueryStrict("还款怎么算", 5,
                KnowledgeRetrievalQueryService.DEFAULT_THRESHOLD, List.of("c1"), List.of("t1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> attrs = ArgumentCaptor.forClass(Map.class);
        verify(securityClient).evaluatePolicy(eq(KnowledgeRetrievalQueryService.ACTION_SEARCH),
                attrs.capture());
        assertEquals("kb", attrs.getValue().get("engine"));
        assertEquals(5, attrs.getValue().get("topK"));
        assertEquals(Boolean.TRUE, attrs.getValue().get("navFiltered"));
        assertEquals(Boolean.TRUE, attrs.getValue().get("tagFiltered"));
        assertFalse(attrs.getValue().containsKey("question"), "检索原文禁进策略引擎载荷");
        assertFalse(attrs.getValue().containsValue("还款怎么算"));
    }

    @Test
    @DisplayName("search：向量降级透传 vectorDegraded + 原因（禁把降级洗成零命中）")
    void searchCarriesVectorDegradation() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(ragResult(List.of(source("chunk-1", "正文", "art-1", 0.5)),
                        "pgvector 扩展不可用，回退关键词检索（JSONB 命中）"));

        KnowledgeRetrievalSearchResultVO vo = service().search(searchRequest("还款", null));

        assertTrue(vo.isVectorDegraded());
        assertEquals("pgvector 扩展不可用，回退关键词检索（JSONB 命中）", vo.getVectorDegradedReason());
    }

    @Test
    @DisplayName("search：供数不可达 ⇒ 502/ECOS-KB-040 原样上浮（禁折叠成零命中）")
    void searchPropagatesSupplyUnavailable() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenThrow(KbErrorCodes.CURATED_SUPPLY_UNREACHABLE.exception("导航子树解析失败"));

        KbErrorCodes.DomainException e = assertThrows(KbErrorCodes.DomainException.class,
                () -> service().search(searchRequest("还款", null)));

        assertEquals(KbErrorCodes.CURATED_SUPPLY_UNREACHABLE.httpStatus(), e.getHttpStatus());
        assertEquals(KbErrorCodes.CURATED_SUPPLY_UNREACHABLE.code(), e.getCode());
    }

    // ── ④ answer：无据不答 / 只把有正文的命中送进生成腿 / 故障上浮 ─────────

    @Test
    @DisplayName("answer：零命中或零正文 ⇒ answer=null（无据不答），LLM 零交互")
    void answerWithoutUsableEvidenceSkipsLlm() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(ragResult(List.of(source("chunk-1", "   ", "art-1", 0.9)), null));

        KnowledgeRetrievalQueryService.AnswerOutcome outcome =
                service().answer(answerRequest("还款如何计算"));

        assertTrue(outcome.noEvidence(), "空白正文不得进 LLM（否则等于让模型凭空作答）");
        assertNull(outcome.answer());
        assertEquals(1, outcome.hits().size(), "召回明细照常回传（承流引用帧的数据源）");
        verifyNoInteractions(answerGenerationHelper);
        verify(securityClient).evaluatePolicy(eq(KnowledgeRetrievalQueryService.ACTION_ANSWER), any());
    }

    @Test
    @DisplayName("answer：有正文命中 ⇒ 仅非空正文命中进生成腿，answer 透传")
    void answerGeneratesStrictWithOnlyNonBlankChunks() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(ragResult(List.of(
                        source("chunk-1", "", "art-1", 0.9),
                        source("chunk-2", "按日计息", "art-2", 0.8)), null));
        when(answerGenerationHelper.generateStrict(anyString(), anyList())).thenReturn("按日计息，月末结算");

        KnowledgeRetrievalQueryService.AnswerOutcome outcome =
                service().answer(answerRequest("还款如何计算"));

        assertFalse(outcome.noEvidence());
        assertEquals("按日计息，月末结算", outcome.answer());
        assertEquals(2, outcome.hits().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> sources = ArgumentCaptor.forClass(List.class);
        verify(answerGenerationHelper).generateStrict(eq("还款如何计算"), sources.capture());
        assertEquals(1, sources.getValue().size(), "空白正文命中被剔除");
        assertEquals("按日计息", sources.getValue().get(0).get("content"));
        assertEquals("chunk-2", sources.getValue().get(0).get("chunkId"));
        assertEquals("art-2", sources.getValue().get(0).get("source"));
    }

    @Test
    @DisplayName("answer：LLM 不可用 ⇒ 503/ECOS-KB-023 原样上浮（禁静默退化为拼接答案）")
    void answerPropagatesLlmUnavailable() {
        when(securityClient.evaluatePolicy(anyString(), any()))
                .thenReturn(AbacEvaluateResponseContract.Verdict.ALLOW);
        when(retrievalService.ragQueryStrict(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(ragResult(List.of(source("chunk-1", "按日计息", "art-1", 0.9)), null));
        when(answerGenerationHelper.generateStrict(anyString(), anyList()))
                .thenThrow(KbErrorCodes.LLM_BASE_UNAVAILABLE.exception("LLM 底座不可用"));

        KbErrorCodes.DomainException e = assertThrows(KbErrorCodes.DomainException.class,
                () -> service().answer(answerRequest("还款如何计算")));

        assertEquals(KbErrorCodes.LLM_BASE_UNAVAILABLE.httpStatus(), e.getHttpStatus());
        assertEquals(KbErrorCodes.LLM_BASE_UNAVAILABLE.code(), e.getCode());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static KnowledgeRetrievalSearchRequest searchRequest(String question,
                                                                 KnowledgeRetrievalFilters filters) {
        KnowledgeRetrievalSearchRequest request = new KnowledgeRetrievalSearchRequest();
        request.setQuestion(question);
        request.setFilters(filters);
        return request;
    }

    private static KnowledgeRetrievalAnswerRequest answerRequest(String question) {
        KnowledgeRetrievalAnswerRequest request = new KnowledgeRetrievalAnswerRequest();
        request.setQuestion(question);
        return request;
    }

    private static Map<String, Object> ragResult(List<Map<String, Object>> sources, String degradeReason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sources", new ArrayList<>(sources));
        result.put("sourcesCount", sources.size());
        result.put("vectorDegraded", degradeReason != null);
        result.put("vectorDegradedReason", degradeReason);
        return result;
    }

    /** 取数腿出参行（键面与 {@code KnowledgeRetrievalServiceImpl} 一致）。 */
    private static Map<String, Object> source(String chunkId, String content, String source, Object score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkId", chunkId);
        row.put("content", content);
        row.put("score", score);
        row.put("source", source);
        return row;
    }
}
