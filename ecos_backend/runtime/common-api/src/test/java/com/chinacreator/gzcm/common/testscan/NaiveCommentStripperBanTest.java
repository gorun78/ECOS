package com.chinacreator.gzcm.common.testscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 禁令：同族源码门禁不得再用朴素正则剥行注释（差距 <b>D-B4-40</b>，#142/B4-3e-5 收口件）。
 *
 * <p><b>成因</b>：朴素口径 {@code replaceAll(<以 // 开头的正则>, " ")} 不区分字符串字面量，
 * 会把 {@code "http://…" } 整行吃掉。#142/B4-3e-4 批实测该盲区令
 * {@code CheckPermissionTool} 从 ABAC 消费集枚举里消失，"消费方少 1 枚"被读成"消费方合规"。
 * 修法不是逐处改一次，而是让"改回去"必须经过本门禁：<b>零豁免面</b>（唯一在册豁免见
 * {@link #singleExemptionIsVerifiedNotJustDeclared()} 的形态核验）。</p>
 *
 * <p><b>判据不靠名字</b>：违例形态分两条独立腿 —— R1「本文件声明了注释剥离器
 * （{@code stripComments}/{@code codeOnly}/{@code normalize}/{@code strip}）却仍带以
 * {@code //} 开头的正则字面量」；R2「该字面量是某个 {@code replace*} 调用的第一参数」，
 * 后者覆盖没有剥离器方法名的散点写法（kb {@code GraphDualFormTest} 正是这种）。
 * 反例基线：{@code DatanetNoExclusionTest} 把 {@code //} 开头的正则用作<b>匹配器</b>
 * （查历史注释形态），既不声明剥离器也不作为 replace 参数 ⇒ 不计违例，
 * 由 {@link #matcherShapedSlashLiteralIsNotAViolation()} 钉住不过度收紧。</p>
 */
class NaiveCommentStripperBanTest {

    /** 两个斜杠由常量拼出：本文件自身不得成为禁令的违例面。 */
    private static final String SLASH2 = "/" + "/";

    /** 以 // 开头的正则字面量候选（可带 (?m)/(?s)/(?i) 内联标志前缀）。 */
    private static final Pattern CANDIDATE_LITERAL = Pattern.compile(
            "\"(?:\\(\\?[a-z]+\\))?" + SLASH2 + "[^\"]*\"");

    /** 字面量作为 replaceAll／replaceFirst 调用的第一参数 ⇒ R2。 */
    private static final Pattern REPLACE_CALL_FIRST_ARG = Pattern.compile(
            "\\.replace(?:All|First)?\\(\\s*\"(?:\\(\\?[a-z]+\\))?" + SLASH2);

    /** 注释剥离器方法名 ⇒ R1 的文件级触发条件。 */
    private static final Pattern STRIPPER_DECL = Pattern.compile(
            "\\b(?:stripComments|codeOnly|normalize|strip)\\s*\\(");

    /** 同名剥离器副本的注册数（2026-10-21 实扫 ecos_backend 的读数）。 */
    private static final int REGISTERED_STRIPPER_COPIES = 7;

    /** 迁移在册清单：这批从朴素口径改到剥离器的门禁文件（must-hit，防"改了个空集"）。 */
    private static final List<String> MIGRATED_GATES = List.of(
            "ecos_backend/runtime/common-api/src/test/java/com/chinacreator/gzcm/common/security/guardrail/GuardrailObligationVocabularySingleSourceTest.java",
            "ecos_backend/runtime/common-api/src/test/java/com/chinacreator/gzcm/common/security/abac/AbacEvaluateResponseSingleSourceTest.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/arch/LlmSingleExitArchTest.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/security/ObligationsApplyTest.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/security/RateLimitObligationConsumerGateTest.java",
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/test/java/com/chinacreator/gzcm/engine/cognitive2/guard/CogScope.java",
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/test/java/com/chinacreator/gzcm/engine/cognitive2/guard/CognitiveSqlDialectTest.java",
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/test/java/com/chinacreator/gzcm/engine/kb/service/GraphDualFormTest.java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ontology/security/OntologyReadAspectWriteBackTest.java",
            "ecos_backend/engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/rolechannel/DecisionChainCoverageTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/QueryHistoryPersistenceTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/arch/RuntimeConvergenceArchTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/arch/WorkspaceLayeringArchTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/security/RlsExecutionContractGateTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/security/PageChannelDecisionLegGateTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/security/WorkspaceAbacWiringTest.java",
            "ecos_backend/workspace/workspace-impl/src/test/java/com/chinacreator/gzcm/workspace/security/ClsColumnSurfaceContractGateTest.java");

    /** 唯一在册豁免：先把字符串字面量整体抹平、其后才剥注释，形态与 D-B4-40 不同。 */
    private static final String EXEMPT_FILE =
            "ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/GatewayRouteIntegrityTest.java";

    /** 用作"匹配器"而非"剥离器"的反例基线（不得被本禁令顺手动刀）。 */
    private static final String MATCHER_SHAPE_FILE =
            "ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/DatanetNoExclusionTest.java";

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        return fail("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker），本门禁不得默认通过");
    }

    private static List<Path> javaSources(String pathFragment) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("ecos_backend"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toString().replace('\\', '/'))
                    .filter(s -> s.contains(pathFragment))
                    .filter(s -> !s.contains("/target/"))
                    .filter(s -> !s.contains("/archive/"))
                    .forEach(s -> out.add(Path.of(s)));
        }
        return out;
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static String rel(Path p) {
        return repoRoot().relativize(p).toString().replace('\\', '/');
    }

    /** 剥离器自身必须先被正确剥离：本门禁的判据面来自 {@link SourceCommentStripper}。 */
    private static List<String> violationsIn(String code) {
        List<String> hits = new ArrayList<>();
        boolean hasStripperDecl = STRIPPER_DECL.matcher(code).find();
        Matcher lit = CANDIDATE_LITERAL.matcher(code);
        while (lit.find()) {
            String value = lit.group();
            int body = value.indexOf(SLASH2);
            String tail = value.substring(body + 2).replace("\\\"", "\"");
            if (!(tail.contains("[^") || tail.contains(".*"))) {
                continue; // 纯 // 常量（如路径分隔）不是注释剥离形态
            }
            int callStart = Math.max(0, lit.start() - 40);
            boolean asReplaceArg = REPLACE_CALL_FIRST_ARG
                    .matcher(code.substring(callStart, lit.end() + 1)).find();
            if (hasStripperDecl || asReplaceArg) {
                hits.add("line " + lineOf(code, lit.start()) + " " + value
                        + (hasStripperDecl ? " [R1 剥离器方法名在册]" : "")
                        + (asReplaceArg ? " [R2 replace 第一参数]" : ""));
            }
        }
        return hits;
    }

    private static int lineOf(String code, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Test
    @Timeout(180)
    @DisplayName("扫描面非空：测试/主源码文件数低于阈值即判扫描根错位，不得当成「无违例」")
    void scanSurfaceIsNotEmpty() throws IOException {
        int testFiles = javaSources("/src/test/java/").size();
        int mainFiles = javaSources("/src/main/java/").size();
        assertTrue(testFiles >= 500, "测试源码面仅 " + testFiles + " 文件 ⇒ 扫描根错位（基线 574）");
        assertTrue(mainFiles >= 2000, "主源码面仅 " + mainFiles + " 文件 ⇒ 扫描根错位（基线 2329）");
    }

    @Test
    @Timeout(300)
    @DisplayName("全域零违例：朴素行注释剥离形态只允许留在剥离器与已核验豁免内")
    void noNaiveLineCommentStripperRemains() throws IOException {
        Map<String, List<String>> offenders = new LinkedHashMap<>();
        for (String fragment : List.of("/src/test/java/", "/src/main/java/")) {
            for (Path p : javaSources(fragment)) {
                String r = rel(p);
                if (r.endsWith("/SourceCommentStripper.java") || r.equals(EXEMPT_FILE)) {
                    continue;
                }
                List<String> hits = violationsIn(SourceCommentStripper.strip(read(p)));
                if (!hits.isEmpty()) {
                    offenders.put(r, hits);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "朴素剥注释形态残留（该写法会吃掉字符串里的 //，把「有违例」读成「无违例」，"
                        + "见 D-B4-40）：\n" + offenders);
    }

    @Test
    @Timeout(120)
    @DisplayName("两条判据腿都能变红：R1 剥离器方法名、R2 replace 第一参数")
    void bothDetectorLegsTurnRed() {
        String withStripperName = "private static String stripComments(String s) {\n"
                + "    return s.replaceAll(\"//" + "[^\\\\n]*\", \" \");\n}\n";
        assertTrue(violationsIn(withStripperName).size() >= 1,
                "R1 失效：声明了剥离器却用朴素正则仍未报 ⇒ 零违例不可信");
        String inlineReplaceOnly = "Stream.of(lines).map(l -> l.replaceAll(\"//" + ".*$\", \"\"));\n";
        assertTrue(violationsIn(inlineReplaceOnly).stream().anyMatch(h -> h.contains("[R2")),
                "R2 失效：无剥离器方法名的散点 replace 写法不被识别 ⇒ kb 那种形态会漏网");
        assertFalse(violationsIn(withStripperName).isEmpty(), "自检：合成违例必须非空");
    }

    @Test
    @Timeout(120)
    @DisplayName("过度收紧自检：以 // 开头的匹配器字面量不算违例")
    void matcherShapedSlashLiteralIsNotAViolation() throws IOException {
        String matcherShape = "Pattern[] banned = { Pattern.compile(\"(?i)//"
                + ".*\\\\bsynth\\\\b.*\\\\bexclud\") };\n";
        assertTrue(violationsIn(matcherShape).isEmpty(),
                "匹配器形态被误判为剥离器 ⇒ 禁令会顺手改掉无关判据: " + violationsIn(matcherShape));
        Path real = repoRoot().resolve(MATCHER_SHAPE_FILE);
        assertTrue(Files.isRegularFile(real), "反例基线文件缺失: " + MATCHER_SHAPE_FILE);
        assertTrue(violationsIn(SourceCommentStripper.strip(read(real))).isEmpty(),
                "DatanetNoExclusionTest 的 // 匹配器被本禁令误计 ⇒ 判据过宽");
    }

    @Test
    @Timeout(120)
    @DisplayName("唯一豁免必须被核验：codeOnly 先抹平字符串字面量，其后 // 才不影响字面量")
    void singleExemptionIsVerifiedNotJustDeclared() throws IOException {
        Path p = repoRoot().resolve(EXEMPT_FILE);
        assertTrue(Files.isRegularFile(p), "豁免文件不存在，豁免登记已失效: " + EXEMPT_FILE);
        String code = SourceCommentStripper.strip(read(p));
        // 1) 豁免是"承重"的：本判据确实会把它算成违例，否则豁免面就是空转
        assertFalse(violationsIn(code).isEmpty(),
                "豁免面已不再产生违例 ⇒ 应删除该豁免登记（零豁免面才是本门禁的目标形态）");
        // 2) 豁免理由成立：先把字符串字面量整体抹平，之后才剥行注释
        Matcher candidate = CANDIDATE_LITERAL.matcher(code);
        assertTrue(candidate.find(), "豁免理由失效：该文件已不再含以 // 开头的剥离形态");
        int blankLiterals = code.indexOf(".replaceAll(\"\\\"(?:");
        assertTrue(blankLiterals >= 0,
                "豁免理由失效：codeOnly 已不再先抹平字符串字面量 ⇒ 这正是 D-B4-40 的失明形态，必须迁移并删豁免");
        assertTrue(blankLiterals < candidate.start(),
                "豁免理由失效：先剥注释再抹字面量 ⇒ 顺序已翻转，必须迁移并删豁免");
    }

    @Test
    @Timeout(180)
    @DisplayName("跨模块副本零漂移：同名剥离器逐字符等价（无 test-jar 机制下的复制体一致性）")
    void stripperCopiesAreIdenticalAcrossModules() throws IOException {
        List<Path> copies = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot().resolve("ecos_backend"))) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("SourceCommentStripper.java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .forEach(copies::add);
        }
        assertEquals(REGISTERED_STRIPPER_COPIES, copies.size(),
                "剥离器副本数与注册值不符 ⇒ 有模块新增/删除了副本而未同步本判据");
        String owner = normalizeExceptPackage(
                repoRoot().resolve("ecos_backend/runtime/common-api/src/test/java/"
                        + "com/chinacreator/gzcm/common/testscan/SourceCommentStripper.java"));
        for (Path c : copies) {
            assertEquals(owner, normalizeExceptPackage(c),
                    "副本与 common-api 原件漂移（行为一致性只在逐字符等价时才成立）: " + rel(c));
        }
    }

    @Test
    @Timeout(180)
    @DisplayName("迁移在册清单逐文件命中：17 处门禁确实改为委托剥离器，不是改了个空集")
    void migratedGatesDelegateToTheStripper() throws IOException {
        assertEquals(17, MIGRATED_GATES.size(), "迁移清单长度即实测站点数，改动需与扫描面同批");
        for (String rel : MIGRATED_GATES) {
            Path p = repoRoot().resolve(rel);
            String code = SourceCommentStripper.strip(read(p));
            assertTrue(code.contains("SourceCommentStripper.strip(")
                            || code.contains("CogScope.stripComments("),
                    "在册门禁未委托剥离器（要么漏改，要么被顺手删掉判据）: " + rel);
        }
    }

    @Test
    @DisplayName("差距编号与批次标记在册：本门禁的上下文不得被后续顺手抹掉")
    void registrationMarkersExist() throws IOException {
        String owner = read(repoRoot().resolve(
                "ecos_backend/runtime/common-api/src/test/java/com/chinacreator/gzcm/common/"
                        + "testscan/SourceCommentStripper.java"));
        assertTrue(owner.contains("D-B4-40"), "剥离器丢失差距编号 ⇒ 台账与本类的对应关系断链");
        String self = SourceCommentStripper.strip(read(repoRoot().resolve(
                "ecos_backend/runtime/common-api/src/test/java/com/chinacreator/gzcm/common/"
                        + "testscan/NaiveCommentStripperBanTest.java")));
        assertTrue(self.contains("D-B4-40") && self.contains("#142/B4-3e-5"),
                "本门禁的差距/批次标记缺失 ⇒ 无法用 git log --grep 溯源");
    }

    private static String normalizeExceptPackage(Path p) throws IOException {
        String text = read(p).replace("\r\n", "\n");
        int firstNewline = text.indexOf('\n');
        assertTrue(text.startsWith("package "), "剥离器副本必须以 package 开头: " + rel(p));
        return text.substring(firstNewline + 1);
    }
}
