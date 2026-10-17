package com.chinacreator.gzcm.workspace.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RLS「执行契约」跨模块键名与形态门禁（#142/B4-3c-2 起，差距 D-B4-17；
 * #142/B4-3d-5 按差距 D-B4-24 改判为「键面单源 + 委托读数」口径）。
 *
 * <p><b>缺陷本体的两代形态</b>：</p>
 * <ul>
 *   <li>D-B4-17（收口前）：生产者应答里根本没有 {@code whereClause}，三消费者按不存在的键
 *       读数取到 {@code null} 后返回空串＝「无行级限制」，页面/本体/AI 三通道行过滤恒等于关闭；</li>
 *   <li>D-B4-24（本批改判）：字段补上之后出现<b>同源三个面 + 四套手抄解析</b>——
 *       data-engine 读表达面 {@code predicate.predicateTemplate} + {@code :name}，
 *       另三通道各抄一份 {@code whereClause} + {@code ?} 的解析（剥括号、arity、拒绝归一各有版本）。
 *       任何一份副本漂了，绕过 {@code RlsPredicateRenderer} 的拒绝归一只是时间问题。</li>
 * </ul>
 *
 * <p><b>收口后的架构承诺</b>（本门禁逐条钉住，缺一条即红）：
 * ① 执行契约键面与片段字面量只存在于 {@code RlsExecutionContract}（runtime 底座）一处；
 * ② 四通道（页面 workspace／本体 ontology／AI ai-engine／数据 data-engine）读数一律经
 * {@code RlsExecutionContract.read}，生产者（渲染器 + {@code /rls/apply} + {@code /decision}）
 * 写数一律经 {@code KEY_*} 常量；
 * ③ 无参数绑定通道的两引擎必须再叠一层 {@code rejectForUnbindableChannel}；
 * ④ fail-open 形态与「读表达面拼 SQL」的形态在各自扫描面内零残留 ——
 * <b>#142/B4-3d-7（差距 D-B4-24b）把第 ④ 条从"键名"升级为"取面"</b>：表达面禁读面同时覆盖
 * {@code get("predicateTemplate")} 与 {@code get(RlsExecutionContract.KEY_PREDICATE_TEMPLATE)}
 * 两种形态（前者是 D-B4-24 原形，后者是键名收口后新出现的"合规键名 + 错误面"形态）。
 * 判据的两种形态各有"缺陷原形必须命中"的自检，见
 * {@link #gateItselfDetectsTheKeyFaceDriftShape()}。</p>
 *
 * <p>反假绿纪律：扫描根解析到 0 文件即判失败；每条静态判据都配一条
 * 「缺陷原形必须命中、收口形状不得误报」的自检
 * （{@link #gateItselfDetectsTheHistoricalFailOpenShape()}、
 * {@link #gateItselfDetectsTheKeyFaceDriftShape()}）；
 * 只扫已知清单＝把缺陷清单当穷举（D-B4-24 的教训），故键面扫描面为全后端
 * {@code src/main/java}（排 {@code target/} 与 {@code archive/}）。</p>
 *
 * <p>诚实边界一：本门禁只作用于<b>生产源码</b>。测试源码允许写字面量（夹具必须能伪装成生产者的
 * 应答形态，否则单源一改就要连带改十几个夹具），夹具漂移由各自的契约用例
 * （{@code RlsFilterContractTest}／{@code AiRlsFailClosedTest}／{@code OntologyRlsFailClosedTest}）承担。</p>
 *
 * <p>诚实边界二：{@code denyIfEmpty} 与 {@code predicate} 两键与 CLS 列面／本体关系谓词面<b>同名</b>
 * （{@code ClsController} 的 {@code denyIfEmpty}、{@code OntologyCandidateService} 的 {@code predicate}
 * 都是各自契约的合法键），故不入<b>本门禁的全后端</b>禁面清单；本门禁只禁五个 RLS 执行面专有键与
 * 表达面的 {@code predicateTemplate}（后者只禁消费者树，生产者的策略写请求入参就是该形态）。
 * {@code denyIfEmpty} 在导出消费侧的禁读由范围更窄的
 * {@code ExportDecisionContractTest#expressionFaceReads} 对<b>单文件</b> {@code ExportDecision.java}
 * 钉住——两门不是同一口径的重复，禁面清单不同是因为同名键在别的契约里合法，
 * 别把它"统一"成全后端禁令（那会误伤 CLS）。</p>
 */
@DisplayName("RLS 执行契约：键面单源 + 四通道委托读数 + fail-open 形态零残留")
class RlsExecutionContractGateTest {

    /** 键面与片段常量的唯一归属文件（runtime 底座，JDK-only）。 */
    private static final String CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/rls/RlsExecutionContract.java";
    private static final String RENDERER_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/rls/RlsPredicateRenderer.java";
    private static final String RLS_APPLY_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/RowLevelSecurityServiceImpl.java";
    private static final String DECISION_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/decision/SecurityDecisionService.java";

    /** 四个消费者的 RLS 读数位（相对仓根，只增不减——第五个消费者出现就要加进来）。 */
    private static final List<String> CONSUMER_RLS_SOURCES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/RlsFilter.java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java",
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/controller/QueryController.java");

    /** 无参数绑定通道的两引擎读数位（必须叠加通道级拒绝）。 */
    private static final List<String> UNBINDABLE_CHANNEL_SOURCES =
            List.of(CONSUMER_RLS_SOURCES.get(1), CONSUMER_RLS_SOURCES.get(2));

    /** 消费者主源码树：具名读数位与表达面禁读面的扫描根（只增不减）。 */
    private static final List<String> CONSUMER_MAIN_TREES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java",
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java");

    /** 键面字面量与 fail-open 形态的全后端扫描根（防「第四消费者」漏项，见类注释）。 */
    private static final String WHOLE_BACKEND_MAIN_ROOT = "ecos_backend";

    /** 执行契约五个键（与 {@code Rendered.toMap()} 一一对应）。 */
    private static final List<String> CONTRACT_KEYS =
            List.of("whereClause", "whereParams", "unresolvedBindings", "noPolicyMatched", "rlsDenyReason");

    /** 表达面键在<b>消费者树</b>内的禁读面（差距 D-B4-24 的原始分裂源头）。 */
    private static final String TEMPLATE_FACE_LITERAL = "\"predicateTemplate\"";

    /**
     * 「取到 null 就返回空串」的原始缺陷形态（#142/B4-3c 收口前的生产代码逐字形状）。
     *
     * <p>必须是<b>跨行窗口</b>而非单行正则：当年的写法是两行
     * （先 {@code Object whereObj = data.get("whereClause");}，后
     * {@code return whereObj == null ? "" : …;}），单行正则对真实缺陷恒不命中＝假门禁。</p>
     */
    private static final Pattern FAIL_OPEN_SHAPE = Pattern.compile(
            "get\\(\\s*\"whereClause\"\\s*\\)[\\s\\S]{0,160}\\?\\s*\"\"\\s*:");

    /**
     * 「读表达面模板」的缺陷形态（D-B4-24 的原始形状 ＋ #142/B4-3d-7 的新形态 D-B4-24b）。
     *
     * <p>B4-3d-5 把键名收进单源常量后，本模式的旧版本只认
     * {@code get("predicateTemplate")}，于是「改读 {@code get(RlsExecutionContract
     * .KEY_PREDICATE_TEMPLATE)}」这条路径变成门禁盲区——键名合规了，<b>面</b>还是错的：
     * 表达面不含渲染器的拒绝归一（零命中在这里是 {@code 1=1}，在执行契约面才是
     * {@code 1=0}＋理由），消费者读它分态即绕过拒绝归一（D-B4-24b 的 workspace
     * {@code ExportDecision} 形状）。故两种形态并列命中；只准加面不准减面。</p>
     */
    private static final Pattern TEMPLATE_SPLICE_SHAPE = Pattern.compile(
            "get\\(\\s*(?:RlsExecutionContract\\.)?KEY_PREDICATE_TEMPLATE\\s*\\)"
                    + "|get\\(\\s*\"predicateTemplate\"\\s*\\)");

    @Test
    void contractClassOwnsTheKeyFaceAndClauseConstants() throws IOException {
        String src = readRepoFile(CONTRACT_SRC);

        for (String key : CONTRACT_KEYS) {
            assertTrue(src.contains("\"" + key + "\""),
                    "单源键面缺键（生产者与消费者共用的键名只能在这一处定义）: " + key);
        }
        assertTrue(src.contains(TEMPLATE_FACE_LITERAL), "单源缺表达面键 predicateTemplate");
        assertTrue(src.contains("\"predicate\""), "单源缺表达面段键 predicate");
        assertTrue(src.contains("= \"1=0\""), "拒绝片段字面量只能在单源里出现一次");
        assertTrue(src.contains("= \"1=1\""), "恒真片段字面量只能在单源里出现一次");
        // 四态齐备是「不可用 ≠ 无权限」（详设-01 §4.5.4）在类型层成立的前提
        for (String state : List.of("ALLOW_ALL", "FILTERED", "DENIED", "UNAVAILABLE")) {
            assertTrue(src.contains(state), "契约四态缺 " + state + "：分态判据退化回布尔");
        }
        // 底座不得反向依赖引擎（否则 common-api 变成第 N 个消费者）
        String code = stripComments(src);
        assertFalse(code.contains("import com.chinacreator.gzcm.engine"),
                "D-B4-24 违规: 契约类反向依赖引擎模块，单源不再中立");
        assertFalse(code.contains("import org.springframework"),
                "D-B4-24 违规: 契约类引入 Spring，JDK-only 承诺被破（四引擎无法同版本复用）");
    }

    @Test
    void executionContractKeyLiteralsAppearNowhereElseInMainSource() throws IOException {
        Path contractAbs = repoFile(CONTRACT_SRC).toAbsolutePath().normalize();
        Scan scan = scanBackend(code -> executionKeyViolations(code),
                file -> !file.toAbsolutePath().normalize().equals(contractAbs));

        assertScanIsReal(scan);
        assertTrue(scan.seenContractFile, "扫描面里没有契约类自身＝判据空转");
        assertTrue(scan.violations.isEmpty(),
                "D-B4-24 违规: 执行契约键面在单源之外被手抄（同键多套读数即漂移空间）: " + scan.violations);
    }

    @Test
    void fourConsumersReadThroughTheSingleSource() throws IOException {
        for (String rel : CONSUMER_RLS_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;"),
                    "D-B4-24 违规: 消费者未依赖单源契约: " + rel);
            assertTrue(src.contains("RlsExecutionContract.read(")
                            || src.contains("RlsExecutionContract.Execution"),
                    "D-B4-24 违规: 消费者自建读数／形态而非委托单源: " + rel);
        }
    }

    @Test
    void unbindableChannelsStackTheirOwnRefusal() throws IOException {
        // 响应侧内存过滤（ontology）与 OAG 只传上下文（ai）都没有绑参腿：
        // 单源四道不变量之外必须再叠一层 rejectForUnbindableChannel，
        // 否则含 ? 的片段会被当成「能用」下传（IR05 违例）。
        for (String rel : UNBINDABLE_CHANNEL_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("rejectForUnbindableChannel("),
                    "D-B4-24 违规: 无参数绑定通道的消费者未叠加通道级拒绝: " + rel);
        }
    }

    @Test
    void producerWritesContractKeysThroughConstants() throws IOException {
        String renderer = stripComments(readRepoFile(RENDERER_SRC));
        int toMapAt = renderer.indexOf("public Map<String, Object> toMap()");
        assertTrue(toMapAt >= 0, "Rendered.toMap() 不存在，应答键失去单源出口");
        String toMapBody = renderer.substring(toMapAt,
                renderer.indexOf('\n', renderer.indexOf("}", toMapAt)));
        for (String key : List.of("KEY_WHERE_CLAUSE", "KEY_WHERE_PARAMS",
                "KEY_UNRESOLVED_BINDINGS", "KEY_NO_POLICY_MATCHED")) {
            assertTrue(toMapBody.contains("RlsExecutionContract." + key),
                    "D-B4-24 违规: 渲染器未用单源常量写应答键: " + key);
        }
        assertTrue(toMapBody.contains("RlsExecutionContract.KEY_RLS_DENY_REASON"),
                "拒绝原因必须随应答外发（禁消费者折叠成「无数据」）");

        String apply = stripComments(readRepoFile(RLS_APPLY_SRC));
        assertTrue(apply.contains("RENDERER.render("),
                "D-B4-17 违规: /rls/apply 未调用渲染器（渲染器在位但无人调用＝假实现）");
        assertTrue(apply.contains("result.putAll(rendered.toMap())"),
                "D-B4-17 违规: /rls/apply 应答未并入执行契约（whereClause/whereParams 不会出现在响应里）");
        assertTrue(apply.contains("RlsExecutionContract.KEY_PREDICATE_SEGMENT"),
                "D-B4-24 违规: /rls/apply 的表达面段键仍手抄");

        String decision = stripComments(readRepoFile(DECISION_SRC));
        assertTrue(decision.contains("RlsExecutionContract.KEY_WHERE_CLAUSE"),
                "D-B4-24 违规: /decision 的 rls 段未与 /rls/apply 用同一份键面（消费者将被迫读两套形态）");
    }

    @Test
    void failOpenShapeIsNowhereInConsumers() throws IOException {
        Scan scan = scanBackend(code -> {
            Matcher m = FAIL_OPEN_SHAPE.matcher(code);
            return m.find() ? List.of("fail-open 形态 ← " + m.group()) : List.of();
        }, file -> true);

        assertScanIsReal(scan);
        assertTrue(scan.violations.isEmpty(),
                "D-B4-17 fail-open 形态回潮（取不到 whereClause 就当无过滤放行）: " + scan.violations);
    }

    @Test
    void templateFaceIsNotReadInConsumerTrees() throws IOException {
        Scan scan = scanBackend(code -> {
            Matcher m = TEMPLATE_SPLICE_SHAPE.matcher(code);
            return m.find() ? List.of("表达面读数 ← " + m.group()) : List.of();
        }, RlsExecutionContractGateTest::isConsumerTree);

        assertScanIsReal(scan);
        for (String tree : CONSUMER_MAIN_TREES) {
            assertTrue(scan.perKnownTree.getOrDefault(tree, 0) > 0,
                    "具名消费者树在本次扫描里为 0 个文件（树被移动/改名即门禁空转）: " + tree);
        }
        assertTrue(scan.violations.isEmpty(),
                "D-B4-24/D-B4-24b 违规: 消费者另读表达面 predicateTemplate（键名形态不限）——"
                        + "绕过渲染器拒绝归一即第二套分态口径: " + scan.violations);
    }

    /**
     * 门禁自检（反假绿）：静态判据之所以可信，是因为它对<b>缺陷原形</b>确实能变红。
     * 这里把收口前的生产代码逐字形状喂给同一个模式，断言命中——
     * 若有人把正则改成恒不命中的形态（「永远绿」的门禁），本用例先红。
     */
    @Test
    void gateItselfDetectsTheHistoricalFailOpenShape() {
        String preFixShape = stripComments("""
                Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
                return whereObj == null ? "" : whereObj.toString();
                """);
        assertTrue(FAIL_OPEN_SHAPE.matcher(preFixShape).find(),
                "门禁模式对缺陷原形不命中＝假门禁，必须修模式而不是删用例");
        assertFalse(FAIL_OPEN_SHAPE.matcher(stripComments("""
                RlsExecutionContract.Execution exec = RlsExecutionContract.read(data, subject);
                if (!exec.usable()) return RlsExecutionContract.DENY_CLAUSE;
                """)).find(), "收口后的形状不得被误报（否则违例清单里全是噪声）");
    }

    /**
     * 键面判据的自检：把「消费者自抄键名／自读表达面」的历史形状喂给<b>与门禁同一</b>的判据函数，
     * 必须报违例；把收口后的形状喂进去，必须零违例。
     *
     * <p>这里刻意复用 {@link #executionKeyViolations} 与 {@link #templateFaceViolations}
     * 而非另写一份断言，否则自检与真实判据可能各自漂移（自检绿而真实判据恒不命中＝假绿）。</p>
     *
     * <p><b>#142/B4-3d-7 翻正（差距 D-B4-24b，原文保留）</b>：本用例旧形态把
     * 「引单源常量读 {@code KEY_PREDICATE_TEMPLATE}」的形状断成<b>不得误报</b> ——
     * 那是在把门禁钉在"键名合规"上、放行"取面错误"，正是 workspace 侧
     * {@code ExportDecision} 收口前的形状。现在该形状归入违例侧，合规侧改为
     * 只引用执行面常量／整体委托 {@code read} 的形状。翻正后覆盖面只增不减。</p>
     */
    @Test
    void gateItselfDetectsTheKeyFaceDriftShape() {
        String preFixConsumer = stripComments("""
                Object whereObj = data.get("whereClause");
                Object params = data.get("whereParams");
                String tpl = (String) predicate.get("predicateTemplate");
                """);
        List<String> hits = executionKeyViolations(preFixConsumer);
        assertTrue(hits.stream().anyMatch(s -> s.contains("whereClause")),
                "键面判据对缺陷原形的 whereClause 不命中＝假门禁");
        assertTrue(hits.stream().anyMatch(s -> s.contains("whereParams")),
                "键面判据对缺陷原形的 whereParams 不命中＝假门禁");
        assertFalse(templateFaceViolations(preFixConsumer).isEmpty(),
                "表达面判据对字面量形态的缺陷原形不命中＝假门禁");

        // D-B4-24b 的新缺陷形态：键名合规（引单源常量），面却仍是表达面 ⇒ 必须命中
        String keyCompliantWrongFace = stripComments("""
                RlsExecutionContract.Execution exec = RlsExecutionContract.read(data, subject);
                String tpl = String.valueOf(predicate
                        .get(RlsExecutionContract.KEY_PREDICATE_TEMPLATE));
                """);
        assertFalse(templateFaceViolations(keyCompliantWrongFace).isEmpty(),
                "D-B4-24b 违规: 表达面判据对「引常量的读法」不命中＝把真门改成假门（键名合规掩盖取面错误）");
        assertTrue(executionKeyViolations(keyCompliantWrongFace).isEmpty(),
                "键面判据把「引单源常量」误报成手抄（违例清单会全是噪声）: "
                        + executionKeyViolations(keyCompliantWrongFace));

        String postFixConsumer = stripComments("""
                RlsExecutionContract.Execution exec = RlsExecutionContract.read(data, subject);
                return exec.denied() ? RlsExecutionContract.DENY_CLAUSE : exec.clause();
                """);
        assertTrue(executionKeyViolations(postFixConsumer).isEmpty(),
                "收口后的形状被误报（判据与生产码同判即噪声）: " + executionKeyViolations(postFixConsumer));
        assertTrue(templateFaceViolations(postFixConsumer).isEmpty(),
                "收口后的形状被误报: " + templateFaceViolations(postFixConsumer));
    }

    @Test
    void workspaceChannelNeverRevertsToClauseOnlyApi() throws IOException {
        // 页面通道两个 Controller 必须用成对出口 buildRowFilter(；退回只取片段的
        // buildRowFilterCondition( 会让带 ? 的片段悬空（同一缺陷的另一半）
        List<String> pageControllers = List.of(
                "ecos_backend/workspace/workspace-impl/src/main/java/"
                        + "com/chinacreator/gzcm/workspace/controller/ObjectController.java",
                "ecos_backend/workspace/workspace-impl/src/main/java/"
                        + "com/chinacreator/gzcm/workspace/controller/ObjectQLController.java");
        for (String rel : pageControllers) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("buildRowFilter("), rel + " 未走成对下推出口 buildRowFilter(");
            assertFalse(src.contains("buildRowFilterCondition("),
                    rel + " 退回只取片段出口 buildRowFilterCondition(（带 ? 片段将无参数可绑）");
        }
    }

    // ═══════════════ 判据（自检与真实扫描共用） ═══════════════

    /** 执行契约键面判据：五键字面量出现即违例（单源文件由调用方排除）。 */
    private static List<String> executionKeyViolations(String strippedCode) {
        List<String> out = new ArrayList<>();
        for (String key : CONTRACT_KEYS) {
            if (strippedCode.contains("\"" + key + "\"")) out.add("键面字面量重复定义: " + key);
        }
        return out;
    }

    /** 表达面判据：消费者树内读表达面键即违例（字面量形态与单源常量形态都算，见 {@link #TEMPLATE_SPLICE_SHAPE}）。 */
    private static List<String> templateFaceViolations(String strippedCode) {
        Matcher m = TEMPLATE_SPLICE_SHAPE.matcher(strippedCode);
        return m.find() ? List.of("表达面读数: " + m.group()) : List.of();
    }

    // ═══════════════ 辅助 ═══════════════

    /** 一次全后端扫描的证据（计数与违例清单即「扫描面是否真在」的证据）。 */
    private record Scan(int scanned,
                        Map<String, Integer> perKnownTree,
                        boolean seenContractFile,
                        List<String> violations) { }

    /**
     * 走全后端生产源码，对每个经 {@code accept} 放行的文件跑 {@code rule}。
     *
     * <p>排除 {@code target/}（构建产物，命中它不是生产事实）、{@code archive/}（停写遗留）、
     * {@code .git/}；只收 {@code /src/main/java/} 路径下的 {@code .java}（测试夹具允许字面量，
     * 见类注释的诚实边界一）。</p>
     */
    private Scan scanBackend(Function<String, List<String>> rule,
                             Predicate<Path> accept) throws IOException {
        List<String> violations = new ArrayList<>();
        int[] scanned = new int[1];
        boolean[] sawContract = new boolean[1];
        Map<String, Integer> perKnownTree = new LinkedHashMap<>();
        Path root = repoFile(WHOLE_BACKEND_MAIN_ROOT);
        Path contractAbs = repoFile(CONTRACT_SRC).toAbsolutePath().normalize();

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if ("target".equals(name) || "archive".equals(name) || ".git".equals(name)) {
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, BasicFileAttributes attrs) throws IOException {
                String normalized = file.toString().replace('\\', '/');
                if (normalized.endsWith(".java") && normalized.contains("/src/main/java/")) {
                    scanned[0]++;
                    if (file.toAbsolutePath().normalize().equals(contractAbs)) sawContract[0] = true;
                    for (String tree : CONSUMER_MAIN_TREES) {
                        if (normalized.contains(tree)) perKnownTree.merge(tree, 1, Integer::sum);
                    }
                    if (accept.test(file)) {
                        String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                        List<String> hits = rule.apply(code);
                        for (String hit : hits) violations.add(root.relativize(file) + " ← " + hit);
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return new Scan(scanned[0], Map.copyOf(perKnownTree), sawContract[0], List.copyOf(violations));
    }

    /** 扫描面真实性断言（防「0 文件＝0 违例」的假绿）。违例清单由调用方自己断言为空。 */
    private static void assertScanIsReal(Scan scan) {
        assertTrue(scan.scanned() > 0,
                "扫描根解析到 0 个 .java＝假绿，必须失败（实测扫描面: " + WHOLE_BACKEND_MAIN_ROOT + "）");
        // 已知四棵消费者树各自必须扫到文件：防「全后端扫描成功、但已知消费者被移出扫描面」
        for (String tree : CONSUMER_MAIN_TREES) {
            assertTrue(scan.perKnownTree().getOrDefault(tree, 0) > 0,
                    "具名消费者树在本次扫描里为 0 个文件（树被移动/改名即门禁空转）: " + tree);
        }
    }

    private static boolean isConsumerTree(Path file) {
        String normalized = file.toString().replace('\\', '/');
        for (String tree : CONSUMER_MAIN_TREES) {
            if (normalized.contains(tree)) return true;
        }
        return false;
    }

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到源文件/目录（跨模块契约的两侧必须都在，禁跳过校验）: "
                + repoRelative);
    }

    private static String readRepoFile(String repoRelative) throws IOException {
        return Files.readString(repoFile(repoRelative), StandardCharsets.UTF_8);
    }

    /** 剥块注释与行注释（与既有跨模块门禁同口径，避免注释里的示例字符串造成假命中）。 */
    private static String stripComments(String src) {
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("//[^\\r\\n]*", " ");
    }
}
