package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.common.security.sql.PhysicalQueryGuard;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code QueryPhysicalTable} 工具腿 —— 把「查一张物理表」的请求交给<b>数据引擎属主</b>，
 * 而不是在 agent 进程里替它执行 SQL（#142/B4-3e-16，收口差距 <b>D-B4-49</b> 的任意 SQL 子项）。
 *
 * <p><b>收口前的形态为什么必须废</b>：本腿此前取 {@code params.get("sql")}（内容由 LLM 决定）
 * 直接交给 {@code AgentToolDao.queryPhysicalTable}，而 DAO 的实现是
 * {@code jdbcTemplate.queryForList(sql + " LIMIT " + maxRows)} —— 整串 SQL 原样下推，
 * 于是这条腿同时具备三个越权面：①绕开 security-engine（铁律约束 3「安全一律走 security-engine
 * REST」，本腿不裁决、不 RLS、不列过滤、不脱敏）；②绕开属主引擎的写通道与历史台账（铁律约束 5），
 * 查了什么只写进本腿自己 {@code INSERT} 的 {@code ecos_query_log}；③字符串拼接 {@code LIMIT}
 * 加上「上游文本即 SQL」＝任何 DML/DDL/多语句都能进连接（{@code ;} 分隔即可）。</p>
 *
 * <p><b>现口径</b>：①前置判据 {@link PhysicalQueryGuard} 只放行「SELECT 起首 + 单表 +
 * 无注释/多语句/写关键字」，判据不过<b>不发起调用</b>；②放行时把 FROM 提取到的表名
 * <b>如实带进请求体 {@code table_name}</b> —— 属主端点
 * {@code QueryController#execute} 只在 {@code table_name} 非空时才跑 RLS（该触发条件覆盖面在
 * #142/B4-3e-18 按实测另立为差距 <b>D-B4-63</b>，与已收口的 D-B4-59「裸回状态行」是两件事），
 * 所以"带上表名"不是装饰，而是这条腿能否被行级安全覆盖的分界；
 * ③基址经 {@link AgentToolEndpointResolver#dataBaseUrl()}（属主 {@code datanet}，
 * 禁内联 {@code localhost:8080}）；④应答按 {@link Outcome} <b>分态</b>归一，
 * §4.7.2「不可用 ≠ 无权限」在此落到 {@code SECURITY_UNAVAILABLE} 与 {@code DENIED} 两个态上，
 * 禁把 503 折成 403；⑤入参键面与返回的 {@code rows}/{@code rowCount} 保持原形（只增不改），
 * 新增 {@code historyId} 等段来自属主应答原样透传。</p>
 *
 * <p><b>为什么两套来源都读</b>：属主端点自 #142/B4-3e-18（收口 <b>D-B4-59</b> 的 HTTP 保真腿）
 * 起，把 DENIED／UNAVAILABLE 经 {@code ResponseEntity} 落成<b>真状态行</b>（403／503），
 * 本腿的 {@link HttpStatusCodeException} 分支因此不再是纸面分支；但 {@code code}／
 * {@code errorCode} 仍是<b>权威</b>分态来源——单体包装、网关前置改写或历史部署都可能只给
 * 200 信封，两个来源共用 {@link Outcome} 这一套词表，谁先到用谁，绝不互相折叠。</p>
 *
 * <p><b>身份载体</b>：属主侧 RLS 以 {@code UserContext}／转发的请求头定主语。本腿取头不再自己读
 * {@code RequestContextHolder}，而是并轨到 #142/B4-3e-21 收口 <b>D-B4-61</b> 建成的单源
 * {@link CallerCredentialSource#currentAuthorization()}（取头＋中继纪律单源，逐字转发不升权）；
 * 取不到载体时<b>不伪造身份、也不静默放行</b>，由属主侧「默认 DENY」裁决，
 * 本腿把结论如实归一成 {@code DENIED}/{@code SECURITY_UNAVAILABLE}。
 * <b>在册余项</b>：agent 的执行腿常在<b>异步/SSE 线程</b>上跑，线程绑定的请求上下文在那里取不到
 * ⇒ 恒为空载体 ⇒ 属主默认 DENY。补齐要的是<b>服务凭证签名</b>（跨线程不靠中继用户 JWT），
 * 属另一批次，不在本批夹带。</p>
 */
@Component
public class QueryPhysicalTableTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryPhysicalTableTool.class);

    /**
     * data-engine 属主端点：统一 SQL 查询执行。
     *
     * <p>路径单源在此，族→属主映射（{@code /api/v1/engine/data} → {@code datanet}）由
     * {@code AgentToolPathReachabilityTest} 按 route-manifest 校验可达性。
     * 本工具在 V55 种子目录里登记的端点即为这一条
     * （{@code docs/50-db_script/02-flyway-migration/gateway/V55__ecos_tool_seed.sql}），
     * 收口前的实现反而没有走它 —— 这是本批裁决的依据之一。</p>
     */
    static final String PATH_DATA_QUERY_EXECUTE = "/api/v1/engine/data/query/execute";

    /** 载荷键：结论态名（{@link Outcome}），与 {@code available} 一起构成本腿的机读分态面。 */
    static final String KEY_STATE = AgentToolResponses.KEY_STATE;

    /** 载荷键：实际下发到属主端点的物理表名（放行时必填，供调用方核对 RLS 作用对象）。 */
    static final String KEY_TABLE_NAME = "tableName";

    /** 载荷键：数据源 ID（原形保留，收口前后同名）。 */
    static final String KEY_DATASOURCE_ID = "datasourceId";

    /** 请求体键：属主端点读取的蛇形键（与 {@code QueryController#execute} 契约一致）。 */
    static final String BODY_DATASOURCE_ID = "datasource_id";

    /** 请求体键：SQL 文本。 */
    static final String BODY_SQL = "sql";

    /** 请求体键：表名 —— 决定属主侧是否启用行级安全。 */
    static final String BODY_TABLE_NAME = "table_name";

    /** 请求体键：行数上限（已由 {@link PhysicalQueryGuard#clampMaxRows(Object)} 收口）。 */
    static final String BODY_MAX_ROWS = "max_rows";

    /** 族名（用于 {@link AgentToolResponses#unreachable(String)} 定位断在哪条腿）。 */
    private static final String FAMILY = "data";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public QueryPhysicalTableTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Map<String, Object> safe = params == null ? Map.of() : params;
        String datasourceId = (String) safe.get("datasourceId");
        String sql = (String) safe.get("sql");
        int maxRows = PhysicalQueryGuard.clampMaxRows(safe.get("maxRows"));

        PhysicalQueryGuard.Decision decision = PhysicalQueryGuard.inspect(sql);
        if (!decision.allowed()) {
            log.warn("[agent-tool] QueryPhysicalTable 前置判据拒绝，未发起调用: {}", decision.reason());
            return outcome(Outcome.GUARD_REJECTED, decision.reason());
        }

        Optional<String> base = endpoints.dataBaseUrl();
        if (base.isEmpty()) {
            // 族内「未发起调用」文案单源在此，另补本腿的 state 键，避免同一族出现两套机读面
            return stateOn(AgentToolResponses.unreachable(FAMILY), Outcome.UNREACHABLE);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(BODY_DATASOURCE_ID, datasourceId);
        body.put(BODY_SQL, sql);
        body.put(BODY_TABLE_NAME, decision.tableName());
        body.put(BODY_MAX_ROWS, maxRows);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        String authHeader = currentAuthorizationHeader();
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }

        Object envelope;
        try {
            envelope = restTemplate.postForObject(base.get() + PATH_DATA_QUERY_EXECUTE,
                    new HttpEntity<>(body, headers), Object.class);
        } catch (HttpStatusCodeException e) {
            log.warn("[agent-tool] QueryPhysicalTable 属主返回非 2xx 状态: status={}, tableName={}",
                    e.getStatusCode(), decision.tableName());
            return outcome(outcomeOfHttpStatus(e.getStatusCode().value()), e.getMessage());
        } catch (RestClientException e) {
            log.warn("[agent-tool] QueryPhysicalTable 调用数据引擎腿失败（按不可用处置，不冒充结果）: {}",
                    e.getMessage());
            return outcome(Outcome.UNREACHABLE, "数据引擎查询端点调用失败：" + e.getMessage());
        }
        return readEnvelope(envelope, datasourceId, decision.tableName());
    }

    @Override
    public boolean supports(String toolCode) {
        return "QueryPhysicalTable".equals(toolCode);
    }

    /**
     * 属主 {@code ApiResponse} envelope → 分态载荷。
     *
     * <p>{@code code} 为 {@code 0} 才算取到数据；{@code 403} 是「无权限」，
     * {@code 503} 或 {@code errorCode == ECOS-SEC-501}（单源
     * {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}，禁在本类抄字面量）是「安全服务不可用」，
     * 两者不得互相折叠；其它非零 {@code code} 属属主业务/系统失败，独立成
     * {@code ENGINE_FAILED}，不与「无权限」混为一谈。</p>
     */
    private Map<String, Object> readEnvelope(Object envelope, String datasourceId, String tableName) {
        if (!(envelope instanceof Map<?, ?> map)) {
            return outcome(Outcome.UNREACHABLE, "属主应答形态不可识别（非 envelope 对象），本次取数结论未知");
        }
        Map<String, Object> env = cast(map);
        Outcome outcome = outcomeOfEnvelopeCode(codeOf(env), errorCodeOf(env));
        if (outcome != Outcome.DATA_RETURNED) {
            return outcome(outcome, messageOf(env));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(AgentToolResponses.KEY_AVAILABLE, true);
        out.put(KEY_STATE, outcome.name());
        out.put(KEY_DATASOURCE_ID, datasourceId);
        out.put(KEY_TABLE_NAME, tableName);
        Object data = env.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            out.putAll(cast(dataMap));
        }
        return out;
    }

    private static Outcome outcomeOfEnvelopeCode(Integer code, String errorCode) {
        // 词表与映射判据单源在 Outcome（#142/B4-3e-22 上收）；读腿终态仍为 DATA_RETURNED，机读值一字未动
        return Outcome.fromEnvelope(code, errorCode, Outcome.DATA_RETURNED);
    }

    /** 真 4xx/5xx（网关前置过滤器也可能先行裁决）与 envelope {@code code} 共用 {@link Outcome} 这一套词表。 */
    private static Outcome outcomeOfHttpStatus(int status) {
        return Outcome.fromHttpStatus(status);
    }

    private static Map<String, Object> outcome(Outcome outcome, String message) {
        return Outcome.payload(outcome, message);
    }

    /** 在族内单源文案（{@link AgentToolResponses}）上补本腿的 {@code state} 键，不改其既有键值。 */
    private static Map<String, Object> stateOn(Map<String, Object> payload, Outcome outcome) {
        return Outcome.stateOn(payload, outcome);
    }

    private static Integer codeOf(Map<String, Object> env) {
        return env.get("code") instanceof Number n ? n.intValue() : null;
    }

    private static String errorCodeOf(Map<String, Object> env) {
        Object v = env.get("errorCode");
        return v == null ? null : v.toString();
    }

    private static String messageOf(Map<String, Object> env) {
        Object v = env.get("message");
        return v == null ? null : v.toString();
    }

    /**
     * 当前线程绑定的 HTTP 请求上的 {@code Authorization} 头；无绑定（非请求线程/异步腿）返回
     * {@code null} —— <b>不伪造身份、不回落到任何默认主体</b>，主语缺失交由属主侧默认 DENY 裁决。
     * 取头判据单源在 {@link CallerCredentialSource}（#142/B4-3e-21 收口 D-B4-61）。
     */
    private static String currentAuthorizationHeader() {
        return CallerCredentialSource.currentAuthorization();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

}
