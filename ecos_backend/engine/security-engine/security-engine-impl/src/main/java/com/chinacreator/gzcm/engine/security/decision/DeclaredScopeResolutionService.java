package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * AI 工具通道「声明域」的<b>服务端真值解析单源</b>（#142/B4-3e-79，差距 D-B4-36）。
 *
 * <p><b>缺陷本质（为什么必须在服务端）</b>：护栏 {@code ecos.data_access} 的 AI 列判据
 * 是「声明域 vs 服务端派生的主体域」比对手，而 §4.7.2 p1（项目负责人域内 ALLOW）要求
 * 声明域是<b>真值</b>。D-B4-29 已把 {@code departmentId} 从 AI 工具入参白名单剔除
 * （被裁决者自报域＝形同放行），但剔除之后现网<b>没有任何一条腿能把
 * {@code projectIds} 展开成真实所属域</b> —— 于是 AI 通道的声明域只剩两种形态：
 * 要么不写（策略落 {@code AI_DEPARTMENT_NOT_DECLARED}），要么由调用方在
 * {@code /decision} 的 {@code resource} 段自报（＝恢复 D-B4-29 刚关掉的洞）。
 * 本类补的正是缺失的那一条：由 security 侧向<b>属主域</b>（data-engine）取数并单点判定。</p>
 *
 * <p><b>为什么走 REST 而不是直读表</b>：真值表 {@code ecos_dw.ecos_biz_project_attribution}
 * 的属主是 data-engine，架构铁律 #5 禁跨引擎操作对方表（同一理由见
 * ontology {@code DataNetResourceClient}、kb {@code CuratedFactSourceRestImpl}）。
 * base URL 一律经 {@link ServiceEndpointResolver} 两态寻址（route-manifest 单源），
 * {@code ecos.route.datanet-base-url} 仅作可覆写显式配置，禁引擎内硬编码端口。</p>
 *
 * <p><b>判定纪律（本类是唯一裁决点）</b>：只有当声明项目集<b>每一项都有权威归属行</b>
 * 且这些行<b>恰好指向同一个部门</b>时，才把该部门写进声明域。其余形态（某项目查无归属、
 * 一个项目多部门、多项目跨部门）一律 {@code departmentId = null} —— 由策略侧落
 * {@code AI_DEPARTMENT_NOT_DECLARED}。<b>解析不出域 ≠ 越权</b>：这里绝不伪造一个域、
 * 也不把"多部门"折算成其中之一，因为那会让横向越权地板拿一个编造值去做比对。</p>
 *
 * <p><b>不可用纪律（§4.7.2「不可用 ≠ 无权限」）</b>：属主端点寻址失败 / 传输失败 /
 * 非 2xx / 应答不可解析 ⇒ 抛 {@link ScopeSourceUnavailableException}，由
 * {@code DecisionController} 的既有 fail-closed 出口转 503 + {@code ECOS-SEC-501}
 * （= {@code RlsExecutionContract.SEC_UNAVAILABLE_CODE} 单源读数），
 * <b>禁</b>折成"解析不出域"（那是 403 语义，会把部署缺陷伪装成用户没权限），
 * 也禁折成放行。</p>
 */
@Service
public class DeclaredScopeResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DeclaredScopeResolutionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接 2s / 读 3s：裁决链上的同步取数，超时上限必须显著小于调用方预算。 */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /** 属主端点路径（data-engine {@code FactController#listAttributionDepartments}）。 */
    static final String PATH_ATTRIBUTION_DEPARTMENTS =
            "/api/v1/datanet/facts/attribution/departments";

    /** 属主不可达 / 应答不可解析的机读理由（进异常 message，供日志与门禁区分形态）。 */
    public static final String REASON_SOURCE_UNAVAILABLE = "DECLARED_SCOPE_SOURCE_UNAVAILABLE";

    /** 应答 {@code data} 段里的分组键（与属主端点形态同源）。 */
    private static final String KEY_BY_PROJECT = "byProject";
    private static final String KEY_REQUESTED = "requested";

    private final Supplier<String> datanetBaseUrlProvider;

    /** 可覆写显式配置（默认空＝走 resolver）；禁带硬编码端口默认值。 */
    private final String datanetBaseUrlOverride;

    /** 跨引擎寻址单源（route-manifest）。缺失时不降级：取数即 fail-loud 转不可用态。 */
    private final ServiceEndpointResolver endpointResolver;

    /**
     * 生产装配：{@code Optional} 只用于<b>装配面</b>（boot 调试态可能不带 runtime-access bean），
     * 取数面仍 fail-loud —— 缺席时 {@link #resolveDatanetBaseUrl()} 抛不可用态，
     * 禁把"没接上"折叠成"该项目没归属"（那是 403 语义，会把部署缺陷伪装成用户没权限）。
     */
    @Autowired
    public DeclaredScopeResolutionService(@Value("${ecos.route.datanet-base-url:}") String datanetBaseUrlOverride,
                                          Optional<ServiceEndpointResolver> endpointResolver) {
        this.datanetBaseUrlOverride = datanetBaseUrlOverride;
        this.endpointResolver = endpointResolver == null ? null : endpointResolver.orElse(null);
        this.datanetBaseUrlProvider = this::resolveDatanetBaseUrl;
    }

    /**
     * 测试/特殊装配入口：base URL 由调用方供给（如指向本地桩或死端口）。
     * 包可见，不参与 Spring 装配（Spring 只把 public 构造当候选，故与上面那枚不冲突）。
     */
    DeclaredScopeResolutionService(Supplier<String> datanetBaseUrlProvider) {
        this.datanetBaseUrlOverride = null;
        this.endpointResolver = null;
        this.datanetBaseUrlProvider = datanetBaseUrlProvider;
    }

    /**
     * 把 AI 工具通道的声明域解析成服务端真值形态。
     *
     * <p><b>入参一律不信</b>：返回值的 {@code departmentId} 只可能来自属主取数，
     * 调用方带进来的 {@code declared.departmentId()} 在这里被无条件丢弃
     * （这是 D-B4-36 的收口点，也是 D-B4-29 白名单纪律在 {@code /decision} 侧的对偶）。
     * 项目集为空＝没声明，无需取数，原样返回空段（不产生任何 HTTP 调用，
     * 页面/导出通道与非声明形态调用方零开销）。</p>
     *
     * @param declared 从请求体读出的声明域（只取 {@code projectIds}）
     * @return {@code new Declared(真值部门 or null, 原项目集)}
     * @throws ScopeSourceUnavailableException 属主端点不可达 / 应答不可解析（调用方转 503，禁折叠）
     */
    public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
        if (declared == null || !declared.hasProjectScope()) {
            // 未声明项目集：策略侧落 AI_SCOPE_NOT_DECLARED，无需取数也不伪造部门
            return declared == null ? DataScopeContract.none()
                    : new DataScopeContract.Declared(null, declared.projectIds());
        }
        List<String> projectIds = distinct(declared.projectIds());
        Map<String, List<String>> byProject = fetchAttributionDepartments(projectIds);
        String department = uniqueDepartment(projectIds, byProject);
        if (department == null) {
            // 解析不出域是"事实不完备"，不是越权：如实留空，让策略落 AI_DEPARTMENT_NOT_DECLARED
            log.warn("声明域真值解析未果（缺归属或多部门歧义），按未声明处置: projectIds={}, byProject={}",
                    projectIds, byProject);
        }
        return new DataScopeContract.Declared(department, projectIds);
    }

    /**
     * 真值判定单点：声明项目集是否<b>恰好</b>指向一个部门。
     *
     * <p>包可见以便用例直读判定的三条腿（缺归属／歧义／一致），不靠 HTTP 夹具凑形态。</p>
     *
     * @return 唯一部门；任一项无权威归属行、或全量部门集合 ≠ 1 ⇒ {@code null}
     */
    static String uniqueDepartment(List<String> requested, Map<String, List<String>> byProject) {
        if (requested == null || requested.isEmpty() || byProject == null) {
            return null;
        }
        Set<String> distinct = new TreeSet<>();
        for (String pid : requested) {
            List<String> depts = byProject.get(pid);
            if (depts == null || depts.isEmpty()) {
                return null;
            }
            for (String dept : depts) {
                if (dept == null || dept.isBlank()) {
                    return null;
                }
                distinct.add(dept.trim());
            }
        }
        return distinct.size() == 1 ? distinct.iterator().next() : null;
    }

    /**
     * 取属主读回并归一成 {@code {projectId: [departmentId...]}}（请求顺序里的项若无行则不落键）。
     *
     * <p>信封纪律（与 {@code AiSecurityEngineClient} 同族）：先看 HTTP 状态行，再认信封
     * {@code code}；{@code code != CODE_SUCCESS} 时 security 拿不到事实 ⇒ 抛不可用态，
     * <b>禁</b>把 400/403 的属主拒绝折成"该项目没归属"（后者会让策略落越权理由，理由是假的）。</p>
     */
    Map<String, List<String>> fetchAttributionDepartments(List<String> projectIds) {
        String base = baseUrl();
        String url = base + PATH_ATTRIBUTION_DEPARTMENTS + queryOf(projectIds);
        Map<String, Object> envelope;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw unavailable("属主端点非 2xx: status=" + resp.statusCode() + " url=" + url);
            }
            envelope = MAPPER.readValue(resp.body(), new TypeReference<Map<String, Object>>() {
            });
        } catch (ScopeSourceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable("属主端点不可达或应答不可解析: url=" + url
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
        if (envelope == null) {
            throw unavailable("属主应答为空: url=" + url);
        }
        Object code = envelope.get("code");
        if (!(code instanceof Number n) || n.intValue() != ApiResponse.CODE_SUCCESS) {
            throw unavailable("属主应答非成功码: code=" + code + " message=" + envelope.get("message"));
        }
        if (!(envelope.get("data") instanceof Map<?, ?> data)) {
            throw unavailable("属主应答缺 data 段: url=" + url);
        }
        if (!(data.get(KEY_BY_PROJECT) instanceof Map<?, ?> byProjectRaw)) {
            throw unavailable("属主应答缺 " + KEY_BY_PROJECT + " 段: dataKeys=" + data.keySet());
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        byProjectRaw.forEach((k, v) -> {
            List<String> depts = new ArrayList<>();
            if (v instanceof List<?> list) {
                for (Object o : list) {
                    String dept = DataScopeContract.text(o);
                    if (dept != null && !depts.contains(dept)) {
                        depts.add(dept);
                    }
                }
            }
            out.put(String.valueOf(k), depts);
        });
        // requested 段用于对账：属主读到的项目集与请求不一致＝键面漂移（属主侧改写而未同步），显式失败
        if (data.get(KEY_REQUESTED) instanceof List<?> requested
                && !requested.stream().map(String::valueOf).toList().equals(projectIds)) {
            throw unavailable("属主回读项目集与请求不一致: requested=" + requested
                    + " projectIds=" + projectIds);
        }
        return out;
    }

    /** 当前生效的 datanet base URL（覆写 → resolver → fail-loud）。 */
    private String baseUrl() {
        return datanetBaseUrlProvider.get();
    }

    private String resolveDatanetBaseUrl() {
        if (datanetBaseUrlOverride != null && !datanetBaseUrlOverride.isBlank()) {
            return datanetBaseUrlOverride.trim();
        }
        if (endpointResolver == null) {
            throw unavailable("ServiceEndpointResolver 未装配，无法寻址 datanet"
                    + "（禁回落到硬编码端口，F02-15 两态寻址口径）");
        }
        return endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH)
                .orElseThrow(() -> unavailable("route-manifest 缺 datanet 条目"));
    }

    /**
     * 项目集去重（保序）。
     *
     * <p>存在理由：属主回读的 {@code requested} 段是去重后的形态，去重前若拿原始列表做对账，
     * 重复项会读出"键面漂移"这一假信号；{@code DataScopeContract.read} 已在读取侧去重，
     * 本方法只覆盖直接构造 {@code Declared} 的通道（AI 载荷与测试夹具）。去重不改声明范围。</p>
     */
    private static List<String> distinct(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                seen.add(s);
            }
        }
        return List.copyOf(seen);
    }

    /** {@code ?projectIds=a&projectIds=b}（逐值 URL 编码，禁拼接未编码外部输入进 URL）。 */
    private static String queryOf(List<String> projectIds) {
        StringBuilder sb = new StringBuilder("?");
        Set<String> seen = new LinkedHashSet<>();
        for (String pid : projectIds) {
            if (!seen.add(pid)) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append('&');
            }
            sb.append(DataScopeContract.KEY_PROJECT_IDS).append('=')
                    // URLEncoder 是 form 编码：空格成 '+'，而 '+' 只在 form 语义下才是空格。
                    // 项目号本身可含字面 '+'，届时两侧读解歧义 ⇒ 统一成 RFC 3986 的 %20。
                    .append(URLEncoder.encode(pid, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static ScopeSourceUnavailableException unavailable(String reason) {
        log.warn("声明域真值源不可用（fail-closed 转 503，禁折叠成未声明/越权）: {}", reason);
        return new ScopeSourceUnavailableException(REASON_SOURCE_UNAVAILABLE + ": " + reason);
    }

    /** 真值源不可用（属主寻址失败 / 非 2xx / 应答不可解析）——调用方必须转不可用态，禁当"没归属"。 */
    public static class ScopeSourceUnavailableException extends RuntimeException {
        public ScopeSourceUnavailableException(String message) {
            super(message);
        }
    }
}
