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
    @DisplayName("自述如实：codeExecution=true / network=false，并标明执行发生在外部沙盒")
    void describeIsHonest() {
        Map<String, Object> desc = withTemplate(mock(RestTemplate.class)).describe();
        assertEquals("SANDBOX", desc.get("kind"));
        assertEquals(Boolean.TRUE, desc.get("codeExecution"));
        assertEquals(Boolean.FALSE, desc.get("network"));
        assertTrue(String.valueOf(desc.get("note")).contains("外部隔离沙盒"));
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
    @DisplayName("成功路径：data 段原样透出（含 metric），评估走与只读档同一套口径")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void successPassesDataThroughAndSharesEvaluation() {
        RestTemplate template = mock(RestTemplate.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", Map.of("bindingCount", 2, "docHitCount", 1));
        data.put("trace", List.of(Map.of("step", "run", "status", "success")));
        data.put("experimentSpec", Map.of("loopIndex", 0));
        data.put("advice", Map.of("summary", "from sandbox"));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(template.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));

        SandboxResearchExecutor executor = withTemplate(template);
        Map<String, Object> out = executor.executeRound("sc-1", "q", List.of(), 0);

        assertEquals("from sandbox", ((Map) out.get("advice")).get("summary"));
        assertTrue(out.get("metric") instanceof Map);

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
