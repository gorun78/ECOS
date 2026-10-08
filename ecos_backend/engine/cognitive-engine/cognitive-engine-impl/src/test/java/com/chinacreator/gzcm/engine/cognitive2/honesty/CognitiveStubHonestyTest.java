package com.chinacreator.gzcm.engine.cognitive2.honesty;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController;
import com.chinacreator.gzcm.engine.cognitive2.controller.DiagnosisController;
import com.chinacreator.gzcm.engine.cognitive2.controller.ScenarioController;
import com.chinacreator.gzcm.engine.cognitive2.controller.Wave3DemoController;
import com.chinacreator.gzcm.engine.cognitive2.controller.WorldModelController;
import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import com.chinacreator.gzcm.engine.cognitive2.model.Scenario;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveObsFixtures;
import com.chinacreator.gzcm.engine.cognitive2.service.WorldModelServiceImpl;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastDiagnosisService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F05-16 伪实现与 stub 显式化门禁（详细设计-05 §F05-16，差距 X-42~X-45）。
 *
 * <p>本类钉住"禁 200 伪数据"四条诚实面：</p>
 * <ol>
 *   <li>{@link #noLiteralUpInHealthPayload()} — X-42：认知域健康类 Controller 的响应里，
 *       状态字面量 {@code "UP"} <b>只允许作为探测表达式的三元真分支</b>出现
 *       （{@code <probeExpr> ? "UP" : "..."}）。形如 {@code put("causalReasoner","UP")}
 *       的常量写入 = 未经探测的假绿，直接违规。
 *       豁免口径：{@code CognitiveEngineOpenHealthController} 的
 *       {@code dbUp ? "UP" : "DOWN"} 与 {@code !dbUp ? "DOWN" : (countsOk ? "UP" : "DEGRADED")}
 *       都是真实探测（{@code probeDb()} 结果）派生，必须合法存在；
 *       {@code STATUS_DISABLED="DISABLED"} 型词表常量不受本门禁约束（门禁只判 {@code "UP"}）。
 *       扫描前先经 {@link CogScope#stripComments(String)} 剥注释，Javadoc 里的禁令散文不是违例。</li>
 *   <li>{@link #worldModelReturnsNotImplemented()} — X-43：world-model 四端点真实 HTTP 501 +
 *       {@code data.stub=true}，且 Service 侧 fail-loud（无 200 固定值路径）。</li>
 *   <li>{@link #diagnoseHistoryIsExplicitStub()} — X-44：{@code /diagnose/history} 不再以进程内
 *       Map 冒充历史，显式 501 + {@code stub=true}。</li>
 *   <li>{@link #demoContentRequiresDemoFlag()} — X-44：demo 内容（Wave3 demo / 内置场景模板）
 *       默认（未配置 {@code ecos.cognitive.demo}）不可达。</li>
 *   <li>{@link #noFrontendCallsToMaskedAiCognitiveStub()} — X-45：ai 侧 {@code CognitiveController}
 *       是零端点弃用桩，前端不得存在 {@code /api/cognitive/**}（无版本段）调用点。</li>
 * </ol>
 */
class CognitiveStubHonestyTest {

    /** 健康类 Controller：类名以 HealthController 结尾的 cognitive2 主源码。 */
    private static final Pattern HEALTH_CONTROLLER = Pattern.compile(".*HealthController\\.java$");

    /** 合法形态：探测表达式三元真分支（条件必须是表达式，不能是裸字面量赋值）。 */
    private static final Pattern PROBED_UP_TERNARY = Pattern.compile("\\?\\s*\"UP\"");

    @Test
    @DisplayName("X-42 健康响应里的每个 \"UP\" 都来自探测三元表达式，禁常量写入")
    void noLiteralUpInHealthPayload() throws IOException {
        List<Path> scope = healthControllers();
        assertTrue(scope.size() >= 2,
                "健康类 Controller 扫描面为空/过小，门禁会假通过（应至少含 "
                        + "CognitiveEngineHealthController + CognitiveEngineOpenHealthController）：" + scope);

        List<String> offenders = new ArrayList<>();
        for (Path p : scope) {
            String src = CogScope.stripComments(Files.readString(p));
            String withoutProbed = PROBED_UP_TERNARY.matcher(src).replaceAll("? ");
            if (withoutProbed.contains("\"UP\"")) {
                offenders.add(p.getFileName() + " 存在未经探测赋值的字面量 \"UP\"（X-42 假绿）");
            }
            // 反向补强：能写出 UP 的健康类必须真有探测调用
            if (src.contains("\"UP\"") && !PROBE_CALL.matcher(src).find()) {
                offenders.add(p.getFileName() + " 写出 UP 却无任何探测调用（probeDb/getIfAvailable/...）");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    private static final Pattern PROBE_CALL = Pattern.compile(
            "probeDb\\(|getIfAvailable\\(|testConnection\\(|listProfiles\\(");

    @Test
    @DisplayName("X-42 重写后的 CognitiveEngineHealthController 输出 6 分量 + checked 明细")
    void healthPayloadExposesCheckedComponentBreakdown() {
        Map<String, Object> payload = CogStubMocks.healthController(true).health();
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) payload.get("components");
        assertNotNull(components, "health 必须输出 components 明细（F05-16 要点 1）");
        for (String key : List.of("db", "causalReasoner", "scenarioSimulator", "paretoOptimizer", "llm", "neo4j")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) components.get(key);
            assertNotNull(item, "缺分量：" + key);
            assertEquals(Boolean.TRUE, item.get("checked"), "分量 " + key + " 必须 checked=true（已真实探测）");
            assertNotNull(item.get("status"), "分量 " + key + " 缺 status");
        }
    }

    @Test
    @DisplayName("X-43 world-model 四端点 = 真实 HTTP 501 + errorCode ECOS-COG-503 + data.stub=true")
    void worldModelReturnsNotImplemented() {
        WorldModelController controller = new WorldModelController();
        List<ResponseEntity<ApiResponse<Map<String, Object>>>> responses = List.of(
                controller.getCurrentState(),
                controller.simulate(new Scenario()),
                controller.recommendStrategy("reduce-churn"),
                controller.getCausalGraph());

        for (ResponseEntity<ApiResponse<Map<String, Object>>> re : responses) {
            assertEquals(HttpStatus.NOT_IMPLEMENTED, re.getStatusCode(), "world-model 端点必须真实 501，禁 200 伪数据");
            ApiResponse<Map<String, Object>> body = re.getBody();
            assertNotNull(body);
            assertEquals(501, body.getCode());
            assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, body.getErrorCode());
            assertEquals(CognitiveErrorCode.MSG_NOT_IMPLEMENTED, body.getMessage());
            Map<String, Object> data = body.getData();
            assertNotNull(data, "501 响应必须带 data 供前端渲染占位");
            assertEquals(Boolean.TRUE, data.get("stub"), "data.stub 必须为 true");
            assertNull(data.get("confidence"), "禁携带固定 confidence 伪数据");
        }
    }

    @Test
    @DisplayName("X-43 WorldModelServiceImpl fail-loud：固定 0.75/0.15/0.3 与硬编码因果边已全部移除")
    void worldModelServiceFailsLoudInsteadOfFixedValues() {
        WorldModelServiceImpl service = new WorldModelServiceImpl();
        assertThrows(CognitiveGuardException.class, service::getCurrentState);
        assertThrows(CognitiveGuardException.class, () -> service.simulate(new Scenario()));
        assertThrows(CognitiveGuardException.class, () -> service.recommendStrategy("goal"));
        assertThrows(CognitiveGuardException.class, service::getCausalGraph);

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, service::getCausalGraph);
        assertEquals(501, e.httpStatus, "stub 异常必须映射真实 501");
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
    }

    /** 09 册 FC-02/FC-03 接缝与 llm-gateway 均缺位的服务（DG-9 fail-loud 形态，控制器构造用）。 */
    private static ForecastDiagnosisService noForecastSeam() {
        return new ForecastDiagnosisService(CogStubMocks.emptyProvider(), CogStubMocks.emptyProvider(),
                CognitiveObsFixtures.unassembled());
    }

    /** §E.5：demo 开关只有 sysman 配置门面一个来源（Controller 不得自带 @Value 默认值）。 */
    private static CognitiveConfigQueryService configWithDemo(boolean demo) {
        CognitiveConfigQueryService cfg = mock(CognitiveConfigQueryService.class);
        when(cfg.demoEnabled()).thenReturn(demo);
        return cfg;
    }

    @Test
    @DisplayName("X-44 /diagnose/history 进程内 Map 已删除，改显式 501 + stub=true")
    void diagnoseHistoryIsExplicitStub() throws IOException {
        DiagnosisController controller = new DiagnosisController(
                mock(com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService.class),
                mock(com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService.class),
                CogStubMocks.emptyProvider(),
                CogStubMocks.emptyProvider(),
                noForecastSeam(),
                false);
        ResponseEntity<ApiResponse<Map<String, Object>>> re = controller.getHistory();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, re.getStatusCode());
        assertEquals(Boolean.TRUE, re.getBody().getData().get("stub"));

        // 源码面：historyCache / 进程内缓存字段必须零残留
        String src = Files.readString(CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/controller/DiagnosisController.java"));
        assertTrue(!src.contains("historyCache"), "DiagnosisController 仍残留 historyCache（X-44）");
        assertSoftItemNotHardcodedGreen(src);
    }

    /**
     * X-44 软项诚实面：preflight 的 {@code neo4jOk} / {@code llmOk} 不得是未经探测的常量 {@code true}。
     *
     * <p>唯一合法形态 = standard 档 Neo4j N/A 分支：赋值必须紧邻 {@code if (!neo4jEnabled) &#123;} 守卫，
     * 且同分支的 message 里写明 N/A 依据（可被读成"该档位不需要"而非"探测通过"）。
     * {@code llmOk} 没有对应的档位豁免 ⇒ 出现常量 {@code true} 即违规。</p>
     */
    private static void assertSoftItemNotHardcodedGreen(String src) {
        List<String> lines = List.of(CogScope.stripComments(src).split("\r?\n", -1));
        List<String> offenders = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            java.util.regex.Matcher m = SOFT_ITEM_TRUE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            boolean naBranch = i > 0 && NA_GUARD.matcher(lines.get(i - 1).trim()).matches()
                    && String.join(" ", lines.subList(i, Math.min(i + 5, lines.size()))).contains("N/A");
            if (!naBranch || m.group(1).equals("llmOk")) {
                offenders.add("DiagnosisController:" + (i + 1) + " 软项 " + m.group(1)
                        + " 被硬编码为 true（未经探测的假绿）");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    private static final Pattern SOFT_ITEM_TRUE = Pattern.compile("(neo4jOk|llmOk)\\s*=\\s*true\\s*;");

    /** N/A 分支守卫：唯一允许软项为 true 的前一行形态（standard 档不需要 Neo4j，ST02）。 */
    private static final Pattern NA_GUARD = Pattern.compile("if\\s*\\(\\s*!neo4jEnabled\\s*\\)\\s*\\{");

    @Test
    @DisplayName("X-44 demo 内容默认不可达：Wave3DemoController 条件装配 + 场景模板 404")
    void demoContentRequiresDemoFlag() {
        ConditionalOnProperty ann = Wave3DemoController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(ann, "Wave3DemoController 必须条件装配（demo 内容不得默认对外，X-44）");
        assertEquals(List.of("ecos.cognitive.demo"), List.of(ann.name()),
                "Wave3DemoController 条件装配必须绑 ecos.cognitive.demo");
        assertEquals("true", ann.havingValue());
        assertTrue(!ann.matchIfMissing(), "未配置 ecos.cognitive.demo 时默认不可达（生产 404）");
        assertNotNull(Wave3DemoController.class.getAnnotation(RestController.class));

        ScenarioController off = new ScenarioController(mock(ScenarioSimulatorService.class),
                noForecastSeam(), configWithDemo(false));
        ResponseEntity<?> denied = off.listTemplates();
        assertEquals(HttpStatus.NOT_FOUND, denied.getStatusCode(),
                "demo 关闭时内置模板必须 404，禁 200 + 硬编码模板");

        ScenarioController on = new ScenarioController(mock(ScenarioSimulatorService.class),
                noForecastSeam(), configWithDemo(true));
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> allowed =
                (ResponseEntity<ApiResponse<List<Map<String, Object>>>>) (ResponseEntity<?>) on.listTemplates();
        assertEquals(HttpStatus.OK, allowed.getStatusCode());
        List<Map<String, Object>> templates = allowed.getBody().getData();
        assertTrue(templates.size() > 0);
        for (Map<String, Object> t : templates) {
            assertEquals(Boolean.TRUE, t.get("demo"), "demo 模板必须标 demo=true");
        }
    }

    @Test
    @DisplayName("X-45 前端无 /api/cognitive/** 调用点（ai 侧零端点弃用桩不被路由掩蔽）")
    void noFrontendCallsToMaskedAiCognitiveStub() throws IOException {
        Path frontendSrc = CogScope.REPO.resolve("ecos_frontend/src");
        assertTrue(Files.isDirectory(frontendSrc), "前端源码目录缺失，门禁无从扫描：" + frontendSrc);
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(frontendSrc)) {
            walk.filter(p -> p.toString().endsWith(".ts") || p.toString().endsWith(".tsx"))
                    .forEach(p -> {
                        try {
                            String src = stripTsComments(Files.readString(p));
                            if (src.contains("/api/cognitive")) hits.add(p + " -> /api/cognitive");
                        } catch (IOException ignored) {
                        }
                    });
        }
        assertTrue(hits.isEmpty(),
                "前端仍在调 ai 侧弃用桩前缀 /api/cognitive/**（X-45 路由掩蔽，应走 /api/v1/cognitive*）：\n"
                        + String.join("\n", hits));
    }

    /** 剥 TS/TSX 块注释与行注释（源码门禁只判代码）。 */
    private static String stripTsComments(String src) {
        String noBlock = Pattern.compile("(?s)/\\*.*?\\*/").matcher(src)
                .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement(
                        mr.group().replaceAll("[^\\n]", "")));
        return Pattern.compile("(?m)^\\s*//[^\\n]*").matcher(noBlock).replaceAll("");
    }

    private static List<Path> healthControllers() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(CogScope.MAIN)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> HEALTH_CONTROLLER.matcher(p.getFileName().toString()).matches())
                    .forEach(out::add);
        }
        assertTrue(containsClass(out, CognitiveEngineHealthController.class),
                "门禁扫描面必须含 CognitiveEngineHealthController，实际面=" + out);
        assertTrue(containsClass(out, CognitiveEngineOpenHealthController.class),
                "门禁扫描面必须含 CognitiveEngineOpenHealthController（真实探测实现须合法存在），实际面=" + out);
        return out;
    }

    /** 扫描面是否含该类的主源文件（文件名 = 简单类名 + .java）。 */
    private static boolean containsClass(List<Path> files, Class<?> type) {
        String expected = type.getSimpleName() + ".java";
        return files.stream().anyMatch(p -> p.getFileName().toString().equals(expected));
    }
}
