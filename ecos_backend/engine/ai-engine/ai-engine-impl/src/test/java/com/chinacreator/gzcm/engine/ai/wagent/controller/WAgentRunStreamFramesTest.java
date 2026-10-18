package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 详设-10 §10.3.6 WAgent run 流八帧契约（#143/B5-5b 收口 D-B5-8）。
 *
 * <p><b>入口驱动</b>：帧名与键面一律从真实发射面（控制器 → {@code SseEmitter} 响应体）断出，
 * 不从内部装配函数断。理由与 OAG 通道同：帧名散在发射点时，任何"只测装配函数"的用例都会
 * 对着已经合流的形状变绿，而实际出帧仍是旧词表。</p>
 *
 * <p><b>本批修复前的红（实跑凭证）</b>：改前该流出 {@code stream_ready / <裸 event_type> /
 * run_terminal / stream_timeout / stream_error}，实测帧序
 * {@code [stream_ready, StepCompleted, run_terminal]} ⇒ 首帧/末帧断言必红。
 * 这不是测试写错，是缺陷原形。</p>
 *
 * <p><b>无假真理的一侧也要断</b>：{@code token/delta/citation} 三帧在 V234/V235 上零生产者，
 * 故本流不发它们；里程碑事件走 {@code milestone} 补充帧（不进八帧序）；
 * 预算耗尽而 Run 未终态时不发 {@code done}。这三条各自有对应用例——否则"改成八帧"
 * 会被实现成"任何收流路径都补末帧、任何事件都塞 step"。</p>
 */
class WAgentRunStreamFramesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("§10.3.6 帧 1/8：首帧 meta（带 runId/planSource/budget/streamMode/payloadKind）、末帧 done")
    void metaIsFirstAndDoneIsLast() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.COMPLETED)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of());

        List<Frame> frames = stream(runDao, eventDao, mock(WAgentRunStepDao.class),
                mock(WAgentReadinessDao.class));
        assertEquals("meta", frames.get(0).event(), "§10.3.6 帧 1 meta 必须为首帧，实得=" + names(frames));
        assertEquals("done", frames.get(frames.size() - 1).event(), "帧 8 done 必须为末帧，实得=" + names(frames));

        Map<String, String> meta = frames.get(0).data;
        assertEquals("r1", meta.get(SseFrameContract.KEY_RUN_ID));
        assertEquals("PLAYBOOK", meta.get(SseFrameContract.KEY_PLAN_SOURCE));
        assertEquals("{\"maxSteps\":8}", meta.get(SseFrameContract.KEY_BUDGET));
        // P-4 未接线必须写明轮询回放形态（禁冒充实时推送），载荷形态按 §6.3 引用型标注
        assertTrue(meta.get(SseFrameContract.KEY_STREAM_MODE).contains("table-polling-replay"), meta.toString());
        assertTrue(meta.get(SseFrameContract.KEY_PAYLOAD_KIND).contains("withheld"), meta.toString());
        assertFalse(meta.get(SseFrameContract.KEY_TRACE_ID).isBlank(), "每帧必带 traceId");
    }

    @Test
    @DisplayName("§10.3.6 帧 2：step 六键取该 attempt 的 V234 行；帧名不得用裸 event_type；无档 attempt 只缺位不借档")
    void stepFrameKeysComeFromThatAttemptRow() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.COMPLETED)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of(
                event("e1", EventTypes.Agent.STEP_FAILED, "{\"step_key\":\"s1\",\"attempt\":1}"),
                event("e2", EventTypes.Agent.STEP_COMPLETED, "{\"step_key\":\"s1\",\"attempt\":2}"),
                event("e3", EventTypes.Agent.STEP_COMPLETED, "{\"step_key\":\"s1\",\"attempt\":9}")));
        when(stepDao.findByRunId("r1")).thenReturn(List.of(
                step("s1", 1, "failed"), step("s1", 2, "succeeded")));

        List<Frame> frames = stream(runDao, eventDao, stepDao, mock(WAgentReadinessDao.class));
        assertTrue(names(frames).stream().noneMatch(EventTypes.Agent.STEP_COMPLETED::equals),
                "Kafka 事件词表与承流帧词表分表登记，禁把裸 event_type 当帧名: " + names(frames));
        List<Frame> steps = framesOf(frames, SseFrameContract.FRAME_STEP);
        assertEquals(3, steps.size(), "三个步骤事件各出一帧 step，实得=" + names(frames));

        Map<String, String> first = steps.get(0).data;
        assertEquals("s1", first.get(SseFrameContract.KEY_STEP_KEY));
        assertEquals("DATA_QUERY", first.get(SseFrameContract.KEY_STEP_TYPE));
        assertEquals("data.query", first.get(SseFrameContract.KEY_TOOL));
        assertEquals("1", first.get(SseFrameContract.KEY_ATTEMPT));
        assertEquals("failed", first.get(SseFrameContract.KEY_STATUS), "attempt 1 的状态不得被 attempt 2 覆盖");
        assertEquals("ALLOW", first.get(SseFrameContract.KEY_POLICY_EFFECT));
        assertEquals("e1", first.get(SseFrameContract.KEY_EVENT_ID), "续传关联位必须带行 id");
        assertEquals("succeeded", steps.get(1).data.get(SseFrameContract.KEY_STATUS));

        Map<String, String> orphan = steps.get(2).data;
        assertEquals("9", orphan.get(SseFrameContract.KEY_ATTEMPT));
        assertFalse(orphan.containsKey(SseFrameContract.KEY_STATUS),
                "V234 无 attempt 9 档 ⇒ 状态缺位，禁借 attempt 2 的结果冒充（实得键面=" + orphan.keySet() + "）");
    }

    @Test
    @DisplayName("§10.3.6 帧 6：degrade 四键真值取 V230 评估头，DG-M1 恒附，白名单外码跳过")
    void degradeFramesComeFromReadinessHead() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        WAgentReadinessDao readinessDao = mock(WAgentReadinessDao.class);
        WAgentRunDO run = run(RunStatus.COMPLETED_DEGRADED);
        run.setReadinessId("rd1");
        when(runDao.findById("r1")).thenReturn(Optional.of(run));
        when(eventDao.findByRunId("r1")).thenReturn(List.of());
        WAgentReadinessDO head = new WAgentReadinessDO();
        head.setId("rd1");
        head.setGrade("B");
        head.setDegradationsJson("[\"DG-D1\",\"DG-GHOST\"]");
        when(readinessDao.findById("rd1")).thenReturn(Optional.of(head));

        List<Frame> frames = stream(runDao, eventDao, mock(WAgentRunStepDao.class), readinessDao);
        List<Frame> degrades = framesOf(frames, SseFrameContract.FRAME_DEGRADE);
        assertEquals(2, degrades.size(), "DG-D1(评估头) + DG-M1(本通道 P-4 自证)，实得=" + names(frames));

        Map<String, String> dgD1 = data(degrades.get(0), SseFrameContract.FRAME_DEGRADE);
        assertEquals("DG-D1", dgD1.get(SseFrameContract.KEY_DG_CODE));
        // DegradeCode 无 applies_to 字段 ⇒ 层位由 ReadinessCheckCode 挂载关系确定性反推（裁决 D-B5-15）
        assertEquals("D,K", dgD1.get(SseFrameContract.KEY_APPLIES_TO));
        assertEquals("true", dgD1.get(SseFrameContract.KEY_REQUIRES_ACK));
        assertTrue(dgD1.get(SseFrameContract.KEY_NOTE).contains("数据新鲜度"), dgD1.toString());

        Map<String, String> dgM1 = data(degrades.get(1), SseFrameContract.FRAME_DEGRADE);
        assertEquals("DG-M1", dgM1.get(SseFrameContract.KEY_DG_CODE));
        assertEquals("progress-channel", dgM1.get(SseFrameContract.KEY_APPLIES_TO));
        assertEquals("false", dgM1.get(SseFrameContract.KEY_REQUIRES_ACK));

        assertTrue(names(frames).stream().noneMatch(n -> n.contains("GHOST")),
                "F10-10 白名单外的 DG 码不得进承流面: " + names(frames));

        Map<String, String> done = onlyFrame(frames, SseFrameContract.FRAME_DONE).data;
        assertEquals("completed_degraded", done.get(SseFrameContract.KEY_STATUS));
        assertEquals("B", done.get(SseFrameContract.KEY_GRADE), "grade 只取评估头实测档");
        assertTrue(done.get(SseFrameContract.KEY_DEGRADATIONS).contains("DG-D1"), done.toString());
    }

    @Test
    @DisplayName("补充帧 milestone：八帧无位的 Run 生命周期事件不冒充 step，且不带 payload 正文")
    void lifecycleMilestonesUseSupplementaryFrameAndWithholdPayload() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.COMPLETED)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of(
                event("e1", EventTypes.Agent.RUN_CREATED, "{\"question\":\"内部提问原文\"}"),
                event("e2", EventTypes.Agent.RUN_INPUT_RECEIVED, "{\"secret\":\"客户手机号13800000000\"}")));

        assertFalse(SseFrameContract.WAGENT_FRAME_ORDER.contains(SseFrameContract.FRAME_MILESTONE),
                "milestone 是通道补充帧，永不进 §10.3.6 八帧序");
        List<Frame> frames = stream(runDao, eventDao, mock(WAgentRunStepDao.class),
                mock(WAgentReadinessDao.class));
        List<Frame> milestones = framesOf(frames, SseFrameContract.FRAME_MILESTONE);
        assertEquals(2, milestones.size(), "两类 Run 生命周期事件应各出一帧 milestone，实得=" + names(frames));
        assertEquals(EventTypes.Agent.RUN_CREATED,
                data(milestones.get(0), SseFrameContract.FRAME_MILESTONE).get(SseFrameContract.KEY_EVENT_TYPE));
        // §6.3 引用型：列里存的实为内联 JSON，承流面对它没有"哪些键可外泄"的裁决依据 ⇒ 一律不出正文
        String body = frames.stream().map(f -> f.raw).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(body.contains("13800000000"), "milestone 帧禁外泄 payload 正文");
        assertFalse(body.contains("内部提问原文"), "milestone 帧禁外泄 payload 正文");
    }

    @Test
    @DisplayName("§10.3.6 帧 7：失败终态 error 紧接 done 之前，码/可重试性经 W 域错误码单源")
    void failedRunEmitsErrorImmediatelyBeforeDone() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.FAILED)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of(
                event("e1", EventTypes.Agent.RUN_FAILED, "{\"code\":\"E-WA-DEP\",\"service\":\"data-engine\"}")));

        List<Frame> frames = stream(runDao, eventDao, mock(WAgentRunStepDao.class),
                mock(WAgentReadinessDao.class));
        Map<String, String> error = onlyFrame(frames, SseFrameContract.FRAME_ERROR).data;
        assertEquals("E-WA-DEP", error.get(SseFrameContract.KEY_ERROR_CODE));
        assertEquals("true", error.get(SseFrameContract.KEY_RETRYABLE), "clientRetryable 取 §5.3 冻结列");
        // message 必须是错误码模板渲染值（含 service 位与诊断码），不是异常原文
        String message = error.get(SseFrameContract.KEY_MESSAGE);
        assertTrue(message.contains("data-engine"), "service 占位必须由渲染腿填充，实得=" + message);
        assertTrue(message.contains("诊断码 " + error.get(SseFrameContract.KEY_TRACE_ID)),
                "错误帧 message 的诊断码必须与本通道 traceId 同源，实得=" + message);
        int errorAt = names(frames).indexOf(SseFrameContract.FRAME_ERROR);
        assertEquals(frames.size() - 2, errorAt, "error 帧必须紧接 done 之前，实得=" + names(frames));
    }

    @Test
    @DisplayName("未注册错误码：只如实出码，retryable 缺位（禁 false 冒充\"已判定不可重试\"）")
    void unregisteredCodeOmitsRetryableKey() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.FAILED)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of(
                event("e1", EventTypes.Agent.RUN_FAILED, "{\"code\":\"E-WA-GHOST\"}")));

        Map<String, String> error = onlyFrame(stream(runDao, eventDao, mock(WAgentRunStepDao.class),
                mock(WAgentReadinessDao.class)), SseFrameContract.FRAME_ERROR).data;
        assertEquals("E-WA-GHOST", error.get(SseFrameContract.KEY_ERROR_CODE));
        assertFalse(error.containsKey(SseFrameContract.KEY_RETRYABLE),
                "码面漂移时该键必须缺位，实得键面=" + error.keySet());
    }

    @Test
    @DisplayName("预算耗尽而 Run 未终态：milestone(stream-timeout) 收流，不发 done（非终态冒充末帧）")
    void budgetExhaustedWithoutTerminalRunEmitsNoDone() throws Exception {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunEventDao eventDao = mock(WAgentRunEventDao.class);
        when(runDao.findById("r1")).thenReturn(Optional.of(run(RunStatus.EXECUTING)));
        when(eventDao.findByRunId("r1")).thenReturn(List.of());

        WAgentRunEventStreamController controller = new WAgentRunEventStreamController(
                runDao, eventDao, mock(WAgentRunStepDao.class), mock(WAgentReadinessDao.class),
                identityResolver(), Runnable::run);
        controller.streamBudgetMs = 1L; // 只缩短等待，帧契约分支不动

        List<Frame> frames = stream(controller);
        List<String> got = names(frames);
        assertFalse(got.contains(SseFrameContract.FRAME_DONE), "未终态不得出末帧，实得=" + got);
        List<Frame> milestones = framesOf(frames, SseFrameContract.FRAME_MILESTONE);
        assertFalse(milestones.isEmpty(), "预算耗尽路径必须补一帧 milestone(stream-timeout)，实得=" + got);
        Map<String, String> timeout = milestones.get(milestones.size() - 1).data;
        assertEquals(WAgentRunEventStreamController.STREAM_TIMEOUT_MILESTONE,
                timeout.get(SseFrameContract.KEY_EVENT_TYPE), "实得=" + got);
        assertEquals("meta", got.get(0), "超时路径同样必须首帧 meta");
    }

    // ── 装配与解析 ────────────────────────────────────────────────

    private static List<Frame> stream(WAgentRunDao runDao, WAgentRunEventDao eventDao,
                                      WAgentRunStepDao stepDao, WAgentReadinessDao readinessDao)
            throws Exception {
        return stream(new WAgentRunEventStreamController(runDao, eventDao, stepDao, readinessDao,
                identityResolver(), Runnable::run));
    }

    /**
     * 起流并取帧：SSE 在 MockMvc 下是异步写出，先直读、读不到再走 asyncDispatch。
     *
     * <p>两条腿都要试：只断其中一条会让"能读到帧"依赖 Spring 版本的派发时序。</p>
     */
    private static List<Frame> stream(WAgentRunEventStreamController controller) throws Exception {
        MockMvc mockMvc = mockMvc(controller);
        MvcResult result = mockMvc.perform(get("/api/v1/wagent/runs/r1/events/stream")).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (!body.contains("event:") && result.getRequest().isAsyncStarted()) {
            body = mockMvc.perform(asyncDispatch(result)).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
        }
        assertTrue(body.contains("event:"), "SSE 响应体未含任何帧（流未建立或派发失败）: " + body);
        return parse(body);
    }

    private static MockMvc mockMvc(WAgentRunEventStreamController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static WAgentIdentityResolver identityResolver() {
        WAgentIdentityResolver resolver = mock(WAgentIdentityResolver.class);
        when(resolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        return resolver;
    }

    private static List<Frame> parse(String sseBody) throws IOException {
        List<Frame> frames = new ArrayList<>();
        String event = null;
        String raw = null;
        for (String line : sseBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
                event = trimmed.substring("event:".length()).trim();
            } else if (trimmed.startsWith("data:")) {
                raw = trimmed.substring("data:".length()).trim();
            } else if (trimmed.isEmpty() && event != null) {
                frames.add(Frame.of(event, raw));
                event = null;
                raw = null;
            }
        }
        if (event != null) {
            frames.add(Frame.of(event, raw));
        }
        return frames;
    }

    private static List<String> names(List<Frame> frames) {
        return frames.stream().map(f -> f.event()).toList();
    }

    private static List<Frame> framesOf(List<Frame> frames, String event) {
        return frames.stream().filter(f -> event.equals(f.event())).toList();
    }

    private static Frame onlyFrame(List<Frame> frames, String event) {
        List<Frame> hit = framesOf(frames, event);
        assertEquals(1, hit.size(), "帧 " + event + " 应恰好一帧，实得=" + names(frames));
        return hit.get(0);
    }

    private static Map<String, String> data(Frame frame, String event) {
        assertEquals(event, frame.event(), "取载荷时帧名不符（解析错位即测试自身缺陷）");
        return frame.data;
    }

    /** 一帧 SSE：event 名 + data 行的字符串化键面（数值/布尔统一按线格式断言）。 */
    private record Frame(String event, Map<String, String> data, String raw) {
        static Frame of(String event, String raw) throws IOException {
            Map<String, String> data = new LinkedHashMap<>();
            if (raw != null) {
                JsonNode node = JSON.readTree(raw);
                node.fields().forEachRemaining(e -> data.put(e.getKey(), e.getValue().isTextual()
                        ? e.getValue().asText() : e.getValue().toString()));
            }
            return new Frame(event, data, raw);
        }
    }

    private static WAgentRunDO run(RunStatus status) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setQuestionId("q1");
        run.setPlanSource("PLAYBOOK");
        run.setStatus(status.value());
        run.setBudgetJson("{\"maxSteps\":8}");
        return run;
    }

    private static WAgentRunEventDO event(String id, String eventType, String payloadRefText) {
        WAgentRunEventDO e = new WAgentRunEventDO();
        e.setId(id);
        e.setRunId("r1");
        e.setEventType(eventType);
        e.setPayloadRefText(payloadRefText);
        e.setOccurTime(LocalDateTime.now());
        return e;
    }

    private static WAgentRunStepDO step(String stepKey, int attempt, String status) {
        WAgentRunStepDO s = new WAgentRunStepDO();
        s.setId("st-" + stepKey + "-" + attempt);
        s.setRunId("r1");
        s.setStepKey(stepKey);
        s.setAttempt(attempt);
        s.setStepType("DATA_QUERY");
        s.setToolName("data.query");
        s.setStatus(status);
        s.setPolicyEffect("ALLOW");
        return s;
    }
}
