package com.chinacreator.gzcm.common.base;

import com.chinacreator.gzcm.common.ontology.CaliberDslContract;
import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 五枚单参错误工厂的 <b>机读码缺席棘轮门</b>（#142/B4-3e-27，收口差距 <b>D-B4-65</b>）。
 *
 * <p><b>差距形态</b>：{@link ApiResponse} 的 {@code badRequest/unauthorized/forbidden/notFound/
 * internalError} 五枚单参工厂走三参私有构造器，<b>从不填</b> {@code errorCode}；本类带
 * {@code @JsonInclude(NON_NULL)} ⇒ JSON 面连 {@code errorCode} 这个键都不出现（不是空串、
 * 不是 null 值）。调用方按机读码分派时拿到的是「字段不存在」，与 D-B4-55（把
 * {@code String.valueOf(int)} 当机读码填进去）是同一坑的两半：55 是「填了假的」，本条是
 * 「干脆没填」。同条还顺出第二面：{@code internalError} 的数字码是
 * {@link ApiResponse#CODE_INTERNAL_ERROR}{@code = -1}，与状态行 500 不同号。</p>
 *
 * <p><b>本门钉五件事</b>：
 * <ol>
 *   <li><b>余面量化、只减不增</b>：全后端 {@code src/main/java}（排除 {@code target/}／
 *       {@code archive/}）内单参工厂调用点逐文件计数入基线。新增即红，<b>下降同样即红</b>
 *       （逼后续收口批同批下调基线，防「基线陈旧＝已收口」的假绿，与
 *       {@code SecurityDenialHttpStatusRatchetTest} 同构）。</li>
 *   <li><b>入口锚点已带码且不得回退</b>：gateway {@code GlobalExceptionHandler} 六枚平台入口
 *       兜底腿（400 参数校验／400 参数格式／400 请求体非法 JSON／401 未认证／403 权限不足×2）
 *       必须经新增的带码重载取 {@code SystemErrorCode} 常量；第七枚
 *       {@code handleNotFound} <b>有意保留单参</b>并登记豁免理由（见该腿 javadoc）——
 *       业务域「资源不存在」的码归各域码表，信封类无权替全仓 255 处 {@code notFound}
 *       指定一个平台码，那是用批量替换冒充归因。豁免腿若哪天被补码，本门因「豁免面陈旧」变红，
 *       逼着同批改判据。</li>
 *   <li><b>族码字面量单源</b>：{@code "ECOS-SYS-<digits>"} 串在 main 面只允许
 *       {@code SystemErrorCode} 本类承载，其余任何出现即红（零容忍、不设棘轮）。这条钉的是
 *       「码表抄一份到调用方」形态——本批把 {@code SystemEventTopicsV1Controller}
 *       的 {@code ERROR_SYS_UNAVAILABLE} 副本改指常量即为其取证。</li>
 *   <li><b>重载形态被钉住</b>：五枚 {@code (String errorCode, String message)} 重载必须存在、
 *       数字码取信封既有约定（400/401/403/404），其中带码 {@code internalError} 取
 *       {@link ApiResponse#CODE_SERVER_ERROR}{@code = 500}（同号）；同时钉单参版
 *       {@code internalError} 仍是 -1，防止后来者误以为负数码约定已废除。</li>
 *   <li><b>判据能变红</b>：合成夹具在仓外 {@code @TempDir} 取证，不往仓库塞违例文件。</li>
 * </ol>
 *
 * <p><b>假绿防护</b>：① 扫描根解析 + 实扫文件数阈值（读不到 ≠ 无违例）；② 先经
 * {@link SourceCommentStripper} 剥注释再比对——本类与 {@link ApiResponse} 的 javadoc 里
 * 大量提到 {@code badRequest(...)}，不剥注释会把说明文字计成违例，也会让真实违例被
 * 「注释里也有」的口径稀释（该盲区系 D-B4-40 已登记形态，本门直接复用其单源剥器）。</p>
 */
@Timeout(120)
class NoMachineCodeFactoryFaceRatchetTest {

    /**
     * 判据：单参工厂调用点（{@code ApiResponse.xxx(} 形态，含静态导入后的裸调用不计＝只认限定名）。
     *
     * <p>末段 {@code (?!\s*SystemErrorCode\s*\.)} 把带码重载排除在外——本门追缴的是「机读码缺席」
     * 这一面，带码出口属已收口形态，不能被计成违例，否则 6 枚入口腿迁到带码重载后总数不降，
     * 「只减不增」的口径就失去意义。该排除由 {@link #fixtureCountsSingleArgSitesOnlyAndIgnoresComments}
     * 的合成夹具取证（夹具里带码那一枚若被计入，剥注释后的读数会从 3 变 4）。</p>
     */
    private static final Pattern SINGLE_ARG_FACTORY = Pattern.compile(
            "ApiResponse\\s*\\.\\s*(badRequest|unauthorized|forbidden|notFound|internalError)\\s*\\(\\s*+(?!SystemErrorCode\\s*\\.)");

    /** 带码重载调用点：第二枚实参前必须出现 {@code SystemErrorCode.} 常量引用。 */
    private static final Pattern CODED_FACTORY = Pattern.compile(
            "ApiResponse\\s*\\.\\s*(badRequest|unauthorized|forbidden|notFound|internalError)\\s*\\(\\s*SystemErrorCode\\s*\\.\\s*(ECOS_SYS_\\w+)");

    private static final String HANDLER =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java";

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到／路径变更 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /**
     * 余面基线（#142/B4-3e-27 本批实跑读数，判据＝{@link SourceCommentStripper} 剥注释后逐次计数，
     * 排除 {@code target/}／{@code archive/}／非 {@code src/main/java}，并排除带 {@code SystemErrorCode.}
     * 的带码重载）。改前同判据读数＝{@code 945} 处／{@code 151} 文件（分枚 badRequest 333／
     * unauthorized 14／forbidden 26／notFound 255／internalError 317）；本批把 gateway 入口七枚腿中
     * 六枚迁到带码重载（badRequest −3、unauthorized −1、forbidden −2，{@code handleNotFound} 登记豁免
     * 保留单参）⇒ −6。改前分枚由 {@code git show HEAD} 的 handler 腿清单逐枚对账（7 腿：90/198/260
     * badRequest、100 notFound、147 unauthorized、157/186 forbidden）。
     *
     * <p><b>#142/B4-3e-36 同批下调（−3 处／−1 文件）**：sysman {@code RequirePermissionAspect} 的三枚
     * 拒绝腿（{@code unauthorized} 1／{@code forbidden} 2）由「返回单参信封」整体改为抛
     * {@code UnauthorizedException}／{@code ForbiddenException}——改后该类不再出现在本判据面上，
     * 故文件数一并 −1。这不是「给它们补机读码」，而是<b>换通路</b>：机读码改由承载面（gateway／
     * sysman-boot {@code GlobalExceptionHandler}）经 {@code SystemErrorCode.ECOS_SYS_401/403} 统一注入，
     * 属主侧只携带语义。该文件此前是本枚余面在 sysman 域的唯一持有者，归零＝该域从这条面上退出，
     * 基线若不同批下调，本门会以「基线陈旧」报红（本门双向）。</p>
     *
     * <p><b>#142/B4-3e-53 同批下调（−9 处／0 文件）**：sysman 配置面三文件（{@code ConfigController}
     * ／{@code SystemParamController}／{@code SysConfigController}）的 15 枚「依赖未装配」腿由
     * 返回单参 {@code internalError} 信封改为抛 {@code DataBridgeException(503, …, ECOS-SYS-503)}
     * 承载（差距 <b>D-B4-83</b> 收口件）。−9 而非 −15：其中 6 枚原本就在 {@code catch} 内表达
     * 真实服务故障，不属本判据的迁移面。文件数不变＝三文件各自仍持有 {@code internalError} 兜底腿，
     * 只是不再用它掩盖缺装配。</p>
     *
     * <p><b>#142/B4-3e-56 同批下调（−2 处／0 文件）**：security 属主 {@code AuditController} 的
     * 「审计服务未就绪」折叠腿（{@code get}／{@code writeLog} 各 1 枚
     * {@code return ApiResponse.internalError("审计服务未就绪")}）改为抛
     * {@code DataBridgeException(503, 503, ECOS-SEC-501)} 承载（差距 <b>D-B4-86</b> 收口件）。
     * 本批实际迁移的未装配腿有 6 枚，但入本判据面的只有这 2 枚：另 4 枚折叠形态是
     * {@code 200＋空集}／{@code 200＋全 0 计数}／{@code 200＋valid=false}（走 {@code success(...)}，
     * 不在单参无机读码工厂面内），由 {@code AuditUnavailableCarrierTest} 的源码面判据钉零。
     * 文件数不变＝该类仍持 6 枚 {@code catch} 内真实故障兜底 {@code internalError}，
     * 那是与缺装配不同的态，本批不动。读数取自本批门禁会话内 {@code -pl runtime/common-api} 全量实跑
     * 的失败报文（{@code sites=925, files=150, internalError=306}），非由旧值做算术推出；
     * 算式 927−2＝925 与 308−2＝306 只用作差异归因，归因与实跑读数不符时以实跑读数为准。</p>
     *
     * <p><b>#146p 同批上调（+3 处／+1 文件，前置补账）</b>：本批（kb 抽取面 Vol-7 第 12/13 条）
     * 自身在该判据面上<b>零新增</b>（新控制器只用 {@code ApiResponse.<T>badRequest/notFound} 类型见证形态，
     * 不被 {@link #SINGLE_ARG_FACTORY} 命中）。上调的三枚全部来自已提交的 #146o／#146o-2
     * （ai 会话运维链，提交时未跑本横切门），逐枚实测对账＝{@code b01b2b00..HEAD} 主源码面
     * 同判据计数差：{@code AgentLoopController} 4→5（{@code ApiResponse.notFound("会话 " + id + " 不存在")}
     * 一枚，裸 404 退出）、{@code AgentSessionOpsController} 0→1、{@code AgentToolController} 2→3
     * （后两者同为 {@code ApiResponse.forbidden(AiErrorClass.GUARDRAIL_DENIED.errorCode(), …)}——
     * <b>带码</b>但机读码取域内 {@code AiErrorClass} 而非 {@code SystemErrorCode.}，落不进豁免面，
     * 属判据「只认 SystemErrorCode」的已知边界面，不改判据）；⇒ 分枚
     * {@code forbidden 22→24}、{@code notFound 255→256}，合计 928。读数取自本批门禁会话内
     * {@code -pl runtime/common-api} 全量实跑的失败报文（{@code sites=928}，分枚 330/12/24/256/306），
     * 非由旧值做算术推出。文件数 {@code 150→151}：{@code AgentSessionOpsController} 此前 0 命中、
     * 本批首次进入判据面（该类经 #146o-2 会话运维链新增一枚 {@code forbidden(AiErrorClass…)}）；
     * 另两枚落在既有命中文件内，不再贡献文件数——该读数同样取自上述实跑报文
     * （{@code sites=928, files=151}），不是从「+1 处 ⇒ +? 文件」推出。</p>
     */
    private static final int BASELINE_SITES = 928;
    private static final int BASELINE_FILES = 151;

    /** 分枚基线：只减不增，且合计必须等于 {@link #BASELINE_SITES}（防「拆细了但总数对不上」）。 */
    private static final Map<String, Integer> BASELINE_BY_FACTORY = new TreeMap<>(Map.of(
            "badRequest", 330,
            "unauthorized", 12,
            "forbidden", 24,
            "notFound", 256,
            "internalError", 306));

    /** gateway 入口面已带码的六枚腿（锚点，不得回退）。 */
    private static final int ENTRY_LEGS_CODED = 6;

    /** {@code ECOS-SYS-*} 族码字面量（带引号的串），单源只允许 {@link #SYSTEM_ERROR_CODE_SOURCE} 承载。 */
    private static final Pattern SYS_FAMILY_LITERAL = Pattern.compile("\"ECOS-SYS-\\d+\"");

    /** 族码常量单源文件（唯一允许出现 {@code "ECOS-SYS-<digits>"} 字面量的 main 面文件）。 */
    private static final String SYSTEM_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/SystemErrorCode.java";

    /**
     * {@code ECOS-RATE-*} 族码字面量（带引号的串），单源只允许 {@link #RATE_LIMIT_ERROR_CODE_SOURCE} 承载。
     *
     * <p><b>#142/B4-3e-92 新登</b>（收口差距 P1.1「ECOS-RATE 族不存在」子项）：本批把
     * {@code RateLimitFilter} 与 {@code QuotaFilter} 两枚 429 出口改指 {@code RateLimitErrorCode}
     * 常量后，仿 sysFamily 的字面量零容差判据防止后续收口批把族码再复制到任何调用方。</p>
     */
    private static final Pattern RATE_FAMILY_LITERAL = Pattern.compile("\"ECOS-RATE-\\d+\"");

    /** {@code ECOS-RATE-*} 族码字面量单源文件（唯一承载点）。 */
    private static final String RATE_LIMIT_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/RateLimitErrorCode.java";

    /**
     * 属主两枚 429 出口的判据：该两文件必须各出现一次 {@code RateLimitErrorCode.ECOS_RATE_<n>}
     * 常量引用（合法出口＝改指向常量）。校验文本已剥注释，防止 javadoc 提及被误判。
     */
    private static final Pattern RATE_CODE_REFERENCE = Pattern.compile(
            "RateLimitErrorCode\\s*\\.\\s*ECOS_RATE_\\d+");

    /** 属主出口文件（两枚 429 腿的合法性锚点，缺一即红＝判据面漂移）。 */
    private static final String RATE_LIMIT_FILTER_SOURCE =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/RateLimitFilter.java";

    /** 属主出口文件（配额耗尽两腿的合法性锚点）。 */
    private static final String QUOTA_FILTER_SOURCE =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/QuotaFilter.java";

    /**
     * {@code ECOS-WF-*} 族码字面量（带引号的串），单源只允许 {@link #WORKFLOW_ERROR_CODE_SOURCE} 承载。
     *
     * <p><b>#146y 新登</b>（收口差距附录A 第 10 条）：本批把 {@code ECOS-WF-020}
     * （详设 §6.3.3 在册唯一一枚 WF 码）首次落进代码，新建 {@code WorkflowErrorCode} 属主，
     * buszhi {@code WorkflowInstanceApprovalController} 的 500 出口取常量引用。
     * 仿 sysFamily/rateFamily 的零容差判据，防止后续批次把 WF 族码复制进调用方，
     * 也防止「族里凭空多一枚未经 §6.3.3 裁决的码」。</p>
     */
    private static final Pattern WF_FAMILY_LITERAL = Pattern.compile("\"ECOS-WF-\\d+\"");

    /** {@code ECOS-WF-*} 族码字面量单源文件（唯一承载点）。 */
    private static final String WORKFLOW_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/WorkflowErrorCode.java";

    /**
     * WF 族出口判据锚：buszhi 实例级审批面必须引用 {@code WorkflowErrorCode.ECOS_WF_020}
     * 常量（合法出口＝取属主常量，非自造字面量）。
     */
    private static final Pattern WF_CODE_REFERENCE = Pattern.compile(
            "WorkflowErrorCode\\s*\\.\\s*ECOS_WF_\\d+");

    /** 属主出口文件（{@code ECOS-WF-020} 唯一承流腿，缺失即红＝单源常量无人消费＝假单源）。 */
    private static final String WORKFLOW_INSTANCE_APPROVAL_SOURCE =
            "ecos_backend/services/buszhi/impl/buszhi-impl/src/main/java/com/chinacreator/gzcm/"
                    + "buszhi/workflow/controller/WorkflowInstanceApprovalController.java";

    /**
     * {@code ECOS-(DATA|DQ)-*} 族码字面量（带引号的串），单源只允许 {@link #DATA_DOMAIN_ERROR_CODE_SOURCE}
     * 承载。
     *
     * <p><b>#142/B4-3e-93 新登</b>（收口差距 P1.1「DATA/DQ 族字面量散落」子项）：与
     * {@link #SYS_FAMILY_LITERAL}／{@link #RATE_FAMILY_LITERAL} 同型的零容差判据。判据锚
     * <b>引号起点族前缀</b>（引号-族前缀-数字），把「独立整枚」与「族码＋详情」两形态
     * 一并钉住——前者如裸 {@code "ECOS-DATA-011"}，后者即消息前缀拼接面（如
     * {@code "ECOS-DATA-023：" + msg}）。本批已实扫 main 面将全部非属主副本（6 枚独立
     * 字面量：PipelineTaskController 011／PipelineServiceImpl 012+013／WriteChannelController
     * 055／FactImportService 幽灵 000 归位 023／DataNetResourceClient 041；加 5 枚消息前缀
     * 拼接腿：FactController 023／ForecastRunSnapshotService 023×2／KnowledgeDocIngestService
     * 041／KbEntityInstanceExtractionService 041）改指 {@code DataDomainErrorCode} 常量后，
     * 全仓 main 面族码字面量应恒 0 枚，故直接零容忍（不设棘轮基线）。
     * 注释提及不算（判据面先过 {@code SourceCommentStripper.strip}）。</p>
     */
    private static final Pattern DATA_FAMILY_LITERAL = Pattern.compile("\"ECOS-(?:DATA|DQ)-\\d+[^\\\"]*\"");

    /** {@code ECOS-DATA-*}／{@code ECOS-DQ-*} 族码字面量单源文件（唯一承载点，22 枚枚定值）。 */
    private static final String DATA_DOMAIN_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/data/DataDomainErrorCode.java";

    /**
     * {@code ECOS-AUTH-0xx} 族码字面量（带引号的串，含数字中段任意前缀文本），单源只允许
     * {@link #AUTH_ERROR_CODE_SOURCE} 承载。
     *
     * <p><b>#142/B4-3e-94 新登</b>（收口差距 P1.1「AUTH 序号 002~011 口径」面词归一子项）：
     * 与 {@link #SYS_FAMILY_LITERAL}／{@link #RATE_FAMILY_LITERAL}／{@link #DATA_FAMILY_LITERAL}
     * 同型的零容差判据。判据锚 <b>引号起点族前缀</b>（引号-{@code ECOS-AUTH-}-数字），把
     * 「独立整枚」与「消息前缀拼接」两形态一并钉住——9 枚 0xx 码（001~006/010/011/020）
     * 属详设-00 §C.2.5 冻结表在册码，本批只归一承载不裁决改号（0xx 细分语义码与
     * 401/403/503 分态状态行双口径并行不悖，D-B4-65 族归并属另批）。本批已实扫 main 面
     * 将全部 14 枚非属主副本（13 枚引号起点 + 1 枚 DecisionController 消息中段拼接腿
     * {@code "无用户上下文（" + ECOS_AUTH_010 + "）"}）改指 {@code AuthErrorCode} 常量，
     * 改后全仓 main 面族码字面量应恒 0 枚，故直接零容忍（不设棘轮基线）。
     * 注释提及不算（判据面先过 {@code SourceCommentStripper.strip}）。</p>
     */
    private static final Pattern AUTH_FAMILY_LITERAL = Pattern.compile("\"ECOS-AUTH-\\d[^\\\"]*\"");

    /** {@code ECOS-AUTH-0xx} 族码字面量单源文件（唯一承载点，9 枚枚定值）。 */
    private static final String AUTH_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/AuthErrorCode.java";

    /**
     * {@code ECOS-SEC-*} 族码字面量（带引号的串，数字段后续允许任意前缀文本），
     * 按枚对账只允许登记属主承载。
     *
     * <p><b>#142/B4-3e-95 新登</b>（收口差距 P1.1「SEC 族未定义码位」子项的纠错面）：
     * 《从零实施版》§4.3.3 表（+文末错误码清单）已把 501/502/401/402/410/411/412/420/430/440
     * 十枚<b>全部在册</b>——台账原句按详设-00 取证只登 501 所指，被《从零实施版》文档面纠正；
     * 「未定义」不成立，本批真身是字面量面散落。判据锚<b>引号起点族前缀</b>
     * （引号-{@code ECOS-SEC-}-数字），「独立整枚」与「消息中段拼接」两形态一并钉住。
     * 与 sys/rate/data/auth 四腿的差异在于<b>逐枚对照登记属主白名单</b>：
     * ① 族属主内 9 枚枚定值（401/402/410/411/412/420/430/440/502）及 501（授值别名）
     * 的带引号字面量只允许 {@link #SECURITY_ERROR_CODE_SOURCE} 承载——其中 501 在族属主内
     * 是授值别名无字面量，其字面量单点归 ②；② 501 只允许 {@link #RLS_CONTRACT_SOURCE} 承载
     * （本族属主经 {@code SecurityErrorCode.ECOS_SEC_501 = RlsExecutionContract.SEC_UNAVAILABLE_CODE}
     * <b>授值</b>非复制，故 501 的字面量仍单点留在 RLS 契约——
     * {@code GuardrailObligationVocabularySingleSourceTest#secUnavailableCodeHasSingleOwner}
     * 的 501 单源判据与 RLS 面判据不冲突）；③ 520 是 §4.3.3 表外的裁决段码，属主留在 RLS，
     * 不把它的字面量搬进本族类，但纳入本判据逐枚对账防复制。未登记码位（含
     * 403——实测仅存 sysman-boot test 面 fixture，main 面 0 枚）在主源码露面即红。
     * 测试方法侧另有<b>属主面回读</b>：每枚登记码必须在至少一个登记属主内仍有字面量锚点
     * （防「属主把值删了＝零违例假绿」，与 {@code GuardrailObligationVocabularySingleSourceTest}
     * 假绿防护③同纪律）。注释提及不算（先过 {@code SourceCommentStripper.strip}）；
     * 扫描面只覆盖 {@code src/main/java}，test 面形态夹具（{@code MachineErrorCodeTest} 的
     * 5011/50112 四位/五位反例）天然不在判据内。</p>
     */
    private static final Pattern SEC_FAMILY_LITERAL = Pattern.compile("\"ECOS-SEC-(\\d+)[^\"]*\"");

    /** SEC 族字面量族属主文件（9 枚枚定值；501 授值自 RLS、520 不在本族）。 */
    private static final String SECURITY_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/SecurityErrorCode.java";

    /** SEC 族中字面量仍归 RLS 契约的承载文件（501 授值源头 + 520 属主）。 */
    private static final String RLS_CONTRACT_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/rls/RlsExecutionContract.java";

    /** 逐枚登记：码 → 允许持有其字面量的 main 面文件（仓根相对路径；白名单只减不增，新增须走裁决批）。 */
    private static final Map<String, List<String>> SEC_LITERAL_HOLDERS = new TreeMap<>(Map.ofEntries(
            Map.entry("401", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("402", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("410", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("411", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("412", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("420", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("430", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("440", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("501", List.of(RLS_CONTRACT_SOURCE)),
            Map.entry("502", List.of(SECURITY_ERROR_CODE_SOURCE)),
            Map.entry("520", List.of(RLS_CONTRACT_SOURCE))));

    /**
     * {@code ECOS-ONTO-*} 族码字面量——<b>位置不限</b>判据（本族与 sys/rate/data/auth/sec 五腿的
     * 唯一判据差异），按 (码, 文件) 双键对账只允许登记属主承载。
     *
     * <p><b>#146af 新登</b>（收口差距 P1.1「错误码族字面量散落」的本体域腿）：本族 12 枚在册码
     * 此前以字面量副本散布在 ontology-engine-impl 的 8 个 main 面文件，common-api 侧零属主。
     * 已有五腿的判据锚都是<b>引号起点</b>（{@code "ECOS-XXX-\d+..."}），只能吃「独立整枚」与
     * 「消息起始位拼接」两形态；本体域实测另有两种形态落在引号中段——异常/信封 message
     * 的<b>人读括号</b>（{@code "...(400 ECOS-ONTO-040)"}，{@code MachineErrorCode} 正则只认
     * 起始位，故该形态本就不是机读载体）与<b>日志格式串</b>（{@code log.error("... fail-closed
     * ECOS-ONTO-050", …)}）。引号起点判据对这两形态<b>永不可见</b>＝值可被改写而门恒绿。本族判据
     * 因此去掉引号锚，凡剥注释后露面即对账；改后 8 文件的这些中段副本全部改指
     * {@code OntologyErrorCode} 常量（消息文本逐字节不变，仅字面量来源归一）。</p>
     *
     * <p>末段 {@code (?![\dA-Za-z])} 用于把文档态简写排除在册枚计数之外：{@code ECOS-ONTO-0xx}
     * （族前缀＋单数字＋x）与 {@code ECOS-ONTO-07x} 若不加此限制，会被 {@code \d{2,4}} 回切成
     * 「07」这类伪码位；实扫 main 面两枚简写只在注释/文档串露面，剥注释后本已不计，加此限制
     * 是防未来有人把简写写进代码。<b>已知不可吃形态</b>：{@code ECOS-ONTO-publish-gate-failed}
     * （非数字段，属消息前缀文案而非码位，登记在册说明见该方法 javadoc）。</p>
     */
    private static final Pattern ONTO_FAMILY_LITERAL = Pattern.compile("ECOS-ONTO-(\\d{2,4})(?![\\dA-Za-z])");

    /** ONTO 族字面量族属主文件（《从零实施版》§6.3.3 在册 13 枚枚定值）。 */
    private static final String ONTOLOGY_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/OntologyErrorCode.java";

    /** 表外成功标记 000 的属主（§6.3.3 无此枚，留在原处待裁决，禁搬进族属主类）。 */
    private static final String ONTO_CALIBER_CONTROLLER_SOURCE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/ontology/controller/CaliberController.java";

    /** 口径 DSL 四关机读码属主（071/072/073 为该类私设号段，§6.3.3 未在册；070 与术语枚同位冲突）。 */
    private static final String ONTO_CALIBER_DSL_VALIDATOR_SOURCE =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/ontology/service/CaliberDslValidator.java";

    /**
     * workspace 侧 DSL 门禁解码表（跨引擎 REST 契约的<b>消费面</b>第二份字面量）。
     *
     * <p>#146ah 起解码键改取 common-api {@code CaliberDslContract} 常量单源（本表 path 常量
     * 保留为判据锚点：消费文件仍在面内，只是字面量来源归一）。#146af 原裁决「至多两枚
     * 属主」的前提（workspace-impl 对 ontology-engine-impl 无编译依赖）已被该单源腿取代：
     * workspace-impl 直接依赖 common-api、ontology-engine-impl 经 runtime-core 传递可达，
     * 零新增 Maven 依赖即可真单源。</p>
     */
    private static final String ONTO_CALIBER_ADDRESSING_SOURCE =
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/caliber/"
                    + "CaliberAddressing.java";

    /**
     * 口径 DSL 四关（闸名 DSL_V1~V4 + 机读码 070~073）跨模块契约单源（#146ah 收口 r5）。
     *
     * <p>该属主面同时承载 070/071/072/073 四枚字面量（070 与 {@code OntologyErrorCode}
     * 登记在册的面为<b>同名不同语义</b>：那边＝术语悬空 F6-11 锚点，这边＝DSL V1 借用面值
     * 的现行 wire 映射，两枚面各自回读各自语义，r2 语义冲突裁决不受本判据牵连）。</p>
     */
    private static final String CALIBER_DSL_CONTRACT_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/ontology/"
                    + "CaliberDslContract.java";

    /**
     * 逐枚登记：码位 → 允许持有其字面量的 main 面文件（白名单只减不增，新增须走裁决批）。
     *
     * <p>#146ah 订正（原文不删）：070~073 随 r5 收口改由 {@code CaliberDslContract} 单源承载，
     * {@code CaliberDslValidator}／{@code CaliberAddressing} 从字面量属主降级为纯消费方
     * （字面量退至 0 枚，由 {@link #caliberDslContractExitsPointToSingleSource} 出口侧基线接管）。</p>
     */
    private static final Map<String, List<String>> ONTO_LITERAL_HOLDERS = new TreeMap<>(Map.ofEntries(
            Map.entry("000", List.of(ONTO_CALIBER_CONTROLLER_SOURCE)),
            Map.entry("011", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("012", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("021", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("022", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("030", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("040", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("041", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("042", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("050", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("060", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("061", List.of(ONTOLOGY_ERROR_CODE_SOURCE)),
            Map.entry("070", List.of(ONTOLOGY_ERROR_CODE_SOURCE, CALIBER_DSL_CONTRACT_SOURCE)),
            Map.entry("071", List.of(CALIBER_DSL_CONTRACT_SOURCE)),
            Map.entry("072", List.of(CALIBER_DSL_CONTRACT_SOURCE)),
            Map.entry("073", List.of(CALIBER_DSL_CONTRACT_SOURCE)),
            Map.entry("080", List.of(ONTOLOGY_ERROR_CODE_SOURCE))));

    /**
     * ONTO 族出口侧的常量引用判据锚：8 个改指文件必须各自仍在取 {@code OntologyErrorCode} 常量。
     *
     * <p>族属主面绿（字面量只在属主内）并不等于出口真的在用常量——出口若哪天改回裸文案，
     * 字面量面照样干净，单源就断在消费侧。逐文件<b>恰 N 枚</b>（#146af 本批实跑读数）：
     * 少＝出口漂移回字面量或漏腿，多＝同一出口重复取码／新增副本改指须同批重设本表。</p>
     */
    private static final Pattern ONTO_CODE_REFERENCE = Pattern.compile(
            "OntologyErrorCode\\s*\\.\\s*ECOS_ONTO_\\d+");

    /** 属主出口文件 → 应出现的常量引用枚数（本批实跑逐文件计数，非估算）。 */
    private static final Map<String, Integer> ONTO_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.of(
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/controller/CaliberController.java", 5,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/controller/FunctionController.java", 2,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/controller/OntologyMetricBindingController.java", 3,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java", 2,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/dto/GateResult.java", 6,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/service/CaliberService.java", 3,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/service/DatNetLineageGuard.java", 3,
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/service/PublishGateService.java", 1));

    /** 登记豁免：唯一保留单参的入口腿。 */
    private static final List<String> REGISTERED_ENTRY_EXEMPTIONS = List.of("handleNotFound");

    // ── 口径 DSL 四关跨模块契约单源面（#146ah 收口 ONTO r5）─────────────────

    /**
     * DSL 契约消费侧判据锚：四处消费文件必须各自仍在取 {@code CaliberDslContract.} 常量。
     *
     * <p>与 {@link #ONTO_CODE_REFERENCE} 分工不同：那边钉的是「§6.3.3 在册码的出口」，
     * 这边钉的是「DSL 四关结构性映射的出口」——若消费文件哪天改回裸 {@code "DSL_V1_SYNTAX"}
     * 字面量，ONTO 族字面量面照样干净（DSL 闸名不在数字码族正则覆盖内），<b>只有本腿</b>
     * 读得见。双表互为镜像 ⇒ 任何一侧字面量/常量面漂移都会撞对称断言或此处基线。</p>
     */
    private static final Pattern CALIBER_DSL_CONTRACT_REFERENCE =
            Pattern.compile("CaliberDslContract\\s*\\.\\s*[A-Z][A-Z0-9_]+");

    /**
     * DSL 契约消费侧基线 = 本批实跑逐文件读数（非估算）：
     * <ul>
     *   <li>ontology 写出面 {@code CaliberDslValidator}：4 闸名授值＋4 码授值＝8 枚；</li>
     *   <li>workspace 解码面 {@code CaliberAddressing}：OWNER_DSL_GATE 四键八值＝8 枚；
     *       {@code CaliberFormulaInvalidException}／{@code CaliberReadService} 四闸词表＝各 4 枚。</li>
     * </ul>
     * 少＝消费面静默改回字面量（治标于命名漂移的回流），多＝新增取码腿须同批重设本表。
     */
    private static final Map<String, Integer> CALIBER_DSL_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/"
                    + "chinacreator/gzcm/engine/ontology/service/CaliberDslValidator.java", 8),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/caliber/CaliberAddressing.java", 8),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CaliberFormulaInvalidException.java", 4),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/caliber/CaliberReadService.java", 4)));

    /**
     * 四个消费文件的契约 import 面：单源成立的前提是消费真的经 {@code CaliberDslContract} 取面值。
     * 改指域外同面值常量后裸文案回读「假绿」时（外观与常量引用不可分辨），<b>只有 import 那行</b>
     * 读得见域内/域外——出口基线管「改指域外同值常量」，本腿管「改回裸串」。</p>
     */
    private static final Pattern CALIBER_DSL_CONSUMER_IMPORT = Pattern.compile(
            "import\\s+com\\.chinacreator\\.gzcm\\.common\\.ontology\\s*\\.\\s*CaliberDslContract\\s*;");

    /** pom 双向可达面（#146ah 前提锚）：单源在 common-api，两个消费模块须各自依赖可达。 */
    private static final String WORKSPACE_IMPL_POM =
            "ecos_backend/workspace/workspace-impl/pom.xml";

    private static final String ONTO_ENGINE_IMPL_POM =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/pom.xml";

    // ── ECOS-COG-* 族单源面（#146ag）─────────────────

    /**
     * COG 族码字面量判据：三位数字 + <b>可选大写字母后缀</b>（400M/403S/409V/422G/429B/502D…），
     * 后缀后不得再接字母数字。
     *
     * <p>ONTO 族的 {@code (\\d{2,4})} 形态在本族不可用——COG 在册码位有 23/31 枚带字母后缀，
     * 只匹配纯数字会把 {@code ECOS-COG-403S} 整枚读成未命中 ⇒ 假绿。位置不限（引号内、
     * message 中段、拼接片段一律入账）。</p>
     */
    private static final Pattern COG_FAMILY_LITERAL =
            Pattern.compile("ECOS-COG-(\\d{3}[A-Z]?)(?![\\dA-Za-z])");

    /** COG 族属主 = 认知引擎 api 侧冻结表（非 common-api：本族先于 P1.1 单源系列存在）。 */
    private static final String COG_ERROR_CODE_SOURCE =
            "ecos_backend/engine/cognitive-engine/cognitive-engine-api/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive/api/CognitiveErrorCode.java";

    /**
     * workspace 侧 501 探测腿（#146ag 登记的<b>跨模块第二承载面</b>）。
     *
     * <p>{@code workspace-impl/pom.xml} 依赖面 = common-api + runtime 底座，<b>无</b>
     * cognitive-engine-api（该侧经 HTTP 契约面判认知应答，不建编译耦合），故此枚只能自带字面量。
     * 风险由本判据兜住：属主改 {@code E503_NOT_IMPLEMENTED} 时，属主码位集腿与
     * {@link #COG_UNREGISTERED_LITERAL_HOLDERS} 回读同时变红，逼使两侧同批对账，
     * 禁静默失配（该腿失效＝stub 应答被读成成功）。彻底消除需把 COG 码族下沉 common-api，待裁决。</p>
     */
    private static final String COG_FORECAST_GATEWAY_SOURCE =
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/forecast/CognitiveForecastRunGateway.java";

    /** context 子域异常（#146ag 唯一保留字面量枚的承载面）。 */
    private static final String COG_CONTEXT_BUILD_EXCEPTION_SOURCE =
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/service/context/ContextBuildException.java";

    /** 在册 31 枚码位 → 允许持有其字面量的 main 面文件（只减不增，新增须先在设计单源登记）。 */
    private static final Map<String, List<String>> COG_LITERAL_HOLDERS = new TreeMap<>(Map.ofEntries(
            Map.entry("400", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400C", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400F", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400M", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400O", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400P", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("400S", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("403", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("403S", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404A", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404D", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404F", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404O", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404R", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("404U", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("409R", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("409S", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("409U", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("409V", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("422C", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("422F", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("422G", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("422R", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("429B", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("500", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("501", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("502", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("502D", List.of(COG_ERROR_CODE_SOURCE)),
            Map.entry("503", List.of(COG_ERROR_CODE_SOURCE, COG_FORECAST_GATEWAY_SOURCE)),
            Map.entry("504", List.of(COG_ERROR_CODE_SOURCE))));

    /**
     * <b>未登记</b>码位的显式承载面（本族唯一一枚）：{@code ECOS-COG-503D}。
     *
     * <p>取证（#146ag）：该枚不在《从零实施版》COG 冻结表内，属主里"必需依赖不可用"登记的是
     * {@code E502_DEPENDENCY_DOWN = "ECOS-COG-502D"}；但 503D 已被跨语言契约三面绑定——
     * {@code ecos_frontend/src/locales/workspace/{zh-CN,en}.json} 的
     * {@code workspace.ctx.err.engine} 文案、{@code ecos_frontend/src/services/cognitiveClient.ts}
     * 的失败语义注释、{@code ecos-tests/tests/e2e/workspace/cognition-surfaces-live.spec.ts}
     * 的 503 断言。改值＝契约变更，须先在设计单源登记该码位（或裁决迁 502D），
     * 故本批保留字面量并在此显式登记为余项；属主一旦补登该枚，
     * {@link #cogFamilyLiteralsAreSingleSourced()} 的属主码位集腿即变红，逼使两处同批对账。</p>
     */
    private static final Map<String, List<String>> COG_UNREGISTERED_LITERAL_HOLDERS =
            Map.of("503D", List.of(COG_CONTEXT_BUILD_EXCEPTION_SOURCE));

    /**
     * COG 族消费侧锚点（#146ag）：五处改指文件必须逐文件<b>恰 N 枚</b>
     * {@code CognitiveErrorCode.E<三位>} 常量引用（实跑读数，非估算）。
     *
     * <p>只登记本批改指面——既有消费者（各 Controller／Advice 早就是常量取用方）不纳入本基线，
     * 否则任何一次正常加腿都会把门拖成常红。少＝出口漂移回字面量（假单源），多＝重复取码或
     * 新增改指腿未同批重设本表。</p>
     */
    private static final Pattern COG_CODE_REFERENCE =
            Pattern.compile("CognitiveErrorCode\\s*\\.\\s*E[0-9]");

    /** 改指文件 → 应出现的属主常量引用枚数。 */
    private static final Map<String, Integer> COG_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/service/scenario/"
                    + "CognitiveGuardException.java", 30),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/service/context/"
                    + "ContextBuildException.java", 5),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/dao/HypothesisReader.java", 2),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/dao/BeliefReader.java", 1),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/cognitive2/service/decision/"
                    + "DecisionBasisService.java", 6)));

    // ── ECOS-AI-* 族单源面（#146ai）─────────────────

    /**
     * AI 族码字面量判据（#146ai）：数字码位＝3 位数字＋<b>可选大写字母后缀</b>（400C/500P），
     * 与 COG 族同型；另有 1 枚<b>非数字形态码</b> {@code ECOS-AI-AGENT-RUNTIME-ABSENT}
     * （装配形态缺失的失败态命名码，不对应 HTTP 状态行序号）单列判据。
     */
    private static final Pattern AI_FAMILY_LITERAL =
            Pattern.compile("ECOS-AI-(\\d{3}[A-Z]?)(?![\\dA-Za-z-])");

    /** 非数字形态码判据（AGENT-RUNTIME-ABSENT；尾段 (?![A-Za-z0-9]) 防前缀误读）。 */
    private static final Pattern AI_RUNTIME_ABSENT_LITERAL =
            Pattern.compile("ECOS-AI-AGENT-RUNTIME-ABSENT");

    /** AI 族属主＝common-api 七类矩阵枚举（12 枚数字码位的字面量唯一合法家）。 */
    private static final String AI_ERROR_CLASS_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/"
                    + "common/error/AiErrorClass.java";

    /** 非数字形态码唯一合法持有面＝属主类自身（4 枚控制器改指后 main 面副本归零）。 */
    private static final Map<String, List<String>> AI_ALPHANUMERIC_LITERAL_HOLDERS = new TreeMap<>(Map.of(
            "ECOS-AI-AGENT-RUNTIME-ABSENT", List.of(AI_ERROR_CLASS_SOURCE)));

    /**
     * 改指文件 → 应出现的 {@code AiErrorClass.} 属主引用枚数（#146ai 本批实跑读数）。
     * 少＝出口漂移回裸串（假单源）；多＝重复取码或新增改指腿未同批重设本表。
     */
    private static final Pattern AI_CODE_REFERENCE =
            Pattern.compile("AiErrorClass\\s*\\.");

    private static final Map<String, Integer> AI_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/controller/AgentChatController.java", 2),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/controller/EvolutionController.java", 2),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/controller/SkillController.java", 1),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/controller/CronJobController.java", 1),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/controller/AgentSessionOpsController.java", 19)));

    // ── ECOS-KB-* 族单源面（#146at）─────────────────

    /**
     * KB 族码字面量判据（#146at 补落，P1.1 族队列收口位）：三位纯数字码位（011~080 业务码
     * 段＋500/501 承流码段），位置不限——引号以外（消息中段、日志格式串、单参文案）与
     * AI/ONTO/COG 族同口径。末段 {@code (?![\dA-Za-z])} 与 ONTO 腿同法，防简写/变长回切伪码位。
     */
    private static final Pattern KB_FAMILY_LITERAL = Pattern.compile("ECOS-KB-(\\d{3})(?![\\dA-Za-z])");

    /** KB 族业务码属主＝kb-engine 码表常量类（18 枚《从零实施版》§7.3.3 冻结码＋ 024/073 代码面补登）。 */
    private static final String KB_ERROR_CODES_SOURCE =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/support/KbErrorCodes.java";

    /**
     * KB 族承流面属主＝kb 局部异常渲染 advice（W94 三级掩蔽链第 1 级）。
     * 自持三枚字面量：050（表缺失兜底复用码表值）、500（无码领域异常回退）、
     * 501（别域 DataBridgeException 域归属码）。
     */
    private static final String KB_ERROR_ADVICE_SOURCE =
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/support/KbErrorAdvice.java";

    /**
     * 逐枚登记：码位 → 允许持有其字面量的 main 面文件（#146at 实扫基线，白名单只减不增）。
     *
     * <p>归属口径：§7.3.3 冻结表在册 18 枚（011/012/020/021/022/023/030/031/040/041/
     * 050/051/060/061/062/070/071/072/080）＋024、073 两枚<b>代码面补登</b>（§7.3.3 未在册、
     * 属主类 javadoc 已逐枚登记补登缘由，文档侧裁定属详设表格面、不另立批）＋500/501 两枚
     * <b>承流面自持</b>（501＝别域桥异常渲染域归属，域归属证据走机读码不取别族值；
     * 500＝无码领域异常回退，号位占本族段非引用）。本批字面量面实扫 <b>非属主副本恒 0</b>，
     * 故零容差直查；KB 族消费面 25 枚文件取的是 {@code KbErrorCodes.X.exception(msg)}
     * 枚举常量而非码值字面量，出口侧基线腿另立批次（本批不扩面生产文件）。</p>
     */
    private static final Map<String, List<String>> KB_LITERAL_HOLDERS = new TreeMap<>(Map.ofEntries(
            Map.entry("011", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("012", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("020", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("021", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("022", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("023", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("024", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("030", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("031", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("040", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("041", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("050", List.of(KB_ERROR_CODES_SOURCE, KB_ERROR_ADVICE_SOURCE)),
            Map.entry("051", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("060", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("061", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("062", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("070", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("071", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("072", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("073", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("080", List.of(KB_ERROR_CODES_SOURCE)),
            Map.entry("500", List.of(KB_ERROR_ADVICE_SOURCE)),
            Map.entry("501", List.of(KB_ERROR_ADVICE_SOURCE))));

    /** 属主面在册码位集（与 {@link #KB_LITERAL_HOLDERS} 键集对账用，双向 equals）。 */
    private static final Set<String> KB_REGISTERED_CODES = new TreeSet<>(KB_LITERAL_HOLDERS.keySet());

    // ── ECOS-FC-* 族单源面（#146ak，台账附录C 第 8 条）─────────────────

    /**
     * FC 族机读码字面量判据：引号起点 + 三位码位（400/403/404/409/503）。
     *
     * <p><b>#146ak 新登</b>（收口差距附录C 第 8 条「第12篇 ECOS-FC-* 全族 0 命中」）：
     * 治理时点 main 面带引号机读码 0 枚（台账销项判据），信封真身＝
     * {@code WorkspaceDomainException#getCode()} 的语义 code，机读码族前缀在信封面
     * 无生产锚（补锚＝API 变更，铁律 #10，本批不做）。本批把冻结表 5 枚码位字面量
     * 首次落进 {@code FCScenarioErrorCode} 属主本类；本腿＝与 sys/rate/data/auth 腿
     * 同型的零容差判据：属主字面量面之外出现任意一枚带引号 {@code "ECOS-FC-\d{3}"}
     * 即红，不设棘轮基线（新增即红）。改后全仓 main 面副本应恒 0 枚。</p>
     */
    private static final Pattern FC_FAMILY_LITERAL = Pattern.compile("\"ECOS-FC-(\\d{3})\"");

    /** FC 族机读码属主（common-api 常量类；5 枚码位字面量唯一合法家）。 */
    private static final String FC_SCENARIO_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/"
                    + "FCScenarioErrorCode.java";

    /** 冻结表（《从零实施版》附录C 第12篇 10411~10421 行）在册码位集（与属主面 equals 对账）。 */
    private static final Set<String> FC_REGISTERED_CODES = Set.of("400", "403", "404", "409", "503");

    /**
     * FC 域语义位字面量判据（逐枚带引号整词）。7 枚＝治理时点有生产出口的 FC 域自有
     * 语义位，字面量单源归 {@code FCScenarioErrorCode.CODE_*} 常量；属主之外任何 main 面
     * 带引号整词出现即红（零容差，不设棘轮）。
     *
     * <p><b>刻意不入本表的托管位</b>（各有归因，均不建本域常量）：
     * 409 PERIOD_CLOSED（409 段唯一枚，owner data-engine 域，独立治理）；
     * 400 ACTION_FIELDS_MISSING（属主 ECOS-ACT 五必填段）；
     * 403 PERMISSION_DENIED（属主 SEC 族，出口仍在 security denier）；
     * 503 SECURITY_UNAVAILABLE（同上，属主 SEC 族）。四枚均为跨族共用位，
     * 逐枚搬流会跨族引用违铁律，归属以冻结表原文为准，属主类 javadoc 逐枚注释登记。</p>
     */
    private static final Map<String, String> FC_SEMANTIC_SLOTS = new TreeMap<>(Map.of(
            "CALIBER_DSL_INVALID", "CODE_CALIBER_DSL_INVALID",
            "COGNITIVE_UNAVAILABLE", "CODE_COGNITIVE_UNAVAILABLE",
            "ONTOLOGY_UNAVAILABLE", "CODE_ONTOLOGY_UNAVAILABLE",
            "SANDBOX_REF_FORBIDDEN", "CODE_SANDBOX_REF_FORBIDDEN",
            "FORECAST_RUN_NOT_FOUND", "CODE_FORECAST_RUN_NOT_FOUND",
            "CALIBER_NOT_FOUND", "CODE_CALIBER_NOT_FOUND",
            "CALIBER_STATE_PRECONDITION", "CODE_CALIBER_STATE_PRECONDITION"));

    /**
     * FC 域消费侧锚点：九处改指文件必须逐文件<b>恰 N 枚</b> {@code
     * FCScenarioErrorCode.CODE_*} 引用（#146ak 本批：9 载体文件 10 处——
     * CaliberFormulaInvalidException 双构造 2 枚，其余各 1 枚），且各自恰 1 枚
     * 属主 import（防改指域外同值常量＝单源名存实亡）。
     */
    private static final Pattern FC_CODE_REFERENCE =
            Pattern.compile("FCScenarioErrorCode\\s*\\.\\s*CODE_[A-Z][A-Z0-9_]+");

    private static final Pattern FC_CONSUMER_IMPORT =
            Pattern.compile("import\\s+com\\.chinacreator\\.gzcm\\.common\\.error\\s*\\.\\s*FCScenarioErrorCode\\s*;");

    private static final Map<String, Integer> FC_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CaliberFormulaInvalidException.java", 2),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CaliberStatePreconditionException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CaliberOwnerUnavailableException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CaliberNotFoundException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/CognitiveEngineUnavailableException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/SandboxReferenceForbiddenException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/forecast/ForecastRunNotFoundException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/CognitiveEngineUnavailableException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/SandboxReferenceForbiddenException.java", 1)));

    // ── 第11篇 ACT/SCN/SBX/INT 语义位单源面（#146am，台账附录C 第 7 条）──────

    /**
     * 第11篇四枚机读码前缀字面量判据（引号起点 + 前缀 + 三位码位）。
     *
     * <p><b>#146am 新登</b>（收口差距附录C 第 7 条「第11篇 ECOS-ACT/SCN/SBX/INT 前缀族
     * 0 命中」）：治理时点 main 面（含 test 面）带引号机读码 <b>0 枚</b>——冻结表第11篇
     * 码位的归因坐标是 {@code ECOS-xx} 域锚，码值即语义位本身（如 {@code 400
     * ACTION_FIELDS_MISSING}），不存在 {@code ECOS-<域>-<三位>} 形态机读码位（建即死码，
     * DG-9/X-43 红线）。本门腿＝与 sys/rate/FC 腿同型零容差判据：任意一枚带引号
     * {@code "ECOS-(ACT|SCN|SBX|INT)-\d{3}"} 出现即红，不设属主豁免（本批不建此类机读码
     * 属主，故不设属主锚；新增＝伪造未经冻结表裁决的族码）。改后恒 0。</p>
     */
    private static final Pattern SC_FAMILY_LITERAL =
            Pattern.compile("\"ECOS-(ACT|SCN|SBX|INT)-(\\d{3})\"");

    /** 第11篇语义位属主（common-api 常量类；9 枚语义位字面量唯一合法家）。 */
    private static final String SCENARIO_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/"
                    + "ScenarioErrorCode.java";

    /**
     * 第11篇 9 枚语义位字面量判据（逐枚带引号整词）。9 枚＝治理时点有生产出口的
     * 第11篇属主语义位，字面量单源归 {@code ScenarioErrorCode.CODE_*} 常量；
     * 属主外 main 面带引号整词出现即红（零容差，不设棘轮）。
     *
     * <p><b>刻意不入本表的托管位/异源位</b>（各有归因，均不建本族常量）：
     * 403 PERMISSION_DENIED／503 SECURITY_UNAVAILABLE（10403／10409 行）属主 ECOS-SEC，
     * 出口仍在 security 裁决面；503 COGNITIVE_UNAVAILABLE（10408 行）与 400
     * SANDBOX_REF_FORBIDDEN（10402 行，ECOS-SBX）属主已在 {@code FCScenarioErrorCode}
     * （双包孪生载体已改指其 {@code CODE_*}，防同值双属主不重复入本表）；500 INTERNAL
     * （10410 行 ECOS-INT）既有平台承载面取 ECOS-SYS-500 前缀机读码＋workspace 面 3 腿
     * 信封语义位，均为在案 wire 面。六枚均为跨族共用位／既有单源，逐枚搬流会跨族引用
     * 违铁律，归属以冻结表原文为准，属主类 javadoc 逐枚注释登记。</p>
     */
    private static final Map<String, String> SC_SEMANTIC_SLOTS = new TreeMap<>(Map.of(
            "ACTION_FIELDS_MISSING", "CODE_ACTION_FIELDS_MISSING",
            "SCENARIO_NOT_FOUND", "CODE_SCENARIO_NOT_FOUND",
            "ISLAND_BINDING", "CODE_ISLAND_BINDING",
            "ILLEGAL_TRANSITION", "CODE_ILLEGAL_TRANSITION",
            "TERMINAL_STATE", "CODE_TERMINAL_STATE",
            "NO_FORMAL_RUN", "CODE_NO_FORMAL_RUN",
            "INCOMPLETE_SCENARIO", "CODE_INCOMPLETE_SCENARIO",
            "LAYOUT_CONFLICT", "CODE_LAYOUT_CONFLICT"));

    /**
     * 第11篇消费侧锚点：十一处改指文件必须逐文件<b>恰 N 枚</b> {@code
     * ScenarioErrorCode.CODE_*} 引用（#146am 本批 17 处——
     * scenario/IllegalScenarioTransitionException 4 枚，
     * blueprint/ScenarioBlueprintStateMachine 3 枚，其余 9 文件各 1 枚），
     * 且各自恰 1 枚属主 import（防改指域外同值常量＝单源名存实亡）。
     */
    private static final Pattern SC_CODE_REFERENCE =
            Pattern.compile("ScenarioErrorCode\\s*\\.\\s*CODE_[A-Z][A-Z0-9_]+");

    private static final Pattern SC_CONSUMER_IMPORT =
            Pattern.compile("import\\s+com\\.chinacreator\\.gzcm\\.common\\.error\\s*\\.\\s*ScenarioErrorCode\\s*;");

    private static final Map<String, Integer> SC_CONSUMER_EXIT_BASELINE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/ActionValidationException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/IslandBindingException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/OptimisticLockConflictException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/ScenarioNotFoundException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/exception/WorkspaceExceptionHandler.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/blueprint/IllegalBlueprintTransitionException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/blueprint/ScenarioBlueprintStateMachine.java", 3),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/ActionValidationException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/IllegalScenarioTransitionException.java", 4),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/IslandBindingException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/OptimisticLockConflictException.java", 1),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/"
                    + "workspace/scenario/ScenarioNotFoundException.java", 1)));

    // ── 1. 余面棘轮 ─────────────────────────────────

    @Test
    @DisplayName("单参工厂余面按实跑基线只减不增，下降（基线陈旧）同样变红")
    void noMachineCodeFactoryFaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        Map<String, Integer> byFactory = scan.countByFactory();
        int sites = scan.totalSites();
        int files = scan.filesWithHit();

        if (sites > BASELINE_SITES) {
            fail("单参（无机读码）工厂调用点上升：" + sites + " > 基线 " + BASELINE_SITES
                    + "，分枚=" + byFactory + "；新增出口必须走带码重载或登记基线并说明归因理由。"
                    + "命中文件（前 20）=" + firstN(new ArrayList<>(scan.hitsByFile.keySet()), 20));
        }
        // 一次给出全部三个实跑读数（总数／文件数／分枚），避免「改一个数跑一轮」的往返
        String actualFace = "sites=" + sites + ", files=" + files + ", byFactory=" + byFactory;
        String expectedFace = "sites=" + BASELINE_SITES + ", files=" + BASELINE_FILES
                + ", byFactory=" + BASELINE_BY_FACTORY;
        assertEquals(expectedFace, actualFace,
                "单参工厂余面与基线不符。上升＝新增无机读码出口；下降＝基线陈旧，收口批必须同批下调 "
                        + "BASELINE_SITES/BASELINE_FILES/BASELINE_BY_FACTORY 并在台账留痕");

        int sum = byFactory.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(BASELINE_SITES, sum,
                "分枚合计必须等于总数基线，否则说明拆分口径与扫描口径不一致");
    }

    // ── 2. 入口锚点 ─────────────────────────────────

    @Test
    @DisplayName("gateway 入口六枚兜底腿必须经带码重载取 SystemErrorCode 常量")
    void platformEntryLegsCarryRegisteredMachineCodes() throws IOException {
        Path handler = locateRepoRoot().resolve(HANDLER);
        assertTrue(Files.exists(handler), "入口 handler 文件不在位：" + handler);
        String src = SourceCommentStripper.strip(Files.readString(handler, StandardCharsets.UTF_8));

        List<String> coded = new ArrayList<>();
        Matcher m = CODED_FACTORY.matcher(src);
        while (m.find()) {
            coded.add(m.group(1) + "→" + m.group(2));
        }
        assertEquals(ENTRY_LEGS_CODED, coded.size(),
                "入口带码腿数与凭证不符（回退即红）：实跑 " + coded);

        // 单参腿只允许登记豁免的那一枚
        List<String> legacy = new ArrayList<>();
        Matcher s = SINGLE_ARG_FACTORY.matcher(src);
        while (s.find()) {
            legacy.add(s.group(1));
        }
        assertEquals(1, legacy.size(),
                "入口面单参（无机读码）腿必须只剩登记豁免的一枚，实跑=" + legacy);
        assertEquals("notFound", legacy.get(0),
                "豁免腿的形态变了（handleNotFound 被补码或改名）：请同批撤豁免并改判据，实跑=" + legacy);
        assertTrue(src.contains("handleNotFound"),
                "豁免反查：基线登记的 handleNotFound 腿已不在 handler 内，豁免清单陈旧");
        for (String ex : REGISTERED_ENTRY_EXEMPTIONS) {
            assertTrue(src.contains(ex), "豁免方法 " + ex + " 不在位，豁免清单需随批修剪");
        }
    }

    // ── 2b. 族码字面量副本（D-B4-65 余项「副本面」收口腿）──

    @Test
    @DisplayName("ECOS-SYS-* 族码字面量在 main 面只由 SystemErrorCode 单源承载（副本即红）")
    void sysFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (SYSTEM_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = SYS_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-SYS-* 字面量副本：出口应改取 SystemErrorCode 常量，"
                        + "新增族码请先登记进该族并同步 §3.2.7。副本清单=" + copies);
    }

    // ── 2c. ECOS-RATE 族码字面量单源 + 属主 429 出口锚点（#142/B4-3e-92 新登，P1.1）──

    @Test
    @DisplayName("ECOS-RATE-* 族码字面量在 main 面只由 RateLimitErrorCode 单源承载（副本即红）")
    void rateFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (RATE_LIMIT_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = RATE_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-RATE-* 字面量副本：出口应改取 RateLimitErrorCode 常量，"
                        + "新增族请先登记进该族并同步 §3.2.7 冻结表与附录 C。副本清单=" + copies);
    }

    // ── 2c′. ECOS-WF 族码字面量单源 + 属主 500 出口锚点（#146y 新登，附录A 第 10 条）──

    @Test
    @DisplayName("ECOS-WF-* 族码字面量在 main 面只由 WorkflowErrorCode 单源承载，且族内枚数不超 §6.3.3 在册集")
    void wfFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (WORKFLOW_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = WF_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-WF-* 字面量副本：出口应改取 WorkflowErrorCode 常量，"
                        + "新增族码须先在详设 §6.3.3 错误码族表在册（禁以代码冒充文档裁决）。副本清单=" + copies);

        // 属主面回读（反假绿）：跳过属主后判据恒真并不等于单源真的在位，须独立证实承载内容与在册集一致。
        Path holder = root.resolve(WORKFLOW_ERROR_CODE_SOURCE);
        assertTrue(Files.exists(holder), "WF 族单源文件不在位：" + holder);
        Set<String> holderLiterals = new TreeSet<>();
        Matcher hm = WF_FAMILY_LITERAL.matcher(
                SourceCommentStripper.strip(Files.readString(holder, StandardCharsets.UTF_8)));
        while (hm.find()) {
            holderLiterals.add(hm.group().replace("\"", ""));
        }
        assertEquals(Set.of("ECOS-WF-020"), holderLiterals,
                "WF 族在册枚集应恰为详设 §6.3.3 表中的 ECOS-WF-020（§6.5 状态矩阵同源）；"
                        + "多＝有人往族里补未在册码，少＝在册码被删而门禁面仍绿。实得=" + holderLiterals);
    }

    @Test
    @DisplayName("buszhi 实例级审批面的 500 出口必须取 WorkflowErrorCode 常量（恰 1 处，缺即红）")
    void workflowApprovalExitPointsToSingleSource() throws IOException {
        Path owner = locateRepoRoot().resolve(WORKFLOW_INSTANCE_APPROVAL_SOURCE);
        assertTrue(Files.exists(owner),
                "WF-020 属主出口文件不在位（附录A 第 10 条承流面被搬走或删除）：" + owner);
        String stripped = SourceCommentStripper.strip(Files.readString(owner, StandardCharsets.UTF_8));
        List<String> hits = new ArrayList<>();
        Matcher m = WF_CODE_REFERENCE.matcher(stripped);
        while (m.find()) {
            hits.add(m.group());
        }
        assertEquals(1, hits.size(),
                "审批面必须恰 1 处 WorkflowErrorCode.ECOS_WF_<n> 引用；0 处＝出口改回裸 500/自造字面量"
                        + "（族码单源断链），>1 处＝同一族码多出口须同批清点。文件=" + owner + "，实跑=" + hits);
    }

    /**
     * {@code ECOS-(DATA|DQ)-*} 族码字面量（独立整枚与消息前缀拼接两形态）在 main 面只由
     * {@code DataDomainErrorCode} 单源承载。此为 P1.1「错误码族系统性漂移」的数据域批
     * （#142/B4-3e-93）零容忍判据：本批已把 11 枚非属主副本（6 枚独立字面量 + 5 枚
     * 消息前缀拼接腿）全部改指属主常量，改后全仓 main 面族码字面量应恒 0 枚——故直接
     * {@code assertEquals(List.of(), …)}，不设棘轮基线（新增即红，无需「只减不增」窗口）。
     */
    @Test
    @DisplayName("ECOS-(DATA|DQ)-* 族码字面量在 main 面只由 DataDomainErrorCode 单源承载（副本即红）")
    void dataFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (DATA_DOMAIN_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = DATA_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-(DATA|DQ)-* 字面量副本（独立整枚或消息前缀拼接）：出口应改取 "
                        + "DataDomainErrorCode 常量，新增族码请先登记进详设-02 §D.1 冻结表并同步本族。"
                        + "副本清单=" + copies);
    }

    /**
     * ECOS-AUTH-0xx 族码字面量零容差判据（#142/B4-3e-94）：本批已把 14 枚非属主副本
     * （13 枚引号起点 + 1 枚消息中段拼接腿）全部改指属主常量，改后全仓 main 面族码
     * 字面量应恒 0 枚——故直接 {@code assertEquals(List.of(), …)}，不设棘轮基线
     * （新增即红，无需「只减不增」窗口）。
     */
    @Test
    @DisplayName("ECOS-AUTH-0xx 族码字面量在 main 面只由 AuthErrorCode 单源承载（副本即红）")
    void authFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (AUTH_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = AUTH_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-AUTH-0xx 字面量副本（独立整枚或消息前缀拼接）：出口应改取 "
                        + "AuthErrorCode 常量，新增族码请先登记进详设-00 §C.2.5 冻结表并同步本族。"
                        + "副本清单=" + copies);
    }

    /**
     * ECOS-SEC-* 族码字面量逐枚零容差判据（#142/B4-3e-95）：11 枚登记码按
     * {@link #SEC_LITERAL_HOLDERS} 白名单逐枚对账（合法属主内零上限，白名单外出现任意一枚
     * 即红），未登记码位（如实测 main 面 0 枚、仅存 sysman-boot test 面 fixture 的 403）
     * 在 main 面露面即红。属主面不做「恰 1 次」钉死——430 在族属主内既有枚定值字面量又被
     * security 侧三处属主常量经本类中转引用，属正常多持有形态。
     *
     * <p>假绿防护＝属主面<b>逐枚回读</b>（见方法体末尾循环）：每枚登记码必须在至少一个
     * 登记属主文件内仍有字面量锚点，否则「白名单外零命中」只是哪边都删了值的假象。</p>
     */
    @Test
    @DisplayName("ECOS-SEC-* 族码字面量按枚仅由登记属主承载，未注册码位与属主面丢字面量均红")
    void secFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        // 登记属主先各剥一次注释，违例扫描与逐枚回读共用同一份剥离结果
        Map<String, String> ownerStripped = new TreeMap<>();
        for (String ownerRel : secOwnerFileSet()) {
            ownerStripped.put(ownerRel, readStripped(root, ownerRel));
        }

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (ownerStripped.containsKey(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = SEC_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                violations.add(rel + " → ECOS-SEC-" + m.group(1));
            }
        }
        assertEquals(List.of(), violations,
                "main 面出现登记属主之外的 ECOS-SEC-* 字面量（或文档 §4.3.3 未登记的码位）："
                        + "出口应改取 SecurityErrorCode（或 501/520 依单源链 RlsExecutionContract）常量，"
                        + "新增族码先登记入《从零实施版》§4.3.3 表并同步本白名单。清单=" + violations);

        // 假绿防护：逐枚回读属主面——登记白名单内的每枚码必须在至少一个登记属主内仍有字面量
        for (Map.Entry<String, List<String>> e : SEC_LITERAL_HOLDERS.entrySet()) {
            boolean anchored = false;
            for (String ownerRel : e.getValue()) {
                String ownerSrc = ownerStripped.get(ownerRel);
                if (ownerSrc != null && ownerSrc.contains("\"ECOS-SEC-" + e.getKey() + "\"")) {
                    anchored = true;
                    break;
                }
            }
            assertTrue(anchored, "属主面回读失败：ECOS-SEC-" + e.getKey()
                    + " 在所有登记属主内都找不到字面量锚点——该码位或白名单已漂移，本门假绿风险，须同批对账");
        }
    }

    /** {@link #SEC_LITERAL_HOLDERS} 值集并出的属主文件集合（两枚仓库相对路径）。 */
    private static Set<String> secOwnerFileSet() {
        Set<String> out = new TreeSet<>();
        for (List<String> holders : SEC_LITERAL_HOLDERS.values()) {
            out.addAll(holders);
        }
        return out;
    }

    private static String readStripped(Path root, String rel) throws IOException {
        Path f = root.resolve(rel);
        assertTrue(Files.exists(f), "登记属主文件不在位（scan/目录变更须同步白名单）：" + rel);
        return SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
    }

    /**
     * ECOS-ONTO-* 族码字面量的 <b>(码, 文件) 双键零容差</b>判据（#146af）：白名单外的任何露面即红，
     * 未登记码位即便落在登记属主文件内也红（防「族里凭空多一枚未经 §6.3.3 裁决的码」）。
     *
     * <p>与 SEC 腿的「先整体跳过属主文件」不同，本族改为<b>逐命中查双键</b>——判据不限位置以后，
     * 属主文件内部同样可能出现越权枚（{@code CaliberDslValidator} 私设 074、
     * {@code OntologyErrorCode} 被补第 14 枚），跳过整文件会把这类漂移读成绿。</p>
     *
     * <p>假绿防护＝逐 (码, 属主) 回读：白名单里每一对都必须在对应属主文件内仍有字面量锚点，
     * 否则「白名单外零命中」只是两边都把值删了／搬走了的假象；属主文件不在位由
     * {@link #readStripped} 的存在性断言先行变红。</p>
     */
    @Test
    @DisplayName("ECOS-ONTO-* 族码字面量按 (码,文件) 双键仅由登记属主承载，message 中段与日志形态同样入账")
    void ontoFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = ONTO_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                String code = m.group(1);
                List<String> holders = ONTO_LITERAL_HOLDERS.get(code);
                if (holders == null) {
                    violations.add(rel + " → ECOS-ONTO-" + code + "（未登记码位）");
                } else if (!holders.contains(rel)) {
                    violations.add(rel + " → ECOS-ONTO-" + code + "（登记属主之外的副本）");
                }
            }
        }
        assertEquals(List.of(), violations,
                "main 面出现 ECOS-ONTO-* 字面量越权露面（含 message 中段括号与日志格式串两形态）："
                        + "出口应改取 OntologyErrorCode 常量，DSL 四关/表外 000 只许留在既有登记属主内；"
                        + "新增族码先登记入《从零实施版》§6.3.3 表并同步本白名单，禁以代码冒充文档裁决。"
                        + "清单=" + violations);

        for (Map.Entry<String, List<String>> e : ONTO_LITERAL_HOLDERS.entrySet()) {
            for (String ownerRel : e.getValue()) {
                assertTrue(readStripped(root, ownerRel).contains("ECOS-ONTO-" + e.getKey()),
                        "属主面回读失败：ECOS-ONTO-" + e.getKey() + " 在登记属主 " + ownerRel
                                + " 内已找不到字面量锚点——白名单陈旧或该枚值被删/搬走，"
                                + "「白名单外零违例」属假绿，须同批对账");
            }
        }
    }

    /**
     * ONTO 族消费侧锚点（#146af）：八处改指文件必须逐文件<b>恰 N 枚</b> {@code
     * OntologyErrorCode.ECOS_ONTO_<n>} 引用。
     *
     * <p>判据面绿只证明「字面量没扩散」，不证明「出口还在取常量」——出口若改回裸文案或新造副本，
     * 字面量面照样干净，单源断在消费侧（假单源）。少＝腿漂移回裸文案，多＝同出口重复取码或
     * 新增改指腿未同批登记。</p>
     */
    @Test
    @DisplayName("ECOS-ONTO 族八处出口逐文件恰取 OntologyErrorCode 常量（假单源或漂移即红）")
    void ontoExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ONTO_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = ONTO_CODE_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
        }
        assertEquals(List.of(), drift,
                "ECOS-ONTO 族出口侧常量引用枚数漂移（八文件基线＝#146af 本批实跑读数）：" + drift);
    }

    // ── 口径 DSL 四关跨模块契约单源（#146ah 收口 ONTO r5）─────────────

    /**
     * 口径 DSL 四关契约属主面（#146ah）：{@code CaliberDslContract} 必须同时承载
     * <b>闸名词表与机读码字面量</b>，且双向映射对称、pom 双向可达前提不被拆断。
     *
     * <p>三段假绿防护：①<b>双边字面量回读</b>——4 枚闸名＋4 枚机读码串都在属主源内
     * （消费侧字面量退到 0 枚后，属主删码面＝全链路同时静默失效，只有回读读得见）；
     * ②<b>双向映射对称</b>：{@code gateNameByCode} 以 4 枚码探得恰 4 枚区分闸名且
     * {@code codeByGate} 恒互逆（删一行 Map.of 即红）；③<b>pom 双向可达</b>——
     * workspace-impl 直挂 common-api、ontology-engine-impl 经 runtime-core 传递可达
     * （#146af 原裁决「至多两枚属主」的前提「跨模块 import 须新挂依赖」不成立才换来零
     * 新依赖真单源；任一侧依赖被拔掉即红，防单链条目被动吸走时假单源固化）。</p>
     */
    @Test
    @DisplayName("口径 DSL 四关契约属主：闸名+机读码双边字面量在位、映射对称、pom 双向可达")
    void caliberDslContractCrossModuleOwnerTest() throws IOException {
        Path root = locateRepoRoot();
        String contract = readStripped(root, CALIBER_DSL_CONTRACT_SOURCE);

        String[] gateValues = {
                "DSL_V1_SYNTAX", "DSL_V2_REFERENCE", "DSL_V3_DIMENSION", "DSL_V4_CYCLE",
                "ECOS-ONTO-070", "ECOS-ONTO-071", "ECOS-ONTO-072", "ECOS-ONTO-073"};
        List<String> missing = new ArrayList<>();
        for (String v : gateValues) {
            if (!contract.contains(v)) {
                missing.add(v);
            }
        }
        assertEquals(List.of(), missing,
                "契约属主缺面值字面量（4 闸名＋4 机读码须同枚在位）：" + missing);

        Set<String> gates = new TreeSet<>();
        for (String code : List.of("ECOS-ONTO-070", "ECOS-ONTO-071",
                "ECOS-ONTO-072", "ECOS-ONTO-073")) {
            String gate = CaliberDslContract.gateNameByCode(code);
            assertNotNull(gate, "gateNameByCode(" + code + ") = null：结构性映射丢码位");
            gates.add(gate);
        }
        assertEquals(4, gates.size(), "gateNameByCode 返回区分闸名 ≠ 4：码位映射坍缩");
        for (String gate : gates) {
            assertEquals(gate, CaliberDslContract.gateNameByCode(CaliberDslContract.codeByGate(gate)),
                    "gateNameByCode ∘ codeByGate 非恒等（双向映射互逆被拆断）：" + gate);
        }

        String workspacePom = Files.readString(root.resolve(WORKSPACE_IMPL_POM), StandardCharsets.UTF_8);
        assertTrue(workspacePom.contains("<artifactId>common-api</artifactId>"),
                "workspace-impl pom 失去 common-api 依赖＝DSL 契约单源前提断链（编译仍绿、单源名存实亡）");
        String ontoPom = Files.readString(root.resolve(ONTO_ENGINE_IMPL_POM), StandardCharsets.UTF_8);
        assertTrue(ontoPom.contains("<artifactId>runtime-core</artifactId>"),
                "ontology-engine-impl pom 失去 runtime-core 依赖＝经其传递的 common-api 可达性断链");
    }

    /**
     * 口径 DSL 四关消费侧出口倒数（#146ah）：4 个消费文件必须<b>逐文件恰 N 枚</b>
     * {@code CaliberDslContract.<CONSTANT>} 引用，且各自保留契约 import。
     *
     * <p>与字面量族白名单的门分工：族腿盯「set 的串还在属主内」，本腿盯「出口真经
     * 属主取面值」——消费面改回裸串（{@code "DSL_V1_SYNTAX"} 之类）时，DSL 闸名不在
     * ONTO/COG 数字码族正则覆盖内 <=> 族腿读不到，<b>只有本腿</b>读得到。
     * 升枚即红（大于登记）同样要打回：新增了取码面就必须同批重设基线，
     * 禁「消费面扩了，登记表不动」（基线陈旧＝假已收口）。</p>
     */
    @Test
    @DisplayName("口径 DSL 四关消费侧四文件逐文件恰取 CaliberDslContract 常量并保留契约 import")
    void caliberDslContractExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : CALIBER_DSL_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = CALIBER_DSL_CONTRACT_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
            long imports = CALIBER_DSL_CONSUMER_IMPORT.matcher(stripped).results().count();
            if (imports != 1) {
                drift.add(e.getKey() + " 契约 import " + imports + " 枚 ≠ 1（改指域外同值常量＝单源名存实亡）");
            }
        }
        assertEquals(List.of(), drift,
                "口径 DSL 四关消费侧漂移（基线＝#146ah 本批实跑读数；回退裸串与域外同值常量两形态均红）：" + drift);
    }

    /**
     * ECOS-COG-* 族码字面量的 <b>(码, 文件) 双键零容差</b>判据（#146ag）。
     *
     * <p>本族改指前最大的一片字面量副本面是 {@code CognitiveGuardException.httpOf} 的 30 枚
     * switch case 标签——case 标签是编译期常量还是裸字面量，源码上看不出来，属主改值时
     * 运行时静默落到 {@code default -> 500}（403 拒绝被读成 500 内部错误）。现全部改取属主常量。</p>
     *
     * <p>三段假绿防护：①逐命中查双键（未登记码位即使在属主文件内也红）；②<b>属主码位集</b>
     * 与登记表 {@code equals} 对账——属主补一枚或删一枚而门禁未同批改，本腿即红，
     * 堵住 ONTO 腿那种"白名单和属主各自漂移"的空间；③逐 (码, 承载面) 回读字面量锚点。</p>
     */
    @Test
    @DisplayName("ECOS-COG-* 族码字面量按 (码,文件) 双键仅由登记属主承载，属主码位集与登记表同号")
    void cogFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = COG_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                String code = m.group(1);
                List<String> holders = COG_LITERAL_HOLDERS.get(code);
                if (holders == null) {
                    holders = COG_UNREGISTERED_LITERAL_HOLDERS.get(code);
                    if (holders == null) {
                        violations.add(rel + " → ECOS-COG-" + code + "（未登记码位）");
                    } else if (!holders.contains(rel)) {
                        violations.add(rel + " → ECOS-COG-" + code + "（未登记码位的登记余项承载面之外副本）");
                    }
                } else if (!holders.contains(rel)) {
                    violations.add(rel + " → ECOS-COG-" + code + "（登记属主之外的副本）");
                }
            }
        }
        assertEquals(List.of(), violations,
                "main 面出现 ECOS-ONTO 之外的 ECOS-COG-* 字面量越权露面（switch case 标签、message 中段、"
                        + "拼接片段一律入账）：出口应改取 CognitiveErrorCode 常量；"
                        + "新增族码先登记入《从零实施版》COG 冻结表并同步本白名单，禁以代码冒充文档裁决。"
                        + "清单=" + violations);

        Set<String> ownerCodes = new TreeSet<>();
        Matcher ownerScan = COG_FAMILY_LITERAL.matcher(readStripped(root, COG_ERROR_CODE_SOURCE));
        while (ownerScan.find()) {
            ownerCodes.add(ownerScan.group(1));
        }
        assertEquals(COG_LITERAL_HOLDERS.keySet(), ownerCodes,
                "属主 CognitiveErrorCode 在册码位集与本门禁登记表不一致：属主单方面补登/删枚"
                        + "（含把未登记余项 503D 直接写进属主）必须同批改判据并回到设计单源裁决，"
                        + "禁让文档冻结表与代码属主各走各路。");

        for (Map.Entry<String, List<String>> e : COG_LITERAL_HOLDERS.entrySet()) {
            for (String holderRel : e.getValue()) {
                assertTrue(readStripped(root, holderRel).contains("ECOS-COG-" + e.getKey()),
                        "承载面回读失败：ECOS-COG-" + e.getKey() + " 在登记承载面 " + holderRel
                                + " 内已找不到字面量锚点——白名单陈旧或该枚值被删/搬走，"
                                + "「白名单外零违例」属假绿，须同批对账");
            }
        }
        for (Map.Entry<String, List<String>> e : COG_UNREGISTERED_LITERAL_HOLDERS.entrySet()) {
            for (String holderRel : e.getValue()) {
                assertTrue(readStripped(root, holderRel).contains("ECOS-COG-" + e.getKey()),
                        "未登记余项 ECOS-COG-" + e.getKey() + " 在登记承载面 " + holderRel
                                + " 内已无字面量锚点：该枚已被删除或迁走，须同批撤销本余项登记");
            }
        }
    }

    /**
     * COG 族消费侧锚点（#146ag）：五处改指文件逐文件<b>恰 N 枚</b>属主常量引用。
     *
     * <p>判据面绿只证明"字面量没扩散"，不证明"出口还在取常量"——switch 若哪天改回裸字面量，
     * 族面照样干净（裸枚会撞越权腿，但属主文件内重复声明这类形态靠本腿兜）。</p>
     */
    @Test
    @DisplayName("ECOS-COG 族五处改指出口逐文件恰取 CognitiveErrorCode 常量（假单源或漂移即红）")
    void cogExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : COG_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = COG_CODE_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
        }
        assertEquals(List.of(), drift,
                "ECOS-COG 族出口侧常量引用枚数漂移（五文件基线＝#146ag 本批实跑读数）：" + drift);
    }

    /**
     * ECOS-AI-* 族码字面量单源判据（#146ai）：数字码位（400/403/413/422/429/404/501/502/503/504/
     * 500P/400C 十二枚）＋非数字形态码（AGENT-RUNTIME-ABSENT）在 main 面只由
     * {@code AiErrorClass} 属主承载。
     *
     * <p>三段假绿防护：①<b>数字码位属主集对账</b>——属主内 12 枚与白名单 {@code equals} 同号
     * （属主单方面补登/删枚而门禁未同批改即红）；②<b>逐命中白名单外即红</b>——新增裸串复制
     * 无论在引号、case 标签还是日志格式串内都入账；③<b>形态码三点鼻</b>——非数字
     * {@code AGENT-RUNTIME-ABSENT} 用独立判据与白名单，且属主文件内回读面值
     * （防它跨包拆走时判据永远读不到假绿）。</p>
     */
    @Test
    @DisplayName("ECOS-AI-* 族码字面量 main 面仅由 AiErrorClass 承载，属主码位集与白名单同号")
    void aiFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));

            Matcher m = AI_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                String code = m.group(1);
                if (!AI_ERROR_CLASS_SOURCE.equals(rel)) {
                    violations.add(rel + " → ECOS-AI-" + code + "（属主之外的数字码位副本）");
                }
            }

            Matcher n = AI_RUNTIME_ABSENT_LITERAL.matcher(stripped);
            while (n.find()) {
                List<String> holders = AI_ALPHANUMERIC_LITERAL_HOLDERS.get("ECOS-AI-AGENT-RUNTIME-ABSENT");
                if (holders == null || !holders.contains(rel)) {
                    violations.add(rel + " → ECOS-AI-AGENT-RUNTIME-ABSENT（属主之外的非数字形态码副本）");
                }
            }
        }
        assertEquals(List.of(), violations,
                "main 面 ECOS-AI-* 字面量越权露面（引号、case、消息中段、格式串一律入账）："
                        + "出口应改取 AiErrorClass 常量；新增族码先在设计冻结表登记再建属主枚，"
                        + "禁以代码冒充文档裁决。清单=" + violations);

        Set<String> ownerCodes = new TreeSet<>();
        Matcher ownerScan = AI_FAMILY_LITERAL.matcher(readStripped(root, AI_ERROR_CLASS_SOURCE));
        while (ownerScan.find()) {
            ownerCodes.add(ownerScan.group(1));
        }
        Set<String> expected = new TreeSet<>(List.of(
                "400", "400C", "403", "404", "413", "422", "429", "500P", "501", "502", "503", "504"));
        assertEquals(expected, ownerCodes,
                "属主 AiErrorClass 在册数字码位集与本门禁白名单不一致：属主单方面补登/删枚"
                        + "必须同批改判据并回到设计单源裁决，禁让文档冻结表与代码属主各走各路。");

        assertTrue(readStripped(root, AI_ERROR_CLASS_SOURCE).contains("ECOS-AI-AGENT-RUNTIME-ABSENT"),
                "非数字形态码在属主 AiErrorClass 内已无字面量锚点——该枚已被删除或迁走（值域扩展），"
                        + "须同批撤销/重设本门禁判据并回到设计单源裁决，禁静默失配");
    }

    /**
     * ECOS-AI 族消费侧出口基线（#146ai）：五枚改指文件逐文件<b>恰 N 枚</b>
     * {@code AiErrorClass.<IDENTIFIER>} 属主引用。
     *
     * <p>家族腿只逛「字面量没扩散」，不逛「出口真在取常量」——本批改指后
     * {@code AgentSessionOpsController:542} 的 {@code "ECOS-AI-503"} 裸串如果未来被
     * 合回一个字面量，家族腿照样见不到（那个码位属主白名单内），<b>只有本腿</b>
     * 靠逐文件 {@code AiErrorClass.} 引用数变红兜住。少=改回裸串/域外同值常量；
     * 多＝新增出口取码未同批重设本表。</p>
     */
    @Test
    @DisplayName("ECOS-AI 族五处改指出口逐文件恰取 AiErrorClass 属主（漂移或假单源即红）")
    void aiExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : AI_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = AI_CODE_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
            long imports = Pattern.compile("import\\s+com\\.chinacreator\\.gzcm\\.common\\.error\\.AiErrorClass;")
                    .matcher(stripped).results().count();
            if (imports != 1) {
                drift.add(e.getKey() + " 属主 import " + imports + " 枚 ≠ 1（改指域外同值常量＝单源名存实亡）");
            }
        }
        assertEquals(List.of(), drift,
                "ECOS-AI 族出口侧属主引用枚数漂移（五文件基线＝#146ai 本批实跑读数）：" + drift);
    }

    /**
     * ECOS-KB-* 族码字面量的 <b>(码, 文件) 双键零容差</b>判据（#146at 补落，P1.1 族队列
     * RATE/DATA/AUTH/SEC/ONTO/COG/AI 之后的 KB 族收口位）：{@link #KB_LITERAL_HOLDERS}
     * 白名单外的任何 main 面露面即红，未登记码位即便落在登记属主文件内也红。
     *
     * <p>与 ONTO/COG/AI 腿同型的位置不限判据：本族此前对五枚「引号起点锚定」引号外形态
     * 恒盲（如 {@code KbErrorAdvice} 消息中段），本腿剥注释后按族前缀正则全量入账。
     * <b>不实装出口侧基线腿</b>：KB 族消费面 25 枚文件取的是 {@code KbErrorCodes.X}
     * 枚举常量（非码值字面量），字面量面＋逐 (码,属主) 回读已覆盖本差距（P1.1 第 1 条
     * 字面量副本失控形态）；消费面逐文件基线另立批次，避免本批扩面 25 枚生产文件。</p>
     */
    @Test
    @DisplayName("ECOS-KB-* 族码字面量按 (码,文件) 双键仅由登记属主承载，属主码位集与白名单双向同号")
    void kbFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> violations = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = KB_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                String code = m.group(1);
                List<String> holders = KB_LITERAL_HOLDERS.get(code);
                if (holders == null) {
                    violations.add(rel + " → ECOS-KB-" + code + "（未登记码位）");
                } else if (!holders.contains(rel)) {
                    violations.add(rel + " → ECOS-KB-" + code + "（登记属主之外的副本）");
                }
            }
        }
        assertEquals(List.of(), violations,
                "main 面出现 ECOS-KB-* 字面量越权露面（引号、消息中段、日志格式串一律入账）："
                        + "出口应改取 KbErrorCodes 常量；新增族码先登记入《从零实施版》§7.3.3 "
                        + "表并同步本白名单，禁以代码冒充文档裁决。清单=" + violations);

        // 属主码位集双向对账：属主文件并集自扫 == 白名单键集（少＝属主删枚，多＝属主私补枚）
        Set<String> ownerCodes = new TreeSet<>();
        for (String ownerRel : List.of(KB_ERROR_CODES_SOURCE, KB_ERROR_ADVICE_SOURCE)) {
            Matcher ownerScan = KB_FAMILY_LITERAL.matcher(readStripped(root, ownerRel));
            while (ownerScan.find()) {
                ownerCodes.add(ownerScan.group(1));
            }
        }
        assertEquals(KB_REGISTERED_CODES, ownerCodes,
                "属主面（KbErrorCodes＋KbErrorAdvice 并集）在册码位集与本门禁白名单不一致："
                        + "属主单方面补登/删枚必须同批改判据并回到 §7.3.3 单源裁决");

        // 逐 (码, 属主) 回读：白名单每一对都须在对应属主文件内仍有字面量锚点，
        // 防「两侧都删/搬走了值」的假绿；属主文件不在位由 readStripped 存在性断言先行变红
        for (Map.Entry<String, List<String>> e : KB_LITERAL_HOLDERS.entrySet()) {
            for (String ownerRel : e.getValue()) {
                assertTrue(readStripped(root, ownerRel).contains("ECOS-KB-" + e.getKey()),
                        "属主面回读失败：ECOS-KB-" + e.getKey() + " 在登记属主 " + ownerRel
                                + " 内已找不到字面量锚点——白名单陈旧或该枚值被删/搬走，"
                                + "「白名单外零违例」属假绿，须同批对账");
            }
        }
    }

    /**
     * ECOS-FC-* 族码字面量单源判据（#146ak）：治理时点 main 面带引号机读码 0 枚
     * （台账附录C 第 8 条销项判据），信封真身＝{@code WorkspaceDomainException#getCode()}
     * 的语义 code、族前缀在信封面无生产锚（补锚＝API 变更，铁律 #10，本批不做）。
     * 本批把冻结表 5 枚码位字面量首次落进 {@code FCScenarioErrorCode} 属主，
     * 三段假绿防护：①<b>属主码位集对账</b>——属主内带引号码位与冻结表登记集
     * {@code equals} 同号（属主单方面补登/删枚即红），堵住 AI 腿那种"忘锚 503"盲区；
     * ②逐命中查属主——属主文件内出现未登记码位（如凭空加 401）也红；
     * ③改后全仓 main 面副本应恒 0 枚，故直接 {@code assertEquals(List.of(), …)}，
     * 不设棘轮（任何新增副本即红）。
     */
    @Test
    @DisplayName("ECOS-FC-* 族码字面量 main 面仅由 FCScenarioErrorCode 承载，属主码位集与冻结表同款集同号")
    void fcFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        // 属主面不存在即直接红（防“文件被搬走 ⇒ 白名单外零命中恒绿”假象）
        assertTrue(Files.exists(root.resolve(FC_SCENARIO_ERROR_CODE_SOURCE)),
                "FC 族机读码单源文件不在位：" + FC_SCENARIO_ERROR_CODE_SOURCE);

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = FC_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                String code = m.group(1);
                if (!FC_SCENARIO_ERROR_CODE_SOURCE.equals(rel)) {
                    copies.add(rel + " → ECOS-FC-" + code + "（属主之外的副本，应改取常量）");
                } else if (!FC_REGISTERED_CODES.contains(code)) {
                    copies.add(rel + " → ECOS-FC-" + code + "（属主内未登记码位：伪造未经附录C 冻结表裁决的族码）");
                }
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-FC-* 族码字面量越权露面（属主外出副本、自由新增枚数、属主内未登记枚数）："
                        + "出口应改取 FCScenarioErrorCode 常量；新增族码先登记入《从零实施版》附录C "
                        + "冻结表并同步本判据，禁以代码冒充文档裁决。清单=" + copies);
    }

    /**
     * FC 域 7 枚语义位字面量 + 消费侧出口基线（#146ak，台账附录C 第 8 条）：
     * 7 枚 FC 域自有语义位（CALIBER_DSL_INVALID 等）的带引号字面量单源归
     * {@code FCScenarioErrorCode.CODE_*} 常量，属主外 main 面任何一枚即红；
     * 9 个改指文件逐文件<b>恰 N 枚</b>属主常量引用 + 恰 1 枚属主 import。
     *
     * <p>三段假绿防护：①<b>FC 字面量面零容差</b>（改后全仓副本恒 0 枚，不设棘轮）；
     * ②<b>消费面基线</b>——字面量面绿只证「没扩散」，不证「出口真在取常量」，
     * 出口改回裸串或新造同值副本会绕过字面量面，仅本腿按逐文件枚数变红；
     * ③<b>逐枚 import</b>——改指域外同值常量时裸串回读不可分辨，只有 import 那行
     * 读得见域内/域外。少＝漂移回字面量；多＝重复取码或新改指腿未同批重设基线。</p>
     */
    @Test
    @DisplayName("FC 域 7 枚语义位字面量 main 面 0 副本，且九处改指出口逐文件恰取属主常量")
    void fcScenarioSemanticSlotsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        // ① FC 字面量面：属主外任意一枚即红
        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (FC_SCENARIO_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            for (String slot : FC_SEMANTIC_SLOTS.keySet()) {
                int n = 0;
                Matcher m = Pattern.compile("\"" + Pattern.quote(slot) + "\"").matcher(stripped);
                while (m.find()) {
                    n++;
                }
                if (n > 0) {
                    copies.add(rel + " → " + slot + "（" + n + " 处，应改指 "
                            + "FCScenarioErrorCode." + FC_SEMANTIC_SLOTS.get(slot) + "）");
                }
            }
            // ③ 改指文件专属：import 恰好 1 枚（防改指域外同值常量）
            for (String consumer : FC_CONSUMER_EXIT_BASELINE.keySet()) {
                if (consumer.equals(rel)) {
                    long imports = FC_CONSUMER_IMPORT.matcher(stripped).results().count();
                    if (imports != 1) {
                        copies.add(rel + " 契约 import " + imports + " 枚 ≠ 1（改指域外同值常量＝单源名存实亡）");
                    }
                }
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 FC 域 7 枚语义位字面量副本（或契约 import 漂移）：出口应改取 "
                        + "FCScenarioErrorCode 常量。清单=" + copies);

        // ② 消费面基线：九文件逐枚恰 N 枚属主常量引用
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : FC_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = FC_CODE_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
        }
        assertEquals(List.of(), drift,
                "FC 域改指出口侧属主引用枚数漂移（九文件基线＝#146ak 本批实跑读数，回退裸文案/域外同值常量两形态均红）：" + drift);
    }

    /**
     * 第11篇 ECOS-ACT/SCN/SBX/INT 四族机读码字面量零容差判据（#146am，台账附录C 第 7 条）：
     * 治理时点全仓（main＋test 面）带引号 {@code "ECOS-(ACT|SCN|SBX|INT)-\d{3}"}
     * <b>0 枚</b>——冻结表第11篇不存在 {@code ECOS-<域>-<三位>} 形态码位（码值即语义位
     * 本身），伪出口先例红线 DG-9/X-43 ⇒ 任意新增即红，不设属主豁免。
     * 本腿无属主豁免＝「造码即红」；属主在场腿防「文件被搬走 ⇒ 零命中恒绿」假象。
     */
    @Test
    @DisplayName("第11篇四族机读码字面量全仓 0 枚（码位形态不存在，造码即红）")
    void scFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        // 属主面不存在即直接红（防被搬走/改名 ⇒ 零命中恒绿假象）
        assertTrue(Files.exists(root.resolve(SCENARIO_ERROR_CODE_SOURCE)),
                "第11篇语义位单源文件不在位：" + SCENARIO_ERROR_CODE_SOURCE);

        List<String> hits = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = SC_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                hits.add(rel + " → " + f.getFileName() + "（伪造第11篇机读码位：冻结表无此形态码，"
                        + "机读码属主在 ECOS-SEC/SYS 域，禁以代码冒充附录C 冻结表裁决）");
            }
        }
        assertEquals(List.of(), hits,
                "main 面出现 ECOS-(ACT|SCN|SBX|INT)-NNN 带引号机读码字面量："
                        + "第11篇码位归因坐标是域锚＋语义位，不存在三位码位形态。清单=" + hits);
    }

    /**
     * 第11篇 8 枚属主语义位字面量 + 消费侧出口基线（#146am，台账附录C 第 7 条）：
     * 8 枚属主语义位（ACTION_FIELDS_MISSING 等）带引号字面量单源归
     * {@code ScenarioErrorCode.CODE_*} 常量，属主外 main 面任何一枚即红；
     * 12 个改指文件逐文件<b>恰 N 枚</b>属主常量引用＋恰 1 枚属主 import。
     *
     * <p>三段假绿防护（与 FC 腿同型）：①<b>字面量面零容差</b>（改后全仓副本恒 0 枚，
     * 不设棘轮）；②<b>消费面基线</b>——字面量面绿只证「没扩散」，不证「出口真在取
     * 常量」，出口改回裸串或新造同值副本会绕过字面量面，仅本腿按逐文件枚数变红；
     * ③<b>逐枚 import</b>——改指域外同值常量时裸串回读不可分辨，只有 import 那行
     * 读得见域内/域外。少＝漂移回字面量；多＝重复取码或新改指腿未同批重设基线。</p>
     */
    @Test
    @DisplayName("第11篇 8 枚语义位字面量 main 面 0 副本，且十二处改指出口逐文件恰取属主常量")
    void scSemanticSlotsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        // ① 第11篇字面量面：属主外任意一枚即红
        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (SCENARIO_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            for (String slot : SC_SEMANTIC_SLOTS.keySet()) {
                int n = 0;
                Matcher m = Pattern.compile("\"" + Pattern.quote(slot) + "\"").matcher(stripped);
                while (m.find()) {
                    n++;
                }
                if (n > 0) {
                    copies.add(rel + " → " + slot + "（" + n + " 处，应改指 "
                            + "ScenarioErrorCode." + SC_SEMANTIC_SLOTS.get(slot) + "）");
                }
            }
            // 改指文件专属：import 恰好 1 枚（防改指域外同值常量）
            if (SC_CONSUMER_EXIT_BASELINE.containsKey(rel)) {
                long imports = SC_CONSUMER_IMPORT.matcher(stripped).results().count();
                if (imports != 1) {
                    copies.add(rel + " 契约 import " + imports + " 枚 ≠ 1（改指域外同值常量＝单源名存实亡）");
                }
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现第11篇 8 枚属主语义位字面量副本（或契约 import 漂移）：出口应改取 "
                        + "ScenarioErrorCode 常量。清单=" + copies);

        // ② 消费面基线：十二文件逐枚恰 N 枚属主常量引用
        List<String> drift = new ArrayList<>();
        for (Map.Entry<String, Integer> e : SC_CONSUMER_EXIT_BASELINE.entrySet()) {
            String stripped = readStripped(root, e.getKey());
            int hits = 0;
            Matcher m = SC_CODE_REFERENCE.matcher(stripped);
            while (m.find()) {
                hits++;
            }
            if (hits != e.getValue()) {
                drift.add(e.getKey() + " 实跑 " + hits + " 枚 ≠ 登记 " + e.getValue() + " 枚");
            }
        }
        assertEquals(List.of(), drift,
                "第11篇改指出口侧属主引用枚数漂移（十二文件基线＝#146am 本批实跑读数，"
                        + "回退裸文案/域外同值常量两形态均红）：" + drift);
    }

    /**
     * 仅裸 429」收口的正向锚点，防后续批次改回 {@code ApiResponse.error(429,"...")} 单参
     * 或加回字面量"ECOS-RATE-429/430"副本。
     *
     * <p>判据＝剥注释后每文件恰 1 枚 {@code RateLimitErrorCode.ECOS_RATE_\d+} 引用：
     * 零枚＝改回单参／新造副本；多枚＝同一出口重复取码，属奇怪但须看板（防未来
     * Filter 拆分成两个 429 出口而常量只在第一处生效）。</p>
     */
    @Test
    @DisplayName("RateLimitFilter 与 QuotaFilter 两枚 429 出口必须取 RateLimitErrorCode 常量单源")
    void quotaAndRateLimit429ExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        assertCarriesRateConstants(root.resolve(RATE_LIMIT_FILTER_SOURCE));
        assertCarriesRateConstants(root.resolve(QUOTA_FILTER_SOURCE));
    }

    private static void assertCarriesRateConstants(Path filterPath) throws IOException {
        assertTrue(Files.exists(filterPath), "属主过滤文件不在位：" + filterPath);
        String stripped = SourceCommentStripper.strip(Files.readString(filterPath, StandardCharsets.UTF_8));
        List<String> hits = new ArrayList<>();
        Matcher m = RATE_CODE_REFERENCE.matcher(stripped);
        while (m.find()) {
            hits.add(m.group());
        }
        assertEquals(1, hits.size(),
                "属主 429 出口必须恰 1 处 RateLimitErrorCode.ECOS_RATE_<n> 引用；"
                        + "0 处＝改回裸 429／单参工厂（判据 C 面亦会红），"
                        + ">1 处＝出口重复取码必须同批清点。文件=" + filterPath + "，实跑=" + hits);
    }

    // ── 3. 重载契约 ─────────────────────────────────

    @Test
    @DisplayName("五枚带码重载存在、数字码与单参版同号、机读码原样进信封")
    void codedOverloadsExistAndEmitMachineCode() throws Exception {
        assertCoded("badRequest", ApiResponse.CODE_BAD_REQUEST);
        assertCoded("unauthorized", ApiResponse.CODE_UNAUTHORIZED);
        assertCoded("forbidden", ApiResponse.CODE_FORBIDDEN);
        assertCoded("notFound", ApiResponse.CODE_NOT_FOUND);
        // 带码 internalError 取 500（与状态行同号），单参版仍 -1，见下一条
        assertCoded("internalError", ApiResponse.CODE_SERVER_ERROR);
    }

    @Test
    @DisplayName("单参 legacy 工厂数字码逐枚保持不变（-1 约定属另批裁决，本批不偷改）")
    void legacySingleArgFactoriesKeepTheirNumericCodes() {
        assertEquals(ApiResponse.CODE_BAD_REQUEST,
                ApiResponse.<Void>badRequest("m").getCode(), "badRequest 单参版数字码被改＝动了 333 处存量语义面");
        assertEquals(ApiResponse.CODE_UNAUTHORIZED,
                ApiResponse.<Void>unauthorized("m").getCode());
        assertEquals(ApiResponse.CODE_FORBIDDEN,
                ApiResponse.<Void>forbidden("m").getCode());
        assertEquals(ApiResponse.CODE_NOT_FOUND,
                ApiResponse.<Void>notFound("m").getCode());
        assertEquals(-1, ApiResponse.<Void>internalError("m").getCode(),
                "CODE_INTERNAL_ERROR 的 -1 是信封 v1「code<0＝系统错误」约定；改它属另批裁决");
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR,
                ApiResponse.<Void>internalError("m").getCode(), "-1 必须来自常量而非散落字面量");
        assertNull(ApiResponse.<Void>internalError("m").getErrorCode(),
                "单参版仍不填机读码＝本门基线追缴的对象，若哪天偷偷批量填码而不改基线，这里必须给出证据");
    }

    private static void assertCoded(String name, int expectedCode) throws Exception {
        Method method = ApiResponse.class.getMethod(name, String.class, String.class);
        Object returned = method.invoke(null, "ECOS-SYS-TEST", "msg");
        ApiResponse<?> envelope = (ApiResponse<?>) returned;
        assertEquals(expectedCode, envelope.getCode(), name + " 带码重载的数字码不符");
        assertEquals("ECOS-SYS-TEST", envelope.getErrorCode(), name + " 带码重载未把机读码放进信封");
        assertEquals("msg", envelope.getMessage());
        assertNull(envelope.getData(), name + " 错误出口的 data 必须为 null");
        assertNotNull(envelope.getTimestamp());
    }

    // ── 4. 判据能变红（仓外合成夹具）────────────────

    @Test
    @DisplayName("合成夹具：带码调用不计、单参调用逐条计入、注释内提及不计")
    void fixtureCountsSingleArgSitesOnlyAndIgnoresComments(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("SyntheticFactoryCarrier.java");
        Files.writeString(f, String.join("\n",
                "class Carrier {",
                "  Object a = ApiResponse.badRequest(SystemErrorCode.ECOS_SYS_400, m);",   // 带码＝不计
                "  Object b = ApiResponse.notFound(m);",                                    // 计 1
                "  Object c = ApiResponse.forbidden(m);",                                   // 计 1
                "  Object d = ApiResponse.internalError(m);",                               // 计 1
                "  // ApiResponse.notFound(m) 只是说明文字，不计",
                "}",
                ""), StandardCharsets.UTF_8);
        List<String> raw = List.of(Files.readString(f, StandardCharsets.UTF_8));
        assertEquals(3, countSites(raw.get(0)), "剥注释后应恰计 3 处单参调用（带码与注释各排除一枚）");
        // 反向：若不剥注释，说明文字会被计成违例＝本门刻意规避的口径漂移
        assertEquals(4, countSitesNaive(raw.get(0)),
            "该夹具同时取证「不剥注释会多计一枚」，判据口径变更必须留下可见差异");
    }

    private static int countSites(String src) {
        return countIn(SourceCommentStripper.strip(src));
    }

    private static int countSitesNaive(String src) {
        return countIn(src);
    }

    private static int countIn(String text) {
        Matcher m = SINGLE_ARG_FACTORY.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // ── 扫描实现 ────────────────────────────────────

    private static final class Scan {
        int scannedFiles;
        final Map<String, Integer> hitsByFile = new TreeMap<>();
        final Map<String, Integer> byFactory = new TreeMap<>();

        int totalSites() {
            return hitsByFile.values().stream().mapToInt(Integer::intValue).sum();
        }

        int filesWithHit() {
            return hitsByFile.size();
        }

        Map<String, Integer> countByFactory() {
            return new TreeMap<>(byFactory);
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
            Matcher m = SINGLE_ARG_FACTORY.matcher(stripped);
            int n = 0;
            while (m.find()) {
                n++;
                scan.byFactory.merge(m.group(1), 1, Integer::sum);
            }
            if (n > 0) {
                scan.hitsByFile.put(rel, n);
            }
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
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

    private static List<String> firstN(List<String> in, int n) {
        return in.size() <= n ? in : in.subList(0, n);
    }

    private static Path locateRepoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
