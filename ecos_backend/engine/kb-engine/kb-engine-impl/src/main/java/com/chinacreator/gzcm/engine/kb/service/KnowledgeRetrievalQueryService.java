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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识检索/问答编排（详设 §7.3.2 附录 A 第 14 行 {@code searchKnowledge} /
 * {@code generateKnowledgeAnswer}，档 B = JWT ＋ 安全裁决，无 {@code kb:*:read} 权限码）。
 *
 * <p><b>裁决前置</b>：先经 {@link KnowledgeNavSecurityEngineClient#evaluatePolicy} 拿契约三态，
 * 再取数。三态不折叠（§4.7.2「不可用 ≠ 无权限」）：{@code DENY} → 403 {@code ECOS-KB-062}，
 * {@code UNEVALUATED} → 503 {@code ECOS-KB-020}（security-engine 缺席/不可读一律 fail-closed），
 * 禁把护栏没说话洗成"策略拒绝"或"零命中"。</p>
 *
 * <p><b>供数唯一口径 = 严格腿</b>：两条编排都走
 * {@link KnowledgeRetrievalService#ragQueryStrict}，供数读失败 ⇒ 502 {@code ECOS-KB-040}；
 * 旧 {@code /rag} 腿"降级为空集"的行为不受影响（只增不改）。</p>
 *
 * <p><b>入参裁决顺序</b>：空 {@code question} ⇒ 400（任何查询前先拒），
 * {@code conversationId} 非空 ⇒ 400（载体缺失声明，见 {@link KnowledgeRetrievalAnswerRequest}）。
 * 两项都在 ABAC 之前判定：缺参是调用方缺陷，不该消耗一次安全裁决。</p>
 *
 * <p><b>ABAC 入参不含检索原文</b>：{@code attrs} 只携带 {@code topK} 与两维过滤是否启用，
 * 不把 {@code question} 送进策略引擎（避免用户问题落进 OPA 决策日志/审计载荷）。</p>
 */
@Service
public class KnowledgeRetrievalQueryService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalQueryService.class);

    /** 与旧 {@code /rag} 腿同值（{@code KnowledgeRetrievalServiceImpl} 未硬筛，仅前端展示用）。 */
    static final double DEFAULT_THRESHOLD = 0.6;

    /** 问答腿固定 Top-K（契约未开放该参数；5 与旧 RAG 默认一致）。 */
    static final int DEFAULT_TOP_K = 5;

    static final String ACTION_SEARCH = "kb.retrieval.search";
    static final String ACTION_ANSWER = "kb.retrieval.answer";

    private final KnowledgeRetrievalService retrievalService;
    private final AnswerGenerationHelper answerGenerationHelper;
    private final KnowledgeNavSecurityEngineClient securityClient;

    public KnowledgeRetrievalQueryService(KnowledgeRetrievalService retrievalService,
                                          AnswerGenerationHelper answerGenerationHelper,
                                          KnowledgeNavSecurityEngineClient securityClient) {
        this.retrievalService = retrievalService;
        this.answerGenerationHelper = answerGenerationHelper;
        this.securityClient = securityClient;
    }

    /**
     * 混合召回（裁决前置）。
     *
     * @throws ValidationException  {@code question} 缺失（400）
     * @throws KbErrorCodes.DomainException 策略拒绝（403 {@code ECOS-KB-062}）／安全不可用
     *         （503 {@code ECOS-KB-020}）／供数不可达（502 {@code ECOS-KB-040}）
     */
    public KnowledgeRetrievalSearchResultVO search(KnowledgeRetrievalSearchRequest request) {
        String question = requireQuestion(request == null ? null : request.getQuestion());
        KnowledgeRetrievalFilters filters = request.getFilters();
        List<String> categoryIds = sanitize(filters == null ? null : filters.getCategoryIds());
        List<String> tags = sanitize(filters == null ? null : filters.getTags());
        Integer topK = request.getTopK();

        authorize(ACTION_SEARCH, topK, categoryIds, tags);

        Map<String, Object> raw = retrievalService.ragQueryStrict(question,
                topK == null ? DEFAULT_TOP_K : topK, DEFAULT_THRESHOLD, categoryIds, tags);
        KnowledgeRetrievalSearchResultVO vo = toSearchResult(raw);
        log.info("knowledge retrieval search: topK={} navFiltered={} tagFiltered={} hits={} vectorDegraded={}",
                raw.get("topK"), !categoryIds.isEmpty(), !tags.isEmpty(),
                vo.getHits().size(), vo.isVectorDegraded());
        return vo;
    }

    /**
     * 问答生成（严格腿）：先严格取数，零命中 ⇒ 无据不答（不调 LLM），否则严格生成。
     *
     * @throws ValidationException  {@code question} 缺失／{@code conversationId} 无载体（400）
     * @throws KbErrorCodes.DomainException 策略拒绝（403）／安全不可用（503）／供数不可达（502）
     *         ／LLM 不可用（503 {@code ECOS-KB-023}）
     */
    public AnswerOutcome answer(KnowledgeRetrievalAnswerRequest request) {
        String question = requireQuestion(request == null ? null : request.getQuestion());
        if (request != null && request.getConversationId() != null
                && !request.getConversationId().isBlank()) {
            throw new ValidationException("conversationId 暂不支持：kb 引擎无会话存储与上下文装配载体，"
                    + "本端点按单轮问答作答（禁静默丢弃多轮参数）");
        }

        authorize(ACTION_ANSWER, DEFAULT_TOP_K, List.of(), List.of());

        Map<String, Object> raw = retrievalService.ragQueryStrict(question, DEFAULT_TOP_K,
                DEFAULT_THRESHOLD, null, null);
        KnowledgeRetrievalSearchResultVO result = toSearchResult(raw);
        List<Map<String, Object>> usableSources = usableSources(result.getHits());
        if (usableSources.isEmpty()) {
            log.info("knowledge retrieval answer: 零命中/零正文，按无据不答收尾（未调 LLM）");
            return new AnswerOutcome(result.getHits(), null,
                    result.isVectorDegraded(), result.getVectorDegradedReason());
        }
        String answer = answerGenerationHelper.generateStrict(question, usableSources);
        log.info("knowledge retrieval answer: hits={} answerLen={} vectorDegraded={}",
                result.getHits().size(), answer.length(), result.isVectorDegraded());
        return new AnswerOutcome(result.getHits(), answer,
                result.isVectorDegraded(), result.getVectorDegradedReason());
    }

    /** ABAC 裁决前置：三态原样分面，禁折叠。 */
    private void authorize(String action, Integer topK, List<String> categoryIds, List<String> tags) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("engine", "kb");
        attrs.put("topK", topK == null ? DEFAULT_TOP_K : topK);
        attrs.put("navFiltered", !categoryIds.isEmpty());
        attrs.put("tagFiltered", !tags.isEmpty());

        AbacEvaluateResponseContract.Verdict verdict = securityClient.evaluatePolicy(action, attrs);
        if (verdict == AbacEvaluateResponseContract.Verdict.DENY) {
            throw KbErrorCodes.PERMISSION_DENIED.exception("知识检索: " + action + " 被安全策略拒绝");
        }
        if (verdict == AbacEvaluateResponseContract.Verdict.UNEVALUATED) {
            throw KbErrorCodes.SECURITY_UNAVAILABLE.exception(
                    "知识检索: " + action + " 未获安全裁决（security-engine 不可用），按 fail-closed 拒绝");
        }
    }

    private static String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new ValidationException("question", "必填（知识检索/问答无空查询语义）");
        }
        return question.trim();
    }

    /** 过滤维清洗：剔除 null/blank、trim、去重保序（与取数腿 tags 清洗同口径）。 */
    private static List<String> sanitize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(values.size());
        for (String v : values) {
            if (v == null || v.isBlank()) {
                continue;
            }
            String trimmed = v.trim();
            if (!out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** 取数腿出参 → 契约 VO（键面逐字承 {@code {hits[],vectorDegraded,maskedColumns[]}}）。 */
    private static KnowledgeRetrievalSearchResultVO toSearchResult(Map<String, Object> raw) {
        Map<String, Object> safe = raw == null ? Map.of() : raw;
        List<KnowledgeRetrievalHitVO> hits = new ArrayList<>();
        Object rawSources = safe.get("sources");
        if (rawSources instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> row)) {
                    continue;
                }
                KnowledgeRetrievalHitVO hit = new KnowledgeRetrievalHitVO();
                hit.setId(text(row.get("chunkId")));
                hit.setScore(row.get("score"));
                hit.setSourceId(text(row.get("source")));
                hit.setChunk(text(row.get("content")));
                hit.setCitations(List.of());
                hits.add(hit);
            }
        }
        KnowledgeRetrievalSearchResultVO vo = new KnowledgeRetrievalSearchResultVO();
        vo.setHits(hits);
        vo.setVectorDegraded(Boolean.TRUE.equals(safe.get("vectorDegraded")));
        Object reason = safe.get("vectorDegradedReason");
        vo.setVectorDegradedReason(reason == null ? null : String.valueOf(reason));
        vo.setMaskedColumns(List.of());
        vo.setMaskedColumnsApplicable(false);
        vo.setCitationsApplicable(false);
        return vo;
    }

    /** LLM 腿只吃有正文的命中（空正文进上下文等于让模型凭空作答）。 */
    private static List<Map<String, Object>> usableSources(List<KnowledgeRetrievalHitVO> hits) {
        List<Map<String, Object>> sources = new ArrayList<>(hits.size());
        for (KnowledgeRetrievalHitVO hit : hits) {
            String chunk = hit.getChunk();
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("content", chunk);
            source.put("chunkId", hit.getId());
            source.put("source", hit.getSourceId());
            sources.add(source);
        }
        return sources;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 问答作答面：{@code hits} 为召回明细（承流引用帧的数据源），
     * {@code answer == null} 表示无据不答（见 {@code STATUS_NO_EVIDENCE}）。
     */
    public record AnswerOutcome(List<KnowledgeRetrievalHitVO> hits, String answer,
                                boolean vectorDegraded, String vectorDegradedReason) {

        public AnswerOutcome {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }

        public boolean noEvidence() {
            return answer == null;
        }
    }
}
