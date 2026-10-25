package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 「Service 层抛 {@link SecurityException} ⇒ 承流面塌成同一枚 500」面棘轮门
 * （差距 <b>D-B4-85</b> 登记件，首枚收口件＝data-engine {@code AssetService.abacCheck}，#142/B4-3e-55）。
 *
 * <p><b>为什么存在</b>：详设 §4.7.2 要求「不可用 ≠ 无权限 ≠ 空结果」三态各自可寻址，
 * 而 {@code SecurityException} 是 {@code java.lang} 的运行时异常，<b>不属</b>
 * {@code DataBridgeException} 承载体系：主源码面 {@code @ExceptionHandler} 对它的覆盖命中数为
 * <b>0</b>（本门 {@link #noExceptionHandlerCoversSecurityException()} 按实跑钉住该前提），
 * 于是任何 Service 抛出的一律落进 {@code GlobalExceptionHandler.handleAny(Exception)}
 * ＝<b>500＋{@code ECOS-SYS-500}</b>。{@code abacCheck} 原三条腿（Bean 未装配／策略 DENY／
 * 策略评估抛异常）因此三态塌成一枚：该触发运维告警的「裁决机构没答」与该走权限申诉的
 * 「用户没权限」在 HTTP 面完全不可分辨，且都伪装成服务端内部错误。</p>
 *
 * <p><b>判据（本门口径）</b>：主源码面（{@code src/main/java}，排除 {@code target/}／{@code archive/}）
 * 经 {@link SourceCommentStripper} 剥注释后，按文件数 {@code throw new SecurityException(} 抛点枚数，
 * 面集整张比对（不只比总数），只减不增；同时数 {@code @ExceptionHandler(…)} 参数表里出现
 * {@code SecurityException} 的腿数（必须为 0，见下）。</p>
 *
 * <p><b>为什么 0 命中也要钉</b>：抛点面的<b>后果</b>由承流面决定。若哪天有人补一条
 * {@code @ExceptionHandler(SecurityException.class)}，本门登记的折叠结论（＝一律 500）当场失效，
 * 面集基线的归因也要重写。把它做成断言而不是注释，是为了让「前提被换掉」这件事变红，
 * 而不是让后来者凭叙述沿用旧口径。</p>
 *
 * <p><b>双向</b>：面集上升＝新写了一枚会塌成 500 的抛出腿；面集下降＝基线陈旧，
 * 收口批必须同批下调 {@link #BASELINE_FACE}／{@link #BASELINE_SITES} 并在台账留痕。</p>
 *
 * <p><b>基线里两枚未收口项的实读归因</b>（登记为 <b>D-B4-91</b>，<b>#142/B4-3e-60 已逐枚处置</b>，
 * 二者与 {@code abacCheck} 同型不同义——抛的都是「入参/路径越界拒绝」，不是 ABAC 三态）：</p>
 * <ul>
 *   <li>{@code FileToolService}（2 枚，<b>仍留面集</b>）：{@code resolveSafe} 的「路径为空／路径越界」，
 *       但同类 3 处调用点（{@code readFile}／{@code writeFile}／{@code patch}）都在本类内
 *       {@code catch (SecurityException)} 转成 {@code success=false} 的结果对象，
 *       {@code resolveSafe} 全仓也无同类外调用方 ⇒ <b>该两枚逃不到 HTTP 状态行</b>，
 *       本族折叠结论对它不适用。裁决＝<b>不删、不改、不豁免</b>：它是运行期真会抛的活体守卫，
 *       只是被调用方就地消化，删除＝拆掉路径护栏（与死面处置相反）；
 *       「失败折叠成结果对象」是否另有归属，另案在册；</li>
 *   <li>{@code OpaPolicyService}（1 枚，<b>本批整行退出面集</b>）：{@code resolveSafe} 的
 *       「越界访问 policyDir」，两处调用点先经 {@code validatePolicyName} 以
 *       {@code ^[a-zA-Z0-9_-]{1,64}$} 白名单把关，拼出的路径不可能含分隔符或 {@code ..}
 *       ⇒ 越界分支<b>恒不可达</b>＝D-B4-89 同型死面。按 3e-58（{@code RuleGraphService} 死腿）
 *       与 D-B4-77 治理腿的既有口径，<b>死面的唯一治理是删除</b>：本批删掉该不可达分支、
 *       javadoc 留三条实读依据，基线同批 −1。把它改成可抛的活体＝给永不触发的分支刷「已收口」＝假绿。</li>
 * </ul>
 *
 * <p><b>#142/B4-3e-60 同批收口 D-B4-90</b>：{@code abacCheck} 的 {@code checker.check(..)} 若抛
 * {@code PolicyEvaluationException} <b>之外</b>的运行时异常，原先直接漏出落 {@code handleAny}＝500。
 * 本批并入既有不可用承载（{@code catch (RuntimeException)}→503＋{@code ECOS-SEC-501}），
 * 并把恒不触发的 {@code catch (SecurityException)} 死保护腿删除。故收口件锚点的不可用承载
 * 由 2 枚升为 <b>3 枚</b>（未装配／评估异常／未分类运行时异常），见
 * {@link #unexpectedCheckFailureJoinsTheUnavailableCarrier()}。</p>
 *
 * <p><b>已声明盲区</b>：① 判据只看 {@code throw new SecurityException(} 字面构造形态，
 * {@code SecurityException} 的子类（如 {@code AccessControlException}）与「先 new 再 throw」
 * 不在其内——前者在承流面同样无 handler（会落 500），后者在本仓主源码面实扫 0 枚；
 * ② 抛点到 HTTP 的链路是否真被承流，本门不自动判定（{@code FileToolService} 就是靠实读源码
 * 才确认它逃不到状态行），因此面集枚数<b>不等于</b>缺陷枚数；③ 只扫 {@code ecos_backend}，
 * 前端 BFF 侧的等价形态不属本门。</p>
 */
@Timeout(180)
class ServiceThrownSecurityExceptionFaceRatchetTest {

    /** 违例形态：主源码面 {@code throw new SecurityException(} 抛出腿。 */
    private static final Pattern THROW_SECURITY_EXCEPTION =
            Pattern.compile("\\bthrow\\s+new\\s+SecurityException\\s*\\(");

    /** 承流面前提：{@code @ExceptionHandler(参数表)}——参数表里出现 SecurityException 即为覆盖。 */
    private static final Pattern EXCEPTION_HANDLER =
            Pattern.compile("@ExceptionHandler\\s*\\(([^)]*)\\)");

    /** 收口件锚点：承载型抛出腿（自带 httpStatus，由具名 handler 命中）。 */
    private static final Pattern FORBIDDEN_CARRIER =
            Pattern.compile("\\bthrow\\s+new\\s+ForbiddenException\\s*\\(");

    private static final Pattern UNAVAILABLE_CARRIER =
            Pattern.compile("\\bthrow\\s+new\\s+DataBridgeException\\s*\\(\\s*"
                    + "RlsExecutionContract\\s*\\.\\s*SEC_UNAVAILABLE_HTTP");

    /** D-B4-90 锚点判据：{@code catch (SecurityException)} 死保护腿（收口后必须为零）。 */
    private static final Pattern CATCH_SECURITY_EXCEPTION =
            Pattern.compile("\\bcatch\\s*\\(\\s*SecurityException\\b");

    /** D-B4-90 收口件判据：未分类运行时异常的承载腿。 */
    private static final Pattern CATCH_RUNTIME_EXCEPTION =
            Pattern.compile("\\bcatch\\s*\\(\\s*RuntimeException\\b");

    /** D-B4-91② 判据：{@code OpaPolicyService} 的名字白名单＝越界分支恒不可达的<b>唯一</b>前置防线。 */
    private static final Pattern OPA_NAME_WHITELIST =
            Pattern.compile("\\[a-zA-Z0-9_-\\]\\{1,64\\}");

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到／路径变更 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    private static final String DATA_ASSET_SERVICE =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/data/service/AssetService.java";

    private static final String AI_FILE_TOOL_SERVICE =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ai/service/FileToolService.java";

    private static final String SECURITY_OPA_POLICY_SERVICE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/security/service/OpaPolicyService.java";

    /**
     * 面集基线（键＝仓根相对路径，值＝枚数）。
     *
     * <p>#142/B4-3e-55 本批收口后实跑读数＝<b>3 枚／2 文件</b>：收口前同判据＝<b>6 枚／3 文件</b>，
     * {@code AssetService.abacCheck} 的 3 枚（{@code ASSET-040} 拒绝腿／{@code ASSET-041} 评估失败腿／
     * {@code ASSET-042} 未装配腿）改指具名承载 ⇒ 基线 −3。</p>
     *
     * <p><b>#142/B4-3e-60（D-B4-91 逐枚处置）现值＝<b>2 枚／1 文件</b></b>：
     * {@code OpaPolicyService} 整行退出＝其 1 枚越界守卫腿经三条实读判为<b>恒不可达死面</b>
     * （白名单前置＋全仓零外部调用方＋抛型无 handler），按 3e-58／D-B4-77 治理口径<b>删除</b>；
     * {@code FileToolService} 2 枚<b>留在面集内不豁免</b>（运行期真会抛的活体路径护栏，
     * 只被本类调用点就地消化，删除＝拆护栏，与死面处置相反）。归因逐枚见类注释。
     * 与 3e-54 同一立场：<b>「数得到」优先于「数得漂亮」</b>——本行只允许「收掉死面」或「改对承载」
     * 两种减法，且都必须在台账留痕。</p>
     */
    private static final Map<String, Integer> BASELINE_FACE = new TreeMap<>(Map.of(
            AI_FILE_TOOL_SERVICE, 2));

    /** 面集总数基线（必须与 {@link #BASELINE_FACE} 各值之和同号，防拆细后总数对不上）。 */
    private static final int BASELINE_SITES = 2;

    /** 承流面前提基线：主源码面覆盖 {@code SecurityException} 的 {@code @ExceptionHandler} 腿数。 */
    private static final int BASELINE_SECURITY_EXCEPTION_HANDLERS = 0;

    /**
     * 收口件锚点基线：拒绝承载 1 枚（DENY→403）＋不可用承载 3 枚（未装配／评估异常→503，
     * 以及 #142/B4-3e-60 D-B4-90 新并入的「未分类运行时异常」→503）。
     * 3e-55 建门时为 1＋2，本批 +1 是<b>承载覆盖面扩大</b>而非新写违例腿。
     */
    private static final int ANCHOR_FORBIDDEN_CARRIERS = 1;
    private static final int ANCHOR_UNAVAILABLE_CARRIERS = 3;

    /** D-B4-91① 存活归因基线：{@code FileToolService} 本类内消化 {@code SecurityException} 的 catch 腿数。 */
    private static final int FILE_TOOL_SELF_DIGEST_CATCHES = 3;

    /** D-B4-91② 白名单防线基线：{@code OpaPolicyService} 内 {@code {1,64}} 词表出现次数与调用点数。 */
    private static final int OPA_WHITELIST_GUARDS = 1;
    private static final int OPA_RESOLVE_SAFE_SITES = 3;

    // ── 1. 面集棘轮 ─────────────────────────────────

    @Test
    @DisplayName("塌 500 面（主源码 throw new SecurityException）按实跑基线只减不增，面集整张比对且基线陈旧同样变红")
    void securityExceptionThrowFaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        if (scan.totalSites() > BASELINE_SITES) {
            fail("塌 500 抛出腿上升：" + scan.totalSites() + " > 基线 " + BASELINE_SITES
                    + "，面集=" + scan.face + "；安全裁决三态必须各用自带状态行的承载"
                    + "（拒绝＝ForbiddenException→403，不可用＝DataBridgeException→503＋ECOS-SEC-501），"
                    + "禁抛无 handler 覆盖的 SecurityException");
        }
        assertEquals(BASELINE_FACE, scan.face,
                "塌 500 抛点面集与基线不符。新增文件＝新写了一枚会塌成 500 的抛出腿；"
                        + "文件消失或枚数减少＝基线陈旧，收口批必须同批下调 BASELINE_FACE/BASELINE_SITES "
                        + "并在台账留痕。实跑面集=" + scan.face);

        int sum = scan.face.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(BASELINE_SITES, sum, "面集合计必须等于总数基线，否则拆分口径与扫描口径不一致");
    }

    // ── 2. 承流面前提 ───────────────────────────────

    @Test
    @DisplayName("承流面前提见证：主源码面没有任何 @ExceptionHandler 覆盖 SecurityException（故抛出必塌 500）")
    void noExceptionHandlerCoversSecurityException() throws IOException {
        Scan scan = scanMainFace();
        assertEquals(BASELINE_SECURITY_EXCEPTION_HANDLERS, scan.handlerHits,
                "承流面前提已变：主源码面出现 " + scan.handlerHits
                        + " 枚覆盖 SecurityException 的 @ExceptionHandler。"
                        + "此时本门「抛 SecurityException ⇒ 一律 500」的归因失效，"
                        + "必须重裁三态承载并同步订正台账 D-B4-85 的判据表述");
    }

    // ── 3. 收口件锚点（D-B4-85）─────────────────────

    @Test
    @DisplayName("D-B4-85 收口件锚点：AssetService 退出塌 500 面，且 1 枚 403 承载＋3 枚 503 承载在位")
    void collectedAbacCheckCarriesThreeStates() throws IOException {
        Path file = locateRepoRoot().resolve(DATA_ASSET_SERVICE);
        assertTrue(Files.exists(file), "收口件文件不在位：" + file);
        String src = SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));

        assertEquals(0, count(THROW_SECURITY_EXCEPTION, src),
                "收口件回退：AssetService 主源码面再现 throw new SecurityException（三态会塌成 500）");

        int forbidden = count(FORBIDDEN_CARRIER, src);
        assertEquals(ANCHOR_FORBIDDEN_CARRIERS, forbidden,
                "拒绝态承载腿数与凭证不符（回退即红）：实跑 " + forbidden + "，应为 DENY 一枚");

        int unavailable = count(UNAVAILABLE_CARRIER, src);
        assertEquals(ANCHOR_UNAVAILABLE_CARRIERS, unavailable,
                "不可用态承载腿数与凭证不符（回退即红）：实跑 " + unavailable
                        + "，应为「Bean 未装配」＋「策略评估失败」＋「未分类运行时异常（D-B4-90）」三枚，"
                        + "且都取 RlsExecutionContract.SEC_UNAVAILABLE_HTTP 单源（禁本域另立码面）");

        Scan scan = scanMainFace();
        assertFalse(scan.face.containsKey(DATA_ASSET_SERVICE), "收口件仍在塌 500 面上：" + scan.face);
    }

    // ── 5. D-B4-90：check() 未分类运行时异常并入不可用承载 ───

    @Test
    @DisplayName("D-B4-90 收口件锚点：abacCheck 的死保护腿已删，未分类 RuntimeException 并入 503 不可用承载")
    void unexpectedCheckFailureJoinsTheUnavailableCarrier() throws IOException {
        String src = strippedRepoFile(DATA_ASSET_SERVICE);

        assertEquals(0, count(CATCH_SECURITY_EXCEPTION, src),
                "D-B4-77 死面回归：AssetService 主源码面再现 catch (SecurityException) 保护腿——"
                        + "全仓 IAbacPermissionChecker 实现面对该型零抛出，该腿恒不触发；"
                        + "未分类运行时异常已由 catch (RuntimeException) 承载腿接住");
        assertEquals(1, count(CATCH_RUNTIME_EXCEPTION, src),
                "D-B4-90 收口件回退：abacCheck 的 RuntimeException 承载腿缺失或被拆细（实跑应恰 1 枚）");

        // 三枚不可用承载＋一枚拒绝承载＝四态齐在，且都不带本域自定义状态字面量
        assertEquals(ANCHOR_UNAVAILABLE_CARRIERS, count(UNAVAILABLE_CARRIER, src),
                "不可用承载腿数与凭证不符（回退即红）：实跑 " + count(UNAVAILABLE_CARRIER, src)
                        + "，应为「Bean 未装配」＋「策略评估失败」＋「未分类运行时异常」三枚，"
                        + "且都取 RlsExecutionContract.SEC_UNAVAILABLE_HTTP 单源（禁本域另立码面）");
        assertEquals(ANCHOR_FORBIDDEN_CARRIERS, count(FORBIDDEN_CARRIER, src),
                "拒绝态承载腿数与凭证不符（回退即红）：实跑 " + count(FORBIDDEN_CARRIER, src) + "，应为 DENY 一枚");
        assertEquals(0, count(THROW_SECURITY_EXCEPTION, src),
                "收口件回退：AssetService 主源码面再现 throw new SecurityException（三态会塌成 500）");
        assertFalse(src.contains("\"ECOS-SEC-501"),
                "机读码必须取 RlsExecutionContract.SEC_UNAVAILABLE_CODE 单源，禁在本域写死字面量（D-B4-31 同族）");
    }

    // ── 6. D-B4-91：余面两枚的处置逐枚钉住（一分二留）────────

    @Test
    @DisplayName("D-B4-91 处置锚点：OpaPolicyService 死面已删且白名单防线在位；FileToolService 活体守卫不豁免")
    void residualFaceDispositionsArePinnedPerFace() throws IOException {
        Scan scan = scanMainFace();

        // ② 死面：整行退出面集，且退出方式必须是「删掉不可达分支」而不是「改成别的抛出形态」
        assertFalse(scan.face.containsKey(SECURITY_OPA_POLICY_SERVICE),
                "OpaPolicyService 的越界守卫腿又回到塌 500 面：" + scan.face);
        String opa = strippedRepoFile(SECURITY_OPA_POLICY_SERVICE);
        assertEquals(0, count(THROW_SECURITY_EXCEPTION, opa),
                "死面回归：白名单前置使越界分支恒不可达，补回 throw new SecurityException＝给永不触发的"
                        + "分支刷「已收口」＝假绿（与 3e-58 RuleGraphService 死腿同判）");
        assertFalse(opa.contains(".startsWith(policyDir)"),
                "被删的恒不可达比较又长回来了：越界防线的唯一在位者是 validatePolicyName 的白名单");
        assertEquals(OPA_WHITELIST_GUARDS, count(OPA_NAME_WHITELIST, opa),
                "白名单词表是删除后置守卫的<b>前提</b>，它一旦被人改松，上面的「死面」结论当场失效，"
                        + "本案必须红而不是默默继续放行：实跑 " + count(OPA_NAME_WHITELIST, opa));
        assertEquals(OPA_RESOLVE_SAFE_SITES, occurrences(opa, "resolveSafe"),
                "resolveSafe 的站点数（定义 1＋调用 2）是「调用方全在白名单之后」这条归因的证据本体，"
                        + "出现第三个绕过白名单的调用方即红：实跑 " + occurrences(opa, "resolveSafe"));

        // ① 活体：留在面集内不豁免，且「逃不到状态行」的归因必须由本类消化腿背书
        assertEquals(Integer.valueOf(2), scan.face.get(AI_FILE_TOOL_SERVICE),
                "FileToolService 的 2 枚是运行期真会抛的路径护栏，删它＝拆护栏；本批只登记归因、不改码："
                        + scan.face);
        String fileTool = strippedRepoFile(AI_FILE_TOOL_SERVICE);
        assertEquals(FILE_TOOL_SELF_DIGEST_CATCHES, count(CATCH_SECURITY_EXCEPTION, fileTool),
                "「本类内消化」归因的前提已变：readFile/writeFile/patch 的 catch (SecurityException) "
                        + "腿数与基线不符（实跑 " + count(CATCH_SECURITY_EXCEPTION, fileTool) + "）。"
                        + "若有调用点不再就地消化，这 2 枚就真的会逃到 HTTP 状态行，塌 500 结论重新成立");
    }

    // ── 4. 判据自证（能变红 + 不许改过头）───────────

    @Test
    @DisplayName("合成夹具取证：抛 SecurityException 被扫出、改具名承载归零，且注释与 handler 参数表不被误数")
    void fixtureSeparatesThrowCarrierFromHandlerPremise(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("MutableAbacGate.java");
        Files.writeString(bad, """
                class MutableAbacGate {
                    // 收口前形态：throw new SecurityException("ASSET-042: ABAC 不可用");
                    void deny() {
                        throw new SecurityException("拒绝");
                    }
                    @ExceptionHandler({ForbiddenException.class})
                    void h() {
                    }
                }
                """, StandardCharsets.UTF_8);
        String badStripped = SourceCommentStripper.strip(Files.readString(bad, StandardCharsets.UTF_8));
        assertEquals(1, count(THROW_SECURITY_EXCEPTION, badStripped),
                "判据对真实文件失效（读不到／剥不动）＝假绿形态；注释里的旧形态不得计入、代码里的那枚必须计入");
        assertEquals(0, handlerHits(badStripped),
                "夹具的 @ExceptionHandler 参数表只含 ForbiddenException，SecurityException 覆盖命中数必须为 0");

        Path fixed = tmp.resolve("MutableAbacGateFixed.java");
        Files.writeString(fixed, """
                class MutableAbacGateFixed {
                    void deny() {
                        throw new ForbiddenException("ASSET-040: 策略拒绝");
                    }
                    void unavailable() {
                        throw new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                                RlsExecutionContract.SEC_UNAVAILABLE_HTTP, "ECOS-SEC-501: 未获裁决");
                    }
                }
                """, StandardCharsets.UTF_8);
        String fixedStripped = SourceCommentStripper.strip(Files.readString(fixed, StandardCharsets.UTF_8));
        assertEquals(0, count(THROW_SECURITY_EXCEPTION, fixedStripped),
                "具名承载形态必须不在违例面上，否则收口批无法下调基线");
        assertEquals(1, count(FORBIDDEN_CARRIER, fixedStripped), "拒绝承载判据须能认出 ForbiddenException");
        assertEquals(1, count(UNAVAILABLE_CARRIER, fixedStripped), "不可用承载判据须认单源常量形态");

        Path handler = tmp.resolve("MutableHandlerPremise.java");
        Files.writeString(handler, """
                class MutableHandlerPremise {
                    @ExceptionHandler(SecurityException.class)
                    void h() {
                    }
                }
                """, StandardCharsets.UTF_8);
        assertEquals(1, handlerHits(SourceCommentStripper.strip(
                        Files.readString(handler, StandardCharsets.UTF_8))),
                "前提见证判据失效：补了 SecurityException handler 时本门必须能数到它，"
                        + "否则承流面变了也不会变红");

        // D-B4-90／D-B4-91 的两枚 catch 判据活性：代码面数得出、注释面不数（否则第 5／6 案是恒真的假绿）
        Path catches = tmp.resolve("MutableCatchFace.java");
        Files.writeString(catches, """
                class MutableCatchFace {
                    // 注释里的 catch (SecurityException) 不得计入
                    void a() {
                        try {
                        } catch (SecurityException e) {
                            throw e;
                        }
                    }
                    void b() {
                        try {
                        } catch (RuntimeException unexpected) {
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        String catchStripped = SourceCommentStripper.strip(Files.readString(catches, StandardCharsets.UTF_8));
        assertEquals(1, count(CATCH_SECURITY_EXCEPTION, catchStripped),
                "死保护腿判据失效：数不到 catch (SecurityException) 时，第 5 案的「已删」是恒真空判");
        assertEquals(1, count(CATCH_RUNTIME_EXCEPTION, catchStripped),
                "承载腿判据失效：数不到 catch (RuntimeException) 时，第 5 案的「收口件回退」永远不会红");
    }

    // ── 扫描实现 ────────────────────────────────────

    private static final class Scan {
        int scannedFiles;
        int handlerHits;
        final Map<String, Integer> face = new TreeMap<>();

        int totalSites() {
            return face.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static Scan scanMainFace() throws IOException {
        Path root = locateRepoRoot();
        Scan scan = new Scan();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            int hits = count(THROW_SECURITY_EXCEPTION, stripped);
            if (hits > 0) {
                scan.face.put(rel, hits);
            }
            scan.handlerHits += handlerHits(stripped);
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    /** 数「参数表里出现 {@code SecurityException}」的 {@code @ExceptionHandler} 腿数（承流面前提）。 */
    private static int handlerHits(String strippedSrc) {
        int n = 0;
        Matcher handler = EXCEPTION_HANDLER.matcher(strippedSrc);
        while (handler.find()) {
            if (handler.group(1).contains("SecurityException")) {
                n++;
            }
        }
        return n;
    }

    private static int count(Pattern pattern, String src) {
        int n = 0;
        Matcher m = pattern.matcher(src);
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 字面出现次数（用于「站点数＝归因证据」类判据，如定义＋调用点的合计）。 */
    private static int occurrences(String src, String literal) {
        int n = 0;
        for (int i = -1; (i = src.indexOf(literal, i + 1)) >= 0; ) {
            n++;
        }
        return n;
    }

    /** 读仓根相对路径的主源码并剥注释；文件不在位即红（禁「读不到就算通过」）。 */
    private static String strippedRepoFile(String relPath) throws IOException {
        Path file = locateRepoRoot().resolve(relPath);
        assertTrue(Files.exists(file), "判据目标文件不在位：" + file);
        return SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/")
                                && !s.contains("/archive/");
                    })
                    .sorted()
                    .forEach(out::add);
        }
    }

    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
