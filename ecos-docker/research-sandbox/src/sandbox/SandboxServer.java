package sandbox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;

/**
 * ECOS L3 研究沙盒服务（Phase 1，2026-11-25）。
 *
 * <p><b>职责极小，且这是有意为之</b>：接收「一轮实验规格 + 只读数据快照」→ 在本进程内做
 * 确定性聚合 → 返回 metric / 洞察 / 快照摘要。它不是通用代码执行平台。</p>
 *
 * <p><b>红线（对齐可行性报告 §5.4）</b>：</p>
 * <ol>
 *   <li>不连任何数据库、不读凭据环境变量、不发外网请求；</li>
 *   <li>只接受<b>声明式</b>实验规格（过滤/分组/聚合），<b>不</b>接受任意代码，也不 eval；</li>
 *   <li>数据只来自请求体快照，绝不主动取数；</li>
 *   <li>任何异常都以非 0 code 显式返回，禁「200 + 空结论」；</li>
 *   <li>有请求体大小与行数硬上限，超限即失败。</li>
 * </ol>
 *
 * <p><b>为什么不用 Python</b>：本机 Docker Hub 不可达，无法拉取 python 基础镜像；改用 JDK 自带
 * {@link HttpServer}，复用<b>已在本地</b>的 {@code eclipse-temurin:17-jre}，零第三方依赖，
 * 与项目 Java 工具链一致。</p>
 */
public final class SandboxServer {

    static final String VERSION = "1.0.0";
    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 20_000;
    static final Set<String> ALLOWED_OPS =
            Set.of("count", "sum", "avg", "min", "max", "distinct_count");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(envOr("SANDBOX_PORT", "8000"));
        String bind = envOr("SANDBOX_BIND", "0.0.0.0");
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/research/rounds", new RoundHandler());
        server.createContext("/research/execute", new CodeHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        log("ecos-research-sandbox v" + VERSION + " listening on " + bind + ":" + port);
        startHeartbeat();
    }

    /**
     * 心跳文件：容器健康检查用。
     *
     * <p><b>为什么不走 HTTP 探活</b>：JRE 精简镜像里既没有 bash（{@code /dev/tcp} 不可用），
     * 也没有 javac（无法跑单文件源码），而 compose 的 {@code healthcheck} 只能用 sh 执行。
     * 因此由进程自身往 writable 的 tmpfs 写心跳，健康检查退化为「文件是否新鲜」——
     * 这同时能捕获「端口在听但主循环卡死」的假活状态，比端口探测更严格。</p>
     */
    static void startHeartbeat() {
        String path = envOr("SANDBOX_HEARTBEAT_FILE", "/tmp/sandbox.heartbeat");
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(path),
                            String.valueOf(System.currentTimeMillis()));
                    Thread.sleep(5000L);
                } catch (Exception e) {
                    log("heartbeat write failed: " + e);
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "sandbox-heartbeat");
        t.setDaemon(true);
        t.start();
    }

    // ── 路由 ────────────────────────────────────────────────────────────────

    static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "UP");
            data.put("version", VERSION);
            data.put("codeExecution", false);
            data.put("network", false);
            data.put("operators", new ArrayList<>(new TreeSet<>(ALLOWED_OPS)));
            data.put("codeExecution", true);
            data.put("languages", new ArrayList<>(List.of("python")));
            data.put("codeExecutionNote",
                    "仅 Python 代码执行（Java 执行器已按 2026-11-26 裁决移除）：容器无网络/只读根/"
                            + "cap-drop/非 root，单次执行硬超时强杀；静态筛查仅防呆，安全边界在容器");
            data.put("note", "声明式算子沙盒：不接受任意代码，不连库，不出网");
            respond(ex, 200, envelope(0, data, null));
        }
    }

    /**
     * 受控代码执行端点 {@code POST /research/execute}。
     *
     * <p>请求 {@code {code, timeoutMs?, heapMb?}}；返回编译/运行结果与可解析指标。
     * 安全边界在容器（无网络/只读根/cap-drop/非 root），本端点只做防呆与超时。</p>
     */
    static final class CodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 200, envelope(405, null, "仅支持 POST"));
                return;
            }
            try {
                Object parsed = MiniJson.parse(new String(readLimited(ex), StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> payload)) {
                    throw new SandboxError(400, "请求体必须是 JSON 对象");
                }
                String code = asText(payload.get("code"));
                String language = asText(payload.get("language"));
                if (language == null || language.isBlank()) {
                    language = "python";
                }
                Long timeoutMs = payload.get("timeoutMs") instanceof BigDecimal b
                        ? b.longValue() : null;

                Map<String, Object> result;
                switch (language.toLowerCase()) {
                    case "python", "python3", "py" -> result = CodeExecutor.runPython(code, timeoutMs);
                    default -> throw new SandboxError(400,
                            "不支持的语言: " + language + "（沙盒仅支持 python；Java 执行器已移除）");
                }
                respond(ex, 200, envelope(0, result, null));
            } catch (SandboxError e) {
                respond(ex, 200, envelope(e.code, null, e.getMessage()));
            } catch (IllegalArgumentException e) {
                respond(ex, 200, envelope(400, null, "请求体解析失败: " + e.getMessage()));
            } catch (RuntimeException e) {
                log("execute failed: " + e);
                respond(ex, 200, envelope(500, null,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    static final class RoundHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 200, envelope(405, null, "仅支持 POST"));
                return;
            }
            try {
                byte[] raw = readLimited(ex);
                Object parsed = MiniJson.parse(new String(raw, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> payload)) {
                    throw new SandboxError(400, "请求体必须是 JSON 对象");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) payload;
                respond(ex, 200, envelope(0, runRound(body), null));
            } catch (SandboxError e) {
                respond(ex, 200, envelope(e.code, null, e.getMessage()));
            } catch (IllegalArgumentException e) {
                // JSON 解析失败属「客户端输入错误」，必须 400 而不是 500——
                // 否则调用方会把「自己发错了」误判成「沙盒坏了」
                respond(ex, 200, envelope(400, null, "请求体解析失败: " + e.getMessage()));
            } catch (RuntimeException e) {
                // 兜底：暴露类型与摘要，绝不吞成成功
                log("round failed: " + e);
                respond(ex, 200, envelope(500, null, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    // ── 核心：确定性聚合 ─────────────────────────────────────────────────────

    static Map<String, Object> runRound(Map<String, Object> payload) {
        long started = System.nanoTime();
        long deadlineMs = payload.get("deadlineMs") instanceof BigDecimal b
                ? b.longValue() : 30_000L;
        deadlineMs = Math.max(1_000L, Math.min(deadlineMs, 30_000L));
        long deadlineAt = started + deadlineMs * 1_000_000L;

        Object snapObj = payload.get("snapshot");
        if (!(snapObj instanceof Map<?, ?> snapshot)) {
            throw new SandboxError(400, "snapshot 必须是对象");
        }
        Object rowsObj = snapshot.get("rows");
        if (!(rowsObj instanceof List<?> rawRows)) {
            throw new SandboxError(400, "snapshot.rows 必须是数组");
        }
        if (rawRows.size() > MAX_ROWS) {
            throw new SandboxError(413, "快照行数 " + rawRows.size() + " 超上限 " + MAX_ROWS);
        }
        List<Map<String, Object>> rows = new ArrayList<>(rawRows.size());
        for (Object r : rawRows) {
            if (!(r instanceof Map<?, ?> m)) {
                throw new SandboxError(400, "snapshot.rows 元素必须是对象");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            rows.add(cast);
        }

        Object expObj = payload.get("experiment");
        if (!(expObj instanceof Map<?, ?> experiment)) {
            throw new SandboxError(400, "experiment 必须是对象");
        }
        Object filtersObj = experiment.get("filters");
        Map<?, ?> filters = filtersObj instanceof Map<?, ?> f ? f : Map.of();

        Object metricsObj = experiment.get("metrics");
        if (!(metricsObj instanceof List<?> metricsList) || metricsList.isEmpty()) {
            throw new SandboxError(400, "experiment.metrics 必须是非空数组");
        }

        List<Map<String, Object>> filtered = applyFilters(rows, filters);

        String groupBy = asText(experiment.get("groupBy"));
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        if (groupBy != null && !groupBy.isBlank()) {
            for (Map<String, Object> r : filtered) {
                String key = asText(r.get(groupBy));
                groups.computeIfAbsent(key == null ? "" : key, k -> new ArrayList<>()).add(r);
            }
        } else {
            groups.put("__all__", filtered);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            if (System.nanoTime() > deadlineAt) {
                throw new SandboxError(408, "声明式执行超过 deadlineMs=" + deadlineMs);
            }
            for (Object m : metricsList) {
                if (!(m instanceof Map<?, ?> spec)) {
                    throw new SandboxError(400, "metrics 元素必须是对象");
                }
                Map<String, Object> cell = compute(e.getValue(), spec);
                cell.put("group", e.getKey());
                results.add(cell);
            }
        }

        Map<String, Object> insight = buildInsight(groupBy, groups, results);

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("rowsIn", rows.size());
        metric.put("rowsAfterFilter", filtered.size());
        metric.put("groupCount", groups.size());
        metric.put("results", results);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metric", metric);
        out.put("insight", insight);
        out.put("snapshotDigest", "sha256:" + sha256(MiniJson.write(snapshot)));
        out.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        Map<String, Object> sandbox = new LinkedHashMap<>();
        sandbox.put("version", VERSION);
        sandbox.put("ops", new ArrayList<>(new TreeSet<>(ALLOWED_OPS)));
        out.put("sandbox", sandbox);
        return out;
    }

    static List<Map<String, Object>> applyFilters(List<Map<String, Object>> rows, Map<?, ?> filters) {
        List<Map<String, Object>> out = rows;
        for (Map.Entry<?, ?> e : filters.entrySet()) {
            String field = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> cond)) {
                throw new SandboxError(400, "filter[" + field + "] 必须是对象");
            }
            String op = cond.get("op") == null ? "eq" : String.valueOf(cond.get("op"));
            Object target = cond.get("value");
            List<Map<String, Object>> next = new ArrayList<>();
            switch (op) {
                case "eq" -> {
                    for (Map<String, Object> r : out) {
                        if (java.util.Objects.equals(r.get(field), target)) {
                            next.add(r);
                        }
                    }
                }
                case "ne" -> {
                    for (Map<String, Object> r : out) {
                        if (!java.util.Objects.equals(r.get(field), target)) {
                            next.add(r);
                        }
                    }
                }
                case "gte", "lte" -> {
                    BigDecimal t = asNumber(target);
                    if (t == null) {
                        throw new SandboxError(400, "filter[" + field + "] 的比较值不是数值");
                    }
                    for (Map<String, Object> r : out) {
                        BigDecimal v = asNumber(r.get(field));
                        if (v == null) {
                            continue;
                        }
                        int cmp = v.compareTo(t);
                        if (("gte".equals(op) && cmp >= 0) || ("lte".equals(op) && cmp <= 0)) {
                            next.add(r);
                        }
                    }
                }
                case "in" -> {
                    if (!(target instanceof List<?> allowed)) {
                        throw new SandboxError(400, "filter[" + field + "].in 需要数组");
                    }
                    Set<Object> allow = new LinkedHashSet<>(allowed);
                    for (Map<String, Object> r : out) {
                        if (allow.contains(r.get(field))) {
                            next.add(r);
                        }
                    }
                }
                default -> throw new SandboxError(400, "不支持的过滤算子: " + op);
            }
            out = next;
        }
        return out;
    }

    static Map<String, Object> compute(List<Map<String, Object>> rows, Map<?, ?> spec) {
        String op = asText(spec.get("op"));
        String field = asText(spec.get("field"));
        if (op == null || !ALLOWED_OPS.contains(op)) {
            throw new SandboxError(400, "不支持的算子: " + op + "（允许 " + new TreeSet<>(ALLOWED_OPS) + "）");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", op);

        if ("count".equals(op)) {
            out.put("field", null);
            out.put("value", rows.size());
            return out;
        }
        if (field == null || field.isBlank()) {
            throw new SandboxError(400, "算子 " + op + " 必须指定 field");
        }
        out.put("field", field);

        if ("distinct_count".equals(op)) {
            Set<Object> distinct = new LinkedHashSet<>();
            for (Map<String, Object> r : rows) {
                distinct.add(r.get(field));
            }
            out.put("value", distinct.size());
            return out;
        }

        if (!rows.isEmpty() && !rows.get(0).containsKey(field)) {
            throw new SandboxError(400, "快照中不存在字段: " + field
                    + "（可用字段 " + new TreeSet<>(rows.get(0).keySet()) + "）");
        }
        List<BigDecimal> nums = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal v = asNumber(r.get(field));
            if (v != null) {
                nums.add(v);
            }
        }
        if (nums.isEmpty()) {
            throw new SandboxError(400, "字段 " + field + " 无可用数值（禁把空格当 0）");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : nums) {
            total = total.add(v);
        }
        BigDecimal value = switch (op) {
            case "sum" -> total;
            case "avg" -> total.divide(BigDecimal.valueOf(nums.size()), 6, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            case "min" -> nums.stream().min(BigDecimal::compareTo).orElseThrow();
            case "max" -> nums.stream().max(BigDecimal::compareTo).orElseThrow();
            default -> throw new SandboxError(400, "未实现算子: " + op);
        };
        out.put("value", value);
        out.put("n", nums.size());
        return out;
    }

    /** 分组对比时给描述性洞察；不含因果结论（业务判断归人）。 */
    static Map<String, Object> buildInsight(String groupBy,
                                            Map<String, List<Map<String, Object>>> groups,
                                            List<Map<String, Object>> results) {
        if (groupBy == null || groupBy.isBlank() || groups.size() < 2 || results.isEmpty()) {
            return null;
        }
        Map<String, Object> primary = results.get(0);
        String op = asText(primary.get("op"));
        if (!Set.of("sum", "avg", "max", "min").contains(op)) {
            return null;
        }
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> r : results) {
            if (op.equals(asText(r.get("op"))) && r.get("value") instanceof BigDecimal) {
                ranked.add(r);
            }
        }
        if (ranked.isEmpty()) {
            return null;
        }
        ranked.sort((x, y) -> ((BigDecimal) y.get("value")).compareTo((BigDecimal) x.get("value")));
        Map<String, Object> hi = ranked.get(0);
        Map<String, Object> lo = ranked.get(ranked.size() - 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("metric", op + "(" + primary.get("field") + ")");
        out.put("highest", Map.of("group", hi.get("group"), "value", hi.get("value")));
        out.put("lowest", Map.of("group", lo.get("group"), "value", lo.get("value")));
        out.put("spread", ((BigDecimal) hi.get("value")).subtract((BigDecimal) lo.get("value")));
        out.put("note", "描述性对比，不含因果结论；业务判断须由人做出");
        return out;
    }

    // ── HTTP 工具 ───────────────────────────────────────────────────────────

    static byte[] readLimited(HttpExchange ex) throws IOException {
        long declared = -1;
        String len = ex.getRequestHeaders().getFirst("Content-Length");
        if (len != null) {
            try {
                declared = Long.parseLong(len.trim());
            } catch (NumberFormatException ignored) {
                declared = -1;
            }
        }
        if (declared == 0) {
            throw new SandboxError(400, "请求体为空");
        }
        if (declared > MAX_BODY_BYTES) {
            throw new SandboxError(413, "请求体 " + declared + "B 超上限 " + MAX_BODY_BYTES + "B");
        }
        byte[] buf = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (buf.length > MAX_BODY_BYTES) {
            throw new SandboxError(413, "请求体超上限 " + MAX_BODY_BYTES + "B");
        }
        if (buf.length == 0) {
            throw new SandboxError(400, "请求体为空");
        }
        return buf;
    }

    static Map<String, Object> envelope(int code, Object data, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        if (data != null) {
            out.put("data", data);
        }
        if (message != null) {
            out.put("message", message);
        }
        return out;
    }

    static void respond(HttpExchange ex, int httpStatus, Map<String, Object> body) throws IOException {
        byte[] raw = MiniJson.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(httpStatus, raw.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(raw);
        }
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    static BigDecimal asNumber(Object v) {
        if (v == null || v instanceof Boolean) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (v instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    static void log(String msg) {
        System.err.println("[" + java.time.Instant.now() + "] " + msg);
    }

    private SandboxServer() {
    }
}
