package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * L3 代码进化单测（2026-11-25 用户授权）。
 *
 * <p>覆盖四类不可让步的语义：</p>
 * <ol>
 *   <li>解析：从围栏取代码、取理由；无围栏不崩；</li>
 *   <li>自检：缺 METRIC / 缺 class / 缺 main / 缺 print 全部拦住，禁把废代码送沙盒；</li>
 *   <li>生成失败 ⇒ 抛异常，<b>不返回假代码</b>；</li>
 *   <li>执行失败（超时/编译错/无指标）⇒ 状态如实为 failed，<b>不因基线成功而掩盖</b>。</li>
 * </ol>
 */
class ResearchCodeEvolutionTest {

    // ── 解析 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("解析：从 ```python 围栏取代码，理由单独提出")
    void extractsCodeFromFence() {
        String raw = "```python\nprint('METRIC 1')\n```\n理由：先做最小验证";
        assertEquals("print('METRIC 1')", ResearchCodeGenerator.extractCode(raw));
        assertTrue(ResearchCodeGenerator.extractRationale(raw).contains("最小验证"));
    }

    @Test
    @DisplayName("解析：无围栏时整体当代码；围栏未闭合返回空（不猜半截）")
    void handlesMissingOrBrokenFence() {
        assertEquals("print(1)", ResearchCodeGenerator.extractCode("print(1)"));
        assertEquals("", ResearchCodeGenerator.extractCode("```python\nprint(1)"));
        assertEquals("", ResearchCodeGenerator.extractCode(null));
    }

    // ── 自检 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("自检：缺指标契约/缺 main/缺 print 全部拦住")
    void selfCheckRejectsBrokenCode() {
        assertNotNull(ResearchCodeGenerator.selfCheck("public class M{}", "java"), "Java 缺 main 应拒");
        assertNotNull(ResearchCodeGenerator.selfCheck(
                "public class M{public static void main(String[] a){}}", "java"), "缺 METRIC 应拒");
        assertNotNull(ResearchCodeGenerator.selfCheck("print('x')", "python"), "Python 缺 METRIC 应拒");
        assertNotNull(ResearchCodeGenerator.selfCheck("", "python"), "空代码应拒");

        // 正例：结构齐全 + 有 METRIC ⇒ 通过
        assertNull(ResearchCodeGenerator.selfCheck(
                "public class M{public static void main(String[] a){System.out.println(\"METRIC 1\");}}",
                "java"));
        assertNull(ResearchCodeGenerator.selfCheck("print('METRIC 2')", "python"));
    }

    // ── 生成失败不编造 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("LLM 未装配 ⇒ 抛 CodeGenerationException，禁返回假代码")
    @SuppressWarnings("unchecked")
    void generationFailsLoudWhenLlmMissing() {
        ObjectProvider<RestTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ResearchCodeGenerator gen = new ResearchCodeGenerator(provider);

        ResearchCodeGenerator.CodeGenerationException e = assertThrows(
                ResearchCodeGenerator.CodeGenerationException.class,
                () -> gen.generate("python", "q", "h", List.of(), null));
        assertTrue(String.valueOf(e.getMessage()).contains("未装配"), e.getMessage());
    }

    @Test
    @DisplayName("LLM 返回不可解析内容 ⇒ 抛异常（不把散文当代码）")
    @SuppressWarnings("unchecked")
    void generationRejectsUnparsableOutput() {
        RestTemplate llm = mock(RestTemplate.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", "抱歉，我无法完成这个请求。");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(llm.exchange(anyString(), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));

        ObjectProvider<RestTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(llm);
        ResearchCodeGenerator gen = new ResearchCodeGenerator(provider);

        // 散文里没有 METRIC ⇒ 自检拒（即便 extractCode 把它当代码）
        assertThrows(ResearchCodeGenerator.CodeGenerationException.class,
                () -> gen.generate("python", "q", "h", List.of(), null));
    }

    // ── 代码进化路径：失败必须可见 ─────────────────────────────────────────────

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);

    private SandboxResearchExecutor executorWith(RestTemplate sandbox, ResearchCodeGenerator gen) {
        return new SandboxResearchExecutor(new ReadOnlyResearchExecutor(dao), sandbox,
                "http://sandbox.local", gen);
    }

    /** 生成器替身：返回固定代码。 */
    @SuppressWarnings("unchecked")
    private static ResearchCodeGenerator generatorReturning(String code) {
        RestTemplate llm = mock(RestTemplate.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", "```python\n" + code + "\n```\n理由：测试");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        envelope.put("data", data);
        when(llm.exchange(anyString(), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(envelope, HttpStatus.OK));
        ObjectProvider<RestTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(llm);
        return new ResearchCodeGenerator(provider);
    }

    @Test
    @DisplayName("代码实验成功 ⇒ state=success 且带 metric，trace 有 code_generate/code_execute")
    void codeExperimentSuccessIsRecorded() {
        RestTemplate sandbox = mock(RestTemplate.class);
        // 第 1 次调用 = 声明式 rounds（基线）；第 2 次 = 代码执行
        Map<String, Object> roundsData = Map.of("metric", Map.of("rowsIn", 1),
                "snapshotDigest", "sha256:x");
        Map<String, Object> roundsEnv = Map.of("code", 0, "data", roundsData);
        Map<String, Object> execData = Map.of("compiled", true, "exitCode", 0,
                "timedOut", false, "measuredMetric", 42, "stdout", "METRIC 42");
        Map<String, Object> execEnv = Map.of("code", 0, "data", execData);
        when(sandbox.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(roundsEnv, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(execEnv, HttpStatus.OK));

        SandboxResearchExecutor ex = executorWith(sandbox,
                generatorReturning("print('METRIC 42')"));
        Map<String, Object> out = ex.executeRound("sc-1", "q",
                List.of(Map.of("text", "h1")), 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exp = (Map<String, Object>) out.get("codeExperiment");
        assertEquals("success", exp.get("state"));
        assertEquals(42, exp.get("metric"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trace = (List<Map<String, Object>>) out.get("trace");
        assertTrue(trace.stream().anyMatch(s -> "code_generate".equals(s.get("step"))));
        assertTrue(trace.stream().anyMatch(s -> "code_execute".equals(s.get("step"))));
    }

    @Test
    @DisplayName("代码实验超时 ⇒ state=failed/stage=timeout，基线 metric 不受影响（不掩盖、不中断）")
    void codeExperimentTimeoutIsVisibleButDoesNotBreakBaseline() {
        RestTemplate sandbox = mock(RestTemplate.class);
        Map<String, Object> roundsEnv = Map.of("code", 0,
                "data", Map.of("metric", Map.of("rowsIn", 1), "snapshotDigest", "sha256:x"));
        Map<String, Object> execData = new java.util.LinkedHashMap<>();
        execData.put("compiled", true);
        execData.put("exitCode", -1);
        execData.put("timedOut", true);
        execData.put("measuredMetric", null);   // Map.of 不允许 null，必须用可空 Map
        execData.put("timeoutMs", 3000);
        Map<String, Object> execEnv = Map.of("code", 0, "data", execData);
        when(sandbox.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(roundsEnv, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(execEnv, HttpStatus.OK));

        // 注意：生成的代码必须含 METRIC，否则会被自检拦在 generate 阶段、测不到超时。
        // 真实场景里 LLM 先打印再死循环，是可能发生的。
        SandboxResearchExecutor ex = executorWith(sandbox,
                generatorReturning("print('METRIC 0')\nwhile True: pass"));
        Map<String, Object> out = ex.executeRound("sc-1", "q",
                List.of(Map.of("text", "h1")), 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exp = (Map<String, Object>) out.get("codeExperiment");
        assertEquals("failed", exp.get("state"));
        assertEquals("timeout", exp.get("stage"));
        assertNull(exp.get("metric"));
        // 关键：基线读数仍在，不因代码实验超时而丢
        assertNotNull(out.get("metric"));
    }

    @Test
    @DisplayName("跑通但未打印 METRIC ⇒ 不算成功（禁把「有输出」当「有指标」）")
    void codeExperimentWithoutMetricIsNotSuccess() {
        RestTemplate sandbox = mock(RestTemplate.class);
        Map<String, Object> roundsEnv = Map.of("code", 0,
                "data", Map.of("metric", Map.of("rowsIn", 1), "snapshotDigest", "sha256:x"));
        Map<String, Object> execData = new java.util.LinkedHashMap<>();
        execData.put("compiled", true);
        execData.put("exitCode", 0);
        execData.put("timedOut", false);
        execData.put("measuredMetric", null);   // Map.of 不允许 null
        execData.put("stdout", "hello world");
        Map<String, Object> execEnv = Map.of("code", 0, "data", execData);
        when(sandbox.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(roundsEnv, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(execEnv, HttpStatus.OK));

        // 「跑通了但没按契约打印 METRIC」：自检要求源码里出现 METRIC 字样，
        // 但运行时不一定真的输出它（例如放在未执行分支里）——这正是 no_metric 的场景。
        SandboxResearchExecutor ex = executorWith(sandbox,
                generatorReturning("if False:\n    print('METRIC 0')\nprint('hello world')"));
        Map<String, Object> out = ex.executeRound("sc-1", "q",
                List.of(Map.of("text", "h1")), 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exp = (Map<String, Object>) out.get("codeExperiment");
        assertEquals("failed", exp.get("state"));
        assertEquals("no_metric", exp.get("stage"));
    }

    @Test
    @DisplayName("LLM 生成失败 ⇒ state=failed/stage=generate，整轮仍返回基线读数")
    void generationFailureDoesNotAbortRound() {
        RestTemplate sandbox = mock(RestTemplate.class);
        Map<String, Object> roundsEnv = Map.of("code", 0,
                "data", Map.of("metric", Map.of("rowsIn", 1), "snapshotDigest", "sha256:x"));
        when(sandbox.exchange(any(java.net.URI.class), any(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(roundsEnv, HttpStatus.OK));

        SandboxResearchExecutor ex = executorWith(sandbox,
                new ResearchCodeGenerator(mockProviderNull()));
        Map<String, Object> out = ex.executeRound("sc-1", "q",
                List.of(Map.of("text", "h1")), 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exp = (Map<String, Object>) out.get("codeExperiment");
        assertEquals("failed", exp.get("state"));
        assertEquals("generate", exp.get("stage"));
        assertNotNull(out.get("metric"), "生成失败不应抹掉基线读数");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RestTemplate> mockProviderNull() {
        ObjectProvider<RestTemplate> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }
}
