package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * §4.7.2 护栏（OPA）面的<b>录制型 oracle 回放底座</b>（#142/B4-3c）。
 *
 * <p><b>为什么是回放而不是 live E2E</b>：dev 的 {@code ecos-opa} 容器以 {@code /opa run --server}
 * 启动、{@code /v1/policies} 实数 0 个，仓内 {@code data_access.rego} 未被加载；把它"修好"要改
 * compose 启动参数并重启共享容器 —— 属未授权的运维面改动（且会改变其他批次依赖的 dev 基线，
 * OPA fail-close 是登记在册的基线态）。因此本批采取的手段是：
 * <ol>
 *   <li>策略源唯一落在仓内 {@code ecos-docker/opa-policies/data_access.rego}；</li>
 *   <li>用<b>真 Rego 引擎</b>（容器内 {@code /opa eval}，只读、离线）对 14 组输入求值，
 *       逐字录制进 {@code data_access_oracles.json}（含 {@code serverBody}＝OPA HTTP 应答原文、
 *       {@code opaVersion}、求值命令、策略文件 SHA-256）；</li>
 *   <li>Java 用例把 {@code serverBody} 原样喂给<b>生产</b> {@code OpaPolicyService}
 *       （真 HTTP 客户端 + 真 {@code parseEvalResponse}），断的是裁决链而非我手写的答案。</li>
 * </ol>
 * <b>诚实边界</b>：这证明「策略语义 → 应答 → 引擎裁决」三段一致，
 * <b>不</b>证明 live OPA 已加载该策略；后者仍需运维授权项（差距文档剩余项）。</p>
 *
 * <p><b>三道防伪钉</b>：
 * ① {@link #assertPolicyNotDrifted()} —— 现算 rego 的 SHA-256 必须等于
 * {@code _provenance.policySha256}，改策略不重录即红；
 * ② {@link #assertOracleSelfConsistent(OracleCase)} —— oracle 内部
 * {@code serverBody.result.allow/obligations} 必须与顶层 {@code allow/obligations} 一致，
 * 手编单字段即红；
 * ③ {@link #assertRequestMatches(OracleCase, Map)} —— 用例断言回放结果之前，
 * 必须先断<b>生产实际发出的请求体</b>与录制时的输入逐字可比（不可比则回放不成立，
 * 而不是"近似就用"）。唯一容许的增量是 {@code action} 字段，且其容许性由
 * 「rego 全文不引用 {@code input.action}」这一静态断言保证（见
 * {@code OpaDocumentShapeTest}）。</p>
 *
 * <p>单源纪律：本类只<b>读</b>那两份文件，不含任何策略语义常量；用例期望值一律来自 oracle
 * 或 {@link OpaPolicyService}/{@link GuardrailDecisionContract} 词表，禁在此内联第二套真值。</p>
 */
public final class OpaOracleReplaySupport implements AutoCloseable {

    /** oracle 单源（security 侧 test resource；ai 侧按同一仓根相对路径读同一文件，禁复制）。 */
    public static final String ORACLE_REPO_PATH =
            "ecos_backend/engine/security-engine/security-engine-impl/src/test/resources/opa/data_access_oracles.json";

    /** 策略唯一源（详设-01 §4.7.2 护栏面）。 */
    public static final String REGO_REPO_PATH = "ecos-docker/opa-policies/data_access.rego";

    /** 生产 {@code OpaPolicyService} 查询策略文档的路径（裸布尔 .../allow 形态已废，D-B4-20）。 */
    public static final String OPA_DOC_PATH = "/v1/data/ecos/data_access";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 一条录制用例：输入 + OPA 应答原文 + 真引擎给出的 allow/obligations。 */
    public record OracleCase(String name,
                             Map<String, Object> input,
                             Map<String, Object> serverBody,
                             boolean allow,
                             List<Map<String, Object>> obligations) {

        /** 该输入是否带 {@code resource.dataScope}（只有 AI 工具裁决载荷会发，故不可走 /decision）。 */
        public boolean hasResourceScope() {
            Object resource = input.get("resource");
            return resource instanceof Map<?, ?> m && m.get("dataScope") != null;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> subject() {
            Object s = input.get("subject");
            return s instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        }
    }

    private final HttpServer server;
    private final List<String> requestedPaths = new ArrayList<>();
    private volatile String lastRequestJson;
    private volatile int respondStatus = 200;
    private volatile String overrideBody;
    private volatile OracleCase replayCase;

    private OpaOracleReplaySupport(HttpServer server) {
        this.server = server;
    }

    // ═══════════════ 单源读取 ═══════════════

    /** 由仓根相对路径取文件（从 cwd 上溯，与 {@code V246SeedFixtures#resolveSeedFile} 同法）。 */
    public static Path repoFile(String repoRelative) throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 12 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IOException("未找到单源文件: " + repoRelative
                + " —— 禁把「读不到」当成「无策略/无 oracle」");
    }

    public static String regoText() throws IOException {
        return Files.readString(repoFile(REGO_REPO_PATH), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> oracleRoot() throws IOException {
        try {
            return MAPPER.readValue(repoFile(ORACLE_REPO_PATH).toFile(), Map.class);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("oracle JSON 不可解析: " + e.getMessage(), e);
        }
    }

    public static String policySha256OnDisk() throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    md.digest(Files.readAllBytes(repoFile(REGO_REPO_PATH))));
        } catch (Exception e) {
            throw new IOException("策略文件摘要计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 漂移钉：现算 {@value #REGO_REPO_PATH} 的 SHA-256 必须与录制时登记值逐字相等。
     *
     * <p>不等即说明策略被改过而 oracle 未重录 —— 此时所有回放用例的期望值都可能失真，
     * 必须在<b>断言之前</b>失败。重录命令见 {@code _provenance.command}。</p>
     */
    public static void assertPolicyNotDrifted() throws IOException {
        Map<String, Object> root = oracleRoot();
        Map<?, ?> provenance = (Map<?, ?>) root.get("_provenance");
        String recorded = provenance == null ? null : String.valueOf(provenance.get("policySha256"));
        String actual = policySha256OnDisk();
        if (recorded == null || !recorded.equals(actual)) {
            throw new AssertionError("策略漂移：oracle 录制时登记的 policySha256=" + recorded
                    + "，实际 " + REGO_REPO_PATH + " 现为 " + actual
                    + "。请改策略后按 _provenance.command 重录 oracle，禁手工编辑 oracle。");
        }
    }

    /** oracle 自洽钉：顶层 allow/obligations 必须由 serverBody.result 里的同名字段逐字支撑。 */
    public static void assertOracleSelfConsistent(OracleCase c) {
        Object result = c.serverBody().get("result");
        if (!(result instanceof Map<?, ?> r)) {
            throw new AssertionError(c.name() + " 的 serverBody 缺 result 段（应答形态与 OPA 文档查询不符）");
        }
        if (!Boolean.valueOf(String.valueOf(r.get("allow"))).equals(c.allow())) {
            throw new AssertionError(c.name() + " 顶层 allow=" + c.allow()
                    + " 与 serverBody.result.allow=" + r.get("allow") + " 不一致（oracle 疑被手编）");
        }
        Object recorded = r.get("obligations");
        List<?> recordedList = recorded instanceof List<?> l ? l : List.of();
        if (!jsonEquals(recordedList, c.obligations())) {
            throw new AssertionError(c.name() + " 顶层 obligations 与 serverBody.result.obligations 不一致"
                    + "（oracle 疑被手编）");
        }
    }

    @SuppressWarnings("unchecked")
    public static List<OracleCase> cases() throws IOException {
        Map<String, Object> root = oracleRoot();
        Object raw = root.get("cases");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new AssertionError("oracle 里 cases 段缺失/为空 —— 禁当成「无 oracle 所以放行」");
        }
        List<OracleCase> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            out.add(new OracleCase(
                    String.valueOf(m.get("name")),
                    (Map<String, Object>) m.get("input"),
                    (Map<String, Object>) m.get("serverBody"),
                    Boolean.TRUE.equals(m.get("allow")),
                    (List<Map<String, Object>>) m.getOrDefault("obligations", List.of())));
        }
        return List.copyOf(out);
    }

    public static OracleCase byName(String name) throws IOException {
        return cases().stream().filter(c -> name.equals(c.name())).findFirst()
                .orElseThrow(() -> new AssertionError("oracle 里没有用例 " + name
                        + "（期望名请逐字取自 data_access_oracles.json 的 cases[].name）"));
    }

    /** 从 oracle 输入里取某主体字段（角色列表按原样返回，用于装配显式上下文）。 */
    @SuppressWarnings("unchecked")
    public static List<String> rolesOf(OracleCase c) {
        Object r = c.subject().get("roles");
        return r instanceof List<?> l ? (List<String>) l : List.of();
    }

    public static String str(OracleCase c, String subjectKey) {
        Object v = c.subject().get(subjectKey);
        return v == null ? null : String.valueOf(v);
    }

    // ═══════════════ 回放服务 ═══════════════

    /** 起临时端口 HTTP 服务，路径无关地记录请求并按 {@link #setCase} 回放 serverBody。 */
    public static OpaOracleReplaySupport start() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        OpaOracleReplaySupport support = new OpaOracleReplaySupport(http);
        http.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (support) {
                support.requestedPaths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                support.lastRequestJson = body;
            }
            String payload;
            int status;
            synchronized (support) {
                status = support.respondStatus;
                payload = support.overrideBody != null ? support.overrideBody
                        : (support.replayCase == null ? "{}" : writeJson(support.replayCase.serverBody()));
            }
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        http.setExecutor(null);
        http.start();
        return support;
    }

    /** 交给 {@code PageChannelHarness.create(String opaBaseUrl)} 的对端地址。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void setCase(String name) throws IOException {
        OracleCase c = byName(name);
        assertOracleSelfConsistent(c);
        synchronized (this) {
            this.replayCase = c;
            this.overrideBody = null;
            this.respondStatus = 200;
            this.lastRequestJson = null;
            this.requestedPaths.clear();
        }
    }

    /** 直回原文（应答形态用例用：状态码与 body 都由用例给定，不经 oracle）。 */
    public void respondRaw(int status, String body) {
        synchronized (this) {
            this.respondStatus = status;
            this.overrideBody = body;
            this.replayCase = null;
            this.lastRequestJson = null;
            this.requestedPaths.clear();
        }
    }

    public synchronized List<String> requestedPaths() {
        return List.copyOf(requestedPaths);
    }

    public synchronized String lastRequestJson() {
        return lastRequestJson;
    }

    /** 生产实际发出的 OPA 输入段（{@code {"input":{...}}} 的 input）。 */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> lastSentInput() throws IOException {
        String json = lastRequestJson;
        if (json == null) {
            throw new AssertionError("回放服务没收到任何请求 —— 护栏源未被咨询，本用例不成立");
        }
        Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        Object input = root.get("input");
        if (!(input instanceof Map<?, ?> m)) {
            throw new AssertionError("生产发出的请求体缺 input 段: " + json);
        }
        return (Map<String, Object>) m;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ═══════════════ 可比性钉 ═══════════════

    /**
     * 断"生产发出的请求体"与"录制 oracle 时的输入"逐字可比。
     *
     * <p>逐字比 {@code purpose}、{@code asset}、{@code subject.userId/department/tenantId}、
     * {@code resource}（深比）。唯一容许的形态差：</p>
     * <ul>
     *   <li>生产多发 {@code action} —— 前提是 rego 不引用它（该前提由
     *       {@code OpaDocumentShapeTest#regoNeverReadsInputAction} 钉；此处同步复核一次文本，
     *       避免只有那条用例能红）；</li>
     *   <li>{@code subject.roles} 顺序 —— 上下文角色来自 {@code Set}，集合成员必须一致而顺序不保证；
     *       rego 侧角色判定是集合运算，与顺序无关。</li>
     * </ul>
     */
    public static void assertRequestMatches(OracleCase c, Map<String, Object> sent) throws IOException {
        if (regoText().contains("input.action") || regoText().contains("\"action\"")) {
            throw new AssertionError("rego 开始引用 input.action，而 oracle 输入里没有该字段"
                    + " ⇒ 回放不再等价，必须按 _provenance.command 补录带 action 的 oracle");
        }
        requireEqual(c.name() + " purpose", c.input().get("purpose"), sent.get("purpose"));
        requireEqual(c.name() + " asset", c.input().get("asset"), sent.get("asset"));
        Map<String, Object> sentSubject = asMap(sent.get("subject"), c.name() + " subject");
        requireEqual(c.name() + " subject.userId", c.subject().get("userId"), sentSubject.get("userId"));
        requireEqual(c.name() + " subject.department", c.subject().get("department"), sentSubject.get("department"));
        if (c.subject().containsKey("tenantId")) {
            requireEqual(c.name() + " subject.tenantId", c.subject().get("tenantId"), sentSubject.get("tenantId"));
        }
        requireEqual(c.name() + " subject.roles(集合等值)",
                new TreeSet<>(stringList(c.subject().get("roles"))),
                new TreeSet<>(stringList(sentSubject.get("roles"))));
        if (c.input().containsKey("resource")) {
            requireEqual(c.name() + " resource(深比)",
                    writeJson(c.input().get("resource")), writeJson(sent.get("resource")));
        }
    }

    /** 回放成立性的两步合查（漂移钉 + 可比性钉），返回该 case 供用例继续断言。 */
    public static OracleCase verifiedReplay(String name, Map<String, Object> sentInput) throws IOException {
        assertPolicyNotDrifted();
        OracleCase c = byName(name);
        assertRequestMatches(c, sentInput);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new AssertionError(label + " 段缺失/形态不符: " + raw);
        }
        return (Map<String, Object>) m;
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) out.add(String.valueOf(o));
        }
        return out;
    }

    private static void requireEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + " 不一致：oracle=" + expected + " 生产发出=" + actual);
        }
    }

    /** JSON 文本级深比（避开 Map 实现差异与顺序歧义；顺序由数组语义保证）。 */
    public static boolean jsonEquals(Object a, Object b) {
        return writeJson(a).equals(writeJson(b));
    }

    public static String writeJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("oracle 片段序列化失败: " + e.getMessage(), e);
        }
    }

    /** 义务集里指定 obligation 类型的项（用于逐字取水印/限流/掩码义务）。 */
    public static List<Map<String, Object>> obligationsOf(String type, List<Map<String, Object>> obligations) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> o : obligations) {
            if (type.equals(String.valueOf(o.get("obligation")))) out.add(o);
        }
        return out;
    }

    // ═══════════════ 两条回放通道（都由 oracle 单源驱动，用例零内联策略语义） ═══════════════

    /** 一次成立的回放：裁决包 + 对应 oracle 记录（用例断言的两侧）。 */
    public record ChainReplay(DecisionBundle bundle, OracleCase oracle) {
    }

    /** 护栏源直连回放：生产 {@code evaluate} 的返回段 + oracle 记录。 */
    public record GuardrailReplay(Map<String, Object> data, OracleCase oracle) {
    }

    /**
     * 经<b>三通道唯一入口</b>回放（{@code /decision} 形态）：主体、角色、组织域、资产
     * 全部取自 oracle 的 {@code input} 段，用例不内联任何策略语义；发出请求体与 oracle 输入
     * 的可比性由 {@link #assertRequestMatches} 在断言结果之前先钉。
     *
     * <p>前置：该 case 必须是 {@code /decision} 可表达的形态（顶层 {@code purpose}+{@code asset}
     * 且不带 {@code resource}），否则 {@link #replayViaGuardrailDirect} 才是正确通道。</p>
     */
    public static ChainReplay replayViaDecisionChain(String caseName,
                                                     DecisionBundle.Purpose purpose,
                                                     List<String> columns) throws IOException {
        OracleCase c = byName(caseName);
        if (c.input().get("purpose") == null || c.input().get("asset") == null) {
            throw new AssertionError(caseName + " 不是 /decision 形态的输入（缺 purpose/asset），"
                    + "应走 replayViaGuardrailDirect");
        }
        if (c.hasResourceScope()) {
            throw new AssertionError(caseName + " 带 resource.dataScope，只有 AI 工具裁决载荷会发；"
                    + "硬塞进行列面入口会得到与录制时不同的请求体 ⇒ 回放不成立");
        }
        assertPolicyNotDrifted();
        try (OpaOracleReplaySupport server = start()) {
            server.setCase(caseName);
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            DecisionBundle bundle = harness.decide(
                    new AssetRef(PageChannelHarness.SCHEMA, (String) c.input().get("asset"), columns),
                    harness.explicitContext(str(c, "userId"), str(c, "tenantId"), str(c, "department"),
                            rolesOf(c)),
                    purpose);
            assertRequestMatches(c, server.lastSentInput());
            assertWireShape(server);
            return new ChainReplay(bundle, c);
        }
    }

    /**
     * 护栏源<b>直连</b>回放（{@code resource.dataScope} 等 AI 工具载荷形态）：
     * 请求体逐字＝oracle 记录的输入，因此可比性钉退化为深比 {@code input} 段本身。
     */
    public static GuardrailReplay replayViaGuardrailDirect(String caseName) throws IOException {
        OracleCase c = byName(caseName);
        assertPolicyNotDrifted();
        try (OpaOracleReplaySupport server = start()) {
            server.setCase(caseName);
            PageChannelHarness harness = PageChannelHarness.create(server.baseUrl());
            Map<String, Object> data = harness.evaluateGuardrail(c.input());
            Map<String, Object> sent = server.lastSentInput();
            if (!writeJson(sent).equals(writeJson(c.input()))) {
                throw new AssertionError(caseName + " 发出的请求体与录制输入不逐字一致：oracle="
                        + writeJson(c.input()) + " 实发=" + writeJson(sent));
            }
            assertWireShape(server);
            return new GuardrailReplay(data, c);
        }
    }

    /**
     * 线路形态钉：恰好一次请求，且打的是策略文档
     * {@value #OPA_DOC_PATH}（= 名合规 + 非裸布尔查询；D-B4-14/D-B4-20 的反面证据）。
     */
    private static void assertWireShape(OpaOracleReplaySupport server) {
        List<String> paths = server.requestedPaths();
        if (paths.size() != 1 || !("POST " + OPA_DOC_PATH).equals(paths.get(0))) {
            throw new AssertionError("护栏请求线路形态不符（期望恰一次 POST " + OPA_DOC_PATH + "）: " + paths);
        }
    }
}
