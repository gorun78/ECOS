package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.ParetoOptimizerService;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.service.EngineOpenHealthQueryService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code GET /api/v1/cognitive/health} — 认知引擎健康检查（详细设计-05 F05-16 / X-42）。
 *
 * <p>门禁语义（由 {@code CognitiveStubHonestyTest#noLiteralUpInHealthPayload} 与
 * {@code CognitiveHealthProbeTest#eachComponentIsActuallyChecked} 双向钉住）：
 * 响应里每个分量状态字面量都必须来自一次真实探测调用，禁 {@code put("x","UP")} 型伪绿。
 * 分量集合 = db / causalReasoner / scenarioSimulator / paretoOptimizer / llm / neo4j，
 * 每项输出 {@code {status, checked:true, detail}}：</p>
 * <ul>
 *   <li>{@code UP} — 探测执行且判定通过</li>
 *   <li>{@code DEGRADED} — 汇总态：DB 可用但任一分量不可用</li>
 *   <li>{@code DOWN} — 探测执行但判定失败或探测抛异常（fail-closed，异常不外抛）</li>
 *   <li>{@code DISABLED} — 该档位不需要此分量（standard 档 Neo4j，依据数据库访问规范 ST02），
 *       不参与汇总，避免被读成假绿</li>
 * </ul>
 *
 * <p>能力分量（causalReasoner / scenarioSimulator / paretoOptimizer）的探测 = 从容器解析能力
 * Bean（{@code ObjectProvider.getIfAvailable()}）：Bean 缺失/解析异常即 DOWN，不做功能级调用
 * （因果/仿真会打 KG 与 LLM，健康检查不得触发推理，见本域"推理不超 30s"约束）。
 * LLM 只经 llm-gateway、Neo4j 只经 runtime-access {@code Neo4jClient}（铁律 §2.5），
 * DB 探活经 {@link EngineOpenHealthQueryService}（铁律 §3.6：Controller 不直连库）。</p>
 *
 * <p>既有对外键 {@code status} / {@code engine} / {@code reasoners.{causalReasoner,
 * scenarioSimulator, paretoOptimizer}} 保持原形（值改为探测派生），新增 {@code components}
 * 明细 — API 只增不改。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class CognitiveEngineHealthController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveEngineHealthController.class);

    /** 该档位不需要该分量（非探测失败），不参与汇总判定。 */
    private static final String STATUS_DISABLED = "DISABLED";

    /** llm-gateway profile 归属子系统标识（与 {@code SuggestionBuilder} 同源）。 */
    private static final String LLM_SUBSYSTEM = "cognitive";

    private final EngineOpenHealthQueryService engineOpenHealthQueryService;
    private final ObjectProvider<CausalReasonerService> causalReasonerProvider;
    private final ObjectProvider<ScenarioSimulatorService> scenarioSimulatorProvider;
    private final ObjectProvider<ParetoOptimizerService> paretoOptimizerProvider;
    private final ObjectProvider<LLMGatewayService> llmGatewayProvider;
    private final ObjectProvider<Neo4jClient> neo4jClientProvider;
    private final boolean neo4jEnabled;

    public CognitiveEngineHealthController(EngineOpenHealthQueryService engineOpenHealthQueryService,
                                           ObjectProvider<CausalReasonerService> causalReasonerProvider,
                                           ObjectProvider<ScenarioSimulatorService> scenarioSimulatorProvider,
                                           ObjectProvider<ParetoOptimizerService> paretoOptimizerProvider,
                                           ObjectProvider<LLMGatewayService> llmGatewayProvider,
                                           ObjectProvider<Neo4jClient> neo4jClientProvider,
                                           @Value("${ecos.cognitive.neo4j-enabled:false}") boolean neo4jEnabled) {
        this.engineOpenHealthQueryService = engineOpenHealthQueryService;
        this.causalReasonerProvider = causalReasonerProvider;
        this.scenarioSimulatorProvider = scenarioSimulatorProvider;
        this.paretoOptimizerProvider = paretoOptimizerProvider;
        this.llmGatewayProvider = llmGatewayProvider;
        this.neo4jClientProvider = neo4jClientProvider;
        this.neo4jEnabled = neo4jEnabled;
    }

    /** 单个分量探测结果：{@code checked} 恒 true（探测已执行），{@code applicable} false = 档位不适用。 */
    record Probe(boolean checked, boolean ok, boolean applicable, String detail) {

        static Probe notApplicable(String detail) {
            return new Probe(true, false, false, detail);
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Probe db = probe("db", () -> {
            Long one = engineOpenHealthQueryService.probeDb();
            return one != null && one == 1L;
        });
        Probe causalReasoner = probeBean("causalReasoner", causalReasonerProvider);
        Probe scenarioSimulator = probeBean("scenarioSimulator", scenarioSimulatorProvider);
        Probe paretoOptimizer = probeBean("paretoOptimizer", paretoOptimizerProvider);
        Probe llm = probe("llm", () -> {
            LLMGatewayService gateway = llmGatewayProvider.getIfAvailable();
            if (gateway == null) {
                throw new IllegalStateException("llm-gateway bean not assembled");
            }
            List<ProfileConfig> profiles = gateway.listProfiles(LLM_SUBSYSTEM);
            return profiles != null && !profiles.isEmpty();
        });
        Probe neo4j = probeNeo4j();

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("db", asMap(db));
        components.put("causalReasoner", asMap(causalReasoner));
        components.put("scenarioSimulator", asMap(scenarioSimulator));
        components.put("paretoOptimizer", asMap(paretoOptimizer));
        components.put("llm", asMap(llm));
        components.put("neo4j", asMap(neo4j));

        Map<String, String> reasoners = new LinkedHashMap<>();
        reasoners.put("causalReasoner", statusOf(causalReasoner));
        reasoners.put("scenarioSimulator", statusOf(scenarioSimulator));
        reasoners.put("paretoOptimizer", statusOf(paretoOptimizer));

        boolean dbOk = db.applicable() && db.ok();
        boolean allApplicableOk = dbOk
                && causalReasoner.ok() && scenarioSimulator.ok() && paretoOptimizer.ok()
                && llm.ok() && (neo4j.applicable() ? neo4j.ok() : true);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", !dbOk ? "DOWN" : (allApplicableOk ? "UP" : "DEGRADED"));
        status.put("engine", "COGNITIVE");
        status.put("reasoners", reasoners);
        status.put("components", components);
        return status;
    }

    /** Neo4j 探测：standard 档（{@code ecos.cognitive.neo4j-enabled=false}）判 N/A，不启客户端。 */
    private Probe probeNeo4j() {
        if (!neo4jEnabled) {
            return Probe.notApplicable("neo4j not required by active edition "
                    + "(ecos.cognitive.neo4j-enabled=false, ST02)");
        }
        return probe("neo4j", () -> {
            Neo4jClient client = neo4jClientProvider.getIfAvailable();
            if (client == null) {
                throw new IllegalStateException("runtime-access Neo4jClient not assembled");
            }
            return client.testConnection();
        });
    }

    /** 能力分量探测：容器内 Bean 可解析即视为该能力已装配（不做功能级调用）。 */
    private Probe probeBean(String name, ObjectProvider<?> provider) {
        return probe(name, () -> provider.getIfAvailable() != null);
    }

    /** 执行一次探测；探测异常 → 分量 DOWN（fail-closed），异常类型进 detail，异常原文只入日志。 */
    private Probe probe(String name, Callable<Boolean> check) {
        try {
            return new Probe(true, Boolean.TRUE.equals(check.call()), true, null);
        } catch (Exception e) {
            log.warn("cognitive health probe '{}' failed: {}", name, e.toString(), e);
            return new Probe(true, false, true, e.getClass().getSimpleName());
        }
    }

    private static String statusOf(Probe probe) {
        if (!probe.applicable()) return STATUS_DISABLED;
        return probe.checked() && probe.ok() ? "UP" : "DOWN";
    }

    private static Map<String, Object> asMap(Probe probe) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", statusOf(probe));
        item.put("checked", true);
        item.put("applicable", probe.applicable());
        if (probe.detail() != null) {
            item.put("detail", probe.detail());
        }
        return item;
    }
}
