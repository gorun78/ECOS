package com.chinacreator.gzcm.workspace.caliber;

import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import com.chinacreator.gzcm.workspace.security.CallerCredentialRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 口径属主（本体域）REST 委托客户端（#146ac 读面三枚 + #146ad 写面三枚的唯一出网点）。
 *
 * <h3>为什么必须走委托，不在 workspace 侧读表</h3>
 * 口径主权归本体域（属主 {@code CaliberController} 类注释即「唯一写入口 + 跨引擎只读面」），
 * 铁律 §3.1「跨引擎不操作对方表」由 {@code WorkspaceLayeringArchTest} 冻结基线把守；
 * 出站通道又必须收敛 runtime 底座（{@code ModuleDependencyArchTest} + {@code RuntimeConvergenceArchTest}
 * 双门禁禁自建 RestTemplate）。因此本类只注入既有 Bean
 * {@code workspaceBuszhiRest}（两态寻址单源 = {@code WorkspaceRestClientConfig} + route-manifest），
 * 业务侧只传相对路径。
 *
 * <h3>本类只解信封，不判契约状态行</h3>
 * 属主面全类裸回 {@code ApiResponse}（D-B4-59 裸回族在册欠账，属主侧收口不归本批），
 * 失败<b>不体现在 HTTP 状态行上，只体现在信封数字码面</b>。若本类按 HTTP 200 一律当成功，
 * 属主的 400/404/500 会被冒充成口径契约的 200——故 {@link OwnerReply} 把信封三元组
 * （code / errorCode / data）原样上抛，由 {@link CaliberAddressing} 单点映射成真实状态行。
 * 传输层故障（连不上／超时／非 2xx／信封不可解析）在本类即归 503 不可得态，
 * 禁止折叠成空列表（X-43/X-44）。
 *
 * <h3>写面为什么能拿到属主侧的审批主语（#146ad）</h3>
 * 属主 {@code CaliberController#approve} 的审批人只认 {@code UserContext.getCurrentUsername()}，
 * 而 route-manifest 里 {@code /api/v1/ontology} 的 {@code owner=buszhi} 配 {@code route="monolith"}
 * ⇒ 既有 Bean {@code workspaceBuszhiRest} 经 {@code ServiceEndpointResolver} 实际解析到
 * gateway :8080，gateway 侧 {@code JwtAuthenticationFilter} 在验签后填充 {@code UserContext}。
 * 因此委托侧<b>唯一缺的是凭证中继</b>：{@link #exchange} 单点对全腿施加
 * {@link CallerCredentialRelay#applyTo(HttpHeaders)}（无请求上下文即不加头、不伪造主语，
 * 由属主端按自己的默认策略裁决）。入口连一枚凭证头都没有时，{@code CaliberWriteService}
 * 在出网之前即拦成 403，不拿匿名请求去属主面撞 400。
 */
@Component
public class CaliberOwnerClient {

    private static final Logger log = LoggerFactory.getLogger(CaliberOwnerClient.class);

    /** 属主 list 面硬上限（{@code CaliberController#list} 内 {@code Math.min(limit, 500)} 的实测值）。 */
    public static final int OWNER_WINDOW_LIMIT = 500;

    private static final String LIST_ALL_PATH = "/ontology/calibers?limit={limit}";
    private static final String LIST_BY_STATUS_PATH = "/ontology/calibers?status={status}&limit={limit}";
    private static final String DETAIL_PATH = "/ontology/calibers/{code}";
    private static final String VALIDATE_PATH = "/ontology/calibers/validate";

    /** 写面三枚（#146ad）：路径模板逐字对齐属主 {@code CaliberController}，业务侧只传相对 URI。 */
    private static final String CREATE_PATH = "/ontology/calibers";
    private static final String ADD_VERSION_PATH = "/ontology/calibers/{code}/versions";
    private static final String APPROVE_PATH = "/ontology/calibers/{code}/versions/{versionNo}/approve";

    private static final ParameterizedTypeReference<Map<String, Object>> ENVELOPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate buszhiRest;

    public CaliberOwnerClient(@Qualifier("workspaceBuszhiRest") RestTemplate buszhiRest) {
        this.buszhiRest = buszhiRest;
    }

    /**
     * 属主口径主行窗口。status 缺省＝不约束（本面不透传该参数，不写「全部状态」逗号串——
     * 属主按 {@code status=?} 等值匹配，逗号串会命中零行并伪装成「无口径」）。
     * 版本链不在本面（属主 list 只回主行），缺席成因见 {@link CaliberReadService}。
     */
    public OwnerReply listHeadRows(String status) {
        boolean filtered = status != null && !status.isBlank();
        return exchange("list", HttpMethod.GET,
                filtered ? LIST_BY_STATUS_PATH : LIST_ALL_PATH, HttpEntity.EMPTY,
                filtered ? new Object[]{status, OWNER_WINDOW_LIMIT} : new Object[]{OWNER_WINDOW_LIMIT});
    }

    /** 属主口径详情（含版本行数组，键面为库侧 snake_case 原样）。 */
    public OwnerReply detailByCode(String code) {
        return exchange("detail", HttpMethod.GET, DETAIL_PATH, HttpEntity.EMPTY, code);
    }

    /** 属主 DSL 四关预检（只校验不落库；判定权全在属主，本面不预校验公式）。 */
    public OwnerReply validate(Map<String, Object> payload) {
        return exchange("validate", HttpMethod.POST, VALIDATE_PATH, new HttpEntity<>(payload));
    }

    /**
     * 属主新建口径主行（{@code CaliberSaveDTO} 七字段面；status 由属主兜底，不由本面决定）。
     *
     * <p>属主对 status 空值的兜底是 {@code ACTIVE}（{@code CaliberService#insertCaliber} 实测），
     * 而跨引擎只读消费面以主表 {@code status='ACTIVE'} 为过滤条件 ⇒ 本面不拿 DRAFT 去顶主行，
     * DRAFT 的真实落点是版本行（见 {@code CaliberWriteService}）。</p>
     */
    public OwnerReply createHeadRow(Map<String, Object> payload) {
        return exchange("create", HttpMethod.POST, CREATE_PATH, new HttpEntity<>(payload));
    }

    /** 属主升版（行不可变，一次一行；H-2 四关在属主写入咽喉内再跑一遍）。 */
    public OwnerReply addVersion(String code, Map<String, Object> payload) {
        return exchange("createVersion", HttpMethod.POST, ADD_VERSION_PATH,
                new HttpEntity<>(payload), code);
    }

    /**
     * 属主审批（DRAFT/REVIEWING→APPROVED）。
     *
     * <p>审批人主语完全由属主侧从 {@code UserContext} 取，本面不传任何身份字段——
     * 身份只经 {@link CallerCredentialRelay} 的凭证头中继，禁把它写进请求体（伪造主语）。</p>
     */
    public OwnerReply approveVersion(String code, String versionNo) {
        return exchange("approve", HttpMethod.POST, APPROVE_PATH, HttpEntity.EMPTY, code, versionNo);
    }

    // ═══════════════ 私有辅助 ═══════════════

    private OwnerReply exchange(String operation, HttpMethod method, String pathTemplate,
                                HttpEntity<?> requestEntity, Object... uriVars) {
        ResponseEntity<Map<String, Object>> response;
        try {
            response = buszhiRest.exchange(pathTemplate, method, withCallerCredentials(requestEntity),
                    ENVELOPE, uriVars);
        } catch (RestClientException ex) {
            // X-41：下游原文（URL／异常 message）只进日志面，响应体只留固定文案 + traceId
            log.warn("口径属主委托失败 operation={} path={} cause={}",
                    operation, pathTemplate, ex.getClass().getSimpleName());
            throw new CaliberOwnerUnavailableException(operation);
        }
        Map<String, Object> body = response.getBody();
        if (body == null) {
            log.warn("口径属主应答空体 operation={} http={}", operation, response.getStatusCode());
            throw new CaliberOwnerUnavailableException(operation);
        }
        Object rawCode = body.get("code");
        if (!(rawCode instanceof Number numericCode)) {
            log.warn("口径属主信封不可解析 operation={} codeKeyType={}",
                    operation, rawCode == null ? "absent" : rawCode.getClass().getSimpleName());
            throw new CaliberOwnerUnavailableException(operation);
        }
        Object errorCode = body.get("errorCode");
        return new OwnerReply(operation, numericCode.intValue(),
                errorCode == null ? null : String.valueOf(errorCode),
                body.get("data"));
    }

    /**
     * 出网前的统一请求头装配：Accept 恒 JSON、有体腿补 Content-Type、全腿中继入口凭证。
     *
     * <p>中继点在 {@link #exchange} 而不是各方法内，是为了让「新增一条腿忘了带凭证」这种
     * 形态不可能出现——属主 approve 腿一旦匿名出网，只回 400 {@code ECOS-ONTO-040}
     * 且审批人不落库，那比失败更难回查。</p>
     */
    private HttpEntity<?> withCallerCredentials(HttpEntity<?> requestEntity) {
        HttpHeaders headers = new HttpHeaders();
        if (requestEntity.getHeaders() != null) {
            requestEntity.getHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (requestEntity.getBody() != null && headers.getContentType() == null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        CallerCredentialRelay.applyTo(headers);
        return new HttpEntity<>(requestEntity.getBody(), headers);
    }

    /**
     * 属主信封三元组（HTTP 状态行在属主面恒 200，真实语义只在 code 面 —— 见类注释）。
     *
     * <p>本记录不做业务判定，只负责「键面形态」的 fail-closed：data 段形态与出网面不符
     * ＝属主契约漂移，归不可得态（503），禁按空集合继续渲染。</p>
     *
     * @param operation 出网点标识（list/detail/validate/create/createVersion/approve），同时用于失败取证
     * @param code      信封数字码（0 = 成功；400/404/500/-1 = 属主侧失败）
     * @param errorCode 属主机读码（{@code ECOS-ONTO-0xx} / {@code CAL-404} / {@code ok}），不带原文 message
     * @param data      信封 data 段（List／Map／null）
     */
    public record OwnerReply(String operation, int code, String errorCode, Object data) {

        public static final int CODE_OK = 0;
        public static final int CODE_BAD_REQUEST = 400;
        public static final int CODE_NOT_FOUND = 404;

        public boolean ok() {
            return code == CODE_OK;
        }

        public boolean notFound() {
            return code == CODE_NOT_FOUND;
        }

        /** data 段的行数组形态（属主 list 面）；非列表形态＝契约漂移 → 503。 */
        public List<Map<String, Object>> rows() {
            if (data instanceof List<?> list) {
                return list.stream().filter(Map.class::isInstance).map(OwnerReply::castRow).toList();
            }
            throw new CaliberOwnerUnavailableException(operation);
        }

        /** data 段的对象形态（属主 detail／validate 面）；缺段＝契约漂移 → 503。 */
        public Map<String, Object> object() {
            if (data instanceof Map<?, ?> map && !map.isEmpty()) {
                return castRow(data);
            }
            throw new CaliberOwnerUnavailableException(operation);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> castRow(Object row) {
            return (Map<String, Object>) row;
        }
    }
}
