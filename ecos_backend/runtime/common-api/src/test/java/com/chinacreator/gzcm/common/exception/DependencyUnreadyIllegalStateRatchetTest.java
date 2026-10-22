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
     * <p><b>#142/B4-3e-40 属主侧首批后现值＝9 个持有者／19 枚腿</b>：runtime-core
     * {@code SecurityCryptoEgress} 的 2 枚「出口不可用」腿已改抛
     * {@code DataBridgeException(503, 503, "ECOS-SYS-503: …")}，脱离 ISE 站点面 ⇒
     * 总站点 139→<b>137</b>、DEPENDENCY 21→<b>19</b>、落兜底腿 14→<b>12</b>、真欠账面 16→<b>14</b>。
     * 该文件剩余 4 枚 ISE 腿（{@code ensureKey 失败／反射调用失败} 两族）语义是<b>本服务自身故障</b>，
     * 留在 OTHER 面按 500，不在本批折叠。</p>
     *
     * <p><b>#142/B4-3e-41 属主侧第二批后现值＝8 个持有者／17 枚腿</b>：workspace
     * {@code DcchengClient} 的 2 枚「cognitive 服务不可用」腿已改抛本域既有 503 载体
     * {@code CognitiveEngineUnavailableException}（经 {@code WorkspaceExceptionHandler
     * .handleDomain} 渲染为 {@code 503 + COGNITIVE_UNAVAILABLE}），脱离 ISE 站点面 ⇒
     * 总站点 137→<b>135</b>、DEPENDENCY 19→<b>17</b>、已承载腿 15→<b>13</b>、真欠账面 14→<b>12</b>
     * （落兜底 12＋承载后仍 500 的 2 枚，本批收掉后者）。该文件剩余 4 枚 ISE 腿
     * （{@code dccheng 返回空}／{@code dccheng 业务错误} 两族）语义是<b>下游应答异常而非未就绪</b>，
     * 词表不命中、留在 OTHER 面，不在本批折叠。</p>
     *
     * <p><b>#142/B4-3e-42 属主侧第三批后现值＝7 个持有者／16 枚腿</b>：kb
     * {@code KbEntityInstanceExtractionService.ontologyBase()} 的「ServiceEndpointResolver 未接入」腿
     * 已改抛本域 503 载体 {@code KbErrorCodes.ONTOLOGY_BASE_UNAVAILABLE.exception(…)}
     * （{@code 503 + ECOS-KB-024}，经 {@code KbErrorAdvice.onDomain} 按携带状态渲染），脱离 ISE 站点面 ⇒
     * 总站点 135→<b>134</b>、DEPENDENCY 17→<b>16</b>、落兜底腿 12→<b>11</b>、真欠账面 12→<b>11</b>
     * （承载腿 13 未动）。该文件其余 {@code IllegalStateException} 站点数为 <b>0</b>（本批前实扫即 1 枚），
     * 故整行退出本表；渲染链的证据在 {@link #kbOwnerSideLegRendersCarriedUnavailableStatusInsteadOfFallback()}。</p>
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
                    + "gzcm/engine/data/writechannel/OlapEngineResolver.java", 1));

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
     *
     * <p><b>#142/B4-3e-40 属主侧首批后的现值</b>：139 → 137、DEPENDENCY 面 21 → 19，差 2 来自
     * {@code SecurityCryptoEgress} 的「出口不可用」两腿改抛 {@code DataBridgeException(503)}——
     * 抛出语句本身不再属于本门判据面（改的是属主侧代码，不是判据）。总站点数与 DEPENDENCY 面
     * <b>同批</b>下调、逐文件基线里该文件行删除，三处任一单独改都会红（漏改即红＝本门的设计）。</p>
     *
     * <p><b>#142/B4-3e-41 属主侧第二批后的现值</b>：137 → <b>135</b>、DEPENDENCY 面 19 → <b>17</b>，
     * 差 2 来自 workspace {@code DcchengClient} 的「cognitive 服务不可用」两腿改抛本域 503 载体。
     * 同批还要改的是承载面：该文件行退出 {@code CARRIER_BASELINE}、已承载腿 15 → <b>13</b>、
     * 真欠账面 14 → <b>12</b>——四处任一单独改都会红。</p>
     */
    private static final int TOTAL_SITE_BASELINE = 134;
    private static final int DEPENDENCY_FACE_BASELINE = 16;
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

    // ══ 验收 5（#142/B4-3e-39）：承流面——「该改 503」的前提是它真的落到了兜底 ══════

    /**
     * <b>本面要纠的是 D-B4-71 自己登记时的一个前提错误。</b>
     *
     * <p>#142/B4-3e-38 把 DEPENDENCY 21 枚＋UNREADABLE 8 枚整批登记为「属主侧应改抛 503」，
     * 依据是 §3.2.7「下游制品不可用 ⇒ 显式 503」。这句话默认了这些腿<b>都会走到 gateway 入口兜底</b>
     * ＝本文件第 6 案钉住的 500。实扫承流面后发现这个默认不成立：
     * {@code AiErrorAdvice}（作用域 {@code com.chinacreator.gzcm.engine.ai}）声明了
     * {@code @ExceptionHandler(Exception.class)}，智能域内抛出的 {@code IllegalStateException}
     * 在<b>本地就被承载</b>，经 {@code ErrorCodeMapper.resolve()} 的 GENERIC 兜底落
     * {@code TOOL_BACKEND_ERROR}＝HTTP 502＋机读码 {@code ECOS-AI-502}，
     * 而该类的 javadoc 明写「执行器未就绪同样归此类（禁 mock-success，X-50）」——
     * 分册 06 D.5 冻结表<b>已经对这一族做过归属裁决</b>。同理 {@code WorkspaceExceptionHandler}
     * 的 {@code handleUnexpected} 承载后回 {@code build(500, "INTERNAL", …)}。</p>
     *
     * <p>所以「21＋8 枚全欠 503」是个<b>没有承流读数的批量定罪</b>：真正需要属主批改码的，
     * 只有实测落进兜底（或被承载后仍回 500）的那一面；已按冻结表落 5xx＋域内机读码的腿，
     * 改它反而违背分册 06 的冻结值。本面把每条腿的<b>承流归属</b>做成实扫读数并双向钉死，
     * 判据错一次就要红一次。</p>
     *
     * <p><b>在册限制（不伪装成已判完）</b>：Spring 的 {@code @RestControllerAdvice} 作用域按
     * <b>控制器所在包</b>匹配，而本面按<b>抛出腿所在包的包名</b>判「该包是否被某个声明了
     * {@code Exception.class} 兜底的 advice 覆盖」＝承流的<b>必要条件</b>而非充分条件：
     * 一条 ai 域的腿若由 gateway 侧控制器调用，实际承载者是网关兜底。故本面登记的是
     * 「静态可达的承载 advice」，属主批动手前仍须逐条核到实际入口控制器；
     * 这条限制写在门里，就是为了让下一个读数的人不把「有 advice」读成「已判过」。
     * 另：{@code assignableTypes} 形态的作用域无法按包判定，本面把它单列为
     * {@code TYPE_SCOPED}，不参与承载判定（宁可漏判成兜底，也不虚判成已承载）。</p>
     */
    private static final Pattern ADVICE_ANNOTATION =
            Pattern.compile("^ *@(?:Rest)?ControllerAdvice\\b", Pattern.MULTILINE);

    private static final Pattern EXCEPTION_HANDLER_ANNOT =
            Pattern.compile("@ExceptionHandler\\s*\\(");

    /** 裸 {@code Exception.class}（左侧不得是标识符字符或点，排除 {@code DataAccessException.class} 这类）。 */
    private static final Pattern BARE_EXCEPTION_CLASS =
            Pattern.compile("(?<![\\w.])Exception\\.class");

    private static final Pattern PACKAGE_DECL =
            Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** advice 选择器里的引号字符串＝包名（{@code assignableTypes} 形态不含引号）。 */
    private static final Pattern QUOTED_SELECTOR = Pattern.compile("\"([\\w.]+)\"");

    /** 一个 {@code @ControllerAdvice}：文件、选择器原文（空白归一）、是否声明裸 Exception 兜底。 */
    record Advice(String relPath, String selector, boolean hasExceptionFallback) {
    }

    private static final String AI_ADVICE =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/controller/AiErrorAdvice.java";
    private static final String AI_ERROR_MAPPER =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/ErrorCodeMapper.java";
    private static final String AI_ERROR_CLASS =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/"
                    + "gzcm/common/error/AiErrorClass.java";
    private static final String WS_ADVICE =
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                    + "gzcm/workspace/exception/WorkspaceExceptionHandler.java";
    private static final String KB_ADVICE =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/support/KbErrorAdvice.java";
    /** #142/B4-3e-42：kb 属主侧第三批渲染取证所依赖的四个源（码表／属主腿／两条 HTTP 承流路径）。 */
    private static final String KB_ERROR_CODES =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/support/KbErrorCodes.java";
    private static final String KB_EXTRACTION_SERVICE =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/service/KbEntityInstanceExtractionService.java";
    private static final String KB_INGEST_CONTROLLER =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/controller/KnowledgeIngestController.java";
    private static final String KB_EXTRACT_CONTROLLER =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/kb/controller/StructuredExtractController.java";

    /**
     * 承载 advice 面基线（相对路径 → 「是否裸 Exception 兜底｜作用域选择器规范化文本」）。
     *
     * <p>#142/B4-3e-39 上限置 0 探测实扫取证：<b>8</b> 枚 {@code @ControllerAdvice}，其中
     * {@code EXCEPTION_FALLBACK}（能接住任意 {@code IllegalStateException}）5 枚、
     * {@code NO_EXCEPTION_FALLBACK} 3 枚中的 kb 一枚作用域正好覆盖 kb 依赖腿。
     * 两枚 {@code GLOBAL}（gateway / sysman-boot {@code GlobalExceptionHandler}）分属<b>不同部署件</b>
     * 的入口兜底，本面在 {@code carrierOf} 里<b>显式排除</b> GLOBAL——它们不是「域内承载者」，
     * 而是「落兜底」这件事本身（读数名 {@code ENTRY_FALLBACK_500}，由本文件第 6 案钉住其 500）。</p>
     *
     * <p>{@code WAgentOrchestrationAdvice}／{@code ContextBuildErrorAdvice} 是
     * {@code assignableTypes} 形态，按在册限制不参与承载判定。</p>
     */
    private static final Map<String, String> ADVICE_BASELINE = Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                            + "gzcm/engine/ai/controller/AiErrorAdvice.java",
                    "EXCEPTION_FALLBACK | basePackages = {\"com.chinacreator.gzcm.engine.ai\"}"),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                            + "gzcm/engine/ai/wagent/web/WAgentOrchestrationAdvice.java",
                    "EXCEPTION_FALLBACK | assignableTypes = {WAgentGoalController.class, "
                            + "WAgentQuestionController.class, WAgentRunController.class, "
                            + "WAgentRunEventStreamController.class, WAgentKillSwitchController.class, "
                            + "WAgentReadinessController.class, WAgentToolContractController.class, "
                            + "WAgentCandidateController.class, WAgentEvidenceTraceController.class, "
                            + "WAgentDecisionActionController.class}"),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/"
                            + "chinacreator/gzcm/engine/cognitive2/controller/ContextBuildErrorAdvice.java",
                    "NO_EXCEPTION_FALLBACK | assignableTypes = ContextBuildController.class"),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/"
                            + "chinacreator/gzcm/engine/cognitive2/controller/scenario/CognitiveErrorAdvice.java",
                    "EXCEPTION_FALLBACK | basePackages = {\"com.chinacreator.gzcm.engine.cognitive2.controller"
                            + ".scenario\", \"com.chinacreator.gzcm.engine.cognitive2.controller\"}"),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/"
                            + "gzcm/engine/kb/support/KbErrorAdvice.java",
                    "NO_EXCEPTION_FALLBACK | basePackages = {\"com.chinacreator.gzcm.engine.kb\"}"),
            Map.entry("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/"
                            + "GlobalExceptionHandler.java",
                    "EXCEPTION_FALLBACK | GLOBAL"),
            Map.entry("ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/"
                            + "gzcm/sysman/boot/handler/GlobalExceptionHandler.java",
                    "EXCEPTION_FALLBACK | GLOBAL"),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/"
                            + "gzcm/workspace/exception/WorkspaceExceptionHandler.java",
                    "EXCEPTION_FALLBACK | basePackages = \"com.chinacreator.gzcm.workspace\""));

    /**
     * DEPENDENCY＋UNREADABLE 面持有者的承流归属基线（相对路径 → 承载者标识）。
     *
     * <p>探测实扫：<b>10</b> 个持有者／<b>29</b> 枚腿，其中被域内 advice 承载 <b>6</b> 个持有者／
     * <b>15</b> 枚腿（ai 4／13 ＋ workspace 1／2），落入口兜底 <b>4</b> 个持有者／<b>14</b> 枚腿
     * （data-engine 3／11 ＋ kb 1／1 ＋ runtime-core 1／2；BaseJdbcAdapter 一家就占 9 枚）。
     * <b>这把 D-B4-71 登记时的「21＋8 枚全欠 503」批定罪改窄成 16 枚真欠账面</b>：ai 的 13 枚
     * 已由分册 06 D.5 冻结表归属为 502＋{@code ECOS-AI-502}，改它反而违背冻结值。</p>
     *
     * <p><b>#142/B4-3e-40 属主侧首批后现值＝9 个持有者／27 枚腿</b>：runtime-core
     * {@code SecurityCryptoEgress}（曾为 ENTRY_FALLBACK_500／2 枚）整行退出本面——它的两枚腿已改抛
     * 携带 503 的 {@code DataBridgeException}，不再是 ISE 站点。承载面 15 枚不变，落兜底面
     * 14→12（data-engine 3／11 ＋ kb 1／1）。</p>
     *
     * <p><b>#142/B4-3e-41 属主侧第二批后现值＝8 个持有者／25 枚腿</b>：workspace
     * {@code DcchengClient}（曾为 {@code WorkspaceExceptionHandler.java}／2 枚）整行退出本面——
     * 它的两枚腿改抛本域 503 载体，经 {@code handleDomain} 而非 {@code handleUnexpected} 渲染。
     * 承载面 15→<b>13</b>（全在 ai），落兜底面 12 不变。</p>
     *
     * <p><b>#142/B4-3e-42 属主侧第三批后现值＝7 个持有者／24 枚腿</b>：kb
     * {@code KbEntityInstanceExtractionService}（曾为 {@code ENTRY_FALLBACK_500}／1 枚）整行退出本面——
     * 它的那枚腿改抛本域 503 载体，由 {@code KbErrorAdvice.onDomain}（无裸 Exception 兜底、按异常携带状态渲染）
     * 接住，不再落 gateway 入口兜底。落兜底面 12→<b>11</b>（全在 data-engine 3 个持有者），承载面 13 未动。</p>
     */
    private static final Map<String, String> CARRIER_BASELINE = Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/agent/mesh/knowledge/Neo4jQueryService.java", "AiErrorAdvice.java"),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/capability/ModelCapabilityExecutorImpl.java", "AiErrorAdvice.java"),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/AgentTemplateService.java", "AiErrorAdvice.java"),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/ai/service/ToolExecutorService.java", "AiErrorAdvice.java"),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datadescription/discovery/impl/DataDescriptionDiscoveryImpl.java",
                    "ENTRY_FALLBACK_500"),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java",
                    "ENTRY_FALLBACK_500"),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/writechannel/OlapEngineResolver.java", "ENTRY_FALLBACK_500"));

    /**
     * 承载者实际渲染的状态行（键＝advice 文件名；锚点逐条钉在
     * {@link #carriersRenderTheStatusTheyArePinnedFor()}，禁凭「有 advice」推断状态）。
     *
     * <p>500 这一枚就是 {@code WorkspaceExceptionHandler.handleUnexpected} 的
     * {@code build(500, "INTERNAL", …)}——<b>被承载≠已改对</b>：它的状态行仍是 500，
     * 且 {@code "INTERNAL"} 不是机读码（D-B4-55 同族，单独在册）。</p>
     *
     * <p><b>#142/B4-3e-41 后的读法</b>：workspace 已无 ISE 腿映射到该 advice（{@code DcchengClient}
     * 两枚改抛本域 503 载体，走 {@code handleDomain}），本行不再被 {@code carrierFace()} 查到；
     * 保留它是因为 {@link #carriersRenderTheStatusTheyArePinnedFor()} 仍按源码锚点钉住该 advice
     * 的 500＋"INTERNAL" 兜底形态——兜底还在，只是不再有腿落在它上面。</p>
     */
    private static final Map<String, Integer> CARRIER_STATUS_BASELINE = Map.of(
            "AiErrorAdvice.java", 502,
            "WorkspaceExceptionHandler.java", 500);

    /** 按承载者聚合的腿数（禁算式推出；#142/B4-3e-39 探测实扫，#142/B4-3e-40 属主侧首批后现值，
     *  #142/B4-3e-41 属主侧第二批后现值＝承载 13／兜底 12，
     *  <b>#142/B4-3e-42 属主侧第三批后现值＝承载 13／兜底 11</b>——kb 那枚脱离 ISE 站点面，
     *  承载面一枚未动，全在 ai）。 */
    private static final int CARRIED_LEGS_BASELINE = 13;
    private static final int ENTRY_FALLBACK_LEGS_BASELINE = 11;

    /**
     * <b>真欠账面</b>＝落入口兜底的腿＋被承载后仍回 500 的腿。
     *
     * <p>#142/B4-3e-39 实扫为 14＋2＝16；<b>#142/B4-3e-40 首批收掉 runtime-core
     * {@code SecurityCryptoEgress} 2 枚</b>（改抛 {@code DataBridgeException(503, …,
     * "ECOS-SYS-503: …")}）后现值 <b>12＋2＝14</b>。<b>#142/B4-3e-41 第二批再收掉 workspace
     * {@code DcchengClient} 2 枚</b>（改抛本域 503 载体，经 {@code handleDomain} 渲染 503）后
     * 现值 <b>12＋0＝12</b>：承载面剩下的 13 枚全在 ai，已由分册 06 D.5 冻结为 502，不是欠账。
     * <b>#142/B4-3e-42 第三批再收掉 kb {@code KbEntityInstanceExtractionService} 1 枚</b>
     * （改抛 {@code KbErrorCodes.ONTOLOGY_BASE_UNAVAILABLE.exception(…)}＝503＋ECOS-KB-024，
     * 经 {@code KbErrorAdvice.onDomain} 按携带状态渲染）后现值 <b>11＋0＝11</b>。
     * 属主侧每批改码必须同批下调本值与本面基线，两头都红才算动过。</p>
     */
    private static final int OWNER_SIDE_DEBT_LEGS_BASELINE = 11;

    private static volatile List<Advice> cachedAdvices;

    private static List<Advice> advices() {
        List<Advice> cached = cachedAdvices;
        if (cached != null) {
            return cached;
        }
        List<Advice> out = new ArrayList<>();
        for (Scanned s : backendMainSources()) {
            if (!ADVICE_ANNOTATION.matcher(s.code()).find()) {
                continue;
            }
            out.add(new Advice(s.relPath(), adviceSelector(s.code()),
                    hasBareExceptionFallback(s.code())));
        }
        out.sort((a, b) -> a.relPath().compareTo(b.relPath()));
        cachedAdvices = List.copyOf(out);
        return cachedAdvices;
    }

    /** 取 advice 注解的圆括号参数表并归一空白；注解名后无圆括号＝全局 advice，返回 {@code GLOBAL}。 */
    private static String adviceSelector(String code) {
        Matcher m = ADVICE_ANNOTATION.matcher(code);
        if (!m.find()) {
            return fail("本方法只应在含 @ControllerAdvice 的文件上调用: 扫描器失步");
        }
        int i = m.end();
        while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
            i++;
        }
        // 注解名之后第一个非空白字符必须是 '('；否则视为无参数注解（全局作用域）。
        // 若不是 '(' 也不是换行后的元素（例如被截断），保守返回 GLOBAL 而不 fail——
        // 承载面宁可把「读不懂」判成「无作用域」，也不虚判成「已承载」。
        if (i >= code.length() || code.charAt(i) != '(') {
            return "GLOBAL";
        }
        String args = argumentText(code, i + 1);
        return args.replaceAll("\\s+", " ").trim();
    }

    /** 该 advice 是否声明了能接住任意异常（含 IllegalStateException）的裸 {@code Exception.class} 兜底。 */
    private static boolean hasBareExceptionFallback(String code) {
        Matcher m = EXCEPTION_HANDLER_ANNOT.matcher(code);
        while (m.find()) {
            String args = argumentText(code, m.end());
            if (BARE_EXCEPTION_CLASS.matcher(args).find()) {
                return true;
            }
        }
        return false;
    }

    private static String packageOf(String relPath) {
        Matcher m = PACKAGE_DECL.matcher(codeOf(relPath));
        if (!m.find()) {
            return fail("读不到 package 声明，承流归属不得默认判成兜底: " + relPath);
        }
        return m.group(1);
    }

    /** 该 advice 的作用域是否覆盖给定包；{@code assignableTypes}（无引号包名）形态不参与判定。 */
    private static boolean covers(Advice a, String pkg) {
        if ("GLOBAL".equals(a.selector())) {
            return true;
        }
        Matcher q = QUOTED_SELECTOR.matcher(a.selector());
        while (q.find()) {
            String scope = q.group(1);
            if (pkg.equals(scope) || pkg.startsWith(scope + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一条腿所在包的承流归属：能接住任意异常的作用域内 advice（多个以 {@code +} 连接文件名）
     * 或 {@code ENTRY_FALLBACK_500}（只能落 gateway 入口兜底，本文件第 6 案钉住的 500）。
     */
    private static String carrierOf(String relPath) {
        String pkg = packageOf(relPath);
        List<String> carriers = new ArrayList<>();
        for (Advice a : advices()) {
            if (!"GLOBAL".equals(a.selector()) && !a.selector().contains("assignableTypes")
                    && a.hasExceptionFallback() && covers(a, pkg)) {
                carriers.add(Path.of(a.relPath()).getFileName().toString());
            }
        }
        if (carriers.isEmpty()) {
            return "ENTRY_FALLBACK_500";
        }
        carriers.sort(String::compareTo);
        return String.join("+", carriers);
    }

    private static Map<String, String> carrierFace() {
        Map<String, String> out = new TreeMap<>();
        List<Leg> legs = new ArrayList<>();
        legs.addAll(dependencyLegs());
        legs.addAll(unreadableLegs());
        for (Leg l : legs) {
            out.putIfAbsent(l.relPath(), carrierOf(l.relPath()));
        }
        return out;
    }

    /**
     * 某文件在 DEPENDENCY＋UNREADABLE 两面里的腿数（不含同文件的 ENV_ALGO／OTHER 腿——
     * {@code BaseJdbcAdapter} 正是 1 枚依赖腿＋8 枚读不出腿的混合持有者）。
     */
    private static int countOf(String relPath) {
        int n = 0;
        for (Leg l : dependencyLegs()) {
            if (l.relPath().equals(relPath)) {
                n++;
            }
        }
        for (Leg l : unreadableLegs()) {
            if (l.relPath().equals(relPath)) {
                n++;
            }
        }
        if (n == 0) {
            return fail("承流面出现非依赖面持有者，计数辅助失步: " + relPath);
        }
        return n;
    }

    private static String renderStrings(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append("    ").append(k).append(" = ").append(v).append('\n'));
        return sb.length() == 0 ? "    (空)\n" : sb.toString();
    }

    /**
     * 字符串值面的逐文件双向对账（承流归属是文本，不是腿数）。
     *
     * <p>三面漂移各有独立文案：基线里有而实扫没有＝持有者消失；值变了＝归属漂移；
     * 实扫里有而基线没有＝未登记持有者。任何一条都必须显式改注释＋同批重测，
     * 与腿数面（{@link #assertPinnedBidirectionally}）同一套纪律。</p>
     */
    private static void assertStringFacePinnedBidirectionally(String faceName,
                                                              Map<String, String> actual,
                                                              Map<String, String> expected) {
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String got = actual.get(e.getKey());
            if (got == null) {
                diff.add(faceName + " 面登记的持有者已从实扫消失（陈旧读数＝下一批人按虚高余面排产）: "
                        + e.getKey() + " 基线 " + e.getValue());
            } else if (!got.equals(e.getValue())) {
                diff.add(faceName + " 面归属漂移（承载关系变了必须重测后同批改）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, String> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diff.add(faceName + " 面出现未登记持有者（豁免与欠账都必须点名）: "
                        + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        assertTrue(diff.isEmpty(), faceName + " 面失真（登记基线如下）:\n"
                + String.join("\n", diff) + "\n  登记面:\n" + renderStrings(expected)
                + "  实扫面:\n" + renderStrings(actual));
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("承载 advice 面逐文件双向等值：作用域／裸 Exception 兜底任一改动即红")
    void adviceInventoryIsPinnedBidirectionally() {
        Map<String, String> actual = new TreeMap<>();
        for (Advice a : advices()) {
            actual.put(a.relPath(), (a.hasExceptionFallback() ? "EXCEPTION_FALLBACK" : "NO_EXCEPTION_FALLBACK")
                    + " | " + a.selector());
        }
        assertStringFacePinnedBidirectionally("ADVICE", actual, ADVICE_BASELINE);
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("DEPENDENCY＋UNREADABLE 每条腿的承流归属逐文件双向等值（禁批量定罪）")
    void dependencyLegsCarriersArePinnedPerFile() {
        assertStringFacePinnedBidirectionally("CARRIER", carrierFace(), CARRIER_BASELINE);
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("承流两面腿数钉死：已承载 vs 落兜底，二者之和＝依赖面腿数（各自独立钉值）")
    void carriedVersusFallbackLegCountsArePinned() {
        int carried = 0;
        int fallback = 0;
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            int n = countOf(e.getKey());
            if ("ENTRY_FALLBACK_500".equals(e.getValue())) {
                fallback += n;
            } else {
                carried += n;
            }
        }
        List<String> drift = new ArrayList<>();
        accountFor(drift, "已承载 advice 腿数", CARRIED_LEGS_BASELINE, carried,
                "＝有腿在承载面进出未登记；「该改 503」的批量定罪不能再靠减法得出");
        accountFor(drift, "落入口兜底腿数", ENTRY_FALLBACK_LEGS_BASELINE, fallback,
                "＝真正的属主侧欠账面，漂了就必须重测后同批改");
        accountFor(drift, "两面腿数之和",
                DEPENDENCY_FACE_BASELINE + UNREADABLE_FACE_BASELINE, carried + fallback,
                "＝承流面没有覆盖全部依赖腿（有持有者读不到包名或被静默漏掉）");
        assertTrue(drift.isEmpty(), "承流账失真:\n" + String.join("\n", drift));
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("真欠账面＝落兜底腿＋承载后仍 500 腿：属主侧批的排产依据，禁批量定罪")
    void ownerSideDebtLegsAreMeasuredFromCarriersRenderedStatus() {
        int debt = 0;
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            int n = countOf(e.getKey());
            if ("ENTRY_FALLBACK_500".equals(e.getValue())) {
                debt += n;
                continue;
            }
            Integer status = CARRIER_STATUS_BASELINE.get(e.getValue());
            if (status == null) {
                fail("承载者没有登记渲染状态行读数，不得凭「有 advice」推断已改对: "
                        + e.getValue() + "（持有者 " + e.getKey() + "）");
                return;
            }
            if (status == 500) {
                debt += n;
            }
        }
        assertEquals(OWNER_SIDE_DEBT_LEGS_BASELINE, debt,
                "属主侧欠账面漂走：落兜底或被承载后仍回 500 的腿发生增减。"
                        + "已按域内冻结表落 5xx＋机读码的腿（ai 13 枚→502 ECOS-AI-502）不是欠账，"
                        + "把它们算进欠账面＝#142/B4-3e-38 那句未实测的批量定罪复发");
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("承载者实际渲染的状态行有锚可回读：advice 在位≠状态行正确")
    void carriersRenderTheStatusTheyArePinnedFor() {
        String aiAdvice = codeOf(AI_ADVICE);
        assertTrue(aiAdvice.contains("@ExceptionHandler(Exception.class)"),
                "AiErrorAdvice 的裸 Exception 兜底被摘＝ai 域依赖腿改落网关 500，承流面整体作废");
        String mapper = codeOf(AI_ERROR_MAPPER);
        assertTrue(mapper.contains("case TOOL, PERSISTENCE, GENERIC -> AiErrorClass.TOOL_BACKEND_ERROR;"),
                "GENERIC 面兜底不再归 TOOL_BACKEND_ERROR＝承载状态行变了，本门 CARRIER 基线必须重测");
        String aiClass = codeOf(AI_ERROR_CLASS);
        assertTrue(aiClass.contains("TOOL_BACKEND_ERROR(\"ECOS-AI-502\", 502"),
                "分册 06 D.5 的 ECOS-AI-502 冻结值被改＝域内归属变了（该值冻结，禁改写）");

        String ws = codeOf(WS_ADVICE);
        int idx = ws.indexOf("handleUnexpected");
        assertTrue(idx > 0, "WorkspaceExceptionHandler.handleUnexpected 不在位＝承流面变了");
        String body = ws.substring(idx, Math.min(ws.length(), idx + 400));
        assertTrue(body.contains("build(500, \"INTERNAL\""),
                "workspace 承载者不再回 500＋\"INTERNAL\"＝CARRIER 基线须重测；"
                        + "注意 \"INTERNAL\" 不是机读码面（D-B4-55 同族），该条单独在册");

        String kb = codeOf(KB_ADVICE);
        assertFalse(hasBareExceptionFallback(kb),
                "KbErrorAdvice 新增了裸 Exception 兜底＝kb 域依赖腿的承载者换了，不能再按落网关兜底判");
    }

    /**
     * <b>#142/B4-3e-42 属主侧第三批的渲染取证</b>（差距 D-B4-71）：本门其余判据只看得到「ISE 站点少了一枚」，
     * 那是<b>脱离判据面</b>的证据，不等于「改抛的类型真被接住、且真渲染成 503」。#142/B4-3e-41 的教训正是
     * 属主侧改了抛出类型、控制器的宽 {@code catch (Exception)} 仍把它折成 {@code badRequest}——那样账面
     * 一样变好，HTTP 面却什么都没变＝假绿。故本案把 kb 这一枚的<b>整条承流链</b>按源码锚点钉死：
     * 码表冻结值 → advice 显式接住并按携带状态渲染 → 属主腿改抛形态 → 错号不得复活 →
     * 放行腿在位 → 另一条 HTTP 路径（E3 同步 dry-run）不得新增长 catch。
     */
    @Test
    void kbOwnerSideLegRendersCarriedUnavailableStatusInsteadOfFallback() {
        String codes = codeOf(KB_ERROR_CODES);
        assertTrue(codes.contains("ONTOLOGY_BASE_UNAVAILABLE = new KbError(503, \"ECOS-KB-024\")"),
                "kb 本体寻址 503 条目（ECOS-KB-024）被改号或改状态＝本批属主腿的渲染值变了，承流面须重测");

        String advice = codeOf(KB_ADVICE);
        int onDomain = advice.indexOf("@ExceptionHandler(DomainException.class)");
        assertTrue(onDomain > 0,
                "KbErrorAdvice 不再显式接住本域 DomainException＝属主腿改抛后没人接，503 仍会落网关 500 兜底");
        String onDomainBody = advice.substring(onDomain, Math.min(advice.length(), onDomain + 700));
        assertTrue(onDomainBody.contains("DataBridgeException.resolveHttpStatus(ex.getHttpStatus())")
                        && onDomainBody.contains("ResponseEntity.status(status)"),
                "onDomain 不再按异常携带状态渲染＝机读码在位而状态行回落固定值，本批 503 承诺作废");

        String svc = codeOf(KB_EXTRACTION_SERVICE);
        assertTrue(svc.contains("throw KbErrorCodes.ONTOLOGY_BASE_UNAVAILABLE.exception("),
                "ontologyBase() 的寻址不可用腿不再抛本域 503 载体＝属主侧改码被回退");
        assertFalse(svc.contains("ECOS-KB-404"),
                "错号 ECOS-KB-404 复活：该号不在分册 04 D.5 冻结表、全仓零引用，且在 message 前缀冒充机读码（D-B4-55 同族）");

        String ingest = codeOf(KB_INGEST_CONTROLLER);
        int pass = ingest.indexOf("catch (KbErrorCodes.DomainException e) {");
        assertTrue(pass > 0,
                "previewBuild 的 503 放行腿被摘＝不可用态重新被同方法宽 catch 折成 internalError（HTTP 200＋code -1）");
        assertTrue(ingest.substring(pass, Math.min(ingest.length(), pass + 400)).contains("throw e;"),
                "放行腿不再原样抛出＝折叠面复活");

        String extract = codeOf(KB_EXTRACT_CONTROLLER);
        int trigger = extract.indexOf(
                "extractionService.extract(req.getOntologyId(), jobId, incremental, true);");
        assertTrue(trigger > 0, "E3 同步 dry-run 调用腿改了形态＝该端点的承流判定须重测");
        String triggerWindow = extract.substring(trigger, Math.min(extract.length(), trigger + 400));
        assertTrue(triggerWindow.contains("catch (ValidationException e)")
                        && !triggerWindow.contains("catch (Exception e)"),
                "E3 同步路径新增长 catch＝本域 503 会在控制器层被折叠，本批承流承诺作废");
    }
}
