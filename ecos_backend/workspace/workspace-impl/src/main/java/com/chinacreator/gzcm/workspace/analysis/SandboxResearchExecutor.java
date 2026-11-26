package com.chinacreator.gzcm.workspace.analysis;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;

/**
 * 沙盒研究执行器客户端（L3 执行器分级第二档，可行性报告 v2.0 §5.4，2026-11-25）。
 *
 * <p><b>什么时候启用</b>：仅当运维显式配置 {@code ecos.research.sandbox.base-url}
 * （独立隔离沙盒服务的内部地址）时才装配。未配置 ⇒ 本 Bean 不存在，
 * 由 {@link ReadOnlyResearchExecutor} 兜底（查询型实验，零代码执行）。</p>
 *
 * <p><b>它不做什么</b>（红线，必读）：</p>
 * <ul>
 *   <li><b>不</b>在 ECOS JVM 或宿主机执行任何代码；所有代码执行由外部沙盒负责；</li>
 *   <li><b>不</b>直连生产库；请求只携带场景上下文摘要与假设，数据由沙盒侧按快照读取；</li>
 *   <li><b>不</b>把沙盒失败折算成成功：不可达/非 0 业务码 ⇒ 抛异常（服务层 503），
 *       绝不返回「执行成功但没数据」的假结论。</li>
 * </ul>
 *
 * <p><b>与只读实现的关系</b>：本类只替换 {@code executeRound} 的执行形态；
 * 预算、谱系、评估降级、审批留痕全部由 {@link ScenarioResearchRunService} 统一承担，
 * 换执行器不改变循环语义。</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "ecos.research.sandbox.base-url")
public class SandboxResearchExecutor implements ResearchExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(SandboxResearchExecutor.class);

    private final ReadOnlyResearchExecutor fallback;
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SandboxResearchExecutor(ReadOnlyResearchExecutor fallback,
                                   @Qualifier("workspaceResearchSandboxRest") RestTemplate restTemplate,
                                   @Value("${ecos.research.sandbox.base-url}") String baseUrl) {
        this.fallback = fallback;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 客户端由 WorkspaceRestClientConfig 构造（业务代码禁自建，F07-17 门禁）；基址已在该 Bean 内绑定
        this.restTemplate = restTemplate;
        log.info("L3 沙盒研究执行器已启用 baseUrl={}（代码执行在外部隔离沙盒，不在 ECOS 进程内）", this.baseUrl);
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "SANDBOX");
        out.put("codeExecution", true);
        out.put("network", false);
        out.put("sandbox", baseUrl);
        out.put("capabilities", List.of("DECLARATIVE_OPERATORS", "CONTROLLED_CODE_EXECUTION"));
        out.put("languages", List.of("java", "python"));
        out.put("note", "声明式算子 + 受控 Java/Python 代码执行；ECOS 本进程不执行任何代码，只编排与留痕");
        return out;
    }

    @Override
    public Map<String, Object> describeContext(String scenarioId) {
        return fallback.describeContext(scenarioId);
    }

    @Override
    public List<Map<String, Object>> proposeHypotheses(String scenarioId, String question) {
        return fallback.proposeHypotheses(scenarioId, question);
    }

    @Override
    public List<Map<String, Object>> refineHypotheses(String scenarioId, String question,
                                                      List<Map<String, Object>> previousRounds) {
        return fallback.refineHypotheses(scenarioId, question, previousRounds);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> executeRound(String scenarioId, String question,
                                            List<Map<String, Object>> hypotheses, int loopIndex) {
        long startedAt = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // 中继调用方凭证：沙盒侧按同一主体做审计与配额，禁裸调
        CallerCredentialSource.applyTo(headers);

        // 契约对齐沙盒服务（ecos-docker/research-sandbox）：snapshot.rows + experiment。
        // 快照只装**请求里已有的**只读行（由调用方从只读探针结果导出），沙盒不主动取数。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("snapshot", Map.of("rows", rowsForSnapshot(scenarioId, loopIndex, hypotheses)));
        body.put("experiment", Map.of(
                "metrics", List.of(Map.of("op", "count")),
                "note", "沙盒轮次仅做确定性计数校验；业务度量由调用方按场景口径补齐"));

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange(URI.create(baseUrl + "/research/rounds"),
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        } catch (RuntimeException e) {
            // 沙盒不可达：显式失败，禁回退成「只读结果」冒充本轮执行（那会让谱系失真）
            throw new IllegalStateException("沙盒研究执行器不可用: " + e.getMessage(), e);
        }
        Map<String, Object> envelope = resp.getBody();
        if (envelope == null) {
            throw new IllegalStateException("沙盒研究执行器应答为空");
        }
        Object code = envelope.get("code");
        if (code instanceof Number n && n.intValue() != 0) {
            throw new IllegalStateException("沙盒研究执行业务错误 code=" + n.intValue()
                    + " " + envelope.get("message"));
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            throw new IllegalStateException("沙盒研究执行器应答缺 data 段");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sandboxData = (Map<String, Object>) dm;
        // 沙盒必须回传确定性读数，缺失即失败（不让「没有 metric」被当作可评估）
        if (!(sandboxData.get("metric") instanceof Map)) {
            throw new IllegalStateException("沙盒研究执行器未回传 metric（禁无读数入评估）");
        }

        // 与服务层契约对齐：只读档返回 trace/experimentSpec/advice/metric，沙盒档必须同构，
        // 否则 json(execResult.get("trace")) 取不到 → exec_trace 落 null（谱系丢证据）。
        // 这里把沙盒的确定性读数翻成同一套结构，**不臆造**额外结论。
        long elapsed = System.currentTimeMillis() - startedAt;
        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(step("sandbox_call", "success",
                "沙盒执行完成（" + elapsed + "ms，算子 " + sandboxData.get("sandbox") + "）"));
        trace.add(step("metric", "success", "沙盒回传确定性读数"));
        trace.add(step("insight", sandboxData.get("insight") == null ? "skipped" : "success",
                sandboxData.get("insight") == null ? "单组或不可比，无描述性对比" : "已产出描述性对比（不含因果）"));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("loopIndex", loopIndex);
        spec.put("question", question);
        spec.put("hypotheses", hypotheses);
        spec.put("dataSlices", List.of("sandbox_snapshot"));
        spec.put("successCriteria", List.of("沙盒回传 metric 且 code=0"));
        spec.put("executor", "SANDBOX");

        Map<String, Object> advice = new LinkedHashMap<>();
        Object insight = sandboxData.get("insight");
        advice.put("summary", insight == null
                ? "沙盒轮次已完成确定性聚合；无分组对比，结论需补充可比切片"
                : "沙盒已给出组间描述性差异，具体业务含义须由人结合场景判断");
        advice.put("actions", List.of("核对快照口径与列白名单", "按需扩展算子或数据切片"));
        advice.put("risks", List.of("沙盒只做声明式算子，不覆盖任意代码实验",
                "业务真值缺失时评估仍为结构评估（显式降级）"));
        advice.put("confidence", "LOW");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trace", trace);
        out.put("experimentSpec", spec);
        out.put("advice", advice);
        out.put("metric", sandboxData.get("metric"));
        out.put("insight", insight);
        out.put("snapshotDigest", sandboxData.get("snapshotDigest"));
        out.put("sandbox", sandboxData.get("sandbox"));
        return out;
    }

    /** 与只读档同形的 trace 步骤。 */
    private static Map<String, Object> step(String name, String status, String detail) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", name);
        s.put("status", status);
        s.put("detail", detail);
        return s;
    }

    @Override
    public Map<String, Object> evaluate(String scenarioId, Map<String, Object> execResult,
                                        List<Map<String, Object>> previousRounds) {
        // 评估口径与只读档共用同一套（结构评估 + 显式降级），保证换执行器不改判定语义
        return fallback.evaluate(scenarioId, execResult, previousRounds);
    }

    /**
     * 构造沙盒快照行。
     *
     * <p><b>只放请求里已有的事实</b>：场景 id、轮次、当轮假设条数。沙盒侧不查库、不取数，
     * 这是 §5.4「只读快照」的最小形态。真实业务度量应由上游按场景口径导出后注入，
     * 本类不臆造列——没有的列就是没有，判据缺失时由评估层显式降级。</p>
     */
    static List<Map<String, Object>> rowsForSnapshot(String scenarioId, int loopIndex,
                                                     List<Map<String, Object>> hypotheses) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenarioId", scenarioId == null ? "" : scenarioId);
        row.put("loopIndex", loopIndex);
        row.put("hypothesisCount", hypotheses == null ? 0 : hypotheses.size());
        return List.of(row);
    }
}
