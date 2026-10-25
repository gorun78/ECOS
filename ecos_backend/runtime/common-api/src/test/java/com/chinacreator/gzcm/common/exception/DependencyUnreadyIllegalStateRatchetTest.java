package com.chinacreator.gzcm.common.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.chinacreator.gzcm.common.base.ApiResponse;
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
 * <p><b>上面那段是 #142/B4-3e-38 的首版读数，后续批已把它压干</b>：3e-39 按承流面实测订正＋
 * 3e-40～3e-43 四批属主侧改抛之后，本门现值＝总站点 <b>137</b>、DEPENDENCY 面 <b>15</b>、
 * ENV_ALGO 10、UNREADABLE 8、OTHER 104（15＋10＋104＋8＝137，四面各自独立钉值、禁减法；
 * 133→137 的 +4 是 #142/B4-3e-63／3e-64 数据源类型词表收口批在同后端面新增的四枚
 * 契约违约腿（runtime-access {@code DataSourceTypeVocabulary} 两枚＋data-engine
 * {@code DataSourceController} 两枚），词表不命中、语义为构造期违约即抛，落入 OTHER
 * 面而未随批重测本门——本批（#142/B4-3e-68）按门规「重测后同批改」补齐重设）。
 * 引用这段历史数字时一律以 {@code TOTAL_SITE_BASELINE}／{@code DEPENDENCY_FACE_BASELINE}
 * 的声明现值为准，否则等于把已收口的腿再判一次欠账。</p>
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
 * <h3>本门钉五件事</h3>
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
 *   <li><b>承流归属第三档</b>（#142/B4-3e-45，新登记 <b>D-B4-77</b>）：「落入口兜底」隐含
 *       「该腿会冒泡到某个 HTTP 属主端点」这个从未验证的前提。本门把兜底面再拆成
 *       <b>无 HTTP 承流</b>（持有对象零调用点／被上游宽 catch 折成非 HTTP 语义）与
 *       <b>可承流欠账</b>两面独立钉值，每条「不可达」宣称由真仓引用计数逐条背书并配正向对照——
 *       账面由此从「余 10 枚待补 503」订正为「0 枚可补 503＋10 枚死面」，
 *       堵住「给没有通路的腿伪造状态行」这条假修路线。</li>
 *   <li><b>承载判据四档化</b>（#142/B4-3e-61，收口 <b>D-B4-72</b>）：{@code carrierOf()} 原本只有两档
 *       ——「作用域内有裸 {@code Exception} 兜底的 advice」或「落 gateway 入口兜底＝500」。第二档隐含
 *       「读不到 advice 就是落兜底」，而 {@code data-engine} 全域实测 <b>0 枚 advice</b>，它的腿实际由
 *       属主端点自己的 catch 面渲染（{@code WriteChannelController} 把 ISE 折成 <b>403</b>），
 *       于是账面 500 与真实 403 <b>两个读数都不对</b>。本档把判据扩成四档互斥完备
 *       （advice／域内显式承载出口／无 HTTP 通路／入口兜底残差），残差档基线 <b>0 枚</b>：
 *       「判据读不到承载者」从此不能冒充「已判定落兜底 500」，新落进来的腿必须先归类才不红。</li>
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
     * <p><b>#142/B4-3e-45 对「余 10 枚＝data-engine 两持有者」这句话的订正（不改数，改口径）</b>：
     * 这 10 枚的账面动作此前被写成「属主端点补 503」，而实测它们<b>没有一条 HTTP 通路</b>
     * （9 枚持有对象在真仓里零调用点＋1 枚被两级宽 catch 折成修复台账字段）。
     * 本批不动生产码，改为把承流归属再切第三档，见 {@code NO_HTTP_CARRIER_LEGS_BASELINE}
     * 与 {@code D-B4-77}；故本表的 15 枚／{@code OWNER_SIDE_DEBT_LEGS_BASELINE} 的 10 枚
     * 读数为<b>实测未变</b>，变的是「这 10 枚该由谁、以什么方式收口」。</p>
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
     * <p><b>#142/B4-3e-43 属主侧第四批后现值＝6 个持有者／15 枚腿</b>：data-engine
     * {@code OlapEngineResolver}（曾为 DEPENDENCY／1 枚＋CARRIER／{@code ENTRY_FALLBACK_500}）整行退出本面——
     * 它的「OLAP 引擎未启用」腿改抛携带 503 的 {@code DataBridgeException}，机读码取 D.1 单源表内语义
     * 对应的 {@code ECOS-DATA-053}（收口前借用的 {@code ECOS-DATA-054} 表内＝Kafka 降级，号义漂移登 D-B4-73）。
     * 总站点 134→<b>133</b>、DEPENDENCY 16→<b>15</b>、落兜底腿 11→<b>10</b>、真欠账面 11→<b>10</b>
     * （承载腿 13 未动）。该文件其余 <b>3</b> 枚 ISE 站点（控制域写 OLAP／层不合法／双引擎并存）语义是
     * <b>MC04 不变式被破坏＝策略拒绝</b>，留在 OTHER 面按属主端点的 403 渲染，不在本批折叠。
     * 本腿收口前并非真「落入口兜底 500」——它被 {@code WriteChannelController.write} 的
     * {@code catch (IllegalStateException)} 折成 403，而 CARRIER 面判据只认 {@code @ControllerAdvice}、
     * 读不到属主端点局部 catch，故账面读数 500 与真实读数 403 <b>两个都不对</b>（新登记 D-B4-72，
     * 判据扩展为余项，本批以 {@link #dataEngineOwnerEndpointRendersUnavailableInsteadOfDenial()}
     * 钉住渲染链）。</p>
     *
     * <p>词表扩到 {@code 未注入|未启用} 后新增的两枚，语义裁决如下（不是随手加的匹配串）：
     * data-engine {@code BaseJdbcAdapter} 的「runtime-access JdbcConnector 建连桥未注入」＝
     * F02-13 建连通道缺失；{@code OlapEngineResolver} 的「OLAP 引擎未启用（dw.olap.engine 未配置）」
     * ＝该部署档未开通这条写通道。两者都是
     * 「制品在本部署不可用」（§3.2.7 ⇒ 属主端点显式 503），不是「不变式被破坏」，
     * 也不是 ENV_ALGO 那种「JVM 自己坏了」。后者已于 #142/B4-3e-43 按本裁决收口退出 ISE 面，
     * 前者仍在册。</p>
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
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java", 1));

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
     *
     * <p><b>#142/B4-3e-43 属主侧第四批后的现值</b>：134 → <b>133</b>、DEPENDENCY 面 16 → <b>15</b>，
     * 差 1 来自 data-engine {@code OlapEngineResolver} 的「OLAP 引擎未启用」腿改抛携带 503 的
     * {@code DataBridgeException}。同批还要改的是承载面与账面：该文件行退出两张表、落兜底腿
     * 11 → <b>10</b>、真欠账面 11 → <b>10</b>——已承载腿 13 未动（data-engine 零枚 advice，
     * 该腿此前本就不在承载面上；承载面判定的盲区另登 D-B4-72，见 {@code CARRIER_BASELINE} 注释）。
     * 该文件剩下的 3 枚 MC04 违规腿是策略拒绝，留在 OTHER 面（值 100 未动）。</p>
     *
     * <p><b>#142/B4-3e-68 同批重设（词表收口批漏测补账）</b>：3e-63／3e-64 在
     * runtime-access {@code DataSourceTypeVocabulary}（词表规范名重复／别名冲突两枚）与
     * data-engine {@code DataSourceController}（词表成员规范名为空／别名表 null 两枚）新增
     * 四枚<b>构造期契约违约腿</b>（词表不命中、语义为「数据源类型目录无法构造，禁静默跳过」，
     * 非依赖未就绪），落入 OTHER 面而未随批重测本门，致本门在下批全模块跑中按设计变红。
     * 本批按门规「必须重测后同批改」重设：总站点 133 → <b>137</b>、OTHER 面 100 → <b>104</b>，
     * DEPENDENCY／ENV_ALGO／UNREADABLE 三面未动（15／10／8）。</p>
     */
    private static final int TOTAL_SITE_BASELINE = 137;
    private static final int DEPENDENCY_FACE_BASELINE = 15;
    private static final int ENV_ALGO_FACE_BASELINE = 10;
    private static final int OTHER_FACE_BASELINE = 104;
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
    /** #142/B4-3e-43：data-engine 属主侧第四批渲染取证所依赖的两个源（属主腿／属主端点）。 */
    private static final String DATA_OLAP_RESOLVER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/writechannel/OlapEngineResolver.java";
    private static final String DATA_WRITE_CHANNEL_CONTROLLER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/controller/WriteChannelController.java";
    /**
     * #142/B4-3e-61：属主端点<b>不直接</b>引用抛出腿——异常要经这一跳服务才冒到 {@code write()} 的
     * catch 面。登记承载出口时必须把它一起钉住，否则「端点里有 catch」和「这条腿真经过它」是两件事
     * （D-B4-72 的盲区正是把前者当成后者）。
     */
    private static final String DATA_WRITE_CHANNEL_SERVICE =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/writechannel/DwWriteChannelService.java";
    /** 承载出口的 catch 锚点（一处声明，登记面与取证面同读，禁各写一份字面量）。 */
    private static final String OWNER_EXIT_ISE_LEG = "catch (IllegalStateException e) {";
    private static final String OWNER_EXIT_CARRIED_LEG = "catch (DataBridgeException e) {";
    private static final String OWNER_EXIT_BROAD_LEG = "catch (Exception e) {";
    /** 承载出口两条腿实际渲染出来的东西：403 出口／按携带值渲染的状态行。 */
    private static final String OWNER_EXIT_ISE_RENDERS = "return failure(ApiResponse.forbidden(e.getMessage()));";
    private static final String OWNER_EXIT_CARRIED_RENDERS = "ResponseEntity.status(status)";
    private static final String OWNER_EXIT_CARRIED_RESOLVER =
            "DataBridgeException.resolveHttpStatus(e.getHttpStatus())";
    /** {@code @RestController}／{@code @Controller}：第二档的承载者必须是 HTTP 出口，不是任何带 catch 的类。 */
    private static final Pattern OWNER_HTTP_ENDPOINT_ANNOTATION =
            Pattern.compile("^ *@(?:Rest)?Controller\\b", Pattern.MULTILINE);
    /** #142/B4-3e-43：D.1 单源表（机读码 503 段）所在码表——号义漂移 D-B4-73 的取证基准。 */
    private static final String DATA_DOMAIN_ERROR_CODE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/"
                    + "gzcm/common/data/DataDomainErrorCode.java";

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
     * 承流四档的标签常量（#142/B4-3e-61 收口 D-B4-72）。一档的取值＝承载 advice 的文件名，
     * 二档＝{@code OWNER_EXIT:<端点文件>#<渲染状态行>}，两者由 {@link CarrierClass} 按前缀分派。
     */
    private static final String ENTRY_FALLBACK_LABEL = "ENTRY_FALLBACK_500";
    private static final String NO_HTTP_CARRIER_LABEL = "NO_HTTP_CARRIER_D-B4-77";
    private static final String OWNER_EXIT_PREFIX = "OWNER_EXIT:";

    /**
     * 承流四档——<b>互斥且完备</b>，判据由 {@link #carrierOf(String)} 单点产出。
     *
     * <p>登记原文（D-B4-72）说的是：二档缺位时，「判据读不到承载者」被静默记成四档
     * 「落网关兜底＝渲染 500」。今天本面的 23 枚腿里，一档 13 枚（ai，502 冻结）、三档 10 枚
     * （data-engine 死面），<b>二档与四档各 0 枚</b>；二档的登记行 {@code OlapEngineResolver→
     * WriteChannelController} 今天不带腿（那条腿已于 3e-43 改抛 {@code DataBridgeException}，脱离 ISE 面），
     * 但它是本档形态的<b>唯一真仓样本</b>，也是 M1 变异取证里让账面从「500」改口为「403」的那条边。</p>
     */
    private enum CarrierClass {
        /** 一档：作用域覆盖本包且声明裸 {@code Exception} 兜底的域内 advice。 */
        ADVICE,
        /** 二档：域内显式承载出口——属主端点自己的具名 catch 腿（D-B4-72 要补的就是这一档）。 */
        OWNER_ENDPOINT_EXIT,
        /** 三档：无 HTTP 承流路径（死面），没有状态行可读。 */
        NO_HTTP_CARRIER,
        /** 四档：残差＝真的落 gateway 入口兜底，渲染 500。 */
        ENTRY_FALLBACK
    }

    /** 一条「谁引用谁」的向上边：{@code typeName} 必须出现在 {@code file} 的剥注释源码里。 */
    record RefEdge(String file, String typeName) {
    }

    /**
     * 一个登记在册的<b>域内显式承载出口</b>：抛出腿持有文件 → 承它的 HTTP 端点。
     *
     * @param holderRelPath  抛出腿所在文件
     * @param ownerFile      承载端点文件名（进标签，供 {@code CARRIER_BASELINE} 双向对账）
     * @param ownerRelPath   该端点的仓内相对路径
     * @param propagationPath 从抛出腿到端点的逐跳引用边；缺一条边＝「端点里有 catch」与「这条腿真经过它」脱钩
     * @param renderedStatus 该出口对本档腿渲染的状态行读数（实测推出，不是「有 catch」的同义词）
     */
    record OwnerExit(String holderRelPath, String ownerFile, String ownerRelPath,
                     List<RefEdge> propagationPath, int renderedStatus) {
    }

    /**
     * 二档登记表（键＝持有文件）。<b>登记只在证据在位时被承认</b>：证据失效则 {@code carrierOf}
     * 不再返回二档标签，该持有者落四档残差＝即时红，见 {@link #carrierOf(String)}。
     */
    private static final List<OwnerExit> OWNER_EXIT_REGISTRY = List.of(new OwnerExit(
            DATA_OLAP_RESOLVER,
            "WriteChannelController.java",
            DATA_WRITE_CHANNEL_CONTROLLER,
            List.of(new RefEdge(DATA_WRITE_CHANNEL_SERVICE, "OlapEngineResolver"),
                    new RefEdge(DATA_WRITE_CHANNEL_CONTROLLER, "DwWriteChannelService")),
            403));

    /** 登记表行数（钉值：新增一行＝先按 {@link #ownerEndpointCarriersAreReMeasuredFromTheirOwnAnchors()} 补证据）。 */
    private static final int OWNER_EXIT_REGISTRY_ROWS_BASELINE = 1;

    /** 承载出口实际渲染的状态行（键＝端点文件名）。403 不是「已改对」——见 {@link #OWNER_EXIT_REGISTRY}。 */
    private static final Map<String, Integer> OWNER_EXIT_STATUS_BASELINE = Map.of(
            "WriteChannelController.java", 403);

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
     *
     * <p>#142/B4-3e-43 属主侧第四批后现值＝6 个持有者／23 枚腿：data-engine
     * {@code OlapEngineResolver}（曾为 {@code ENTRY_FALLBACK_500}／1 枚）整行退出本面。
     * 落兜底面 11→<b>10</b>（data-engine <b>2</b> 个持有者：{@code DataDescriptionDiscoveryImpl} 1 枚＋
     * {@code BaseJdbcAdapter} 9 枚），承载面 13 未动。</p>
     *
     * <p><b>#142/B4-3e-61 收口 D-B4-72：本面的取值从两档扩成四档</b>。此前 {@code carrierOf()} 只有
     * 「域内 advice」与「{@code ENTRY_FALLBACK_500}」两种取值，而第二档把「判据读不到承载者」直接记成
     * 「落网关兜底＝渲染 500」。{@code data-engine} 全域实扫 <b>0 枚 advice</b>，所以该域每一条腿都被
     * 记成 500——{@code OlapEngineResolver} 收口前那枚实际被 {@code WriteChannelController.write} 的
     * {@code catch (IllegalStateException)} 折成 <b>403</b>：账面 500 与真实 403 <b>两个读数都不对</b>。
     * 现在四档互斥完备（{@link CarrierClass}）：advice／<b>域内显式承载出口</b>／无 HTTP 通路／入口兜底残差。
     * 本面的 data-engine 两行因此取值改为 {@code NO_HTTP_CARRIER_D-B4-77}（其「无通路」宣称由
     * {@link #unreachableCarrierClaimsAreReMeasuredFromReferenceScan()} 的引用计数逐条背书），
     * 残差档现值 <b>0 枚持有者／0 枚腿</b>——今后零 advice 域新落一枚腿进来，必须先判它是二档还是三档，
     * 不能像 D-B4-72 那样靠默认值蒙过去。</p>
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
                    NO_HTTP_CARRIER_LABEL),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java",
                    NO_HTTP_CARRIER_LABEL));

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

    /**
     * 「不可用族」状态行（§4.7.2：下游制品不可用＝5xx 中的不可用三枚）。
     *
     * <p>{@code 500} <b>不在</b>此集合：它是「服务端自己坏了」，把依赖未就绪渲染成 500 正是本面
     * 要收口的折叠；{@code 403} 同样不在——把不可用说成无权限是反向折叠，D-B4-72 登记时
     * {@code WriteChannelController} 的读数正是这一枚。判欠账只按本集合判，禁按「有承载者」判。</p>
     */
    private static final Set<Integer> UNAVAILABLE_STATUS_FAMILY = Set.of(502, 503, 504);

    /** 按承载者聚合的腿数（禁算式推出；#142/B4-3e-39 探测实扫，#142/B4-3e-40 属主侧首批后现值，
     *  #142/B4-3e-41 属主侧第二批后现值＝承载 13／兜底 12，
     *  <b>#142/B4-3e-42 属主侧第三批后现值＝承载 13／兜底 11</b>——kb 那枚脱离 ISE 站点面，
     *  承载面一枚未动，全在 ai）。
     *  <b>#142/B4-3e-43 属主侧第四批后现值＝承载 13／兜底 10</b>——data-engine 那枚脱离 ISE 站点面，
     *  承载面仍一枚未动（该域零枚 advice，判据盲区见 {@code CARRIER_BASELINE} 注释的 D-B4-72）。
     *  <b>#142/B4-3e-61 四档化后的现值＝一档 13／二档 0／三档 10／四档 0</b>：原来的「兜底 10」不是
     *  被改码收掉的，而是那 10 枚本来就没有 HTTP 通路（#142/B4-3e-45 的引用计数实测），
     *  旧判据把「读不到承载者」记成「落兜底 500」才是 D-B4-72 的差距本体。 */
    private static final int CARRIED_LEGS_BASELINE = 13;
    /** 二档（域内显式承载出口）腿数：今天 0 枚——登记表那一行的腿已于 3e-43 改抛脱离 ISE 面。 */
    private static final int OWNER_EXIT_LEGS_BASELINE = 0;
    /**
     * 四档残差（真落 gateway 入口兜底、渲染 500）腿数。<b>现值 0</b>＝本面没有任何一条腿是靠网关兜底
     * 渲染的。这一档从此是<b>未归类报警位</b>而非默认档：新落一枚进来即红，红字要求先判二档还是三档。
     */
    private static final int ENTRY_FALLBACK_LEGS_BASELINE = 0;

    /**
     * <b>真欠账面</b>＝四档残差（落入口兜底 500）＋承载者实际渲染的不是「不可用族」状态行的腿。
     *
     * <p>#142/B4-3e-39 实扫为 14＋2＝16；#142/B4-3e-40～43 四批属主侧逐枚收口后为 10。
     * <b>#142/B4-3e-61 起现值 0，且这个 0 不是「欠账被修完」，是「账面里本来就不该有它们」</b>：
     * 那 10 枚是 {@link #NO_HTTP_CARRIER_LEGS_BASELINE} 的死面（无状态行可读，治理在 D-B4-77 队列＝
     * 删除或接通路，不在「属主端点补 503」这条线上），把它们挂在欠账面会让下一批人写出假修
     * ——给一条没有 HTTP 通路的腿配状态行，本门只看到「ISE 站点少一枚」而客户端读数什么都没变。
     * 二档一旦出现腿，其渲染读数按 {@link #OWNER_EXIT_STATUS_BASELINE} 判：403 属于「把不可用说成无权限」，
     * <b>计入欠账</b>（这正是 D-B4-72 登记时的真实形态），只有 5xx 不可用族才算已承载到位。</p>
     *
     * <p>属主侧每批改码必须同批下调本值与本面基线，两头都红才算动过。</p>
     */
    private static final int OWNER_SIDE_DEBT_LEGS_BASELINE = 0;

    // ── #142/B4-3e-45：承流归属第三档（无 HTTP 承流面）与其可重跑证据 ────────────

    /** 死 SPI 面持有者（那枚「建连器未注入」腿所在文件）。 */
    private static final String DATA_DISCOVERY_IMPL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datadescription/discovery/impl/DataDescriptionDiscoveryImpl.java";

    /** 该死 SPI 面在真仓里的唯一装配者（无参构造里 {@code new} 出上面那个实现）。 */
    private static final String DATA_SCHEMA_REGISTRY_IMPL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datadescription/service/impl/SchemaRegistryServiceImpl.java";

    /** 存储适配器基类：本面 9 枚腿的持有者（1 枚建连桥＋8 枚未连接守卫）。 */
    private static final String DATA_BASE_JDBC_ADAPTER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java";

    /** 适配器在真仓里的<b>唯一</b>生产消费者（只走 createAdapter→testConnection→disconnect）。 */
    private static final String DATA_ADAPTER_CONSUMER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/datasource/service/impl/DataSourceServiceImpl.java";

    /** 建连桥那条<b>可达</b>腿的实际出口：DQ 自动修复台账的 {@code lastError} 字段，不是状态行。 */
    private static final String DATA_DQ_AUTO_REPAIR =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/quality/service/DqAutoRepairServiceImpl.java";

    /** 八枚「未连接」守卫腿对应方法的调用面——真仓实扫调用点恒 0，即不可达证据本体。 */
    private static final String[] ADAPTER_GUARD_CALLS = {
            "adapter.query(", "adapter.write(", "adapter.count(", "adapter.update(",
            "adapter.delete(", "adapter.getSchema(", "adapter.getMetadata(", "adapter.beginTransaction("
    };

    /** 同一消费者文件里<b>确有</b>调用的两条腿：不可达判定的正向对照，缺它则「恒 0」可能只是扫描器失明。 */
    private static final String[] ADAPTER_LIVE_CALLS = {"adapter.testConnection(", "adapter.disconnect("};

    /**
     * <b>无 HTTP 承流面</b>基线（#142/B4-3e-45 新登记 <b>D-B4-77</b> 的修法；键＝持有文件，值＝腿数）。
     *
     * <p><b>差距形态</b>：{@code carrierOf()} 此前只有两档——域内 {@code @ControllerAdvice} 承载，
     * 或 {@code ENTRY_FALLBACK_500}「落 gateway 入口兜底」。后者隐含一个从未验证的前提：
     * <b>这条腿会沿调用栈冒泡到某个 HTTP 属主端点</b>。data-engine 余下的 10 枚一枚都不满足——</p>
     * <ul>
     *   <li>{@code DataDescriptionDiscoveryImpl} 的腿只在 {@code jdbcConnector == null} 时触发，
     *       该对象全仓唯一构造点写在 {@code SchemaRegistryServiceImpl} 的无参构造里，
     *       而该无参构造的调用点实扫 <b>0</b>；</li>
     *   <li>{@code BaseJdbcAdapter} 的 8 枚「未连接」守卫腿所在方法
     *       （query／write／count／update／delete／getSchema／getMetadata／beginTransaction）
     *       在真仓里<b>没有任何适配器调用点</b>——适配器唯一消费者只走
     *       createAdapter→testConnection→disconnect 三条腿；{@code ScoringContext} 的类注释亦自证
     *       「IStorageAdapter 拉样本属 Phase 3，本阶段评估器仅读既有字段」；</li>
     *   <li>第 9 枚（建连桥未注入）<b>可达</b>，但被两级宽 {@code catch (Exception)} 依次折成
     *       {@code DataSourceException}（无码、无状态行的 checked 异常）与修复台账的
     *       {@code lastError} 字段，永远到不了 HTTP 状态行。</li>
     * </ul>
     *
     * <p><b>为什么必须单列这一档</b>：把这 10 枚记成「属主端点补 503」的待收口队列，下一批人只能写假修——
     * 给一条没有 HTTP 通路的腿配状态行，本门只看到「ISE 站点少一枚」而客户端读数什么都没变
     * （#142/B4-3e-41 同族）。故把「该谁改」再切一刀：<b>可 HTTP 承流的欠账</b> vs <b>无承流路径的死面</b>，
     * 两面各自独立遍历得出、各自双向钉值，<b>减法不参与</b>（D-B4-71 首版「恒等式假绿」的教训）。</p>
     *
     * <p><b>豁免必须点名，宣称必须可重跑</b>：本面每个持有者由
     * {@link #unreachableCarrierClaimsAreReMeasuredFromReferenceScan()} 的引用计数逐条背书；
     * 有人把死面接上通路时该案即红，账面必须重测。死代码本身的删除治理撞「只增不改」＋备份前置，
     * 另立余项，不在本批。</p>
     *
     * <p><b>#142/B4-3e-61 后本表的角色变了</b>：它不再只是测试案内部的局部口径，而是承流<b>第三档的
     * 成员表</b>——{@code carrierOf()} 直接按它给持有文件打 {@code NO_HTTP_CARRIER_D-B4-77} 标签，
     * 该标签又进 {@code CARRIER_BASELINE} 参与双向对账。<b>所以它的效力全挂在上那句「可重跑」上</b>：
     * 引用计数一翻身，成员必须撤，撤不干净就落第四档残差＝红，不可能静默漂成「已判过的死面」。</p>
     */
    private static final Map<String, Integer> NO_HTTP_CARRIER_LEGS_BASELINE = Map.ofEntries(
            Map.entry(DATA_DISCOVERY_IMPL, 1),
            Map.entry(DATA_BASE_JDBC_ADAPTER, 9));

    /** 无承流路径腿数合计（独立钉值；不由 {@code NO_HTTP_CARRIER_LEGS_BASELINE} 求和推出）。 */
    private static final int NO_HTTP_CARRIER_LEGS_TOTAL_BASELINE = 10;

    /**
     * 落兜底腿里<b>确有</b> HTTP 承流路径的枚数。现值 <b>0</b>＝D-B4-71 的属主侧队列在 data-engine
     * 这一域没有可收口项（此前 10 枚是按「落兜底必然渲染 500」的未验证前提排产的）。
     */
    private static final int HTTP_REACHABLE_OWNER_SIDE_DEBT_BASELINE = 0;

    /**
     * 死面持有者文件里必须出现的<b>停写标记</b>原文（#142/B4-3e-48 对 D-B4-77 的通路侧裁决＝
     * 不接承流、按「显式停写＋下线登记」处置）。标记写在类注释里而不是代码里，是因为本面对它的
     * 要求正是「让读代码的人知道此面禁接」；引用面仍由 {@code codeOf} 的剥注释口径判可达性，
     * 两个口径各读各的层面，不许互相冒充（D-B4-62 的镜像形态由
     * {@link #writeStopMarkersAndUnreachableClaimsCrossCertifyEachOther()} 拦）。
     */
    private static final String DEAD_SURFACE_STOP_MARKER = "【死面停写登记 D-B4-77】";

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
     * 一条腿所在包的承流归属：<b>四档互斥完备</b>（#142/B4-3e-61 把两档扩成四档，收口 D-B4-72）。
     *
     * <ol>
     *   <li>一档 {@code <AdviceFile>[+<AdviceFile>]}：作用域覆盖本包且声明裸 {@code Exception} 兜底的域内 advice；</li>
     *   <li>二档 {@code OWNER_EXIT:<端点文件>#<渲染状态行>}：该域<b>一枚 advice 都没有</b>，但登记在册的
     *       属主端点用自己的具名 catch 腿渲染这条腿。<b>登记只在逐跳证据在位时被承认</b>——证据失效就
     *       不返回二档标签，而是让它落进四档残差即时红；「以前登记过」不等于「现在还成立」；</li>
     *   <li>三档 {@code NO_HTTP_CARRIER_D-B4-77}：持有对象在真仓没有 HTTP 通路（死面），没有状态行可读；</li>
     *   <li>四档 {@code ENTRY_FALLBACK_500}：残差＝真的落 gateway 入口兜底、渲染 500。基线 <b>0 枚</b>，
     *       出现即红。D-B4-72 的原形态正是这一档被当成默认值用。</li>
     * </ol>
     */
    private static String carrierOf(String relPath) {
        String advice = adviceCarrierOf(relPath);
        if (advice != null) {
            if (OWNER_EXIT_REGISTRY.stream().anyMatch(x -> x.holderRelPath().equals(relPath))) {
                return fail("承流档互斥性失效：同一持有者既有域内 advice 承载、又登记了属主端点承载出口，"
                        + "判据不得靠取值顺序默认选一档: " + relPath);
            }
            return advice;
        }
        String ownerExit = ownerExitCarrierOf(relPath);
        if (ownerExit != null) {
            if (NO_HTTP_CARRIER_LEGS_BASELINE.containsKey(relPath)) {
                return fail("承流档互斥性失效：同一持有者既登记了属主端点承载出口、又登记为无 HTTP 承流面: "
                        + relPath);
            }
            return ownerExit;
        }
        if (NO_HTTP_CARRIER_LEGS_BASELINE.containsKey(relPath)) {
            return NO_HTTP_CARRIER_LABEL;
        }
        return ENTRY_FALLBACK_LABEL;
    }

    /** 一档：能接住任意异常（含 {@code IllegalStateException}）的作用域内 advice 文件名，多个以 {@code +} 连接；无则 null。 */
    private static String adviceCarrierOf(String relPath) {
        String pkg = packageOf(relPath);
        List<String> carriers = new ArrayList<>();
        for (Advice a : advices()) {
            if (!"GLOBAL".equals(a.selector()) && !a.selector().contains("assignableTypes")
                    && a.hasExceptionFallback() && covers(a, pkg)) {
                carriers.add(Path.of(a.relPath()).getFileName().toString());
            }
        }
        if (carriers.isEmpty()) {
            return null;
        }
        carriers.sort(String::compareTo);
        return String.join("+", carriers);
    }

    /**
     * 二档：登记在册<b>且</b>逐跳证据在位才算「域内显式承载出口」。
     *
     * <p>证据不全时返回 null＝该持有者落四档残差，由 {@code CARRIER_BASELINE} 案与
     * {@code ENTRY_FALLBACK_LEGS_BASELINE} 同时报红，再由
     * {@link #ownerEndpointCarriersAreReMeasuredFromTheirOwnAnchors()} 说出是哪条边断的。
     * 这里<b>不 fail()</b>：判据要保持纯函数，红只能由账面出。</p>
     */
    private static String ownerExitCarrierOf(String relPath) {
        OwnerExit exit = null;
        for (OwnerExit x : OWNER_EXIT_REGISTRY) {
            if (x.holderRelPath().equals(relPath)) {
                exit = x;
            }
        }
        if (exit == null || !ownerExitEvidenceInPlace(exit)) {
            return null;
        }
        Integer status = OWNER_EXIT_STATUS_BASELINE.get(exit.ownerFile());
        if (status == null) {
            return fail("承载出口没登记渲染状态行读数，不得凭「端点里有 catch」推断已改对: "
                    + exit.ownerFile() + "（持有者 " + relPath + "）");
        }
        return OWNER_EXIT_PREFIX + exit.ownerFile() + "#" + status;
    }

    /** 二档登记的实测前提：HTTP 出口在位、同模块、逐跳引用边在位、catch 腿齐备且排序正确。 */
    private static boolean ownerExitEvidenceInPlace(OwnerExit exit) {
        String owner = codeOf(exit.ownerRelPath());
        if (!OWNER_HTTP_ENDPOINT_ANNOTATION.matcher(owner).find()) {
            return false;
        }
        if (!moduleRootOf(exit.holderRelPath()).equals(moduleRootOf(exit.ownerRelPath()))) {
            return false;
        }
        for (RefEdge edge : exit.propagationPath()) {
            if (!codeOf(edge.file()).contains(edge.typeName())) {
                return false;
            }
        }
        int ise = owner.indexOf(OWNER_EXIT_ISE_LEG);
        int carried = owner.indexOf(OWNER_EXIT_CARRIED_LEG);
        int broad = owner.indexOf(OWNER_EXIT_BROAD_LEG);
        if (ise < 0 || carried < 0 || broad < 0 || ise > carried || carried > broad) {
            return false;
        }
        return owner.substring(ise, Math.min(owner.length(), ise + 400)).contains(OWNER_EXIT_ISE_RENDERS)
                && owner.substring(carried, Math.min(owner.length(), carried + 800))
                        .contains(OWNER_EXIT_CARRIED_RESOLVER)
                && owner.substring(carried, Math.min(owner.length(), carried + 800))
                        .contains(OWNER_EXIT_CARRIED_RENDERS);
    }

    /** 模块根＝{@code /src/main/java/} 之前那一段路径，用来判「承载端点与抛出腿同属一个部署件」。 */
    private static String moduleRootOf(String relPath) {
        int i = relPath.indexOf("/src/main/java/");
        if (i < 0) {
            return fail("路径里没有 /src/main/java/ 段，模块根无从比较: " + relPath);
        }
        return relPath.substring(0, i);
    }

    /** 承流四档的分派：按标签前缀判档，一档的标签就是 advice 文件名本身。 */
    private static CarrierClass carrierClassOf(String label) {
        if (label.startsWith(OWNER_EXIT_PREFIX)) {
            return CarrierClass.OWNER_ENDPOINT_EXIT;
        }
        if (NO_HTTP_CARRIER_LABEL.equals(label)) {
            return CarrierClass.NO_HTTP_CARRIER;
        }
        if (ENTRY_FALLBACK_LABEL.equals(label)) {
            return CarrierClass.ENTRY_FALLBACK;
        }
        return CarrierClass.ADVICE;
    }

    /**
     * 承载者实际渲染出来的状态行。
     *
     * <p>{@code null}＝<b>这一档／这一行没有可信读数</b>：三档本来就无状态行可读（死面），
     * 一档承载者没进 {@code CARRIER_STATUS_BASELINE} 则是登记缺口。两种都由调用侧点名，
     * 判据不在这里 {@code fail()}——四档判据要保持纯函数，红只能由账面出。</p>
     */
    private static Integer renderedStatusOf(String holder, String label) {
        CarrierClass cls = carrierClassOf(label);
        if (cls == CarrierClass.NO_HTTP_CARRIER) {
            return null;
        }
        if (cls == CarrierClass.OWNER_ENDPOINT_EXIT) {
            for (OwnerExit x : OWNER_EXIT_REGISTRY) {
                if (x.holderRelPath().equals(holder)) {
                    return x.renderedStatus();
                }
            }
            return fail("标签写着二档却找不到登记行: " + holder + " = " + label);
        }
        if (cls == CarrierClass.ENTRY_FALLBACK) {
            // 残差档的读数＝网关入口兜底的读数，由 handleIllegalState 那条 500 钉住（见类注释第 6 条）。
            return 500;
        }
        return CARRIER_STATUS_BASELINE.get(label);
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
    @DisplayName("承流四档腿数各自钉死且之和＝依赖面腿数：残差档为空（D-B4-72 的判据扩展）")
    void carrierClassesPartitionTheFaceAndEachCountIsPinned() {
        Map<CarrierClass, Integer> byClass = new EnumMap<>(CarrierClass.class);
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            byClass.merge(carrierClassOf(e.getValue()), countOf(e.getKey()), Integer::sum);
        }
        int one = byClass.getOrDefault(CarrierClass.ADVICE, 0);
        int two = byClass.getOrDefault(CarrierClass.OWNER_ENDPOINT_EXIT, 0);
        int three = byClass.getOrDefault(CarrierClass.NO_HTTP_CARRIER, 0);
        int four = byClass.getOrDefault(CarrierClass.ENTRY_FALLBACK, 0);

        List<String> drift = new ArrayList<>();
        accountFor(drift, "一档 已承载 advice 腿数", CARRIED_LEGS_BASELINE, one,
                "＝有腿在承载面进出未登记；「该改 503」的批量定罪不能再靠减法得出");
        accountFor(drift, "二档 域内显式承载出口腿数", OWNER_EXIT_LEGS_BASELINE, two,
                "＝登记在册的属主端点接住了一条依赖腿：它的渲染读数按 OWNER_EXIT_STATUS_BASELINE 判，"
                        + "403 属「把不可用说成无权限」仍计欠账（D-B4-72 的原形态），不是「已被承载＝已改对」");
        accountFor(drift, "三档 无 HTTP 承流腿数", NO_HTTP_CARRIER_LEGS_TOTAL_BASELINE, three,
                "＝死面腿数变了却没同批改合计钉值：把死面接上通路属承流判定变更，须整面重测");
        accountFor(drift, "四档 落入口兜底残差腿数", ENTRY_FALLBACK_LEGS_BASELINE, four,
                "＝出现一条既无 advice、又没登记承载出口、也不是死面的腿。这正是 D-B4-72 的形态："
                        + "判据读不到承载者被默认记成「落网关兜底 500」。必须先判它属二档还是三档，"
                        + "再显式改本钉值");
        accountFor(drift, "四档腿数之和",
                DEPENDENCY_FACE_BASELINE + UNREADABLE_FACE_BASELINE, one + two + three + four,
                "＝承流面没有覆盖全部依赖腿（有持有者读不到包名或被静默漏掉）");
        assertTrue(drift.isEmpty(), "承流账失真:\n" + String.join("\n", drift));
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("四档持有者集合逐文件互斥：一档/二档/三档/残差各归一档，重复归类即红")
    void carrierClassesAreMutuallyExclusivePerHolder() {
        Map<String, List<CarrierClass>> membership = new TreeMap<>();
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            List<CarrierClass> hit = new ArrayList<>();
            String label = e.getValue();
            if (adviceCarrierOf(e.getKey()) != null) {
                hit.add(CarrierClass.ADVICE);
            }
            if (ownerExitCarrierOf(e.getKey()) != null) {
                hit.add(CarrierClass.OWNER_ENDPOINT_EXIT);
            }
            if (NO_HTTP_CARRIER_LEGS_BASELINE.containsKey(e.getKey())) {
                hit.add(CarrierClass.NO_HTTP_CARRIER);
            }
            if (ENTRY_FALLBACK_LABEL.equals(label)) {
                hit.add(CarrierClass.ENTRY_FALLBACK);
            }
            membership.put(e.getKey(), hit);
        }
        List<String> both = new ArrayList<>();
        membership.forEach((holder, hit) -> {
            if (hit.size() != 1) {
                both.add("  " + holder + " 归入 " + hit.size() + " 档（必须恰为 1）: " + hit);
            }
        });
        assertTrue(both.isEmpty(), "承流档互斥性／完备性失效——一档多归＝判据靠取值顺序偷偷选一档，"
                + "零档＝分类器与账面失步:\n" + String.join("\n", both));
    }

    @Test
    @Timeout(value = 240)
    @DisplayName("真欠账面＝渲染读数不在不可用族的腿：三档死面不计入（D-B4-72 的账面调解）")
    void ownerSideDebtLegsAreMeasuredFromCarriersRenderedStatus() {
        int debt = 0;
        List<String> noReading = new ArrayList<>();
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            int n = countOf(e.getKey());
            if (carrierClassOf(e.getValue()) == CarrierClass.NO_HTTP_CARRIER) {
                // 三档：真仓没有 HTTP 通路，根本没有状态行可读。把它挂进欠账面，
                // 下一批人只能写假修（给无通路的腿配状态行），治理队列在 D-B4-77。
                continue;
            }
            Integer status = renderedStatusOf(e.getKey(), e.getValue());
            if (status == null) {
                noReading.add("  " + e.getKey() + " 标签 " + e.getValue()
                        + " 推不出渲染状态行＝承载者没登记读数");
                continue;
            }
            if (!UNAVAILABLE_STATUS_FAMILY.contains(status)) {
                debt += n;
            }
        }
        assertTrue(noReading.isEmpty(),
                "有承载者却没登记渲染状态行，不得凭「有 advice／有 catch」推断已改对:\n"
                        + String.join("\n", noReading));
        assertEquals(OWNER_SIDE_DEBT_LEGS_BASELINE, debt,
                "属主侧欠账面漂走：承载者渲染出的读数不在不可用族（502/503/504）的腿发生增减。"
                        + "四类都不算还清——落入口兜底的 500、被 advice 折成 500（workspace 形态）、"
                        + "被属主端点折成 403（D-B4-72 的真实形态）都继续挂账；"
                        + "而已按域内冻结表落 5xx＋机读码的腿（ai 13 枚→502 ECOS-AI-502）不是欠账，"
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

    /**
     * <b>#142/B4-3e-43 属主侧第四批的渲染取证</b>（差距 D-B4-71，形态与 3e-41 同族）：data-engine
     * 全域零枚 {@code @ControllerAdvice}，本门的 {@code carrierOf} 因此把该域所有依赖腿记成
     * {@code ENTRY_FALLBACK_500}——而 {@code WriteChannelController.write} 实际把 {@code IllegalStateException}
     * 一律折成 {@code forbidden}＝403。账面 500 与真实 403 两个读数都不对（新登记 D-B4-72），
     * 本案就是把这个「判据看不见的那条腿」按源码锚点钉到真实渲染链上：属主腿携带 503 →
     * 承载腿排在宽 {@code catch (Exception)} <b>之前</b> → 状态行取携带值经单源裁决 →
     * 信封机读码取 message 前缀 → MC04 违规腿仍归 403（不可用与无权限不得互折，§4.7.2）→
     * 机读码号义与 D.1 单源表一致（D-B4-73）。
     *
     * <p>#142/B4-3e-61 起本案的 catch 锚点与渲染锚点一律取 {@code OWNER_EXIT_*} 常量：同一批字面量
     * 在登记表判据（{@link #ownerExitEvidenceInPlace(OwnerExit)}）和这里被读两遍，各写一份就是
     * 「改了一份、另一份还在绿」的单源缺陷（D-B4-45 同族）。</p>
     */
    @Test
    void dataEngineOwnerEndpointRendersUnavailableInsteadOfDenial() {
        String resolver = codeOf(DATA_OLAP_RESOLVER);
        assertTrue(resolver.contains("throw new DataBridgeException(503, 503,")
                        && resolver.contains("DataDomainErrorCode.ECOS_DATA_053 + \" — OLAP 引擎未启用"),
                "「OLAP 引擎未启用」腿不再抛携带 503＋ECOS-DATA-053 的载体＝属主侧改码被回退，"
                        + "该腿会重新落 " + OWNER_EXIT_ISE_LEG + " 折成 403 无权限");
        assertEquals(3, literalCount(resolver, "throw new IllegalStateException("),
                "OlapEngineResolver 的 IllegalStateException 站点数变了：本批只收「未启用」这一枚，"
                        + "另外 3 枚 MC04 违规腿是策略拒绝（403），不得随批改型");
        assertEquals(1, literalCount(resolver, "throw new DataBridgeException(503, 503,"),
                "该文件的 503 承载腿不止一枚＝本批范围被悄悄扩大，MC04 违规腿可能已被一起改型");

        String controller = codeOf(DATA_WRITE_CHANNEL_CONTROLLER);
        int carried = controller.indexOf(OWNER_EXIT_CARRIED_LEG);
        int broad = controller.indexOf(OWNER_EXIT_BROAD_LEG);
        assertTrue(carried > 0,
                "WriteChannelController.write 的承载腿被摘＝携带 503 的不可用态重新落宽 catch，"
                        + "折成 ECOS-DATA-055＋CODE_INTERNAL_ERROR(-1)＝假绿");
        assertTrue(broad > 0 && carried < broad,
                "承载腿排在 " + OWNER_EXIT_BROAD_LEG + " 之后＝Java catches 顺序失效，503 永远走不到本腿");
        String carriedBody = controller.substring(carried, Math.min(controller.length(), carried + 800));
        assertTrue(carriedBody.contains(OWNER_EXIT_CARRIED_RESOLVER)
                        && carriedBody.contains(OWNER_EXIT_CARRIED_RENDERS)
                        && carriedBody.contains("MachineErrorCode.orNumeric(e.getMessage(), e.getErrorCode())"),
                "承载腿不再按携带状态渲染或不再取 message 前缀机读码＝状态行/机读码回落字面量，"
                        + "属主侧携带值与出口读数脱钩");

        int ise = controller.indexOf(OWNER_EXIT_ISE_LEG);
        assertTrue(ise > 0 && ise < carried,
                "IllegalStateException 腿不再先于承载腿＝MC04 违规腿的 403 策略拒绝会被改判成 503，"
                        + "§4.7.2 的反向折叠（无权限折成不可用）同样禁止");
        assertTrue(controller.substring(ise, Math.min(controller.length(), ise + 400))
                        .contains(OWNER_EXIT_ISE_RENDERS),
                "MC04 违规腿的 forbidden 出口被改＝不变式违反与部署不可用两类状态行重新混用");

        // 号义（「053＝OLAP 降级」）写在码表的 javadoc 上，本门其余判据一律剥注释；这里刻意取原文，
        // 因为本案要钉的是「码表对外的语义承诺」而不是可执行语句——与 D-B4-40 的反向用法，须点名才可这样读。
        String codes = readRepoFile(DATA_DOMAIN_ERROR_CODE);
        int at053 = codes.indexOf("ECOS_DATA_053 = \"ECOS-DATA-053\"");
        assertTrue(at053 > 0
                        && codes.substring(Math.max(0, at053 - 160), at053)
                                .contains("Doris/ClickHouse OLAP 降级"),
                "ECOS-DATA-053 在 D.1 单源表里的号义不再是 OLAP 降级＝本批属主腿的机读码语义失去单源依据"
                        + "（号义漂移 D-B4-73 的取证锚，改号须同批改本断言与分册 02 D.1）");
    }

    /**
     * <b>#142/B4-3e-61 第二档（域内显式承载出口）的登记表取证案</b>（收口 D-B4-72）。
     *
     * <p><b>为什么本案必须独立存在</b>：二档是四档里唯一「账面说不清」的一档——它今天在本面
     * <b>0 枚腿</b>（登记表那一行的腿已于 3e-43 改抛 {@code DataBridgeException}，脱离了 ISE 判据面）。
     * 若只把它当装饰写进 {@code carrierOf()}，D-B4-72 就又回到「判据里有档、档里没证据」的形态。
     * 故把登记行按它自己的锚点逐条重跑，并让 {@code carrierOf()} 在证据失效时<b>不返回二档标签</b>
     * （该持有者落四档残差＝残差钉值 0 立即报红）。两边互为闭环：登记不会烂在账上，也不会凭空生效。</p>
     *
     * <p><b>四条实测前提</b>：①承载者必须是 HTTP 出口（带 catch 的普通类不算，那是把折叠换个地方写）；
     * ②与抛出腿同属一个部署件（跨模块的 catch 接不住冒泡）；③逐跳引用边在位——端点并不直接引用
     * {@code OlapEngineResolver}，异常要经 {@code DwWriteChannelService} 那一跳才到 {@code write()} 的
     * catch 面；缺这条边就是「端点里有 catch」与「这条腿真经过它」脱钩，正是 D-B4-72 的盲区本体；
     * ④catch 腿齐备且排序正确。</p>
     *
     * <p><b>状态行读数不写常量</b>：403 由源码里的 {@code ApiResponse.forbidden(} 经
     * {@code resolveHttpStatus(CODE_FORBIDDEN)} 推出，再与登记值、基线值三方对账——
     * 登记值与源码脱钩即红（「有 catch」不等于「渲染对了」，与一档的
     * {@link #carriersRenderTheStatusTheyArePinnedFor()} 同一口径）。</p>
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("二档登记表逐条重跑锚点：断一条边即落残差，禁「登记过＝现在还成立」（D-B4-72）")
    void ownerEndpointCarriersAreReMeasuredFromTheirOwnAnchors() {
        assertEquals(OWNER_EXIT_REGISTRY_ROWS_BASELINE, OWNER_EXIT_REGISTRY.size(),
                "承载出口登记表行数变了：加一行必须同批补渲染读数与本案的逐跳证据，"
                        + "删一行则是承流档位账面须重测——本案不认「顺手清表」");

        List<String> broken = new ArrayList<>();
        for (OwnerExit exit : OWNER_EXIT_REGISTRY) {
            String owner = codeOf(exit.ownerRelPath());

            if (!OWNER_HTTP_ENDPOINT_ANNOTATION.matcher(owner).find()) {
                broken.add("  ①承载者不是 HTTP 出口（@RestController/@Controller 读不到）: "
                        + exit.ownerRelPath());
            }
            if (!moduleRootOf(exit.holderRelPath()).equals(moduleRootOf(exit.ownerRelPath()))) {
                broken.add("  ②抛出腿与承载端点跨部署件，冒泡接不住: " + exit.holderRelPath()
                        + " → " + exit.ownerRelPath());
            }
            for (RefEdge edge : exit.propagationPath()) {
                if (!codeOf(edge.file()).contains(edge.typeName())) {
                    broken.add("  ③逐跳引用边断裂: " + edge.file() + " 里读不到 " + edge.typeName()
                            + "＝「端点里有 catch」与「这条腿真经过它」脱钩");
                }
            }

            int ise = owner.indexOf(OWNER_EXIT_ISE_LEG);
            int carried = owner.indexOf(OWNER_EXIT_CARRIED_LEG);
            int broad = owner.indexOf(OWNER_EXIT_BROAD_LEG);
            if (ise < 0 || carried < 0 || broad < 0) {
                broken.add("  ④catch 腿缺失 ise=" + ise + " carried=" + carried + " broad=" + broad
                        + " : " + exit.ownerRelPath());
            } else {
                if (ise > carried || carried > broad) {
                    broken.add("  ④catch 排序失效（须 ise < carried < broad）: " + exit.ownerRelPath());
                }
                String iseBody = owner.substring(ise, Math.min(owner.length(), ise + 400));
                if (!iseBody.contains(OWNER_EXIT_ISE_RENDERS)) {
                    broken.add("  ④IllegalStateException 腿不再渲染 forbidden 出口: " + exit.ownerRelPath());
                } else {
                    int derived = DataBridgeException.resolveHttpStatus(ApiResponse.CODE_FORBIDDEN);
                    if (derived != exit.renderedStatus()) {
                        broken.add("  ⑤渲染读数与源码脱钩: 源码 forbidden 经单源推出 " + derived
                                + "，登记 " + exit.renderedStatus());
                    }
                    Integer pinned = OWNER_EXIT_STATUS_BASELINE.get(exit.ownerFile());
                    if (pinned == null || pinned != exit.renderedStatus()) {
                        broken.add("  ⑤登记表读数与基线脱钩: " + exit.ownerFile()
                                + " 基线 " + pinned + "，登记 " + exit.renderedStatus());
                    }
                }
            }

            String tag = ownerExitCarrierOf(exit.holderRelPath());
            String expected = OWNER_EXIT_PREFIX + exit.ownerFile() + "#" + exit.renderedStatus();
            if (tag == null) {
                broken.add("  判据未承认这条二档登记（证据已失效，该持有者落四档残差）: "
                        + exit.holderRelPath() + "——残差钉值 0 会同步报红");
            } else if (!expected.equals(tag)) {
                broken.add("  二档标签与登记行不一致: 判据给 " + tag + "，登记推 " + expected);
            }
            if (CARRIER_BASELINE.containsKey(exit.holderRelPath())) {
                broken.add("  登记行的抛出腿同时进了承流基线＝本档今天应当 0 枚腿，"
                        + "腿数一旦非 0 要整面重测而不是追加登记: " + exit.holderRelPath());
            }
        }
        assertTrue(broken.isEmpty(),
                "域内显式承载出口的登记失去证据（判据会把这条腿记成四档残差＝即时红）:\n"
                        + String.join("\n", broken));
    }

    /**
     * 主源码缓存：本批三案都要在全后端做引用计数，逐案走树会把门禁拖进超时区
     * （与 {@code cachedLegs}／{@code cachedAdvices} 同法，只增缓存不改判据）。
     */
    private static volatile List<Scanned> cachedMainSources;

    private static List<Scanned> mainSources() {
        List<Scanned> cached = cachedMainSources;
        if (cached != null) {
            return cached;
        }
        synchronized (DependencyUnreadyIllegalStateRatchetTest.class) {
            if (cachedMainSources == null) {
                cachedMainSources = List.copyOf(backendMainSources());
            }
            return cachedMainSources;
        }
    }

    /** 剥注释后含该字面量的主源码文件（repo-root 相对路径，字典序；空清单＝真仓零引用）。 */
    private static List<String> filesContaining(String needle) {
        List<String> out = new ArrayList<>();
        for (Scanned s : mainSources()) {
            if (s.code().contains(needle)) {
                out.add(s.relPath());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * <b>#142/B4-3e-45 引入、#142/B4-3e-61 重定的第三／四档拆分</b>（差距 D-B4-77／D-B4-72）。
     *
     * <p>3e-45 时这一案的职责是「把 {@code ENTRY_FALLBACK_500} 面再按可达性切两半」；四档化后
     * {@code carrierOf()} 自己就产出第三档标签，再按标签切同一刀就成了判据的自我循环。故本案的职责
     * 改为把<b>三档成员表与四档残差面各自逐文件钉死</b>：三档必须与 {@code NO_HTTP_CARRIER_LEGS_BASELINE}
     * 双向等值（有人撤了死面却没改表即红），四档残差必须是<b>空面</b>——D-B4-72 的原形态正是这一档
     * 被当作默认值使用，残差非空＝有一枚腿没被任何显式判据接住。</p>
     *
     * <p>三面（实扫面／三档面／四档面）各自独立遍历 {@code carrierFace()} 得出，<b>不用减法</b>；
     * 合计与残差各自另有独立钉值，改一处必须改两处才过得去。</p>
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("三档成员表双向等值、四档残差面必须为空：判据默认值已作废（D-B4-72/77）")
    void deadSurfaceFaceIsPinnedAndResidualFaceIsEmpty() {
        Map<String, Integer> noCarrier = new TreeMap<>();
        Map<String, Integer> residual = new TreeMap<>();
        for (Map.Entry<String, String> e : carrierFace().entrySet()) {
            CarrierClass cls = carrierClassOf(e.getValue());
            if (cls == CarrierClass.NO_HTTP_CARRIER) {
                noCarrier.put(e.getKey(), countOf(e.getKey()));
            } else if (cls == CarrierClass.ENTRY_FALLBACK) {
                residual.put(e.getKey(), countOf(e.getKey()));
            }
        }
        assertPinnedBidirectionally("三档 无 HTTP 承流", noCarrier, NO_HTTP_CARRIER_LEGS_BASELINE);
        assertPinnedBidirectionally("四档 入口兜底残差", residual, Map.of());

        List<String> drift = new ArrayList<>();
        accountFor(drift, "三档腿数合计", NO_HTTP_CARRIER_LEGS_TOTAL_BASELINE, sum(noCarrier),
                "＝死面腿数变了却没同批改合计钉值：把死面接上通路属承流判定变更，须整面重测");
        accountFor(drift, "四档残差腿数（独立钉值）", ENTRY_FALLBACK_LEGS_BASELINE, sum(residual),
                "＝出现一条既无域内 advice、又没登记承载出口、也不是死面的腿。必须先判它属二档"
                        + "（补 OWNER_EXIT_REGISTRY 一行并过逐跳取证案）还是三档（补引用计数背书），"
                        + "再显式改本钉值——判据不得再用默认值替这条腿做决定");
        accountFor(drift, "四档残差＝确有 HTTP 通路的属主欠账", HTTP_REACHABLE_OWNER_SIDE_DEBT_BASELINE,
                sum(residual),
                "＝同一枚残差面的第二个口径（与上一条独立声明）：只有真有 HTTP 通路的兜底腿才是"
                        + "「属主端点补 503」的合法排产项；两个口径一起漂才算有人读过账");
        assertTrue(drift.isEmpty(), "承流三／四档账失真:\n" + String.join("\n", drift));
    }

    /**
     * 死面宣称的<b>可重跑背书</b>：每条「不可达」都由真仓引用面计数产出，并配<b>正向对照</b>
     * （同一消费者文件里确有调用的两条腿必须读到 1）——没有正向对照的「恒 0」与 D-B4-68
     * （锚点切片不吃判据 token）、D-B4-62（注释面冒充代码面）是同一种假绿：扫描器读不到，
     * 判据却把它当成「不存在」。
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("无承流面逐条重跑引用计数：一旦接上通路即红，禁凭注释豁免（D-B4-77）")
    void unreachableCarrierClaimsAreReMeasuredFromReferenceScan() {
        List<String> blind = new ArrayList<>();

        // 面 A：发现服务（死 SPI 面）——装配面与构造点
        assertEquals(List.of(DATA_DISCOVERY_IMPL, DATA_SCHEMA_REGISTRY_IMPL),
                filesContaining("DataDescriptionDiscoveryImpl("),
                "发现服务实现的引用文件集合变了＝该 SPI 被接上装配，其「建连器未注入」腿重新有承流路径，"
                        + "本面承流归属与真欠账面必须重测");
        assertEquals(2, literalCount(codeOf(DATA_DISCOVERY_IMPL), "requireJdbcConnector("),
                "requireJdbcConnector( 的发生面不再是 2 枚（实测＝方法定义 1 枚＋唯一调用点 1 枚）。"
                        + "调用点增多＝该腿的触发面被扩大，定义与调用各自要重数");
        assertEquals(1, literalCount(codeOf(DATA_SCHEMA_REGISTRY_IMPL), "new DataDescriptionDiscoveryImpl("),
                "SchemaRegistryServiceImpl 里发现服务实现的构造点不再是 1 枚＝本案的引用基准失步");
        assertEquals(List.of(), filesContaining("new SchemaRegistryServiceImpl("),
                "SchemaRegistryServiceImpl 的无参构造出现调用点＝死 SPI 面复活，"
                        + "DataDescriptionDiscoveryImpl 那枚腿须按属主侧批重判（届时还要读它的建连器注入来源）");

        // 面 B：适配器八枚「未连接」守卫腿——唯一消费者的调用面
        String consumer = codeOf(DATA_ADAPTER_CONSUMER);
        for (String call : ADAPTER_GUARD_CALLS) {
            int got = literalCount(consumer, call);
            if (got != 0) {
                blind.add("适配器取数／写入腿出现调用点（" + call + " 实扫 " + got
                        + "，登记 0）＝八枚「未连接」守卫腿重新有承流路径");
            }
        }
        for (String call : ADAPTER_LIVE_CALLS) {
            int got = literalCount(consumer, call);
            if (got != 1) {
                blind.add("正向对照失效（" + call + " 实扫 " + got
                        + "，登记 1）＝本案读的不是真消费者文件，或该文件已不含这条调用，"
                        + "此时上面那八个 0 不可信（扫描器失明而非真不可达）");
            }
        }
        assertEquals(List.of(DATA_ADAPTER_CONSUMER), filesContaining("new JdbcAdapterFactory("),
                "适配器工厂的实例化点不再唯一＝出现了新的适配器持有者，取数面的可达性判定须重跑");
        assertEquals(List.of(), filesContaining(".getAdapter("),
                "工厂的 getAdapter 门面出现调用点＝适配器可能经它流向别处，八枚守卫腿不再恒不可达");

        // 面 C：建连桥那条可达腿的折叠链（两级宽 catch，出口是台账字段而非状态行）
        assertEquals(List.of(DATA_DQ_AUTO_REPAIR), filesContaining(".testDataSource("),
                "连接测试的调用面变了＝该腿可能新增 HTTP 属主通路，折叠链判定须重跑");
        assertTrue(consumer.contains("catch (Exception e) {")
                        && consumer.contains("throw new DataSourceException("),
                "消费者不再用宽 catch 折成 DataSourceException＝建连桥腿的承流形态变了，本面须重测");
        String repair = codeOf(DATA_DQ_AUTO_REPAIR);
        assertTrue(repair.contains("catch (Exception e) {")
                        && repair.contains("errMsg = truncate(e.getMessage(), 200);"),
                "修复台账不再把异常消息落成字段＝建连桥腿的出口从台账语义改回异常冒泡，须重判承流档位");

        assertTrue(blind.isEmpty(), "无 HTTP 承流的宣称被真实引用面推翻:\n" + String.join("\n", blind));
    }

    /**
     * 本批<b>零生产改动</b>的自证，兼作假修路线的具名拦截：两枚持有者的腿必须仍是
     * {@code IllegalStateException}，且文件里不得出现 503 载体或本域机读码常量——给没有
     * HTTP 通路的腿配状态行，本门只看见「ISE 站点少一枚」而客户端读数什么都没变。
     * 谁要走这条路，先被 {@link #unreachableCarrierClaimsAreReMeasuredFromReferenceScan()}
     * 的引用计数拦住（没接通路就配状态行＝本案红），而不是被账面数字放行。
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("死面腿保持原抛型：本批零生产改动，禁给无通路腿伪造 503（假修拦截）")
    void deadCarrierLegsStayUnreachableAndMustNotGainAFabricatedStatusLine() {
        String discovery = codeOf(DATA_DISCOVERY_IMPL);
        String adapter = codeOf(DATA_BASE_JDBC_ADAPTER);
        assertEquals(1, literalCount(discovery, "throw new IllegalStateException("),
                "发现服务的 ISE 站点数变了＝本批「零生产改动」不成立，须回写台账并同批重测承流三档");
        assertEquals(9, literalCount(adapter, "throw new IllegalStateException("),
                "适配器基类的 ISE 站点数变了＝本批「零生产改动」不成立，须回写台账并同批重测承流三档");
        assertFalse(discovery.contains("DataBridgeException"),
                "死 SPI 面被写上 503 载体＝给无 HTTP 通路的腿伪造状态行（本批裁决明禁的假修）");
        assertFalse(adapter.contains("DataBridgeException"),
                "适配器死面被写上 503 载体＝同上；建连桥那枚的合法修法要先解两级宽 catch 的折叠，"
                        + "而不是在守卫处换个异常类型");
        assertFalse(discovery.contains("ECOS-DATA-05"),
                "死 SPI 面出现本域降级机读码字面量＝号义被挂到一条永不出口的腿上，D.1 单源表与代码脱钩");
        assertFalse(adapter.contains("ECOS-DATA-05"),
                "适配器死面出现本域降级机读码字面量＝同上");
    }

    /**
     * <b>#142/B4-3e-48 通路侧处置的在位门</b>（收口 D-B4-77 的「下线登记」腿）。
     *
     * <p><b>裁决</b>＝<b>不接承流，按「显式停写＋下线登记」处置</b>，三条实测依据：①详设-02 未规定本域
     * 属主端点，接通须先凭空新增路径与签名；②唯一可达通路 {@code testDataSource} 的生产调用方是
     * DQ 自动修复取证链，它把异常当重试轮次文本消费，接通即改既有取证语义；③删除死面撞
     * 「只增不改」＋备份前置，另立余项。</p>
     *
     * <p><b>为什么还要门</b>：停写宣称只写在测试注释里＝读代码的人无从得知此面禁接；
     * 反过来，注释挂上「停写」而真仓已把它接上＝账面在说谎，是 D-B4-62（注释面冒充代码面）的镜像形态。
     * 故把两面<b>互为背书</b>：引用面为 0 时标记不得缺席，引用面非 0 时标记不得仍在——单向下一侧即红。</p>
     */
    @Test
    @Timeout(value = 240)
    @DisplayName("死面停写标记与引用面互为背书：标记缺席即红，接通后仍挂停写亦红（D-B4-77 下线登记）")
    void writeStopMarkersAndUnreachableClaimsCrossCertifyEachOther() {
        boolean discoveryWired = !filesContaining("new SchemaRegistryServiceImpl(").isEmpty();
        checkStopMarkerTracksWiring(DATA_DISCOVERY_IMPL, discoveryWired,
                "死 SPI 面（发现服务的建连器未注入腿）");

        int guardCallSites = 0;
        for (String call : ADAPTER_GUARD_CALLS) {
            guardCallSites += literalCount(codeOf(DATA_ADAPTER_CONSUMER), call);
        }
        checkStopMarkerTracksWiring(DATA_BASE_JDBC_ADAPTER, guardCallSites != 0,
                "适配器取数／写入面（八枚「未连接」守卫腿）");
    }

    /**
     * 停写标记取<b>原文</b>（注释面本来就承载在原文里），可达性取<b>剥注释后的引用面</b>——
     * 两个口径各按各自该看的层面读，混用即「注释冒充代码」。
     */
    private static void checkStopMarkerTracksWiring(String relative, boolean wired, String label) {
        boolean markerInPlace = readRepoFile(relative).contains(DEAD_SURFACE_STOP_MARKER);
        if (wired) {
            assertFalse(markerInPlace, label + "已被接上通路，「" + DEAD_SURFACE_STOP_MARKER + "」却仍在位＝"
                    + "注释宣称的死面与真仓引用面矛盾（镜像的注释冒充代码）。须同批把标记改为「已接通」，"
                    + "并重测承流三档账面与无承流面合计基线");
        } else {
            assertTrue(markerInPlace, label + "在真仓仍无承流路径，但持有文件里找不到「"
                    + DEAD_SURFACE_STOP_MARKER + "」＝下线登记只活在测试注释里，"
                    + "读代码的人无从得知此面禁接（只增不改红线没有载体）");
        }
    }

    /** 字面量出现次数（本门不用正则，避免 {@code (} 等元字符把锚点吃掉）。 */
    private static int literalCount(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
