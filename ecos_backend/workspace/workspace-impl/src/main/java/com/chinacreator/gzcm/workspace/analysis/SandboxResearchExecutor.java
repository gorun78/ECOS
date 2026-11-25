package com.chinacreator.gzcm.workspace.analysis;

import java.net.URI;
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
        out.put("note", "代码执行发生在外部隔离沙盒；ECOS 仅编排与留痕，不执行代码");
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // 中继调用方凭证：沙盒侧按同一主体做审计与配额，禁裸调
        CallerCredentialSource.applyTo(headers);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenarioId", scenarioId);
        body.put("question", question);
        body.put("hypotheses", hypotheses);
        body.put("loopIndex", loopIndex);
        body.put("executor", "SANDBOX");

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
        Map<String, Object> out = new LinkedHashMap<>();
        ((Map<String, Object>) dm).forEach(out::put);
        // 沙盒必须回传确定性读数，缺失即失败（不让「没有 metric」被当作可评估）
        if (!(out.get("metric") instanceof Map)) {
            throw new IllegalStateException("沙盒研究执行器未回传 metric（禁无读数入评估）");
        }
        return out;
    }

    @Override
    public Map<String, Object> evaluate(String scenarioId, Map<String, Object> execResult,
                                        List<Map<String, Object>> previousRounds) {
        // 评估口径与只读档共用同一套（结构评估 + 显式降级），保证换执行器不改判定语义
        return fallback.evaluate(scenarioId, execResult, previousRounds);
    }
}
