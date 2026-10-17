package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-02 要点 2 验收 —— obligations 在<b>结果回 LLM 之前</b>落实，且
 * ai-engine 内<b>不实现任何脱敏/行过滤算法</b>（铁律 §2.4-1/2/3）。
 *
 * <p>doc 冻结的两个标识：</p>
 * <ul>
 *   <li>{@code ObligationsApplyTest#maskObligationAppliedBeforeLlmSeesResult} ——
 *       用 security 侧返回值断言"原文不在结果里、脱敏值在结果里"，并从咽喉
 *       {@code ToolExecutorService.execute} 出口再验一次（回 LLM 之前的可观测点）；</li>
 *   <li>{@code ObligationsApplyTest#engineDoesNotSelfImplementMasking} —— grep 计数门禁：
 *       对 ai-engine 各模块 src/main/java 下的全量生产源码扫描本地脱敏算法痕迹，命中数必须为 0；
 *       脱敏/行过滤端点出口必须<b>只有一个文件</b>（{@code AiSecurityEngineClient}）。</li>
 * </ul>
 *
 * <p>fail-closed 取向一并锁定：security 不可用、结果不可解析、行数超预算、mask 缺规则名
 * 四种形态都必须丢弃整条结果，禁"先返回再说明"。</p>
 */
class ObligationsApplyTest {

    private static final String RAW_PHONE = "13800001111";
    private static final String MASKED_PHONE = "138****1111";
    private static final String RESULT_JSON =
            "{\"rows\":[{\"id\":1,\"phone\":\"" + RAW_PHONE + "\"},"
            + "{\"id\":2,\"phone\":\"" + RAW_PHONE + "\"}],\"count\":2}";

    private static final ToolAdjudicationRequest.Subject SUBJECT =
            new ToolAdjudicationRequest.Subject("u-9", List.of("analyst"), "dept-a", "t-1");

    // ── 脱敏/行过滤算法痕迹（本类唯一的"算法"判据；扫描对象是生产源码） ──────────

    /**
     * 本地脱敏算法痕迹模式。命中即说明引擎侧自己把值改写了 —— 而按 §2.4-3，
     * 改写权只在 security-engine，引擎侧只允许"拆结构 + 委托 + 写回返回值"。
     *
     * <p>注意：{@code AgentToolPolicyGate#maskLiterals} 是 SQL 字面量归一（供关键字扫描用），
     * 不改写任何返回给用户/LLM 的数据值，因此不落在这些模式内。</p>
     */
    private static final List<Pattern> LOCAL_MASKING_MARKERS = List.of(
            Pattern.compile("\\*\\*\\*"),
            Pattern.compile("replaceAll\\s*\\([^)]*,\\s*\"\\\\?\\*"),
            Pattern.compile("(?i)\\b(?:desensitize|anonymize|deidentify|obfuscate)\\w*\\s*\\("),
            Pattern.compile("(?i)\\bmask(?:Phone|IdCard|IDCard|Email|BankCard|Amount|Name|ColumnValue)\\s*\\(")
    );

    /**
     * 允许出现 "mask" 字样的生产文件登记（只增不缩）：新文件一旦自称与脱敏相关，
     * 本门禁即失败，强制走架构官复核 —— 这正是 §2.4-7"禁重复实现"的可判化形态。
     */
    private static final Set<String> MASK_AWARE_MAIN_FILES = Set.of(
            "AiSecurityEngineClient.java",   // security 脱敏/行过滤端点的唯一出口
            "ToolObligations.java",          // 义务载荷容器（只解析结构）
            "ToolObligationApplier.java",    // 拆结果集 + 委托 security + 写回
            "AgentToolPolicyGate.java",      // maskLiterals：SQL 字面量归一，非数据脱敏
            "GuardrailsServiceImpl.java",    // 片段定位 + 委托 security
            "ToolExecutorService.java",      // 仅注释提及 mask[] 义务
            "AgentLoopService.java",         // 仅变量名 masked（承载 security 返回值）
            // 2026-10-11 实跑本门禁（历史各批 -DskipTests 从未执行）登记两条 wagent 面：
            // 二者只引用 PolicyEffect.ALLOW_WITH_MASK 枚举名作裁决效果判定，零本地脱敏算法
            // （algorithmHits 断言在同用例内已单独校验），符合 §2.4-7「只点名不实现」口径。
            "WAgentEnums.java",              // PolicyEffect 四值单源：allow_with_mask 枚举字面量
            "WAgentPolicyGuardPort.java"     // 依据 PolicyEffect 判定放行，不执行脱敏
    );

    // ── 验收 1：脱敏义务在结果回 LLM 之前生效 ────────────────────────────────

    @Test
    @DisplayName("F06-02 mask 义务在结果回 LLM 之前落实：咽喉出口只含 security 返回的脱敏值")
    void maskObligationAppliedBeforeLlmSeesResult() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        when(client.applyMasking(any(), any())).thenAnswer(inv -> maskEverything(inv.getArgument(0)));
        ToolObligations obligations = maskOf("phone", "phone");

        // ① 执行器层：返回值即被替换，原文不再出现
        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));
        ToolObligationApplier.Outcome outcome =
                applier.apply("query_db", RESULT_JSON, obligations, SUBJECT, null);
        assertTrue(outcome.applied(), "security 可用时义务必须落实: " + outcome.failReason());
        assertNotNull(outcome.json());
        assertFalse(outcome.json().contains(RAW_PHONE),
                "脱敏后原文仍随结果下发: " + outcome.json());
        assertTrue(outcome.json().contains(MASKED_PHONE), outcome.json());
        verify(client, times(1)).applyMasking(eq(List.of(RAW_PHONE, RAW_PHONE)), eq(List.of("phone")));

        // ② 咽喉层（回 LLM 的可观测出口）：ToolResult.content 已是脱敏后的文本
        Fixture f = new Fixture(client);
        ToolExecutorService.ToolResult r = f.service.execute("query_db", Map.of("scenarioId", "s-1"));
        assertTrue(r.isSuccess(), "ALLOW + 义务已落实：结果应正常回传: " + r.getError());
        assertFalse(r.getContent().contains(RAW_PHONE),
                "咽喉出口泄漏原文（F06-02 禁'先返回再说明'）: " + r.getContent());
        assertTrue(r.getContent().contains(MASKED_PHONE), r.getContent());
        assertEquals("ALLOW", r.getDecision());
    }

    // ── 验收 2：grep 计数门禁（引擎侧零脱敏算法） ────────────────────────────

    @Test
    @DisplayName("F06-02/§2.4-3 grep 计数门禁：ai-engine 生产源码内本地脱敏算法命中数 = 0")
    void engineDoesNotSelfImplementMasking() throws IOException {
        List<Path> mains = aiEngineMainSources();
        assertFalse(mains.isEmpty(), "未定位到 ai-engine 生产源码目录，门禁不得空转通过");

        List<String> algorithmHits = new ArrayList<>();
        Set<String> maskMentioning = new java.util.LinkedHashSet<>();
        List<String> endpointOwners = new ArrayList<>();

        for (Path file : mains) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String code = stripComments(text);
            for (Pattern marker : LOCAL_MASKING_MARKERS) {
                if (marker.matcher(code).find()) {
                    algorithmHits.add(file.getFileName() + " ← " + marker);
                }
            }
            if (text.toLowerCase(java.util.Locale.ROOT).contains("mask")) {
                maskMentioning.add(file.getFileName().toString());
            }
            if (code.contains("PATH_MASKING_APPLY") || code.contains("PATH_EVALUATE_FILTER")
                    || code.contains("/api/v1/security/masking/apply")
                    || code.contains("/api/security/evaluate-filter")) {
                endpointOwners.add(file.getFileName().toString());
            }
        }

        assertTrue(algorithmHits.isEmpty(),
                "ai-engine 内出现本地脱敏算法（应改为委托 security）: " + algorithmHits);
        assertEquals(MASK_AWARE_MAIN_FILES, maskMentioning,
                "脱敏相关文件清单发生偏移 —— 新增文件须先经架构官确认（§2.4-7），登记理由写在测试注释里");
        assertEquals(List.of("AiSecurityEngineClient.java"), endpointOwners.stream().distinct().sorted().toList(),
                "脱敏/行过滤端点的 URL 出口必须只有单一文件（§2.5 底座收口）；"
                + "他处仅允许在注释/错误文案里提端点名，不允许自行拼 URL");
    }

    // ── 行过滤：只保留 security 放行的行，判定权不在引擎 ──────────────────────

    @Test
    @DisplayName("F06-02 rowFilter 逐行交 security 判定；不放行的行丢弃（引擎不做谓词求值）")
    void rowFilterKeepsOnlySecurityAllowedRows() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        when(client.evaluateFilter(eq("dept = 'dept-a'"), any(), eq("analyst")))
                .thenAnswer(inv -> {
                    Map<String, Object> row = inv.getArgument(1);
                    boolean ok = Integer.valueOf(2).equals(row.get("id"));
                    Map<String, Object> verdict = new LinkedHashMap<>();
                    verdict.put("allowed", ok);
                    return verdict;
                });

        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));
        ToolObligations obligations = new ToolObligations(List.of(), "dept = 'dept-a'", Map.of());
        ToolObligationApplier.Outcome outcome =
                applier.apply("query_db", RESULT_JSON, obligations, SUBJECT, null);

        assertTrue(outcome.applied(), outcome.failReason());
        assertEquals(1, outcome.filtered(), "只应保留 security 放行的那一行");
        assertTrue(outcome.json().contains("\"count\":1"),
                "count 必须与过滤后行数一致（I-3 数字↔引用块同源纪律）: " + outcome.json());
        verify(client, times(2)).evaluateFilter(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("F06-02 行过滤判定缺放行字段 → 整行不放行（fail-closed，不默认放行）")
    void rowWithoutExplicitAllowIsDropped() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        when(client.evaluateFilter(anyString(), any(), anyString())).thenReturn(Map.of());

        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));
        ToolObligationApplier.Outcome outcome = applier.apply("query_db", RESULT_JSON,
                new ToolObligations(List.of(), "dept = 'dept-a'", Map.of()), SUBJECT, null);

        assertTrue(outcome.applied(), "判定本身可用，只是不放行 —— 属 DENY 面而非不可用面");
        assertEquals(0, outcome.filtered());
        assertFalse(outcome.json().contains(RAW_PHONE), outcome.json());
    }

    // ── 四种 fail-closed 形态：整条结果丢弃，禁"先返回再说明" ────────────────

    @Test
    @DisplayName("F06-02 security 脱敏端点不可用 → 整条结果丢弃 + FAIL_CLOSED 原因")
    void maskingServiceFailureDiscardsWholeResult() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        when(client.applyMasking(any(), any()))
                .thenThrow(new AiSecurityEngineClient.SecurityResponseUnparsableException("masking/apply 响应不可解析"));

        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));
        ToolObligationApplier.Outcome outcome =
                applier.apply("query_db", RESULT_JSON, maskOf("phone", "phone"), SUBJECT, null);

        assertFalse(outcome.applied());
        assertNull(outcome.json());
        assertTrue(outcome.failReason().contains("不可用"), outcome.failReason());

        // 咽喉层：义务落实失败必须把成功的工具结果翻转成 fail-closed 错误
        Fixture f = new Fixture(client);
        ToolExecutorService.ToolResult r = f.service.execute("query_db", Map.of("scenarioId", "s-1"));
        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), r.getError());
        assertFalse(String.valueOf(r.getError()).contains(RAW_PHONE),
                "丢弃结果时不得把原文写进错误文案: " + r.getError());
    }

    @Test
    @DisplayName("F06-02 结果不可解析为行结构而义务又存在 → 丢弃（不得假设'没有敏感列'）")
    void unparseableResultWithObligationsIsFailClosed() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));

        ToolObligationApplier.Outcome outcome = applier.apply("query_db",
                "查询完成，共 2 条记录（非结构化文本）", maskOf("phone", "phone"), SUBJECT, null);

        assertFalse(outcome.applied(), "不可解释的结构必须丢弃: " + outcome.json());
        assertTrue(outcome.failReason().contains("不可解析"), outcome.failReason());
    }

    @Test
    @DisplayName("F06-02 行数超逐行裁决上限 → 丢弃（禁抽样过滤后放行）")
    void rowOverBudgetIsFailClosed() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        StringBuilder sb = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < ToolObligationApplier.MAX_FILTER_ROWS + 1; i++) {
            sb.append(i > 0 ? "," : "").append("{\"id\":").append(i).append("}");
        }
        sb.append("],\"count\":").append(ToolObligationApplier.MAX_FILTER_ROWS + 1).append("}");

        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));
        ToolObligationApplier.Outcome outcome = applier.apply("query_db", sb.toString(),
                new ToolObligations(List.of(), "dept = 'dept-a'", Map.of()), SUBJECT, null);

        assertFalse(outcome.applied());
        assertTrue(outcome.failReason().contains("超逐行裁决上限"), outcome.failReason());
    }

    @Test
    @DisplayName("F06-02 mask 义务缺规则名 → 丢弃（无规则名即无从交 security 执行）")
    void maskWithoutRuleIsFailClosed() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));

        ToolObligationApplier.Outcome outcome = applier.apply("query_db", RESULT_JSON,
                maskOf("phone", null), SUBJECT, null);

        assertFalse(outcome.applied());
        assertTrue(outcome.failReason().contains("缺少规则名"), outcome.failReason());
        verify(client, never()).applyMasking(any(), any());
    }

    @Test
    @DisplayName("F06-02 无义务时原样透传（不得凭空加工结果）")
    void noObligationPassesResultThroughUnchanged() throws Exception {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        ToolObligationApplier applier = new ToolObligationApplier(client, new ObjectMapper(), gateFor(client));

        ToolObligationApplier.Outcome outcome =
                applier.apply("query_db", RESULT_JSON, ToolObligations.none(), SUBJECT, null);

        assertTrue(outcome.applied());
        assertEquals(RESULT_JSON, outcome.json());
        verify(client, never()).applyMasking(any(), any());
        verify(client, never()).evaluateFilter(anyString(), any(), anyString());
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    /**
     * 列面带装置（#142/B4-3d-6）：真口径 {@link AiToolColumnSurfaceGate}=真实门 + 桩 client。
     * 这些用例不指定表资产（asset=null），列面带在直测层面恒 NOT_APPLICABLE，
     * 不改变原有义务的判据；资产非 NONE 的列面路径由 {@code AiClsColumnSurfaceTest}
     * 与咽喉端到端用例单独覆盖。
     */
    private static AiToolColumnSurfaceGate gateFor(AiSecurityEngineClient client) {
        return new AiToolColumnSurfaceGate(client);
    }

    private static ToolObligations maskOf(String column, String rule) {
        return new ToolObligations(List.of(new ToolObligations.Mask(column, rule)), null, Map.of());
    }

    /** 模拟 security 的 masking/apply 返回：逐条给 index + masked。 */
    private static List<Map<String, Object>> maskEverything(List<String> values) {
        List<Map<String, Object>> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("masked", MASKED_PHONE);
            item.put("rule", "phone");
            out.add(item);
        }
        return out;
    }

    /** 咽喉装置：真执行器 + 假闸（ALLOW 带 mask 义务）+ 假注册表返回原文结果。 */
    private static final class Fixture {
        final ToolExecutorService service = new ToolExecutorService();
        final ToolRegistry registry = mock(ToolRegistry.class);

        Fixture(AiSecurityEngineClient client) {
            ToolDao toolDao = mock(ToolDao.class);
            AgentToolPolicyGate gate = mock(AgentToolPolicyGate.class);
            ToolSubjectResolver subjectResolver = mock(ToolSubjectResolver.class);
            when(subjectResolver.resolve()).thenReturn(SUBJECT);
            when(gate.review(any(), any(), any()))
                    .thenReturn(ToolDecision.allow("p-1", maskOf("phone", "phone"), 1L));
            when(registry.has("query_db")).thenReturn(true);
            when(registry.execute(eq("query_db"), any())).thenReturn(
                    ToolExecutorService.ToolResult.ok("call-1", "query_db", RESULT_JSON, 1L));

            service.setToolDao(toolDao);
            service.setPolicyGate(gate);
            service.setSubjectResolver(subjectResolver);
            service.setObligationApplier(new ToolObligationApplier(
                    client, new ObjectMapper(), gateFor(client)));
            ReflectionTestUtils.setField(service, "toolRegistry", registry);
        }
    }

    // ── 源码扫描定位（失败即 fail-loud，禁"找不到目录=通过"） ─────────────────

    /**
     * 去掉块注释与行注释，门禁只判<b>代码行为</b>：注释里写端点名是允许的文档行为
     * （{@code GuardrailsServiceImpl} 的类注释即声明"脱敏一律经 masking/apply 执行"），
     * 但代码里出现 URL 字面量或 {@code PATH_*} 常量就是自行拼请求 = §2.5 底座泄漏。
     */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ");
    }

    private static List<Path> aiEngineMainSources() {
        Path root = resolveAiEngineRoot();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .forEach(files::add);
        } catch (IOException e) {
            fail("扫描 ai-engine 源码失败: " + e.getMessage(), e);
        }
        return files;
    }

    private static Path resolveAiEngineRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            for (String candidate : List.of("engine/ai-engine",
                    "ecos_backend/engine/ai-engine", "ai-engine")) {
                Path resolved = p.resolve(candidate);
                if (Files.isDirectory(resolved)) return resolved;
            }
        }
        return fail("未能从 user.dir=" + start + " 定位 engine/ai-engine，脱敏门禁不得默认通过");
    }
}
