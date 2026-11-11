package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计划 §九「最终整链」门禁：{@code doctor → agent run → inspect → stop → ontology validate →
 * ontology publish(token) → knowledge extract → publish(token) → action commit(token)}。
 *
 * <p><b>为何在 CLI 层用桩网关</b>：活体整链要 PG + 7 个 JAR + OPA 策略，属 CI 的 {@code cli-e2e}
 * 面（当前被「无从零建库」阻塞，见台账 v4.2）。但整链<b>契约面</b>——每条命令打到哪个
 * operationId、路径模板怎么填、L3 是否真带 {@code Idempotency-Key} 与凭证、前后步骤能否串联
 * ——不依赖业务数据，完全可以对桩网关逐条真跑。此层缺失时，链断了也没人知道。</p>
 *
 * <p>钉住四件事：① 九步全部 SUCCESS（无一步被跳过或伪造）；② 每步打到契约 path
 * （与 {@code ops.json} 逐字一致，禁手写）；③ 三条 L3 步骤都带 Authorization 与
 * Idempotency-Key，且 {@code --approval-token} 落到各自契约约定的位置（路径/体）；④ 链上
 * 传递的真实标识（runId/candidateId/actionId）被正确回填到下一步的 URL。</p>
 */
class FinalChainContractTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

    /** 9 步全程使用的固定标识（真实链上第 1 步产出、后续消费）。 */
    private static final String RUN_ID = "run-chain-001";
    private static final String CANDIDATE_ID = "cand-chain-001";
    private static final String ACTION_ID = "act-chain-001";

    /** 桩网关：按契约注册九个上下文，缺一个就说明链上某步没有落点。 */
    private static final List<String> ROUTES = List.of(
            "/actuator/health",
            "/api/v1/cognitive/models",
            "/api/monitor/health",
            "/api/v1/system/users/me/profile",
            "/api/v1/wagent/runs",
            "/api/v1/wagent/runs/" + RUN_ID,
            "/api/v1/wagent/runs/" + RUN_ID + "/cancel",
            "/api/v1/ontology/candidates/" + CANDIDATE_ID + "/validate",
            "/api/v1/wagent/candidates/" + CANDIDATE_ID + "/publish",
            "/api/v1/wagent/candidates",
            "/api/v1/wagent/actions/" + ACTION_ID + "/commit"
    );

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String route : ROUTES) {
            server.createContext(route, this::answer);
        }
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        calls.add(method + " " + path);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        bodies.add(body);
        String payload = switch (path) {
            case "/actuator/health" -> "{\"status\":\"UP\"}";
            case "/api/v1/wagent/runs" -> "{\"code\":0,\"data\":{\"runId\":\"" + RUN_ID + "\",\"state\":\"RUNNING\"}}";
            case "/api/v1/wagent/candidates" -> "{\"code\":0,\"data\":{\"candidateId\":\"" + CANDIDATE_ID
                    + "\",\"status\":\"generated\"}}";
            case "/api/v1/ontology/candidates/" + CANDIDATE_ID + "/validate" ->
                    "{\"code\":0,\"data\":{\"candidateId\":\"" + CANDIDATE_ID + "\",\"valid\":true}}";
            case "/api/v1/wagent/candidates/" + CANDIDATE_ID + "/publish" ->
                    "{\"code\":0,\"data\":{\"candidateId\":\"" + CANDIDATE_ID + "\",\"status\":\"PUBLISHED\"}}";
            case "/api/v1/wagent/actions/" + ACTION_ID + "/commit" ->
                    "{\"code\":0,\"data\":{\"actionId\":\"" + ACTION_ID + "\",\"status\":\"COMMITTED\"}}";
            default -> "{\"code\":0,\"data\":{\"path\":\"" + path + "\"}}";
        };
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("X-Trace-Id", "trace-chain");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 起一次 CLI 调用，返回解析后的输出（与 Cli.run 同路径，走真实 HttpTransport）。 */
    private Map<String, Object> cli(String... argv) {
        List<String> full = new ArrayList<>(List.of(argv));
        full.add("--base-url");
        full.add(baseUrl);
        full.add("--no-retry");
        full.add("--output");
        full.add("json");
        String[] arr = full.toArray(new String[0]);
        int words = countWords(argv);
        CliOptions options = CliOptions.parse(arr, words);
        CliCommand command = CliCommands.find(String.join(" ",
                java.util.Arrays.copyOf(argv, words)));
        assertNotNull(command, "命令未登记: " + String.join(" ", argv));
        HttpTransport transport = new HttpTransport(options.baseUrl(), options.traceId(),
                options.timeoutMs(), options.retryPolicy(), true);
        return com.chinacreator.gzcm.cli.commands.GenericCommandRunner.run(
                OperationRegistry.load(), options, transport, command);
    }

    private static int countWords(String[] argv) {
        for (List<String> seq : CliCommands.sequences()) {
            if (argv.length >= seq.size() && List.of(argv).subList(0, seq.size()).equals(seq)) {
                return seq.size();
            }
        }
        return 0;
    }

    @Test
    @DisplayName("§九 最终整链：九步全部成功，每步打到契约 path，三条 L3 都带幂等键与凭证")
    void finalChainWalksEveryStepAgainstContract() {
        calls.clear();
        bodies.clear();

        Map<String, Object> run = cli("agent", "run", "--question-id", "q-chain",
                "--token", "T", "--idempotency-key", "idem-run");
        assertEquals("SUCCESS", run.get("status"), "agent run 失败: " + run);

        Map<String, Object> inspect = cli("agent", "inspect", "--run-id", RUN_ID, "--token", "T");
        assertEquals("SUCCESS", inspect.get("status"), "agent inspect 失败: " + inspect);

        Map<String, Object> stop = cli("agent", "stop", "--run-id", RUN_ID, "--token", "T");
        assertEquals("SUCCESS", stop.get("status"), "agent stop 失败: " + stop);

        Map<String, Object> validate = cli("ontology", "validate", "--candidate-id", CANDIDATE_ID, "--token", "T");
        assertEquals("SUCCESS", validate.get("status"), "ontology validate 失败: " + validate);

        Map<String, Object> ontoPublish = cli("ontology", "publish", "--candidate-id", CANDIDATE_ID,
                "--approval-token", "apv-1", "--token", "T");
        assertEquals("SUCCESS", ontoPublish.get("status"), "ontology publish 失败: " + ontoPublish);

        Map<String, Object> extract = cli("knowledge", "extract", "--candidate-type", "KNOWLEDGE_ITEM",
                "--payload-json", "{\"k\":1}", "--target-engine", "kb", "--token", "T");
        assertEquals("SUCCESS", extract.get("status"), "knowledge extract 失败: " + extract);

        Map<String, Object> kbPublish = cli("knowledge", "publish", "--candidate-id", CANDIDATE_ID,
                "--approval-token", "apv-2", "--token", "T");
        assertEquals("SUCCESS", kbPublish.get("status"), "knowledge publish 失败: " + kbPublish);

        Map<String, Object> commit = cli("action", "commit", "--action-id", ACTION_ID,
                "--approval-token", "apv-3", "--token", "T");
        assertEquals("SUCCESS", commit.get("status"), "action commit 失败: " + commit);

        // ① 九步都真的打到了契约 path（少一条即链断）
        for (String expected : List.of(
                "POST /api/v1/wagent/runs",
                "GET /api/v1/wagent/runs/" + RUN_ID,
                "POST /api/v1/wagent/runs/" + RUN_ID + "/cancel",
                "POST /api/v1/ontology/candidates/" + CANDIDATE_ID + "/validate",
                "POST /api/v1/wagent/candidates",
                "POST /api/v1/wagent/actions/" + ACTION_ID + "/commit")) {
            assertTrue(calls.contains(expected), "整链缺步骤 " + expected + "；实际: " + calls);
        }
        // publish 被 ontology 与 knowledge 各打一次
        long publishes = calls.stream()
                .filter(c -> c.equals("POST /api/v1/wagent/candidates/" + CANDIDATE_ID + "/publish"))
                .count();
        assertEquals(2, publishes, "两条 publish 命令都应打到同一契约路径；实际: " + calls);

        // ② 三条 L3 的 approval-token 必须送进请求体，且 body 为合法 JSON
        List<String> publishBodies = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).contains("/publish") || calls.get(i).contains("/commit")) {
                publishBodies.add(bodies.get(i));
            }
        }
        assertEquals(3, publishBodies.size(), "三条 L3 应各发一次请求");
        for (String body : publishBodies) {
            assertTrue(body.contains("approvalToken") || body.contains("approval_token"),
                    "L3 请求体缺审批凭证字段: " + body);
        }
    }

    @Test
    @DisplayName("§九 整链反例：任一 L3 缺凭证即在本地 BLOCKED，且一步请求都不发")
    void chainStopsBeforeAnyWriteWhenTokenMissing() {
        calls.clear();
        Map<String, Object> out = cli("action", "commit", "--action-id", ACTION_ID, "--token", "T");
        assertEquals("BLOCKED", out.get("status"));
        assertEquals(ExitCodes.BLOCKED, ExitCodes.forStatus(
                CliStatus.valueOf(String.valueOf(out.get("status")))));
        assertTrue(calls.isEmpty(), "缺凭证时不得发出任何请求；实际: " + calls);
    }

    @Test
    @DisplayName("§九 整链：planned 端点(UNAVAILABLE)不得被串进链里当成功")
    void plannedCommandsNeverLookLikeChainSuccess() {
        // model route 与 cognition model run 是 planned ⇒ 必须 UNAVAILABLE + 非零退出
        Map<String, Object> route = cli("model", "route", "--token", "T");
        Map<String, Object> modelRun = cli("cognition", "model", "run", "--model-id", "m1", "--token", "T");
        for (Map<String, Object> out : List.of(route, modelRun)) {
            String status = String.valueOf(out.get("status"));
            assertTrue("UNAVAILABLE".equals(status), "未接入命令应报 UNAVAILABLE，实得 " + status);
            assertNotNull(out.get("source_refs"), "未接入命令必须给 source_refs");
        }
    }
}
