package com.chinacreator.gzcm.common.security.guardrail;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 护栏裁决线词表／键面的<b>单源静态门禁</b>（#142/B4-3e-2，收口差距 <b>D-B4-31</b>；
 * 与同族的 {@code RateLimitObligationConsumerGateTest}（D-B4-21）、
 * {@code DataScopeSingleSourceGateTest}（D-B4-20）、{@code RlsExecutionContractGateTest}（D-B4-17）、
 * {@code ClsColumnSurfaceContractGateTest}（D-B4-25）同构）。
 *
 * <p><b>钉三件事</b>：
 * <ol>
 *   <li><b>词面单源</b>：护栏专属词（{@code obligations}/{@code obligation}/
 *       {@code deniedReason}/{@code maskHints}/{@code rowFilter}/{@code row_filter}/
 *       {@code policyState}/{@code mask}/{@code rateLimit}/{@code rateLimitKey}）的
 *       <b>带引号字面量</b>在 {@code ecos_backend/**&#47;src/main/java/**} 内只允许出现在
 *       {@link GuardrailDecisionContract}；同名但属他域的词
 *       （{@code watermark}＝数据分层校验字段名、{@code policyRef}＝蓝图审批引用号）
 *       按<b>登记过的属主白名单</b>放行，白名单之外新增持有者即红；
 *       不可用码 {@code ECOS-SEC-501} 的唯一持有者
 *       是 {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}。</li>
 *   <li><b>委托真在链上</b>：逐个消费者文件断言它<b>引用了单源常量/读数点</b>，
 *       并断言咽喉侧不再出现 {@code get("…")} 形态的裸键读法——只加常量类不加这条，
 *       下一版重构就能把委托静默摘掉而全绿。</li>
 *   <li><b>策略侧仍在签发</b>：复核 {@code ecos-docker/opa-policies/data_access.rego}
 *       逐字签发这批词面，且<b>不签发</b>兼容别名（{@code maskHints}/{@code rowFilter}），
 *       防止"策略与门禁同时把键删掉"的自洽空转，也防止把兼容别名误读成第五类义务。</li>
 * </ol>
 *
 * <p><b>为什么 {@code allow}/{@code DENIED}/{@code column}/{@code field}/{@code strategy}/
 * {@code rule}/{@code value} 不进全仓禁列</b>（刻意取舍，不是遗漏）：这些词在主源码里大量是
 * <b>与护栏裁决无关</b>的同名用途（实测 {@code "allow"} 7 个非单源文件持有——ABAC evaluate 响应键、
 * 列面 {@code mode} 取值、台账词输出三类；{@code "DENIED"} 8 个非单源文件持有——各域自己的裁决态串；
 * {@code "column"} 5 个非单源文件持有——MyBatis 结果集别名与 DQ 指标列名）。
 * 全仓禁列会得到一份要逐条做属主判定的违例清单，反而稀释信号（同族
 * {@code RateLimitObligationConsumerGateTest} 类注释记过同一取舍）。
 * 本门禁改为<b>按位置钉</b>：护栏侧写面/读面（{@code DecisionBundle}、
 * {@code SecurityDecisionService#safeMasks}）必须引用
 * {@link GuardrailDecisionContract#KEY_ALLOW}／{@code KEY_MASK_COLUMN}／{@code KEY_MASK_STRATEGY}；
 * ABAC {@code evaluate} 响应键面缺单源一事登记为新差距 <b>D-B4-37</b>
 * （生产侧键面定位是它的第一步）。</p>
 *
 * <p><b>假绿防护</b>：① 扫描根解析文件数阈值；② 先剥注释再比对（注释里提键名是文档行为）；
 * ③ 每个禁列词都要在单源文件里<b>实命中</b>一次，否则"零违例"只是词面消失的假象；
 * ④ 断言用的字面量取自常量本身（常量漂移即红），不另抄字符串；
 * ⑤ 反向 fixture 证明判据能变红（见 {@code matcherTurnsRedOnSyntheticBareLiteral}）。</p>
 */
class GuardrailObligationVocabularySingleSourceTest {

    /** 词表唯一属主（按仓根相对路径比对，跨机不依赖绝对路径）。 */
    private static final String CONTRACT =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/"
                    + "security/guardrail/GuardrailDecisionContract.java";
    private static final String RLS_CONTRACT =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/"
                    + "security/rls/RlsExecutionContract.java";
    private static final String REGO = "ecos-docker/opa-policies/data_access.rego";

    /** 白名单条目＝登记过属主的同名他域持有者（仓根相对路径）。 */
    private static final String DATA_ENGINE_LAYER_SVC =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/service/DataLayerInstanceService.java";
    private static final String AI_TOOL_OBLIGATIONS =
            "ecos_backend/engine/ai-engine/ai-engine-api/src/main/java/com/chinacreator/gzcm/engine/"
                    + "ai/guardrail/ToolObligations.java";
    private static final String WS_BLUEPRINT =
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/"
                    + "blueprint/ScenarioBlueprintService.java";

    /** 禁列词 → 允许的第二持有者（空集＝除单源外零持有）。白名单只减不增：新增须走裁决批。 */
    private static final List<String> BANNED = List.of(
            GuardrailDecisionContract.KEY_OBLIGATIONS,
            GuardrailDecisionContract.KEY_OBLIGATION,
            GuardrailDecisionContract.KEY_DENIED_REASON,
            GuardrailDecisionContract.KEY_MASK_HINTS,
            GuardrailDecisionContract.KEY_ROW_FILTER,
            GuardrailDecisionContract.KEY_ROW_FILTER_ALT,
            GuardrailDecisionContract.KEY_POLICY_STATE,
            GuardrailDecisionContract.OBLIGATION_MASK,
            GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
            GuardrailDecisionContract.KEY_RATE_LIMIT_KEY,
            GuardrailDecisionContract.OBLIGATION_WATERMARK,
            GuardrailDecisionContract.KEY_POLICY_REF);

    /** 主源码扫描结果缓存（一次遍历，多条断言共用；test 级超时风险由阈值断言覆盖）。 */
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
     * 只判代码行为：块注释与行注释一律抹平（换行保留 ⇒ 违例定位仍可读）。
     *
     * <p>口径单源在 {@link SourceCommentStripper}：朴素正则会把字符串里的 {@code //}
     * 当行注释起点，连带整行代码一起吃掉 ⇒ 消费方枚举假阴性（D-B4-40）。
     * 由 {@code NaiveCommentStripperBanTest} 全域禁止改回正则。</p>
     */
    private static String stripComments(String source) {
        return SourceCommentStripper.strip(source);
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

    /** 主源码里持有该<b>带引号</b>字面量的文件清单（已剥注释）。 */
    private static Set<String> holdersOf(List<Scanned> scanned, String literal) {
        String quoted = "\"" + literal + "\"";
        Set<String> holders = new TreeSet<>();
        for (Scanned s : scanned) {
            if (s.code().contains(quoted)) holders.add(s.relPath());
        }
        return holders;
    }

    // ── 验收 1：词面单源 ────────────────────────────────────────────────────

    @Test
    @DisplayName("护栏线词表键面单源：禁列词在 main 源码内除单源＋登记白名单外零持有")
    void guardrailVocabularyHasSingleOwner() {
        List<Scanned> scanned = backendMainSources();
        // 假绿防护①：扫描根必须真吃到文件
        assertTrue(scanned.size() > 500,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");

        Set<String> watermarkOwners = Set.of(CONTRACT, DATA_ENGINE_LAYER_SVC);
        Set<String> policyRefOwners = Set.of(CONTRACT, WS_BLUEPRINT);

        List<String> violations = new ArrayList<>();
        for (String literal : BANNED) {
            Set<String> allowed = switch (literal) {
                case "watermark" -> watermarkOwners;
                case "policyRef" -> policyRefOwners;
                default -> Set.of(CONTRACT);
            };
            Set<String> holders = holdersOf(scanned, literal);
            // 假绿防护③：单源必须实命中
            assertTrue(holders.contains(CONTRACT),
                    "单源不再持有字面量 \"" + literal + "\" ⇒ 该词的\"零违例\"是空转，不是收口: " + holders);
            Set<String> extra = new TreeSet<>(holders);
            extra.removeAll(allowed);
            if (!extra.isEmpty()) {
                violations.add("\"" + literal + "\" 的未登记持有者: " + extra);
            }
        }
        assertTrue(violations.isEmpty(),
                "护栏裁决线词面出现第二套写法（各写各的正是 D-B4-31 的根因形态；"
                        + "新增属主须走裁决批并同步本白名单）:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("ECOS-SEC-501 不可用码单源：main 源码内唯一持有者是 RlsExecutionContract")
    void secUnavailableCodeHasSingleOwner() {
        List<Scanned> scanned = backendMainSources();
        Set<String> holders = holdersOf(scanned, RlsExecutionContract.SEC_UNAVAILABLE_CODE);
        assertEquals(Set.of(RLS_CONTRACT), holders,
                "不可用码字面量回到了副本里（告警按一个码寻址「引擎没答」，抄两份＝两套检索口径）。"
                        + "本批消除的四处副本＝QueryController / CryptoKeyAdminController / "
                        + "SecurityEngineUnavailableException / WorkspaceSecurityEngineClient，"
                        + "消费者一律引用 RlsExecutionContract.SEC_UNAVAILABLE_CODE 或 isUnavailableEnvelope");
    }

    // ── 验收 2：委托真在链上（摘掉委托即红） ────────────────────────────────

    @Test
    @DisplayName("security 侧写面委托单源常量：OpaPolicyService / SecurityDecisionService 无裸键")
    void securityProducersDelegateToContract() {
        String opa = codeOf("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/security/service/OpaPolicyService.java");
        String svc = codeOf("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/security/decision/SecurityDecisionService.java");

        for (String f : List.of(opa, svc)) {
            assertTrue(f.contains("GuardrailDecisionContract.KEY_OBLIGATIONS"), "写面未引用义务集单源键");
            assertTrue(f.contains("GuardrailDecisionContract.OBLIGATION_DENIED"), "拒绝义务词未取单源常量");
            assertTrue(f.contains("GuardrailDecisionContract.KEY_DENIED_REASON"), "拒绝理由键未取单源常量");
            assertTrue(f.contains("GuardrailDecisionContract.KEY_ALLOW"), "放行键未取单源常量");
            assertTrue(f.contains("GuardrailDecisionContract.KEY_POLICY_STATE"), "裁决态键未取单源常量");
            assertNoBareKeyRead(f, GuardrailDecisionContract.KEY_OBLIGATIONS);
            assertNoBareKeyRead(f, GuardrailDecisionContract.KEY_DENIED_REASON);
            assertNoBareKeyRead(f, GuardrailDecisionContract.KEY_ALLOW);
        }
        // safeMasks 写的是裁决线上的 mask 义务形态：键面必须取单源（DAO 结果集别名不在禁列）
        assertTrue(svc.contains("GuardrailDecisionContract.KEY_MASK_COLUMN")
                        && svc.contains("GuardrailDecisionContract.KEY_MASK_STRATEGY"),
                "mask 义务形态的写出键未取单源（D-B4-31 在 security 写面的最后一处裸键）");
        assertFalse(svc.contains(".put(\"column\""), "mask 写出面回到裸 \"column\" 键");
        assertFalse(svc.contains(".put(\"strategy\""), "mask 写出面回到裸 \"strategy\" 键");
    }

    @Test
    @DisplayName("bundle／ai 侧读数收敛到单源读数点：禁再各自写一遍扫描循环")
    void scanningAlgorithmsCollapsedIntoContractReaders() {
        String bundle = codeOf("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/security/decision/DecisionBundle.java");
        assertTrue(bundle.contains("GuardrailDecisionContract.readWatermark"),
                "水印扫描未委托单源读数点（本批把两处手写循环收敛为一处）");
        assertTrue(bundle.contains("GuardrailDecisionContract.readDeniedReason"),
                "拒绝理由扫描未委托单源读数点");
        assertTrue(bundle.contains("GuardrailDecisionContract.obligationsOf"), "义务集取段未走单源读数点");
        assertTrue(bundle.contains("GuardrailDecisionContract.KEY_ALLOW"),
                "护栏放行位仍写裸 \"allow\" 键（D-B4-31 的键面漂移形态）");
        assertNoBareKeyRead(bundle, GuardrailDecisionContract.KEY_ALLOW);

        String guardrails = codeOf("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/ai/service/GuardrailsServiceImpl.java");
        assertTrue(guardrails.contains("GuardrailDecisionContract.readDeniedReason"),
                "AI 侧拒绝理由扫描未委托单源读数点");
        assertTrue(guardrails.contains("GuardrailDecisionContract.obligationsOf")
                        && guardrails.contains("GuardrailDecisionContract.KEY_POLICY_REF"),
                "AI 侧义务集／策略引用未走单源");
        assertNoBareKeyRead(guardrails, GuardrailDecisionContract.KEY_DENIED_REASON);
        assertNoBareKeyRead(guardrails, GuardrailDecisionContract.KEY_POLICY_REF);
    }

    @Test
    @DisplayName("ai 义务解析委托单源键面（含兼容别名 maskHints/field/rule/row_filter）")
    void obligationParsingDelegatesToKeyConstants() {
        String ob = codeOf(AI_TOOL_OBLIGATIONS);
        for (String ref : List.of("GuardrailDecisionContract.OBLIGATION_MASK",
                "GuardrailDecisionContract.KEY_MASK_HINTS",
                "GuardrailDecisionContract.KEY_MASK_COLUMN",
                "GuardrailDecisionContract.KEY_MASK_COLUMN_ALT",
                "GuardrailDecisionContract.KEY_MASK_STRATEGY",
                "GuardrailDecisionContract.KEY_MASK_RULE_ALT",
                "GuardrailDecisionContract.KEY_ROW_FILTER",
                "GuardrailDecisionContract.KEY_ROW_FILTER_ALT")) {
            assertTrue(ob.contains(ref), "解析侧未引用单源键 " + ref + "（改回裸字面量会重新产生键面分裂）");
        }
    }

    @Test
    @DisplayName("消费者面逐个钉：cognitive/workspace 侧读面引用单源，不内联词面")
    void downstreamConsumersDelegateToContract() {
        String decider = codeOf("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/cognitive2/service/backtest/SecurityPolicyReviewRoleDecider.java");
        for (String ref : List.of("GuardrailDecisionContract.KEY_POLICY_REF",
                "GuardrailDecisionContract.KEY_ALLOW",
                "GuardrailDecisionContract.KEY_OBLIGATIONS",
                "GuardrailDecisionContract.KEY_DENIED_REASON")) {
            assertTrue(decider.contains(ref), "认知回测裁决读面未引用单源键 " + ref);
        }

        String readGuard = codeOf("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                + "gzcm/workspace/security/ScenarioReadGuard.java");
        assertTrue(readGuard.contains("GuardrailDecisionContract.KEY_DENIED_REASON"),
                "场景读咽喉的拒绝理由键未取单源");

        String export = codeOf("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                + "gzcm/workspace/forecast/ForecastExportService.java");
        String pack = codeOf("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                + "gzcm/workspace/forecast/ForecastAuditPackService.java");
        assertTrue(export.contains("GuardrailDecisionContract.KEY_WATERMARK"), "导出水印键未取单源");
        assertTrue(pack.contains("GuardrailDecisionContract.KEY_WATERMARK"), "审计包水印清单键未取单源");

        String client = codeOf("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                + "gzcm/workspace/security/WorkspaceSecurityEngineClient.java");
        assertTrue(client.contains("RlsExecutionContract.isUnavailableEnvelope"),
                "不可用信封判据又本地写了一份（该判据 #142/B4-3d-10 起由 RLS 单源持有）");

        String crypto = codeOf("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/security/controller/CryptoKeyAdminController.java");
        assertTrue(crypto.contains("RlsExecutionContract.SEC_UNAVAILABLE_CODE"),
                "密钥管理端点的不可用码回到裸字面量");
        String q = codeOf("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                + "com/chinacreator/gzcm/engine/data/controller/QueryController.java");
        assertTrue(q.contains("RlsExecutionContract.SEC_UNAVAILABLE_CODE"),
                "data-engine 的不可用码回到裸字面量");
    }

    // ── 验收 3：策略侧仍在签发这批词面 ──────────────────────────────────────

    @Test
    @DisplayName("data_access.rego 逐字签发同一套词面；兼容别名不由策略发出")
    void policySideStillIssuesTheSameVocabulary() {
        String rego = readRepoFile(REGO);
        // 义务集段名由 security 侧 Java 包进 data 信封，rego 侧以规则名持有 ⇒ 钉规则名而不是带引号键
        assertTrue(rego.contains(GuardrailDecisionContract.KEY_OBLIGATIONS + " :="),
                "策略侧 obligations 规则改名 ⇒ 单源的义务集键面与策略结论已经分叉");
        for (String literal : List.of(
                GuardrailDecisionContract.KEY_OBLIGATION,
                GuardrailDecisionContract.OBLIGATION_DENIED,
                GuardrailDecisionContract.KEY_DENIED_REASON,
                GuardrailDecisionContract.OBLIGATION_MASK,
                GuardrailDecisionContract.KEY_MASK_COLUMN,
                GuardrailDecisionContract.KEY_MASK_STRATEGY,
                GuardrailDecisionContract.OBLIGATION_WATERMARK,
                GuardrailDecisionContract.KEY_WATERMARK,
                GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
                GuardrailDecisionContract.KEY_RATE_LIMIT_KEY)) {
            assertTrue(rego.contains("\"" + literal + "\""),
                    "策略侧不再签发 \"" + literal + "\" ⇒ 单源与本门禁的验收依据同时失效");
        }
        // 兼容别名只是消费侧读法：策略不签发它们，否则本批 javadoc 的「不是第五类义务」口径失效
        assertFalse(rego.contains("\"" + GuardrailDecisionContract.KEY_MASK_HINTS + "\""),
                "策略侧新签发了 maskHints ⇒ 单源 javadoc 里「别名只由消费者兼容读法持有」的登记失真");
        assertFalse(rego.contains("\"" + GuardrailDecisionContract.KEY_ROW_FILTER + "\""),
                "策略侧新签发了 rowFilter ⇒ 义务类型数从四类变五类，须走裁决批");
    }

    // ── 验收 4：判据能变红（反向 fixture） ──────────────────────────────────

    @Test
    @DisplayName("反向 fixture：判据对合成违例能变红，对注释提及不误报")
    void matcherTurnsRedOnSyntheticBareLiteral() {
        String quotedObligations = "\"" + GuardrailDecisionContract.KEY_OBLIGATIONS + "\"";
        // ① 能变红：合成一份"第二属主"代码，谓词必须命中
        String synthetic = "Map<String, Object> m = new LinkedHashMap<>();\n"
                + "m.get(" + quotedObligations + ");";
        assertTrue(stripComments(synthetic).contains(quotedObligations),
                "判据对裸字面量失灵 ⇒ 本门禁的「零违例」不可信");

        // ② 不误报：只在注释里提键名属文档行为，剥注释后必须不命中
        String docOnly = "/** 说明：这里的 " + GuardrailDecisionContract.KEY_OBLIGATIONS
                + " 键由单源常量持有。 */\n// obligations 只在注释里出现\n"
                + "m.get(GuardrailDecisionContract.KEY_OBLIGATIONS);";
        assertFalse(stripComments(docOnly).contains(quotedObligations),
                "判据把注释提及算成持有 ⇒ 白名单会被文档噪音淹没");

        // ③ 白名单不是万能：把违例文件放进禁列词的允许集之外必须被识别
        Set<String> holders = new TreeSet<>(List.of("some/other/Owner.java"));
        Set<String> diff = new TreeSet<>(holders);
        diff.removeAll(Set.of(CONTRACT));
        assertEquals(Set.of("some/other/Owner.java"), diff,
                "未登记持有者的识别算子失灵 ⇒ 验收 1 的断言只是摆设");
    }

    @Test
    @DisplayName("D-B4-31 收口凭据与后续登记在位（新登记 D-B4-37 不得被顺手抹掉）")
    void closureEvidenceAndFollowUpRegistrationExist() {
        String contract = readRepoFile(CONTRACT);
        assertTrue(contract.contains("D-B4-31"),
                "D-B4-31 的批次引用被抹掉 ⇒ 同族限流门禁的「为何当时只钉两个字面量」失去上下文");
        assertTrue(contract.contains("#142/B4-3e-2"),
                "本批收口标记缺失：后续批无从判断词面扩到哪一步");
        assertTrue(contract.contains("rowFilter") && contract.contains("第五类"),
                "「别名不是第五类义务」的口径登记缺失");
    }

    // ── 装置辅助 ────────────────────────────────────────────────────────────

    private static String codeOf(String relative) {
        return stripComments(readRepoFile(relative));
    }

    /** 钉"不再出现 {@code get("literal")} / {@code path("literal")} 形态的裸键读法"。 */
    private static void assertNoBareKeyRead(String code, String literal) {
        String quoted = "\"" + literal + "\"";
        for (String accessor : List.of(".get(" + quoted, ".path(" + quoted, ".put(" + quoted)) {
            assertFalse(code.contains(accessor),
                    "单源键 \"" + literal + "\" 又回到裸读/裸写形态: " + accessor);
        }
    }
}
