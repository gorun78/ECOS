package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * 沙盒研究执行器单测（2026-11-25，可行性报告 v2.0 §5.4 执行器分级）。
 *
 * <p><b>为什么必须单独测</b>：缺陷若出在本档，默认环境（未配置 base-url）永远走只读档，
 * 门禁全绿也发现不了。本类用 mock RestTemplate 覆盖三条 fail-closed 路径——</p>
 * <ol>
 *   <li>沙盒不可达 ⇒ 抛异常（禁回退成只读结果冒充本轮执行）；</li>
 *   <li>沙盒业务码非 0 ⇒ 抛异常（禁把失败当成功）；</li>
 *   <li>应答缺 metric ⇒ 抛异常（禁无读数入评估）；</li>
 * </ol>
 * <p>并断言成功路径把 data 段原样透出、且自述如实声明 codeExecution=true / network=false。</p>
 */
class SandboxResearchExecutorTest {

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private final ReadOnlyResearchExecutor fallback = new ReadOnlyResearchExecutor(dao);

    private SandboxResearchExecutor withTemplate(RestTemplate template) {
        return new SandboxResearchExecutor(fallback, template, "http://sandbox.local");
    }

    @Test
    @DisplayName("快照行只含已有事实：场景/轮次/假设条数，不臆造业务列")
    void snapshotRowsContainOnlyKnownFacts() {
        List<Map<String, Object>> rows = SandboxResearchExecutor.rowsForSnapshot(
                "sc-1", 2, List.of(Map.of("text", "a"), Map.of("text", "b")));
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("sc-1", row.get("scenarioId"));
        assertEquals(2, row.get("loopIndex"));
        assertEquals(2, row.get("hypothesisCount"));
        // 关键：不得出现业务度量列（没有的数据就是没有）
        assertEquals(3, row.size(), "快照行应只有 3 个键，实际: " + row.keySet());
    }

    @Test
    @DisplayName("快照行对 null 输入不抛异常，如实记 0/空串")
    void snapshotRowsTolerateNulls() {
        Map<String, Object> row = SandboxResearchExecutor.rowsForSnapshot(null, 0, null).get(0);
        assertEquals("", row.get("scenarioId"));
        assertEquals(0, row.get("loopIndex"));
        assertEquals(0, row.get("hypothesisCount"));
    }

    @Test
    @DisplayName("自述如实：codeExecution=true / network=false，且不隐瞒受控代码执行能力")
    void describeIsHonest() {
        Map<String, Object> desc = withTemplate(mock(RestTemplate.class)).describe();
        assertEquals("SANDBOX", desc.get("kind"));
        assertEquals(Boolean.TRUE, desc.get("codeExecution"));
        assertEquals(Boolean.FALSE, desc.get("network"));
        // 自述必须如实：
        // ①ECOS 本进程不执行代码（责任边界）；②能力枚举含受控代码执行（不隐瞒能力）。
        String note = String.valueOf(desc.get("note"));
        assertTrue(note.contains("不执行任何代码"), "须声明 ECOS 本进程不执行代码: " + note);
        Object caps = desc.get("capabilities");
        assertTrue(caps instanceof List && ((List<?>) caps).contains("CONTROLLED_CODE_EXECUTION"),
                "须如实列出受控代码执行能力: " + caps);
    }

    @Test
    @DisplayName("沙盒不可达 ⇒ 抛异常，绝不回退成只读结果冒充本轮执行")
    void unreachableSandboxThrowsInsteadOfSilentFallback() {
        RestTemplate template = mock(RestTemplate.class);
        when(template.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        SandboxResearchExecutor executor = withTemplate(template);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> executor.executeRound("sc-1", "q", List.of(), 0));
        assertTrue(String.valueOf(e.getMessage()).contains("不可用"), "异常须指明沙盒不可用: " + e.getMessage());
    }

    @Test
    @DisplayName("业务码非 0 ⇒ 抛异常，禁把失败当成功")
    void nonZeroBusinessCodeThrows() {
        RestTemplate template = mock(RestTemplate.class);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 500);
        envelope.put("message", "sandbox boom");
        when(template.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> withTemplate(template).executeRound("sc-1", "q", List.of(), 0));
        assertTrue(String.valueOf(e.getMessage()).contains("500"), "异常须带业务码: " + e.getMessage());
    }

    @Test
    @DisplayName("应答缺 metric ⇒ 抛异常，禁无读数入评估")
    void missingMetricThrows() {
        RestTemplate template = mock(RestTemplate.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trace", new ArrayList<>());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(template.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> withTemplate(template).executeRound("sc-1", "q", List.of(), 0));
        assertTrue(String.valueOf(e.getMessage()).contains("metric"), "异常须指明缺 metric: " + e.getMessage());
    }

    @Test
    @DisplayName("成功路径：与服务层契约同构（必须有 trace/advice/experimentSpec，否则 exec_trace 落 null）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void successProducesServiceContractShape() {
        RestTemplate template = mock(RestTemplate.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", Map.of("rowsIn", 2, "rowsAfterFilter", 2,
                "results", List.of(Map.of("op", "count", "value", 2))));
        data.put("insight", Map.of("metric", "sum(amt)", "spread", 220));
        data.put("snapshotDigest", "sha256:abc");
        data.put("sandbox", Map.of("version", "1.0.0"));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(template.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));

        SandboxResearchExecutor executor = withTemplate(template);
        Map<String, Object> out = executor.executeRound("sc-1", "q", List.of(), 0);

        // 服务层靠这三个键落库；缺任一即证据丢失（本轮修复的真实缺陷）
        assertTrue(out.get("trace") instanceof List, "必须有 trace");
        assertTrue(out.get("advice") instanceof Map, "必须有 advice");
        assertTrue(out.get("experimentSpec") instanceof Map, "必须有 experimentSpec");
        assertEquals("SANDBOX", ((Map) out.get("experimentSpec")).get("executor"));
        assertTrue(out.get("metric") instanceof Map);
        assertEquals("sha256:abc", out.get("snapshotDigest"));
        assertEquals(3, ((List) out.get("trace")).size(), "trace 应含 sandbox_call/metric/insight 三步");

        // 评估口径同源：绑定>0 且 命中>0 ⇒ accepted，且恒定降级（无业务真值）
        Map<String, Object> eval = executor.evaluate("sc-1", out, List.of());
        assertEquals(Boolean.TRUE, eval.get("accepted"));
        assertEquals(Boolean.TRUE, eval.get("degraded"));

        // 只读档对同一输入给出同一判定 ⇒ 换执行器不改语义
        Map<String, Object> fbEval = fallback.evaluate("sc-1", out, List.of());
        assertEquals(fbEval.get("accepted"), eval.get("accepted"));
        assertEquals(fbEval.get("degraded"), eval.get("degraded"));
    }
}
