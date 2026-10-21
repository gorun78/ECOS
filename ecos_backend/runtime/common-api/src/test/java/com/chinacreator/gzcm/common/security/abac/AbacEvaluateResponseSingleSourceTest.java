package com.chinacreator.gzcm.common.security.abac;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ABAC {@code policy-engine/evaluate} 应答<b>裁决键面的单源静态门禁</b>
 * （#142/B4-3e-4，收口差距 <b>D-B4-37</b>，并首次把同批实证的 <b>D-B4-39</b>
 * 「只认历史别名 ⇒ 静默恒 DENY」钉成可判据；与同族的
 * {@code GuardrailObligationVocabularySingleSourceTest}（D-B4-31）、
 * {@code RlsExecutionContractGateTest}（D-B4-17）、
 * {@code ClsColumnSurfaceContractGateTest}（D-B4-25）同构）。
 *
 * <p><b>钉四件事</b>：
 * <ol>
 *   <li><b>消费集枚举</b>：扫描根内所有主源码经<b>引号感知</b>剥注释后仍含
 *       {@code policy-engine/evaluate} 字面量的文件＝该应答的消费方，必须与登记清单
 *       逐字相等。新增消费方不登记即红（"没人知道有第八个读侧"正是 D-B4-39 能活下来的原因）。</li>
 *   <li><b>键面单源</b>：七个读侧文件内（建批口径；#142/B4-3e-8 收口 D-B4-43 后为八个，工具腿 {@code CheckPermissionTool}
 *       由透传应答原文改为三态读数，从此也受本判据约束），裁决词（{@code allow}/{@code result}/
 *       {@code decision}/{@code verdict}/{@code source}/{@code fallback}/{@code opaError}/
 *       {@code DENY_OPA_DOWN}）的<b>读形态访问器</b>（{@code .get("…")}/{@code .path("…")}/
 *       {@code .getOrDefault("…")}/{@code .containsKey("…")}/{@code .has("…")}）零命中，
 *       兜底取值字面量（{@code "fallback"}/{@code "DENY_OPA_DOWN"}）零命中。
 *       <b>只禁读形态</b>：审计事件自身的 {@code event.put("result", …)}／
 *       {@code event.get("result")} 属审计 payload 契约，不在本键面（见豁免登记）。</li>
 *   <li><b>委托真在链上</b>：逐个读侧断言它引用了 {@link AbacEvaluateResponseContract}
 *       的具体读数点（{@code verdictOf}／{@code isExplicitAllow}／键常量），
 *       生产侧 {@code OpaPolicyService} 的写面同样取单源 —— 只加常量类不钉这条，
 *       下一版重构就能把委托静默摘掉而全绿。</li>
 *   <li><b>判据自身可信</b>：反向 fixture 证明对合成裸键能变红、对注释提及不误报，
 *       并证明<b>字符串内的 {@code //} 不会被当行注释抹掉</b>
 *       （朴素正则实测会把 {@code "http://localhost:8080/api/v1/policy-engine/evaluate"}
 *       整行吃掉 ⇒ 该消费方从消费集里凭空消失，登记为 <b>D-B4-40</b>）。</li>
 * </ol>
 *
 * <p><b>HEAD 基线（本批收口口径，逐文件 {@code git show HEAD:… | grep -c} 实测）</b>：
 * 裸读形态 31 处，其中 1 处属审计事件回读（豁免），裁决键面 30 处 ——
 * {@code AiSecurityEngineClient} 8 / {@code SecurityEngineBridge} 9（含 1 豁免）/
 * {@code PipelineSecurityService} 9 / 认知 {@code SecurityPolicyReviewRoleDecider} 2 /
 * kb·本体·workspace 三个咽喉各 1。故台账里 D-B4-37 原文的"三个消费方"是<b>下偏计数</b>，
 * 本批按 9 文件 / 7 读侧 / 30 处订正。</p>
 */
class AbacEvaluateResponseSingleSourceTest {

    /** 裁决词表唯一属主（本门禁的"允许持有者"）。 */
    private static final String ABAC_CONTRACT =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/"
                    + "security/abac/AbacEvaluateResponseContract.java";
    private static final String GUARDRAIL_CONTRACT =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/"
                    + "security/guardrail/GuardrailDecisionContract.java";

    /** 应答生产侧（写面键面同样必须取单源）。 */
    private static final String OPA_PRODUCER =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/OpaPolicyService.java";

    // ── 消费集登记清单（仓根相对路径；新增/删除消费方必须同步改这里＝强制登记） ──

    private static final String AI_CLIENT =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/security/AiSecurityEngineClient.java";
    private static final String LLM_BRIDGE =
            "ecos_backend/runtime/llm-gateway/src/main/java/com/chinacreator/"
                    + "gzcm/runtime/llm/security/SecurityEngineBridge.java";
    private static final String PIPELINE_SVC =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/pipeline/PipelineSecurityService.java";
    private static final String KB_CLIENT =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/security/KnowledgeNavSecurityEngineClient.java";
    private static final String ONTOLOGY_CLIENT =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ontology/security/SecurityEngineClient.java";
    private static final String WORKSPACE_CLIENT =
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/security/WorkspaceSecurityEngineClient.java";
    private static final String COGNITIVE_DECIDER =
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/cognitive2/service/backtest/SecurityPolicyReviewRoleDecider.java";
    /** 只持端点路径、不读应答的一支（限流表）。 */
    private static final String RATE_LIMIT_FILTER =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/RateLimitFilter.java";
    /** 工具透传腿：#142/B4-3e-8（D-B4-43）前只持路径不读数，现改为三态归一读侧。 */
    private static final String CHECK_PERMISSION_TOOL =
            "ecos_backend/services/agent-service/src/main/java/com/chinacreator/gzcm/services/"
                    + "agent/runtime/toolrouter/tools/CheckPermissionTool.java";

    /** 真正读取应答、必须委托单源读数的八个文件（＝ D-B4-39 的受影响面 + 工具腿）。 */
    private static final Set<String> READERS = Set.of(
            AI_CLIENT, LLM_BRIDGE, PIPELINE_SVC, KB_CLIENT, ONTOLOGY_CLIENT,
            WORKSPACE_CLIENT, COGNITIVE_DECIDER, CHECK_PERMISSION_TOOL);

    /** 全量登记清单＝实测消费集（读侧 8 + 只持路径不读数 1）。 */
    private static final Set<String> REGISTERED_CONSUMERS = Set.of(
            AI_CLIENT, LLM_BRIDGE, PIPELINE_SVC, KB_CLIENT, ONTOLOGY_CLIENT,
            WORKSPACE_CLIENT, COGNITIVE_DECIDER, RATE_LIMIT_FILTER, CHECK_PERMISSION_TOOL);

    /**
     * 读形态豁免：文件 → 允许保留的读形态串。
     *
     * <p>{@code SecurityEngineBridge} 的 {@code event.get("result")} 读的是<b>自己组装的
     * 审计事件</b>（裁决落审计时的字段回读），键名属审计 payload 契约、不属 ABAC 应答键面。
     * 豁免只到"具体文件 + 具体访问器"粒度，新增第二处即红。</p>
     */
    private static final Map<String, Set<String>> READ_EXEMPTIONS = Map.of(
            LLM_BRIDGE, Set.of(".get(\"" + AbacEvaluateResponseContract.KEY_RESULT_LEGACY + "\""));

    /** 禁列的裁决词（取单源常量，禁在门禁里另抄一份字面量）。 */
    private static final List<String> BANNED_WORDS = List.of(
            AbacEvaluateResponseContract.KEY_ALLOW,
            AbacEvaluateResponseContract.KEY_RESULT_LEGACY,
            AbacEvaluateResponseContract.KEY_DECISION,
            AbacEvaluateResponseContract.KEY_VERDICT,
            AbacEvaluateResponseContract.KEY_SOURCE,
            AbacEvaluateResponseContract.KEY_FALLBACK,
            AbacEvaluateResponseContract.KEY_OPA_ERROR,
            AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN,
            GuardrailDecisionContract.KEY_POLICY_STATE);

    /** 读形态访问器（写形态 {@code .put("…")} 属生产侧，单独由验收 4 钉）。 */
    private static final List<String> READ_ACCESSORS = List.of(
            ".get(\"", ".path(\"", ".getOrDefault(\"", ".containsKey(\"", ".has(\"",
            ".optString(\"", ".optBoolean(\"", ".getAsBoolean(\"");

    /** 兜底态取值字面量在消费侧同样不得内联（读数应交给 {@code verdictOf}）。 */
    private static final List<String> BANNED_VALUE_LITERALS = List.of(
            AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK,
            AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN);

    private record Scanned(Path file, String relPath, String code) {
    }

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        return fail("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker），本门禁不得默认通过");
    }

    private static String readRepoFile(String relative) {
        Path p = repoRoot().resolve(relative);
        if (!Files.isRegularFile(p)) {
            fail("门禁依赖的文件不存在，不得当作\"无违例\": " + relative);
        }
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("读取源码失败: " + p, e);
        }
    }

    /**
     * 引号感知的注释剥离：字符串/字符字面量内的 {@code //} 与 {@code /*} 不是注释。
     *
     * <p>同族门禁沿用的朴素正则 {@code replaceAll("//[^\\n]*", " ")} 会把
     * {@code postForObject("http://localhost:8080/api/v1/policy-engine/evaluate", …)}
     * 整行吃掉 —— 本批实测正是靠它才发现 {@code CheckPermissionTool} 在朴素口径下
     * <b>从消费集里消失</b>（差距 <b>D-B4-40</b>）。口径实现单源在
     * {@link SourceCommentStripper}，本处只作委托；{@code codeDoesNotStripUrlCommentMarkers}
     * 与 {@code NaiveCommentStripperBanTest} 双向钉住"不得改回正则"。</p>
     */
    private static String stripComments(String src) {
        return SourceCommentStripper.strip(src);
    }

    private static List<Scanned> backendMainSources() {
        Path root = repoRoot().resolve("ecos_backend");
        List<Scanned> out = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            List<Path> files = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .forEach(files::add);
            for (Path f : files) {
                String rel = repoRoot().relativize(f).toString().replace('\\', '/');
                out.add(new Scanned(f, rel, stripComments(new String(
                        Files.readAllBytes(f), StandardCharsets.UTF_8))));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描主源码失败: " + e.getMessage(), e);
        }
        return out;
    }

    /** 消费集判据：剥注释后仍写端点路径字面量的主源码文件。 */
    private static Set<String> evaluateConsumers(List<Scanned> scanned) {
        Set<String> consumers = new TreeSet<>();
        for (Scanned s : scanned) {
            if (s.code().contains("policy-engine/evaluate")) consumers.add(s.relPath());
        }
        return consumers;
    }

    /** 某文件内的读形态违例清单（{@code 访问器("词"} 形态，已按豁免集过滤）。 */
    private static Set<String> bareReads(String code, String relPath) {
        Set<String> exempt = READ_EXEMPTIONS.getOrDefault(relPath, Set.of());
        Set<String> hits = new TreeSet<>();
        for (String word : BANNED_WORDS) {
            for (String acc : READ_ACCESSORS) {
                String needle = acc + word + "\"";
                if (code.contains(needle) && !exempt.contains(needle)) {
                    hits.add(needle);
                }
            }
        }
        for (String value : BANNED_VALUE_LITERALS) {
            String needle = "\"" + value + "\"";
            if (code.contains(needle)) hits.add(needle + " (取值字面量)");
        }
        return hits;
    }

    private static String codeOf(String relative) {
        return stripComments(readRepoFile(relative));
    }

    // ── 验收 1：消费集枚举与登记清单一致 ────────────────────────────────────

    @Test
    @Timeout(value = 120)
    @DisplayName("消费集：写端点路径的主源码文件必须逐个登记（新增读侧不登记即红）")
    void evaluateConsumerSetIsRegistered() {
        List<Scanned> scanned = backendMainSources();
        assertTrue(scanned.size() > 500,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");

        Set<String> consumers = evaluateConsumers(scanned);
        assertEquals(REGISTERED_CONSUMERS, consumers,
                "policy-engine/evaluate 消费集与登记清单不符。新增消费方＝又一个没人知道的读侧，"
                        + "必须先把它纳进本批验收 2/3（D-B4-39 的成因形态）；"
                        + "实测消费集=" + consumers);
        assertTrue(consumers.containsAll(READERS), "八个读侧咽喉有文件掉出消费集");
    }

    // ── 验收 2：读侧键面零内联 ──────────────────────────────────────────────

    @Test
    @Timeout(value = 120)
    @DisplayName("八个读侧文件内裁决键面零裸读：读形态访问器与兜底取值字面量都不得出现")
    void readersCarryNoInlineVerdictKeys() {
        List<Scanned> scanned = backendMainSources();
        Map<String, String> byRel = new TreeMap<>();
        for (Scanned s : scanned) {
            if (READERS.contains(s.relPath())) byRel.put(s.relPath(), s.code());
        }
        assertEquals(READERS.size(), byRel.size(),
                "读侧文件未全部进入扫描结果（应为 8 个），不得对缺文件默认放行: " + byRel.keySet());

        Map<String, Set<String>> violations = new TreeMap<>();
        for (Map.Entry<String, String> e : byRel.entrySet()) {
            Set<String> hits = bareReads(e.getValue(), e.getKey());
            if (!hits.isEmpty()) violations.put(e.getKey(), hits);
        }
        assertTrue(violations.isEmpty(),
                "ABAC 应答裁决键又回到消费方内联读法（D-B4-37 的根因形态；改键名即静默恒判，D-B4-39）:\n"
                        + violations);
    }

    // ── 验收 3：委托真在链上（把委托摘掉即红） ──────────────────────────────

    @Test
    @DisplayName("读侧逐个钉：三态读数点/键常量必须真被引用")
    void everyReaderDelegatesToTheContract() {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put(AI_CLIENT, List.of("AbacEvaluateResponseContract.verdictOf"));
        expected.put(LLM_BRIDGE, List.of("AbacEvaluateResponseContract.verdictOf",
                "AbacEvaluateResponseContract.Verdict"));
        expected.put(PIPELINE_SVC, List.of("AbacEvaluateResponseContract.isExplicitAllow",
                "AbacEvaluateResponseContract.KEY_FALLBACK"));
        expected.put(KB_CLIENT, List.of("AbacEvaluateResponseContract.isExplicitAllow"));
        expected.put(ONTOLOGY_CLIENT, List.of("AbacEvaluateResponseContract.isExplicitAllow"));
        // #142/B4-3e-6（D-B4-41）：两支咽喉改取三态读数——isExplicitAllow 对 DENY 与
        // UNEVALUATED 同值，拿它做布尔结论即把"护栏没说话"折成"策略拒绝"。
        expected.put(WORKSPACE_CLIENT, List.of("AbacEvaluateResponseContract.verdictOf("));
        expected.put(COGNITIVE_DECIDER, List.of("AbacEvaluateResponseContract.verdictOfTree",
                "AbacEvaluateResponseContract.Verdict"));
        // #142/B4-3e-8（D-B4-43）：CheckPermission 工具腿收口前把应答原文透传给 LLM（零读数），
        // 现改取三态并只回载荷 —— 它从此也是读侧，委托被摘掉即回到"模型看到一坨 envelope"的形态。
        expected.put(CHECK_PERMISSION_TOOL, List.of("AbacEvaluateResponseContract.verdictOf",
                "AbacEvaluateResponseContract.Verdict"));

        for (Map.Entry<String, List<String>> e : expected.entrySet()) {
            String code = codeOf(e.getKey());
            for (String ref : e.getValue()) {
                assertTrue(code.contains(ref),
                        e.getKey() + " 未引用单源读数点 " + ref
                                + "（委托被摘掉＝回到各写各的键面，门禁的\"零违例\"随之空转）");
            }
        }
    }

    @Test
    @DisplayName("D-B4-39 三支恒 DENY 咽喉必须走 allow 单源读数，不得只剩别名读法")
    void theThreeSilentlyDenyingGatesReadTheCurrentForm() {
        for (String rel : List.of(KB_CLIENT, ONTOLOGY_CLIENT)) {
            String code = codeOf(rel);
            assertTrue(code.contains("AbacEvaluateResponseContract.isExplicitAllow"),
                    rel + " 未改读单源（收口前它只认历史别名 result，现契约应答恒读到 null ⇒ 恒 DENY）");
            assertFalse(code.contains("Boolean.TRUE.equals"),
                    rel + " 又回到裸布尔判定形态（该形态正是 D-B4-39 的发生位置）");
        }
        // workspace 咽喉在 #142/B4-3e-6 进一步改取三态（D-B4-41）：读数升级后不再允许退回布尔helper
        String workspace = codeOf(WORKSPACE_CLIENT);
        assertTrue(workspace.contains("AbacEvaluateResponseContract.verdictOf("),
                WORKSPACE_CLIENT + " 未取三态读数（D-B4-41 的折叠形态已回归）");
        assertFalse(workspace.contains("AbacEvaluateResponseContract.isExplicitAllow"),
                WORKSPACE_CLIENT + " 退回布尔 helper ⇒ DENY 与 UNEVALUATED 再次同值");
    }

    // ── 验收 3b：不可用折叠面棘轮（#142/B4-3e-6，D-B4-41／新登记 D-B4-44） ────

    /**
     * 把裁决读数做成<b>布尔结论</b>的消费点（DENY 与 UNEVALUATED 同值）。
     *
     * <p>两条形态：①直接调
     * {@code AbacEvaluateResponseContract.isExplicitAllow(...)}——该 helper 只对 ALLOW 返回
     * {@code true}，对 DENY 与 UNEVALUATED 都返回 {@code false}；②本类自带一个
     * {@code private/static boolean isExplicitAllow(…)} helper，内部虽委托
     * {@code verdictOf} 拿三态，但对外只回布尔，调用方无从分态。两种都是差距
     * <b>D-B4-41</b> 的形态。本批只闭合台账在册的两支（认知 decider／workspace 导出咽喉）；
     * 复扫时另发现同型四处（kb 导航写闸、本体操作授权、data pipeline 执行裁决三处调用、
     * llm-gateway 本地布尔 helper）—— 它们把不可用落成 403/拒绝 reason，形态与 workspace
     * 收口前一致，但分别需要各自的模块异常类型与状态映射，<b>不在本批顺手动刀</b>，登记为
     * <b>D-B4-44</b> 并以下面这份清单做棘轮：清单只准缩短，新增未登记折叠点即红。</p>
     */
    private static final Set<String> UNEVALUATED_FOLDING_SITES = Set.of(
            KB_CLIENT, ONTOLOGY_CLIENT, PIPELINE_SVC, LLM_BRIDGE);

    @Test
    @Timeout(value = 120)
    @DisplayName("折叠面棘轮：仍用布尔结论做裁决的读侧必须逐个在册（D-B4-44），新增即红、收口须同步删项")
    void unevaluatedFoldingSitesAreRatcheted() {
        Map<String, List<String>> found = new TreeMap<>();
        for (Scanned s : backendMainSources()) {
            if (!s.code().contains("policy-engine/evaluate")) continue;
            String shape = booleanConclusionShape(s.code());
            if (shape != null) {
                found.put(s.relPath(), List.of(shape));
            }
        }
        assertEquals(UNEVALUATED_FOLDING_SITES, found.keySet(),
                "ABAC 不可用折叠面与在册清单不符（新增未登记＝又一个没人知道的假 403；"
                        + "收口未删项＝棘轮失效）。实测=" + found);
        // 已闭合的两支不得回归（同一条判据对它们必须为假）
        for (String closed : List.of(COGNITIVE_DECIDER, WORKSPACE_CLIENT)) {
            assertNull(booleanConclusionShape(codeOf(closed)),
                    closed + " 退回布尔 helper ⇒ D-B4-41 复发");
        }
    }

    /**
     * 该文件是否存在"对外只回布尔"的裁决结论形态；命中则返回形态说明（供断言消息定位），
     * 未命中返回 {@code null}。
     *
     * <p>形态②要求<b>同文件内确有调用点</b>（出现次数 &gt; 1 即声明之外还有调用）：
     * ai-engine 的 {@code AiSecurityEngineClient#isExplicitAllow} 只有声明、<b>零调用方</b>
     * （实测全仓 main 面仅 1 处命中＝声明本身），其裁决消费链一律走
     * {@code verdictOf} 的三态串（{@code AgentToolPolicyGate}／{@code GuardrailsServiceImpl}
     * 把 UNEVALUATED 映射成 {@code FAIL_CLOSED}＋503，实测），故不在册；
     * 若日后有人调用它，本判据立刻把它抓进清单——那时要么分态改造，要么显式登记。</p>
     */
    private static String booleanConclusionShape(String code) {
        int contractHelper = code.indexOf("AbacEvaluateResponseContract.isExplicitAllow");
        if (contractHelper >= 0) {
            return "调用契约布尔 helper，offset " + contractHelper;
        }
        int hits = 0;
        for (int i = code.indexOf("isExplicitAllow("); i >= 0; i = code.indexOf("isExplicitAllow(", i + 1)) {
            hits++;
        }
        if (hits > 1 && code.contains("boolean isExplicitAllow(")) {
            return "本类自带 boolean isExplicitAllow(…) helper 且同文件有调用点（三态在内部被压成布尔）";
        }
        return null;
    }

    @Test
    @DisplayName("生产侧写面取单源：OpaPolicyService 不内联 source/fallback/opaError 键与取值")
    void producerWritesViaContractKeys() {
        String code = codeOf(OPA_PRODUCER);
        assertTrue(code.contains("AbacEvaluateResponseContract.KEY_SOURCE")
                        && code.contains("AbacEvaluateResponseContract.VALUE_SOURCE_OPA")
                        && code.contains("AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK"),
                "生产侧来源键/取值未取单源（写侧一改，七个读侧同时静默失配）");
        assertTrue(code.contains("AbacEvaluateResponseContract.KEY_FALLBACK")
                        && code.contains("AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN"),
                "兜底理由键/取值未取单源");
        assertTrue(code.contains("AbacEvaluateResponseContract.KEY_OPA_ERROR"),
                "OPA 错误标记键未取单源");
        for (String needle : List.of(".put(\"source\"", ".put(\"fallback\"", ".put(\"opaError\"",
                "\"DENY_OPA_DOWN\"", "\"fallback\"")) {
            assertFalse(code.contains(needle),
                    "生产侧写面回到裸字面量: " + needle + "（D-B4-37 要求生产/消费同一份键面）");
        }
        // OPA 上游信封的 result 不属本契约（那是 /v1/data 应答形态），钉它仍在、且未被误当裁决别名读
        assertTrue(code.contains("root.get(\"result\")"),
                "OPA 上游信封读数点被挪动：那是另一个契约（不是 evaluate 应答别名），"
                        + "若本批把它并键，登记即失真");
    }

    // ── 验收 4：单源自身在位（防空转） ──────────────────────────────────────

    @Test
    @DisplayName("单源确实持有这套键面：常量漂移即红，\"零违例\"才不是词面消失的假象")
    void contractItselfHoldsTheKeySurface() {
        String code = codeOf(ABAC_CONTRACT);
        for (String word : List.of(AbacEvaluateResponseContract.KEY_RESULT_LEGACY,
                AbacEvaluateResponseContract.KEY_DECISION,
                AbacEvaluateResponseContract.KEY_VERDICT,
                AbacEvaluateResponseContract.KEY_SOURCE,
                AbacEvaluateResponseContract.KEY_FALLBACK,
                AbacEvaluateResponseContract.KEY_OPA_ERROR,
                AbacEvaluateResponseContract.VALUE_SOURCE_OPA,
                AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK,
                AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN)) {
            assertTrue(code.contains("\"" + word + "\""),
                    "单源不再持有字面量 \"" + word + "\" ⇒ 本门禁的零违例是空转");
        }
        // KEY_ALLOW 是引用而非重定义（禁两套字面量并存）
        assertFalse(code.contains("String KEY_ALLOW = \"allow\""),
                "单源重定义了护栏侧的 allow 字面量（KEY_ALLOW 必须引用 GuardrailDecisionContract）");
        assertTrue(codeOf(GUARDRAIL_CONTRACT).contains("\"" + GuardrailDecisionContract.KEY_ALLOW + "\""),
                "护栏侧 allow 字面量缺失 ⇒ 本批的分工登记失真");
    }

    @Test
    @DisplayName("批次与差距登记在位：D-B4-37/D-B4-39 的上下文不得被后续顺手抹掉")
    void registrationMarkersExist() {
        String contract = readRepoFile(ABAC_CONTRACT);
        assertTrue(contract.contains("D-B4-37") && contract.contains("D-B4-39"),
                "单源类注释丢失差距编号 ⇒ 台账与本类的对应关系断链");
        assertTrue(contract.contains("#142/B4-3e-4"), "本批收口标记缺失");
        assertTrue(contract.contains("GuardrailDecisionContract"),
                "两份契约的分工（义务词表／envelope 读数）登记缺失");
        // #142/B4-3e-6（D-B4-41）：折叠纪律与树形态读数点必须留在单源里
        assertTrue(contract.contains("D-B4-41") && contract.contains("#142/B4-3e-6"),
                "单源丢失 D-B4-41 的折叠纪律登记 ⇒ \"只调 isExplicitAllow 即合规\"的误读会回来");
        assertTrue(contract.contains("verdictOfTree"),
                "树形态读数点缺失 ⇒ 认知侧的 JsonNode 消费形态又要自己抄一份判定顺序");
    }

    // ── 验收 5：判据自身可信（反向 fixture + 剥离器可靠性） ──────────────────

    @Test
    @DisplayName("反向 fixture：合成裸读键能变红；注释提及不误报；URL 字面量不被误剥")
    void matcherTurnsRedAndStripperIsSound() {
        String allow = AbacEvaluateResponseContract.KEY_ALLOW;
        // ① 能变红：合成一份"第二属主"读法
        String synthetic = "Map<String, Object> d = resp.get(\"data\");\n"
                + "boolean ok = Boolean.TRUE.equals(d.get(\"" + allow + "\"));";
        assertTrue(bareReads(stripComments(synthetic), "some/BrandNewReader.java")
                        .contains(".get(\"" + allow + "\""),
                "判据对裸读形态失灵 ⇒ 验收 2 的\"零违例\"不可信");

        // ② 不误报：注释里提键名属文档行为
        String docOnly = "/** 现契约 data 段发 \"" + allow + "\" 键 */\n"
                + "// " + allow + " 只在注释出现\n"
                + "boolean ok = AbacEvaluateResponseContract.isExplicitAllow(d);";
        assertFalse(stripComments(docOnly).contains(".get(\"" + allow + "\""),
                "判据把注释提及算成违例 ⇒ 清单会被文档噪音淹没");

        // ③ 剥离器不可把字符串内的 // 当行注释（D-B4-40 的反向钉）
        String urlOnly = "return restTemplate.postForObject(\"http://localhost:8080/api/v1/"
                + "policy-engine/evaluate\", params, Object.class);";
        assertTrue(stripComments(urlOnly).contains("policy-engine/evaluate"),
                "引号感知剥离失灵 ⇒ 消费集枚举会漏掉整支消费方（D-B4-40 的成因）");
        // 朴素正则口径确实会吃掉这一行：把它当"反面证据"钉住，防止有人改回正则
        assertTrue(SourceCommentStripper.naiveStrip(urlOnly).indexOf("policy-engine/evaluate") < 0,
                "反面证据失效：正则不再吃掉 URL ⇒ D-B4-40 的登记依据需要重写");

        // ④ 豁免粒度不可外溢：换一个文件用同一读法必须仍被识别
        String aliasNeedle = ".get(\"" + AbacEvaluateResponseContract.KEY_RESULT_LEGACY + "\"";
        Set<String> inOtherFile = bareReads("m." + aliasNeedle + ");", "some/Other.java");
        assertTrue(inOtherFile.contains(aliasNeedle),
                "读形态豁免泄漏到其它文件 ⇒ 审计事件那一处成了全域通行证");
    }
}
