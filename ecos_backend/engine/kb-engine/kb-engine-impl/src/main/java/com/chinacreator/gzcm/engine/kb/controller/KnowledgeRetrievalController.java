package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalAnswerRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalHitVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeRetrievalSearchResultVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeRetrievalQueryService;
import com.chinacreator.gzcm.engine.kb.stream.KnowledgeRetrievalSseFrames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 知识检索/问答族 Controller — 详设 §7.3.2 ＋ 附录 A 第 14 行（台账缺失清单第 14 条）。
 *
 * <p>两端点（路径/入参/出参逐字承契约）：</p>
 * <ul>
 *   <li><b>{@code POST /api/v1/knowledge/retrieval/search}</b>（档 B，裁决前置）→ 200
 *       {@code {hits[{id,score,sourceId,chunk,citations}],vectorDegraded,maskedColumns[]}}；</li>
 *   <li><b>{@code POST /api/v1/knowledge/retrieval/answer}</b>（档 B，SSE）→ 事件
 *       {@code meta → delta → citation* → done}，末帧带 traceId。</li>
 * </ul>
 *
 * <p><b>状态行保真</b>：本类零 try/catch 折叠、零信封工厂。鉴权拒绝（403
 * {@code ECOS-KB-062}）、安全不可用（503 {@code ECOS-KB-020}）、供数不可达（502
 * {@code ECOS-KB-040}）、LLM 不可用（503 {@code ECOS-KB-023}）、入参缺失（400）全部
 * <b>在请求线程</b>抛出并上浮，由 {@code KbErrorAdvice} 渲染成真实状态行 —— 包括 SSE 端点：
 * security 裁决、严格取数、LLM 生成三步都在 {@code SseEmitter} 创建之前完成，因此故障不会
 * 退化成"200 + 一条 error 帧"。代价是响应首字节在生成结束后才发出（非 token 级流的如实后果，
 * 见 {@link KnowledgeRetrievalSseFrames} 类注释）。</p>
 *
 * <p><b>与旧 {@code /api/v1/knowledge/rag} 的边界</b>：后者是 PMO-50 历史腿
 * （{@code RagController}，宽供数、失败降级为空集、拼 answer），本族是附录 A 第 14 行契约腿
 * （严格供数、零命中不调 LLM、SSE 帧序单源）。两族语义不同源，禁互相代理；
 * 旧 {@code GET /rag?stream=true} 在前端仍有调用（D-B5-12，后端该端点不存在），
 * 前端改接本族属另一批次。</p>
 *
 * @group KNOWLEDGE
 */
@RestController
@RequestMapping("/api/v1/knowledge/retrieval")
public class KnowledgeRetrievalController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalController.class);

    /** SSE 连接上限（生成已完成，此处只覆盖下发窗口）。 */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final KnowledgeRetrievalQueryService queryService;

    public KnowledgeRetrievalController(KnowledgeRetrievalQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 混合召回（档 B）：ABAC {@code kb.retrieval.search} 裁决 → 严格取数 → 契约出参。
     *
     * <p>出参的 {@code maskedColumns} 恒空且 {@code maskedColumnsApplicable=false}
     * （本端点不接列级脱敏），见 {@link KnowledgeRetrievalSearchResultVO}。</p>
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<KnowledgeRetrievalSearchResultVO>> search(
            @RequestBody(required = false) KnowledgeRetrievalSearchRequest body) {
        KnowledgeRetrievalSearchResultVO vo = queryService.search(body);
        return ResponseEntity.ok(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    /**
     * 问答生成（档 B，SSE）：ABAC {@code kb.retrieval.answer} 裁决 → 严格取数 →
     * 零命中则 {@code done(NO_EVIDENCE)}（不调 LLM）→ 否则严格生成后整段下发。
     */
    @PostMapping(value = "/answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@RequestBody(required = false) KnowledgeRetrievalAnswerRequest body) {
        // 请求线程内完成：故障 ⇒ 真实状态行（见类注释）
        KnowledgeRetrievalQueryService.AnswerOutcome outcome = queryService.answer(body);

        String traceId = currentTraceId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> log.debug("知识问答 SSE 完成: traceId={}", traceId));
        emitter.onTimeout(() -> log.warn("知识问答 SSE 超时: traceId={}", traceId));
        emitter.onError(ex -> log.warn("知识问答 SSE 通道异常: traceId={} reason={}", traceId, ex.getMessage()));

        KnowledgeRetrievalSseFrames frames =
                new KnowledgeRetrievalSseFrames(traceId, sinkOf(emitter));
        try {
            writeAnswer(frames, outcome);
            emitter.complete();
        } catch (IOException e) {
            // 收件人已断开：没有可发的帧，按通道错误收尾并留证
            log.warn("知识问答 SSE 写出失败（客户端可能已断开）: traceId={} reason={}",
                    traceId, e.getMessage());
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 帧装配（包内可见，供帧序测试直调）：{@code meta → delta → citation* → done}。
     *
     * <p>零命中（{@link KnowledgeRetrievalQueryService.AnswerOutcome#noEvidence()}）只发
     * {@code meta + done(NO_EVIDENCE)}：无据不答，不发空 {@code delta} 冒充"模型答了"。
     * 引用帧逐条对应命中，空 {@code sourceId}/{@code chunkId} 由写出器跳过（宁缺毋造）。</p>
     */
    void writeAnswer(KnowledgeRetrievalSseFrames frames,
                     KnowledgeRetrievalQueryService.AnswerOutcome outcome) throws IOException {
        frames.meta(outcome.hits().size());
        if (outcome.noEvidence()) {
            frames.done(KnowledgeRetrievalSseFrames.STATUS_NO_EVIDENCE, outcome.hits().size());
            return;
        }
        frames.delta(outcome.answer());
        int index = 0;
        for (KnowledgeRetrievalHitVO hit : outcome.hits()) {
            index++;
            frames.citation(index, hit.getSourceId(), hit.getId(), hit.getScore());
        }
        frames.done(KnowledgeRetrievalSseFrames.STATUS_ANSWERED, outcome.hits().size());
    }

    /** {@code SseEmitter} 桥：事件名与载荷都由写出器给定，本处只做线路装配。 */
    private static KnowledgeRetrievalSseFrames.SseFrameSink sinkOf(SseEmitter emitter) {
        return (event, data) -> emitter.send(
                SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    }

    /** 当前请求 traceId（帧载荷与信封同源；无 MDC 时为 null，由写出器落缺失标记）。 */
    private static String currentTraceId() {
        try {
            return MDC.get("traceId");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
