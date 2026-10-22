package com.chinacreator.gzcm.common.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-38（新登记差距 <b>D-B4-71</b>，收口 D-B4-58 余项的<b>判据侧</b>）——
 * 「依赖未就绪」型 {@code IllegalStateException} 腿的<b>分类</b>棘轮门。
 *
 * <h3>为什么这是一条差距，而不是"写法偏好"</h3>
 * <p>#142/B4-3e-26 把 gateway {@code handleIllegalState} 的兜底从 400 改成 500 时实扫过：
 * 全后端主源码 {@code throw new IllegalStateException(} 登记为 <b>140</b> 枚，其中消息带
 * 「未就绪／不可用／未接入」语义的一批被登记为余项，当时的读数是 <b>18</b> 枚且<b>判据未定型</b>
 * （按消息粗扫、含注释提及、未区分两种完全不同的语义）。本批重测把这笔账做实：剥注释后真实站点
 * <b>139</b>（第 140 枚是 {@code GlobalExceptionHandler} 里登记该数字的 javadoc 自身——
 * D-B4-40/62「注释面冒充代码面」同族），四面向数为 DEPENDENCY 21／ENV_ALGO 10／
 * UNREADABLE 8／OTHER 100，其中依赖词表命中的 21 枚与当年粗扫的 18 枚<b>并不相同</b>
 * （当年口径既漏了「未注入／未启用」两族，又把 JVM 算法腿算了进去）。</p>
 *
 * <p>这条余项的危险在于它<b>同时</b>藏着一个过度矫正的坑：§3.2.7 要求「下游制品不可用」落 <b>503</b>，而
 * {@code throw new IllegalStateException("SHA-256 不可用")} 这类
 * <b>JVM/JDK 强制算法缺失＝环境故障</b>的腿，按 §4.7.2 就该留 <b>500</b>——
 * 实扫 10 枚 ENV_ALGO 腿里有 <b>9</b> 枚的消息写着「不可用／unavailable」，
 * 若按这三个字一刀切改 503，等于把「服务端自己的运行环境坏了」
 * 说成「下游还没就绪、客户端可重试」，是另一种折叠。</p>
 *
 * <p>所以本批不动生产码，先把<b>判据定型并钉成可红门</b>：四面互斥完备、逐文件登记、双向对账。
 * 属主侧（依赖腿改抛 {@code DataBridgeException(503, 域内不可用码, …)}）按域另立
 * #142/B4-3e-39+ 逐批做，每批必须同批下调本门基线——「只减不增＋减了要登记」两头都红。</p>
 *
 * <h3>本门钉四件事</h3>
 * <ol>
 *   <li><b>四面读数全部钉死，禁恒等式对账</b>：每条 {@code throw new IllegalStateException(…)} 语句
 *       的消息字面量按「JVM/JDK 强制算法（{@code SHA-256}/{@code HMAC}）」→ ENV_ALGO、
 *       「依赖未就绪词表」→ DEPENDENCY、其余 → OTHER。首版这里写的是
 *       {@code other = total - env - dep} 再断言三面之和＝total，那是<b>由构造恒真的假绿</b>，
 *       已改为总站点数、三面读数、两词表交叠腿数四个各自独立的钉值——任一面漂走都要显式改注释。
 *       判据按算法词<b>优先</b>归属，故交叠腿（"SHA-256 不可用"）计入 ENV_ALGO。</li>
 *   <li><b>双向逐文件基线</b>：DEPENDENCY 面与 ENV_ALGO 面各自按文件计数
 *       {@code equals} 登记基线——新增一枚未登记即红（不许「看不见」冒充「已判过」）；
 *       收口一枚未下调同样即红（基线陈旧＝下一批人以为还剩这么多）。</li>
 *   <li><b>判据自身的漏点也要点名</b>：参数表里没有字符串字面量的抛出＝判据读不到消息，
 *       必然静默落 OTHER（D-B4-62「违例条数盲区」同族）。这类腿按零豁免登记制单列一面；
 *       另配<b>行号可回读</b>反证——每条已登记腿都要能在剥注释源码的该读行上读到该条抛出与消息片段，
 *       否则扫描器坐标漂移会让后续属主批改错位置（合计对账抓不到这种漂移）。</li>
 *   <li><b>兜底归型回钉</b>：gateway {@code handleIllegalState} 的 500 归型（D-B4-58 的收口面）
 *       由文本锚钉住；若有人为了"少几枚 500"把它改回 400，本门与
 *       {@code BusinessExceptionStatusParityRatchetTest} 同时红。</li>
 * </ol>
 *
 * <p>取证纪律：判据全部先经 {@link SourceCommentStripper} 剥注释（D-B4-40/62 同口径，
 * 禁朴素正则剥注释——{@code NaiveCommentStripperBanTest} 在链）；消息提取按<b>括号深度＋
 * 字符串字面量感知</b>扫描整条语句，不按行截断（{@code throw new IllegalStateException("…"
 * + e.getMessage(), e)} 跨行时逐行判据会漏读）。</p>
 */
class DependencyUnreadyIllegalStateRatchetTest {

    /** gateway 入口兜底属主（D-B4-58 收口腿所在文件）。 */
    private static final String GATEWAY_HANDLER =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java";

    private static final Pattern ISE_THROW =
            Pattern.compile("throw\\s+new\\s+IllegalStateException\\s*\\(");

    /**
     * 依赖未就绪词表（应落 503 的那一族）。
     *
     * <p>{@code 未注入} 一条是 #142/B4-3e-38 探测期补入的：data-engine
     * {@code BaseJdbcAdapter} 的建连腿写的是
     * {@code "（F02-13：runtime-access JdbcConnector 建连桥未注入，禁止回退 DriverManager）"}，
     * 语义上正是「下游制品不可用」，首版词表（不可用／未就绪／未装配／未接入）读不到它，
     * 让它静默落进 OTHER＝判据口径欠一个动词。补词表而不是放过这条腿。</p>
     */
    private static final Pattern DEPENDENCY_READY_VOCAB = Pattern.compile(
            "不可用|未就绪|未装配|未接入|未注入|未启用"
                    + "|not\\s+ready|unavailable|not\\s+wired|not\\s+configured|not\\s+injected",
            Pattern.CASE_INSENSITIVE);

    /** JVM/JDK 强制算法腿（属环境故障，按 §4.7.2 留 500，不得伪装成"下游未就绪"）。 */
    private static final Pattern JVM_MANDATORY_ALGORITHM =
            Pattern.compile("sha-?256|hmac", Pattern.CASE_INSENSITIVE);

    /** 字符串字面量提取（消息可能由多段字面量拼接）。 */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * DEPENDENCY 面基线（repo-root 相对路径 → 枚数）。
     *
     * <p>#142/B4-3e-38 按「上限置 0 探测」同法<b>实扫</b>填入（取证：
     * {@code _win_tasks/logs/mvn_b4-3e-38_probe3.log}，非算式推出）：10 个持有者 / <b>21</b> 枚腿。
     * 属主侧收口批必须同批下调本表。</p>
     *
     * <p>词表扩到 {@code 未注入|未启用} 后新增的两枚，语义裁决如下（不是随手加的匹配串）：
     * data-engine {@code BaseJdbcAdapter} 的「runtime-access JdbcConnector 建连桥未注入」＝
     * F02-13 建连通道缺失；{@code OlapEngineResolver} 的「OLAP 引擎未启用（dw.olap.engine 未配置）」
     * ＝该部署档未开通这条写通道，消息里已自带 {@code ECOS-DATA-054} 机读码。两者都是
     * 「制品在本部署不可用」（§3.2.7 ⇒ 属主端点显式 503），不是「不变式被破坏」，
     * 也不是 ENV_ALGO 那种「JVM 自己坏了」。</p>
     */
    private static final Map<String, Integer> DEPENDENCY_BASELINE = Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/agent/mesh/knowledge/Neo4jQueryService.java", 5),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/capability/ModelCapabilityExecutorImpl.java", 1),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/AgentTemplateService.java", 1),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/ToolExecutorService.java", 6),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datadescription/discovery/impl/DataDescriptionDiscoveryImpl.java", 1),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java", 1),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/writechannel/OlapEngineResolver.java", 1),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/service/KbEntityInstanceExtractionService.java", 1),
            Map.entry("ecos_backend/runtime/runtime-core/src/main/java/com/chinacreator/"
                    + "gzcm/runtime/core/crypto/SecurityCryptoEgress.java", 2),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/scenario/DcchengClient.java", 2));

    /**
     * ENV_ALGO 面基线（故意保持 500 的豁免面，登记制、必须点名）：
     * 9 个持有者 / <b>10</b> 枚腿，同批实扫取证。
     */
    private static final Map<String, Integer> ENV_ALGO_BASELINE = Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/wagent/orchestrator/WAgentHashSupport.java", 1),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/cognitive2/service/calc/DeterministicForecastCalculator.java", 1),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/cognitive2/service/calc/ModelContentHasher.java", 1),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/cognitive2/service/context/ContextDigestCalculator.java", 1),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/event/EcosOntologyEventConsumer.java", 1),
            Map.entry("ecos_backend/runtime/common-api/src/main/java/com/chinacreator/"
                    + "gzcm/common/security/header/EcosServiceCredentialSigner.java", 2),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/QueryHistoryService.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/blueprint/ScenarioBlueprintService.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/forecast/ForecastAuditPackService.java", 1));

    /**
     * 四面读数与总站点数的对账常量（#142/B4-3e-38 探测实扫，禁算式推出）。
     *
     * <p>钉总站点数而不是只钉两张登记表，是为了堵<b>判据自身的失步</b>：一条消息来自变量
     * （i18n 键面）的抛出抽不出字面量，词表对它必然不命中，会静默落进 OTHER＝「判据看不见」
     * 冒充「已判过」（D-B4-62 违例条数盲区同族）。故本门把<b>读不出消息</b>单列为第四面
     * UNREADABLE 并按零豁免点名登记，四面读数＋总站点数全部钉死——任一面漂走都要显式改注释。</p>
     *
     * <p><b>总站点数与台账「140」的差 1 的归因</b>：{@code GlobalExceptionHandler} 的 javadoc 里
     * 字面写着这句抛出语句的判据原文（那段注释正是在登记「实扫 140 枚」），朴素 grep 会把注释
     * 自己数进去。剥注释后真实站点为 {@code TOTAL_SITE_BASELINE}——D-B4-40/62「注释面冒充
     * 代码面」的同族形态，已同批在该注释处订正。</p>
     */
    private static final int TOTAL_SITE_BASELINE = 139;
    private static final int DEPENDENCY_FACE_BASELINE = 21;
    private static final int ENV_ALGO_FACE_BASELINE = 10;
    private static final int OTHER_FACE_BASELINE = 100;
    private static final int UNREADABLE_FACE_BASELINE = 8;

    /**
     * 「同时命中两词表」的腿数（{@code SHA-256 不可用} 这类）：判据按<b>算法词优先</b>归 ENV_ALGO。
     * 钉住这个交叠数，改文案把环境故障挪进依赖面（或反向）就不再是免费动作——它同时改变两个面的读数。
     *
     * <p>实扫 9 枚（10 枚 ENV_ALGO 腿里只有 {@code WAgentHashSupport} 的
     * 「JVM 缺 SHA-256 算法（不符合 Java 17 基线）」不带依赖词）＝<b>绝大多数</b>环境故障腿
     * 都写着「不可用」，这正是"按消息一刀切换 503"会把 JVM 故障说成下游未就绪的原因。</p>
     */
    private static final int BOTH_PATTERN_OVERLAP_BASELINE = 9;

    /**
     * 判据<b>读不出消息</b>的腿（参数表里无字符串字面量，消息来自变量／i18n 键）。
     *
     * <p>实扫：data-engine {@code BaseJdbcAdapter} 8 枚（行 180／329／396／445／490／531／572／598），
     * 消息全部来自 {@code String errorMsg = I18nUtils.getErrorMessage("connection.failed"
     * ｜"write.failed"｜…, locale)}。它们语义上正是「建连／读写通道不可用」，但词表读不到键面——
     * 属主批要判它们，必须<b>连 i18n 键面一起改</b>（补字面量或改键映射），不是在本门加匹配串。
     * 该文件另有 1 枚（行 108）带字面量补语，已入 DEPENDENCY 面，两面相加＝该文件 9 枚 ISE 腿。</p>
     */
    private static final Map<String, Integer> UNREADABLE_BASELINE = Map.of(
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java", 8);

    // ── 扫描与分类 ──────────────────────────────────────────────────────

    private record Scanned(String relPath, String code) {
    }

    /** 一条 ISE 抛出语句：所属文件、语句起始行号、拼接后的消息文本。 */
    record Leg(String relPath, int line, String message) {
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

    private static String codeOf(String relative) {
        return SourceCommentStripper.strip(readRepoFile(relative));
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
                out.add(new Scanned(rel, SourceCommentStripper.strip(
                        new String(Files.readAllBytes(f), StandardCharsets.UTF_8))));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描主源码失败: " + e.getMessage(), e);
        }
        return out;
    }

    /**
     * 从 {@code (} 之后按<b>括号深度＋字符串感知</b>取整条参数表，再抽全部字面量拼成消息。
     *
     * @param code  剥注释后的源码
     * @param start 参数表左圆括号<b>之后</b>的下标
     * @return 参数表文本（不含最外层括号）；扫描不闭合即 fail，不得默认放行
     */
    private static String argumentText(String code, int start) {
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return code.substring(start, i);
                }
            }
        }
        return fail("ISE 抛出语句参数表未闭合（判据扫描器失步，不得当作\"零违例\"）: "
                + code.substring(start, Math.min(code.length(), start + 120)));
    }

    private static String joinedLiterals(String args) {
        StringBuilder sb = new StringBuilder();
        Matcher m = STRING_LITERAL.matcher(args);
        while (m.find()) {
            sb.append(m.group(1));
        }
        return sb.toString();
    }

    /** 全站点缓存：一趟全后端扫描秒级，但每条腿重复走树会把本门拖成分钟级并顶穿 {@code @Timeout}。 */
    private static volatile List<Leg> cachedLegs;

    /** 全站点：每条 ISE 抛出语句的（文件，行号，消息）。 */
    private static List<Leg> allIllegalStateLegs() {
        List<Leg> local = cachedLegs;
        if (local != null) {
            return local;
        }
        synchronized (DependencyUnreadyIllegalStateRatchetTest.class) {
            if (cachedLegs != null) {
                return cachedLegs;
            }
            List<Leg> out = new ArrayList<>();
            for (Scanned s : backendMainSources()) {
                Matcher m = ISE_THROW.matcher(s.code());
                while (m.find()) {
                    int openParen = m.end();
                    String args = argumentText(s.code(), openParen);
                    int line = 1;
                    for (int i = 0; i < m.start(); i++) {
                        if (s.code().charAt(i) == '\n') {
                            line++;
                        }
                    }
                    out.add(new Leg(s.relPath(), line, joinedLiterals(args)));
                }
            }
            cachedLegs = List.copyOf(out);
            return cachedLegs;
        }
    }

    private static boolean isJvmAlgorithmLeg(Leg leg) {
        return JVM_MANDATORY_ALGORITHM.matcher(leg.message()).find();
    }

    private static boolean isDependencyReadyLeg(Leg leg) {
        return DEPENDENCY_READY_VOCAB.matcher(leg.message()).find();
    }

    private static Map<String, Integer> perFile(List<Leg> legs) {
        Map<String, Integer> out = new TreeMap<>();
        for (Leg l : legs) {
            out.merge(l.relPath(), 1, Integer::sum);
        }
        return out;
    }

    private static String render(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append("    ").append(k).append(" = ").append(v).append('\n'));
        return sb.length() == 0 ? "    (空)\n" : sb.toString();
    }

    private static int sum(Map<String, Integer> map) {
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 双向逐文件对账：未登记的新腿、未下调的旧腿、文件消失，三种漂移分别给出不同文案——
     * 「只减不增」的单向门在这里不够用，因为本面是要被后续批<b>逐域清空</b>的账。
     */
    private static void assertPinnedBidirectionally(String faceName, Map<String, Integer> actual,
                                                    Map<String, Integer> expected) {
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got > e.getValue()) {
                diff.add(faceName + " 面扩张（新增未登记的腿＝「看不见」冒充「已判过」）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            } else if (got < e.getValue()) {
                diff.add(faceName + " 面收缩但基线未同批下调（陈旧读数＝下一批人按虚高余面排产）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diff.add(faceName + " 面出现未登记持有者（豁免与欠账都必须点名）: "
                        + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        assertTrue(diff.isEmpty(), faceName + " 面失真（登记基线如下）:\n"
                + String.join("\n", diff) + "\n  登记面:\n" + render(expected)
                + "  实扫面:\n" + render(actual));
    }

    // ── 验收 1：四面读数全部钉死（禁恒等式假绿） ──────────────────────────

    @Test
    @Timeout(value = 240)
    @DisplayName("分类对账：总站点数与四面读数逐一钉死，四面之和＝总数（D-B4-62 同族盲区防线）")
    void everyIllegalStateLegFallsIntoExactlyOneClass() {
        int env = jvmAlgorithmLegs().size();
        int dep = dependencyLegs().size();
        int unreadable = unreadableLegs().size();
        int other = otherLegs().size();
        int total = allIllegalStateLegs().size();

        // 五条独立钉值。首版这里写的是 other = total - env - dep 之后断言三面之和＝total，
        // 那是由构造恒真的假绿；现在每一面都各自被钉，任何一面漂走都必须显式改注释。
        List<String> drift = new ArrayList<>();
        accountFor(drift, "总站点数", TOTAL_SITE_BASELINE, total,
                "＝判据口径或站点本身变了，必须重测后同批改（D-B4-71）");
        accountFor(drift, "DEPENDENCY 面", DEPENDENCY_FACE_BASELINE, dep,
                "＝有腿进出该面未登记（改文案逃判据的发生形态）");
        accountFor(drift, "ENV_ALGO 面", ENV_ALGO_FACE_BASELINE, env,
                "＝同上，且该面是「故意留 500」的豁免面，进出都要点名");
        accountFor(drift, "UNREADABLE 面", UNREADABLE_FACE_BASELINE, unreadable,
                "＝判据读不出消息的腿发生增减，见 legsTheJudgementCannotReadAreNamed()");
        accountFor(drift, "OTHER 面", OTHER_FACE_BASELINE, other,
                "＝OTHER 不是欠账清单，但它是闭合账的一部分，进出必须对账");

        // 逐文件基线与面读数必须一致：否则登记表面与判据读数各说各话。
        accountFor(drift, "DEPENDENCY 逐文件基线合计", sum(DEPENDENCY_BASELINE), dep,
                "＝登记面与面读数不一致＝有腿换面未登记");
        accountFor(drift, "ENV_ALGO 逐文件基线合计", sum(ENV_ALGO_BASELINE), env,
                "＝登记面与面读数不一致");
        accountFor(drift, "UNREADABLE 逐文件基线合计", sum(UNREADABLE_BASELINE), unreadable,
                "＝登记面与面读数不一致");

        // 闭合性：四面互斥且无遗漏（互斥写在分类器里，遗漏只能由这条抓住）。
        accountFor(drift, "四面之和", total, env + dep + unreadable + other,
                "＝判据有口径外盲区（腿未落任何一面＝D-B4-62 违例条数盲区同族）");

        // 互斥性的真判据：判据按算法词优先，交叠腿数本身钉死——改文案跨面迁移不再是免费动作。
        int overlap = (int) allIllegalStateLegs().stream()
                .filter(DependencyUnreadyIllegalStateRatchetTest::isJvmAlgorithmLeg)
                .filter(DependencyUnreadyIllegalStateRatchetTest::isDependencyReadyLeg)
                .count();
        accountFor(drift, "两词表交叠腿数", BOTH_PATTERN_OVERLAP_BASELINE, overlap,
                "＝有人改了「环境故障／依赖未就绪」的边界文案，须显式对账");

        assertTrue(drift.isEmpty(), "分类账失真:\n" + String.join("\n", drift));
    }

    private static void accountFor(List<String> drift, String faceName, int expected, int actual,
                                   String reason) {
        if (expected != actual) {
            drift.add("  " + faceName + "：登记 " + expected + " → 实扫 " + actual + reason);
        }
    }

    // ── 验收 2：两张登记表面向文件双向等值 ────────────────────────────────

    @Test
    @Timeout(value = 240)
    @DisplayName("DEPENDENCY 面逐文件双向等值：新增即红，收口未下调同样即红")
    void dependencyLegsArePinnedPerFile() {
        Map<String, Integer> actual = perFile(dependencyLegs());
        assertPinnedBidirectionally("DEPENDENCY", actual, DEPENDENCY_BASELINE);
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("ENV_ALGO 豁免面逐文件双向等值：故意留 500 的腿必须点名登记")
    void jvmAlgorithmLegsAreNamedNotSilentlyExcluded() {
        Map<String, Integer> actual = perFile(jvmAlgorithmLegs());
        assertPinnedBidirectionally("ENV_ALGO", actual, ENV_ALGO_BASELINE);
    }

    /**
     * 四面分类器＝互斥且完备的划分（先剔读不出消息的，再按算法词优先，最后词表命中）。
     * 完备性由 {@code everyIllegalStateLegFallsIntoExactlyOneClass()} 的「四面之和＝总站点数」钉住。
     */
    private static List<Leg> unreadableLegs() {
        return allIllegalStateLegs().stream()
                .filter(l -> l.message().isBlank())
                .toList();
    }

    private static List<Leg> jvmAlgorithmLegs() {
        return allIllegalStateLegs().stream()
                .filter(l -> !l.message().isBlank())
                .filter(DependencyUnreadyIllegalStateRatchetTest::isJvmAlgorithmLeg)
                .toList();
    }

    private static List<Leg> dependencyLegs() {
        return readableLegs().stream()
                .filter(l -> !isJvmAlgorithmLeg(l))
                .filter(DependencyUnreadyIllegalStateRatchetTest::isDependencyReadyLeg)
                .toList();
    }

    private static List<Leg> otherLegs() {
        return readableLegs().stream()
                .filter(l -> !isJvmAlgorithmLeg(l))
                .filter(l -> !isDependencyReadyLeg(l))
                .toList();
    }

    private static List<Leg> readableLegs() {
        return allIllegalStateLegs().stream().filter(l -> !l.message().isBlank()).toList();
    }

    // ── 验收 3：判据读不出消息的腿必须点名（OTHER 面之外的唯一漏点） ────────

    /**
     * 参数表里没有字符串字面量的抛出（消息来自 {@code errorMsg} 这类局部变量／i18n 键）＝
     * 判据<b>读不到消息</b>，词表对它必然不命中。这类腿不进 DEPENDENCY 面不代表它语义上不是
     * 「依赖未就绪」——data-engine {@code BaseJdbcAdapter} 的 9 枚建连／读写腿全是
     * {@code I18nUtils.getErrorMessage("connection.failed", …)} 的键面形态，正是本门要抓的族。
     *
     * <p>所以本面按「零豁免登记制」点名：属主批要把这些腿纳入 503 判定，必须<b>连 i18n 键面
     * 一起改</b>（改键名映射或在抛出处补字面量），不能只改词表——词表加不上一个读不到的消息。
     * 本面新增一枚即红，收口一枚未下调同样即红。</p>
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("读不出消息的 ISE 腿逐文件点名：属主批的前置条件，禁静默落 OTHER 面")
    void legsTheJudgementCannotReadAreNamed() {
        assertPinnedBidirectionally("UNREADABLE", perFile(unreadableLegs()), UNREADABLE_BASELINE);
    }

    // ── 验收 4：合计钉值（探测实跑产出，禁算式推出） ──────────────────────

    /**
     * 行号归因反证：判据给出的 (文件, 行号) 必须真能在剥注释源码上读到该条抛出。
     *
     * <p>这条不是重复劳动——它守的是<b>扫描器与源码的坐标一致性</b>。{@code argumentText}
     * 用括号深度扫描跨行参数表，若扫描器对某种写法失步（例如把上一条语句的参数吃进下一条），
     * 站点数仍可能正确、消息仍能抽出，但<b>行号会漂</b>——后续属主批照行号去改码就会改错地方，
     * 登记面从此与真实代码脱钩。合计对账抓不到这种漂移，只有逐腿回读源文件才抓得到。</p>
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("两面每条腿的行号可回读：该行确有该条抛出与消息字面量（防扫描器坐标漂移）")
    void classifiedLegsAreReadableAtTheirReportedLine() {
        List<Leg> legs = new ArrayList<>();
        legs.addAll(dependencyLegs());
        legs.addAll(jvmAlgorithmLegs());
        List<String> bad = new ArrayList<>();
        Map<String, String> codeCache = new TreeMap<>();
        for (Leg leg : legs) {
            String code = codeCache.computeIfAbsent(leg.relPath(), DependencyUnreadyIllegalStateRatchetTest::codeOf);
            String[] lines = code.split("\n", -1);
            if (leg.line() < 1 || leg.line() > lines.length) {
                bad.add(leg.relPath() + ":" + leg.line() + " 行号越界（该文件剥注释后 "
                        + lines.length + " 行）");
                continue;
            }
            int from = leg.line() - 1;
            int to = Math.min(lines.length, from + 4);
            String window = String.join("\n", java.util.Arrays.copyOfRange(lines, from, to));
            if (!window.contains("throw new IllegalStateException")) {
                bad.add(leg.relPath() + ":" + leg.line() + " 该处读不到抛出语句＝行号漂移，登记面与代码脱钩");
                continue;
            }
            String head = leg.message().substring(0, Math.min(10, leg.message().length()));
            if (!window.contains(head)) {
                bad.add(leg.relPath() + ":" + leg.line() + " 该处读不到消息片段 \"" + head
                        + "\"＝抽取器与行号归因不一致");
            }
        }
        assertTrue(bad.isEmpty(), "扫描器坐标漂移（后续批按此登记改码会改错位置）:\n"
                + String.join("\n", bad));
    }

    // ── 验收 4：兜底归型回钉（D-B4-58 收口面不得被"少几枚 500"翻回 400） ──

    @Test
    @DisplayName("gateway handleIllegalState 仍归 500：翻回 400 即红（D-B4-58 复发）")
    void gatewayFallbackStillClassifiesIllegalStateAsServerError() {
        String h = codeOf(GATEWAY_HANDLER);
        assertTrue(h.contains("@ExceptionHandler(IllegalStateException.class)"),
                "gateway 不再具名兜底 IllegalStateException＝本门的面变了");
        int idx = h.indexOf("handleIllegalState");
        assertTrue(idx > 0, "找不到 handleIllegalState 方法体＝属主侧结构改动，判据需重测");
        String before = h.substring(Math.max(0, idx - 400), idx);
        assertTrue(before.contains("@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)"),
                "handleIllegalState 的上位注解不再是 500＝D-B4-58 复发（服务端缺陷被说成客户端错误）");
        assertFalse(before.contains("@ResponseStatus(HttpStatus.BAD_REQUEST)"),
                "handleIllegalState 上方出现 BAD_REQUEST＝兜底把服务端缺陷折回 400（D-B4-58 原形态）");
        assertTrue(h.contains("SystemErrorCode.ECOS_SYS_500"),
                "500 腿的机读码不再取单源常量＝「改码不改行」的漂移形态（D-B4-52 同族）");
    }
}
