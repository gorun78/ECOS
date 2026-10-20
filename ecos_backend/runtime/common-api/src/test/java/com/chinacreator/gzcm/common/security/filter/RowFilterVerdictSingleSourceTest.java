package com.chinacreator.gzcm.common.security.filter;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code evaluate-filter}（行过滤）<b>键面与读数单源的静态门禁</b>
 * （#142/B4-3e-7，收口差距 <b>D-B4-42</b>，并首次把同批实证的<b>D-B4-45</b>
 * 「谓词宿主重复」钉成棘轮）。与同族
 * {@code AbacEvaluateResponseSingleSourceTest}（D-B4-37/39/41）、
 * {@code GuardrailObligationVocabularySingleSourceTest}（D-B4-31）、
 * {@code RlsExecutionContractGateTest}（D-B4-17）、
 * {@code ClsColumnSurfaceContractGateTest}（D-B4-25）同构。
 *
 * <p><b>钉六件事</b>：
 * <ol>
 *   <li><b>族面登记</b>：剥注释后仍含 {@code evaluateFilter}／{@code evaluate-filter}
 *       的主源码文件必须逐个登记（实测 6 支＝2 个写面宿主 + 1 个端点 + 1 条传输腿 +
 *       2 条裁决消费腿）。新增一支不登记即红。</li>
 *   <li><b>读侧键面零内联</b>：两支消费腿（{@code ToolObligationApplier}／
 *       {@code ToolExecutorService}）内，8 个族键的读形态访问器零命中。</li>
 *   <li><b>布尔折叠归零</b>：D-B4-42 的正身是"对外只回布尔"的 {@code rowAllowed} 腿，
 *       本批两支都改判三态 ⇒ 判据基线为<b>零</b>（不是棘轮而是归零），
 *       任何一支退回布尔结论或新增第三支都立刻红。</li>
 *   <li><b>写面走工厂</b>：两个宿主都不得再内联 {@code put("allowed"} 或 rhai 引擎串，
 *       必须经 {@link RowFilterVerdictContract#evaluated}／
 *       {@link RowFilterVerdictContract#evaluationFailed}。
 *       {@code success}/{@code error}/{@code engine} 的写形态<b>不在本禁列</b>：
 *       实测同文件的 {@code maskValue}／{@code decrypt} 两段应答也写这三个键，
 *       那是另两个 payload 契约 —— 文件级禁列会把它们误算成违例，
 *       外形正确性交给 security-engine 侧的行为不变式
 *       （{@code SecuritySandboxRowFilterVerdictTest}）钉。</li>
 *   <li><b>委托真在链上</b>：逐支断言引用了契约的具体读数点／键常量，
 *       防止"常量类加了但没人用"的空转。</li>
 *   <li><b>D-B4-45 谓词宿主棘轮</b>：内联行过滤谓词链（判据取 {@code "blocked_dept"}
 *       这个只有谓词链才写的串）的宿主清单当前实测 2 项，<b>只准缩短</b>；
 *       新增第三个复刻宿主即红，裁撤一个即删一项（关账条件见台账登记）。</li>
 * </ol>
 *
 * <p><b>判据自身可信</b>：反向 fixture 证明对合成裸读键／合成布尔腿能变红、
 * 对注释提及不误报，并证明键面字面量确实还在单源里（否则"零违例"只是词面消失的假象）。</p>
 */
class RowFilterVerdictSingleSourceTest {

    private static final String CONTRACT =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/"
                    + "security/filter/RowFilterVerdictContract.java";

    // ── 族面登记清单（仓根相对路径） ──

    private static final String GW_CONTROLLER =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/SecurityController.java";
    private static final String SEC_PRODUCER =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/security/service/SecuritySandboxService.java";
    private static final String SEC_CONTROLLER =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/security/controller/SecurityController.java";
    private static final String AI_CLIENT =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/security/AiSecurityEngineClient.java";
    private static final String APPLIER =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/security/ToolObligationApplier.java";
    private static final String EXECUTOR =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/ToolExecutorService.java";

    /** 族面全量登记＝实测剥注释后仍含族记号的主源码文件（单源自身除外）。 */
    private static final Set<String> REGISTERED_FAMILY = Set.of(
            GW_CONTROLLER, SEC_PRODUCER, SEC_CONTROLLER, AI_CLIENT, APPLIER, EXECUTOR);

    /** 真读应答裁决的两支消费腿（＝ D-B4-42 的发生位置）。 */
    private static final Set<String> READERS = Set.of(APPLIER, EXECUTOR);

    /** 写应答段的两支宿主（＝ D-B4-45 的谓词宿主）。 */
    private static final Set<String> PREDICATE_HOSTS = Set.of(GW_CONTROLLER, SEC_PRODUCER);

    /** 禁列的族键（取单源常量，禁在门禁里另抄字面量）。 */
    private static final List<String> BANNED_KEYS = List.of(
            RowFilterVerdictContract.KEY_SUCCESS,
            RowFilterVerdictContract.KEY_ALLOWED,
            RowFilterVerdictContract.KEY_ERROR,
            RowFilterVerdictContract.KEY_ENGINE,
            RowFilterVerdictContract.KEY_EXPRESSION,
            RowFilterVerdictContract.REQ_KEY_ROW_DATA,
            RowFilterVerdictContract.REQ_KEY_USER_ROLE);

    /** 读形态访问器（与同族 ABAC 门禁同一套；{@code .optString} 等留给 JSON 形态读侧）。 */
    private static final List<String> READ_ACCESSORS = List.of(
            ".get(\"", ".path(\"", ".getOrDefault(\"", ".containsKey(\"", ".has(\"",
            ".optString(\"", ".optBoolean(\"", ".getAsBoolean(\"");

    /**
     * 布尔折叠判据：命中即"对外只回布尔"的行过滤裁决腿（D-B4-42 正身）。
     *
     * <p>三种形态：①本类自带 {@code boolean rowAllowed(…)} 之类的私有布尔 helper；
     * ②裸读 {@code allowed} 键；③把契约三态再压成布尔的 {@code == Verdict.ALLOW}
     * 之后<b>不带</b> UNEVALUATED 分支。③难以纯文本判据覆盖，故本门禁改用
     * "消费腿必须同时出现 ALLOW／DENY／UNEVALUATED 三个分支记号"的正向断言
     * （见 {@link #everyReaderDelegatesAndSplitsThreeStates}），此处只钉 ①②。</p>
     */
    private static String booleanCollapseShape(String code) {
        if (code.contains("rowAllowed(")) {
            return "布尔放行 helper（rowAllowed）仍在链上";
        }
        if (code.contains(".get(\"" + RowFilterVerdictContract.KEY_ALLOWED + "\"")) {
            return "裸读 allowed 键 ⇒ success 形态被绕过（D-B4-42 的发生形态）";
        }
        return null;
    }

    private record Scanned(String relPath, String code) {
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

    private static String stripComments(String src) {
        return SourceCommentStripper.strip(src);
    }

    private static String codeOf(String relative) {
        return stripComments(readRepoFile(relative));
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
                out.add(new Scanned(rel, stripComments(new String(
                        Files.readAllBytes(f), StandardCharsets.UTF_8))));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描主源码失败: " + e.getMessage(), e);
        }
        return out;
    }

    /** 族面判据：剥注释后仍写族记号的主源码文件（单源自身除外——它是属主不是消费方）。 */
    private static Set<String> familySurface(List<Scanned> scanned) {
        Set<String> hits = new TreeSet<>();
        for (Scanned s : scanned) {
            if (CONTRACT.equals(s.relPath())) continue;
            if (s.code().contains("evaluateFilter") || s.code().contains("evaluate-filter")) {
                hits.add(s.relPath());
            }
        }
        return hits;
    }

    private static Set<String> bareReads(String code) {
        Set<String> hits = new TreeSet<>();
        for (String key : BANNED_KEYS) {
            for (String acc : READ_ACCESSORS) {
                String needle = acc + key + "\"";
                if (code.contains(needle)) hits.add(needle);
            }
        }
        return hits;
    }

    // ── 验收 1：族面登记 ────────────────────────────────────────────────────

    @Test
    @Timeout(value = 180)
    @DisplayName("族面登记：evaluate-filter 的写侧/读侧文件必须逐个登记（新增一支不登记即红）")
    void familySurfaceIsRegistered() {
        List<Scanned> scanned = backendMainSources();
        assertTrue(scanned.size() > 500,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");
        Set<String> found = familySurface(scanned);
        assertEquals(REGISTERED_FAMILY, found,
                "evaluate-filter 族面与登记清单不符。新增一支＝又一个没人知道的读侧/写侧"
                        + "（D-B4-42 的成因形态）。实测=" + found);
        assertEquals(6, found.size(), "族面基线为实测 6 支，改动清单必须与代码同步订正");
    }

    // ── 验收 2：消费腿键面零内联 ────────────────────────────────────────────

    @Test
    @Timeout(value = 180)
    @DisplayName("两支消费腿内族键零裸读：读形态访问器一律不得出现")
    void readersCarryNoInlineKeys() {
        Map<String, Set<String>> violations = new TreeMap<>();
        for (String rel : READERS) {
            Set<String> hits = bareReads(codeOf(rel));
            if (!hits.isEmpty()) violations.put(rel, hits);
        }
        assertTrue(violations.isEmpty(),
                "行过滤应答键又回到消费方内联读法（D-B4-42 的根因形态；改键名即静默恒判）:\n" + violations);
    }

    // ── 验收 3：布尔折叠归零（不是棘轮，是基线零） ──────────────────────────

    @Test
    @Timeout(value = 180)
    @DisplayName("D-B4-42 归零：族面内不得存在布尔放行腿（rowAllowed helper / 裸读 allowed）")
    void booleanCollapseIsGone() {
        Map<String, String> found = new TreeMap<>();
        for (Scanned s : backendMainSources()) {
            if (!familySurface(List.of(s)).isEmpty()) {
                String shape = booleanCollapseShape(s.code());
                if (shape != null) found.put(s.relPath(), shape);
            }
        }
        assertTrue(found.isEmpty(),
                "行过滤裁决又被压成布尔结论 ⇒ UNEVALUATED 与 DENY 重新同值（D-B4-42 复发）:\n" + found);
    }

    // ── 验收 4：委托真在链上 + 三态分支齐备 ─────────────────────────────────

    @Test
    @DisplayName("消费腿逐个钉：必须引用契约读数点，且三态分支齐备")
    void everyReaderDelegatesAndSplitsThreeStates() {
        String applier = codeOf(APPLIER);
        assertTrue(applier.contains("RowFilterVerdictContract.verdictOf"),
                APPLIER + " 未取三态读数（委托被摘掉＝回到各写各的键面）");
        assertTrue(applier.contains("case ALLOW") && applier.contains("case DENY")
                        && applier.contains("case UNEVALUATED"),
                APPLIER + " 的三态分支不完整 ⇒ 某一态又会被静默当成另一态处理");

        String executor = codeOf(EXECUTOR);
        assertTrue(executor.contains("RowFilterVerdictContract.verdictOf"),
                EXECUTOR + " 未取三态读数（反射腿的布尔折叠形态已回归）");
        assertTrue(executor.contains("Verdict.UNEVALUATED") && executor.contains("Verdict.DENY"),
                EXECUTOR + " 未对 UNEVALUATED／DENY 分态处置 ⇒ 引擎故障又会报成 403 sandbox_block");

        String client = codeOf(AI_CLIENT);
        assertTrue(client.contains("RowFilterVerdictContract.KEY_EXPRESSION")
                        && client.contains("RowFilterVerdictContract.REQ_KEY_ROW_DATA")
                        && client.contains("RowFilterVerdictContract.REQ_KEY_USER_ROLE"),
                AI_CLIENT + " 的请求键面未取单源（传输腿与两个宿主各写一份即键面分裂）");

        String secController = codeOf(SEC_CONTROLLER);
        assertTrue(secController.contains("RowFilterVerdictContract.REQ_KEY_ROW_DATA")
                        && secController.contains("RowFilterVerdictContract.VALUE_DEFAULT_ROLE"),
                SEC_CONTROLLER + " 的请求读取未取单源（缺省角色字面量散落＝一处改成 admin 即静默放宽）");
    }

    // ── 验收 5：写面走工厂 ──────────────────────────────────────────────────

    @Test
    @DisplayName("两个写侧宿主必须经契约工厂组应答，不得内联 allowed 键与引擎串")
    void producersWriteViaFactory() {
        for (String rel : PREDICATE_HOSTS) {
            String code = codeOf(rel);
            assertTrue(code.contains("RowFilterVerdictContract.evaluated(")
                            && code.contains("RowFilterVerdictContract.evaluationFailed("),
                    rel + " 的应答段未走契约工厂（工厂一改，两个消费腿同时静默失配）");
            assertFalse(code.contains(".put(\"" + RowFilterVerdictContract.KEY_ALLOWED + "\""),
                    rel + " 仍在应答里内联写 allowed 键: " + rel);
            assertFalse(code.contains("\"Rust rhai"),
                    rel + " 仍内联引擎标记串（该串属单源取值面）");
        }
    }

    // ── 验收 6：D-B4-45 谓词宿主棘轮（只准缩短） ────────────────────────────

    @Test
    @Timeout(value = 180)
    @DisplayName("谓词宿主棘轮：内联行过滤谓词链的宿主必须逐个登记在册（D-B4-45，新增即红）")
    void predicateHostsAreRatcheted() {
        Set<String> found = new TreeSet<>();
        for (Scanned s : backendMainSources()) {
            if (s.code().contains("\"blocked_dept\"")) {
                found.add(s.relPath());
            }
        }
        assertEquals(PREDICATE_HOSTS, found,
                "行过滤谓词链的宿主清单与在册不符。本批只收键面/外形，承流归属待 PM 裁决"
                        + "（铁律 §2.4-1「安全裁决一律走 security-engine REST」）⇒ 清单只准缩短，"
                        + "新增第三个复刻宿主即红。实测=" + found);
    }

    // ── 验收 7：单源自身在位（防空转） ──────────────────────────────────────

    @Test
    @DisplayName("单源确实持有这套键面：常量漂移即红，\"零违例\"才不是词面消失的假象")
    void contractItselfHoldsTheKeySurface() {
        String code = readRepoFile(CONTRACT);
        for (String key : BANNED_KEYS) {
            assertTrue(code.contains("\"" + key + "\""),
                    "单源不再持有字面量 \"" + key + "\" ⇒ 本门禁的零违例是空转");
        }
        assertTrue(code.contains("\"" + RowFilterVerdictContract.VALUE_ENGINE_RHAI + "\"")
                        && code.contains("\"" + RowFilterVerdictContract.VALUE_ENGINE_FALLBACK_FAILED + "\""),
                "引擎标记取值缺失 ⇒ 写面工厂失效");
        assertTrue(code.contains("\"" + RowFilterVerdictContract.VALUE_DEFAULT_ROLE + "\""),
                "缺省角色取值缺失");
        // 词汇单源：本契约不得另立第二套裁决枚举
        assertFalse(code.contains("enum Verdict"),
                "单源重定义了裁决枚举 ⇒ 与 AbacEvaluateResponseContract 的三态词汇分裂");
        assertTrue(code.contains("AbacEvaluateResponseContract.Verdict")
                        || code.contains("import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict"),
                "单源未引用 ABAC 的三态词汇（词表分裂即本批分工登记失真）");
        // 词表同样引用，不重定义
        for (String vocab : List.of("TOKEN_TRUE", "TOKEN_ALLOW", "TOKEN_PERMIT",
                "TOKEN_FALSE", "TOKEN_DENY", "TOKEN_DENIED")) {
            assertTrue(code.contains("AbacEvaluateResponseContract." + vocab),
                    "读数未复用 ABAC 词表常量 " + vocab + " ⇒ 又会出现两份\"看起来等价\"的词表");
            assertFalse(code.contains("String " + vocab + " ="),
                    "单源重定义了词表常量 " + vocab);
        }
        assertFalse(code.contains("boolean isExplicitAllow("),
                "单源新增布尔 helper ⇒ D-B4-41 的折叠形态会在行过滤面复发");
    }

    // ── 验收 8：批次与差距登记在位 ──────────────────────────────────────────

    @Test
    @DisplayName("差距编号与本批标记不得被后续顺手抹掉")
    void registrationMarkersExist() {
        String contract = readRepoFile(CONTRACT);
        assertTrue(contract.contains("D-B4-42") && contract.contains("#142/B4-3e-7"),
                "单源类注释丢失差距编号/批次标记 ⇒ 台账与本类的对应关系断链");
        assertTrue(contract.contains("D-B4-41") && contract.contains("D-B4-39"),
                "单源丢失折叠面与别名面的谱系登记 ⇒ \"这份契约是重复建设\"的误读会回来");
        for (String rel : READERS) {
            assertTrue(readRepoFile(rel).contains("D-B4-42"),
                    rel + " 未留 D-B4-42 收口标记（改回布尔腿时无从追溯为什么必须三态）");
        }
        assertTrue(readRepoFile(GW_CONTROLLER).contains("D-B4-45"),
                "gateway 宿主未留 D-B4-45 登记标记 ⇒ 谓词复刻会被后人像\"本批漏改\"一样顺手删掉或照抄");
    }

    // ── 验收 9：判据自身可信（反向 fixture） ────────────────────────────────

    @Test
    @DisplayName("反向 fixture：合成裸读键与合成布尔腿都能变红；注释提及不误报")
    void matcherTurnsRedAndStripperIsSound() {
        String allowed = RowFilterVerdictContract.KEY_ALLOWED;
        // ① 能变红：合成一份"第二个读侧"的裸键读法
        String synthetic = "Map<String, Object> v = resp.get(\"data\");\n"
                + "Object flag = v.get(\"" + allowed + "\");";
        assertTrue(bareReads(stripComments(synthetic)).contains(".get(\"" + allowed + "\""),
                "判据对裸读形态失灵 ⇒ 验收 2 的\"零违例\"不可信");
        // ② 布尔腿判据能变红
        String syntheticBool = "private boolean rowAllowed(Map<String, Object> v) { return true; }";
        assertTrue(booleanCollapseShape(stripComments(syntheticBool)) != null,
                "布尔折叠判据失灵 ⇒ 验收 3 的归零基线不可信");
        String syntheticAlias = "Object f = verdict.get(\"" + allowed + "\");";
        assertTrue(booleanCollapseShape(stripComments(syntheticAlias)) != null,
                "裸读 allowed 未被折叠判据识别 ⇒ 消费腿改回旧读法不会红");

        // ③ 不误报：注释里提键名属文档行为（契约类自身就是这样一句注释）
        String docOnly = "/** 收口前这里读 \"" + allowed + "\" 与 \"pass\" 两键 */\n"
                + "AbacEvaluateResponseContract.Verdict s = RowFilterVerdictContract.verdictOf(v);";
        assertTrue(bareReads(stripComments(docOnly)).isEmpty(),
                "判据把注释提及算成违例 ⇒ 清单会被文档噪音淹没");

        // ④ 委托判据不可空转：把委托摘掉必须红
        Map<String, List<String>> delegationShape = new LinkedHashMap<>();
        delegationShape.put(APPLIER, List.of("RowFilterVerdictContract.verdictOf", "case UNEVALUATED"));
        String withoutDelegation = "if (verdict == null) return false;";
        for (Map.Entry<String, List<String>> e : delegationShape.entrySet()) {
            for (String ref : e.getValue()) {
                assertFalse(withoutDelegation.contains(ref),
                        e.getKey() + " 的委托记号 \"" + ref + "\" 出现在合成无委托样本里 ⇒ 断言恒真");
            }
        }
    }
}
