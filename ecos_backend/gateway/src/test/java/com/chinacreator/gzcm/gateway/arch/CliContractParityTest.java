package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * CLI 契约一致性门禁（P-5 结案件 / ADR-21 §3 验收标识）。
 *
 * <p><b>为什么在 gateway 而不是 CLI 工程</b>：本门禁要同时看到三面——契约 YAML、CLI 命令清单、
 * 全部 Controller 源码。只有 gateway 的测试类路径同时拥有三者（CLI 工程不依赖后端模块）。</p>
 *
 * <p><b>四道判据</b>：
 * ① {@code everyCommandMapsToOpenApiOperation}：每个 CLI 命令的 operationId 必须在契约中存在，
 * 且命令记录不得自带 path/url（禁手写 URL）；
 * ② {@code noCommandTargetsServicePortDirectly}：CLI 源码不得出现任何 service 端口或直连库串，
 * 且必须保留默认网关 :8080（防「删光端口就绿」）；
 * ③ {@code everyContractPathExistsInController}：每条 implemented 契约路径必须由真实 Controller
 * 映射覆盖（静态解析 @*Mapping + *ApiPaths 常量拼接）；
 * ④ {@code contractRegistryNonVacuous}：契约可解析、sha 与清单绑定、operationId/path 全局唯一、
 * 计数不低于下限（防契约被删空后门禁空转）。</p>
 */
class CliContractParityTest {

    private static final String CONTRACT_DIR = "docs/10-架构/api-contracts/openapi";
    private static final String MANIFEST_REL = "tools/ecos-cli/src/main/resources/contract/ops.json";
    private static final String CLI_SRC_REL = "tools/ecos-cli/src/main/java";
    /**
     * 契约 operation 下限。87 → 91（CLI 生产化批 B：新增 validateCognitiveModel、
     * startCognitiveModelRun、listLlmModels、resolveModelRoute 四条；readiness/evidence
     * 签名收敛只改路径不改条数）→ 92（批 D：新增 createApprovalToken）。与 CLI 侧
     * {@code ContractManifestTest.MIN_OPERATIONS} 必须同值。
     */
    private static final int MIN_OPERATIONS = 95;
    private static final int MIN_CONTROLLER_ROUTES = 200;

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> STATUSES = Set.of("implemented", "partial", "planned", "framework");
    private static final Pattern MAPPING = Pattern.compile(
            "@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\b\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern CONST_REF = Pattern.compile("([A-Za-z_$][\\w$]*)\\.([A-Z_][A-Z0-9_]*)");
    private static final Pattern CONST_DECL = Pattern.compile(
            "(?:static\\s+final\\s+)?String\\s+([A-Z_][A-Z0-9_]*)\\s*=\\s*([^;]+);");
    private static final Pattern PATH_VAR = Pattern.compile("\\{[^/}]+}");

    @Test
    @DisplayName("① 每个 CLI 命令映射到契约 operationId，且命令记录不含 path/url")
    void everyCommandMapsToOpenApiOperation() throws Exception {
        Path root = repoRoot();
        JsonNode manifest = readManifest(root);
        Map<String, JsonNode> ops = operationsById(manifest);

        JsonNode commands = manifest.path("commands");
        assertTrue(commands.isArray() && !commands.isEmpty(), "命令清单为空（CLI 不得宣称有命令树）");
        for (JsonNode command : commands) {
            String name = command.path("name").asText();
            assertFalse(command.has("path") || command.has("url"),
                    "命令 " + name + " 自带 path/url ⇒ 违反「禁手写 URL」（ADR-21 §2.2）");
            assertTrue(command.path("operationIds").isArray(), "命令 " + name + " 缺 operationIds");
            for (JsonNode id : command.path("operationIds")) {
                assertTrue(ops.containsKey(id.asText()),
                        "命令 " + name + " 映射到契约中不存在的 operationId: " + id.asText());
            }
            for (JsonNode flag : command.path("flags")) {
                String opId = flag.path("operationId").asText();
                assertTrue(ops.containsKey(opId), "flag " + flag.path("flag").asText()
                        + " 指向不存在的 operationId: " + opId);
                assertTrue(parameterNames(root, ops.get(opId)).contains(flag.path("parameter").asText()),
                        "flag " + flag.path("flag").asText() + " 的参数名不在契约 " + opId
                                + " 的 parameters 中: " + flag.path("parameter").asText());
            }
        }
    }

    @Test
    @DisplayName("② CLI 源码零 service 端口/直连库串，且保留默认网关 :8080")
    void noCommandTargetsServicePortDirectly() throws Exception {
        Path src = repoRoot().resolve(CLI_SRC_REL);
        assertTrue(Files.isDirectory(src), "CLI 源码目录不存在: " + src);

        List<String> violations = new ArrayList<>();
        boolean baseUrl8080 = false;
        Pattern servicePort = Pattern.compile(":(180\\d{2}|190\\d{2})\\b");
        Pattern hostPort = Pattern.compile("https?://([A-Za-z0-9.\\-]+):(\\d+)");

        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (servicePort.matcher(text).find()) {
                    violations.add(file + " 含 service 端口字面量");
                }
                if (text.contains("jdbc:")) {
                    violations.add(file + " 含 jdbc 直连串");
                }
                Matcher m = hostPort.matcher(text);
                while (m.find()) {
                    String host = m.group(1);
                    String port = m.group(2);
                    if (!"8080".equals(port) || !"localhost".equals(host)) {
                        violations.add(file + " 出现非网关地址: " + m.group());
                    }
                    if ("8080".equals(port)) {
                        baseUrl8080 = true;
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "CLI 只允许经 gateway:8080，违规：\n" + String.join("\n", violations));
        assertTrue(baseUrl8080, "CLI 源码未见默认网关 :8080（删除它会制造「零端口即绿」的空转门禁）");
    }

    @Test
    @DisplayName("③ 每条 implemented 契约路径都能被真实 Controller 映射覆盖")
    void everyContractPathExistsInController() throws Exception {
        Path root = repoRoot();
        JsonNode manifest = readManifest(root);
        Set<Route> routes = new HashSet<>(scanControllerRoutes(root.resolve("ecos_backend")).values());
        assertTrue(routes.size() >= MIN_CONTROLLER_ROUTES,
                "Controller 路由解析数 " + routes.size() + " 低于下限 " + MIN_CONTROLLER_ROUTES
                        + "（解析器退化或扫描根错误，此时「全部命中」是空转出来的绿）");

        Set<Route> implemented = new HashSet<>();
        for (JsonNode op : manifest.path("operations")) {
            if ("implemented".equals(op.path("status").asText())) {
                implemented.add(new Route(op.path("method").asText(), normalizePath(op.path("path").asText())));
            }
        }
        assertFalse(implemented.isEmpty(), "清单中没有 implemented 端点（契约与清单脱钩）");

        List<String> missing = new ArrayList<>();
        for (Route route : implemented) {
            if (!routes.contains(route)) {
                missing.add(route.method + " " + route.path);
            }
        }
        assertTrue(missing.isEmpty(),
                "契约声明 implemented 但 Controller 实测无此映射（" + missing.size() + " 条）：\n"
                        + String.join("\n", missing)
                        + "\n⇒ 修契约状态为 planned，或修 Controller 路径；禁把缺口留成假绿。");
    }

    @Test
    @DisplayName("④ 契约可解析、sha 与清单绑定、operationId/path 全局唯一、计数不低于下限")
    void contractRegistryNonVacuous() throws Exception {
        Path root = repoRoot();
        JsonNode manifest = readManifest(root);

        Set<String> ids = new TreeSet<>();
        Set<String> routes = new TreeSet<>();
        int operations = 0;
        for (JsonNode op : manifest.path("operations")) {
            String id = op.path("operationId").asText();
            String method = op.path("method").asText();
            String path = op.path("path").asText();
            String status = op.path("status").asText();
            assertTrue(!id.isBlank(), "存在空 operationId");
            assertTrue(ids.add(id), "operationId 跨契约重复: " + id);
            assertTrue(routes.add(method + " " + normalizePath(path)), "(method,path) 跨契约重复: " + method + " " + path);
            assertTrue(STATUSES.contains(status), "x-implementation-status 非法: " + id + " -> " + status);
            assertTrue(path.startsWith("/api/") || path.startsWith("/actuator/"),
                    "契约 path 前缀不在白名单: " + path);
            operations++;
        }
        assertTrue(operations >= MIN_OPERATIONS,
                "契约 operation 数 " + operations + " 低于下限 " + MIN_OPERATIONS
                        + "（防契约被删空后门禁空转；契约增减须在 CLI-MVP 落地登记册留凭证）");

        // 两处阈值是「同批同值」的口头约定，此处改为机器校验：改一处忘另一处即红。
        Path manifestTest = root.resolve("tools/ecos-cli/src/test/java/com/chinacreator/gzcm/cli/"
                + "ContractManifestTest.java");
        assertTrue(Files.isRegularFile(manifestTest), "CLI 侧契约门禁缺失: " + manifestTest);
        Matcher cliMin = Pattern.compile("MIN_OPERATIONS\\s*=\\s*(\\d+)")
                .matcher(Files.readString(manifestTest, StandardCharsets.UTF_8));
        assertTrue(cliMin.find(), "CLI 侧 ContractManifestTest 未见 MIN_OPERATIONS 常量");
        assertEquals(MIN_OPERATIONS, Integer.parseInt(cliMin.group(1)),
                "两处 MIN_OPERATIONS 必须同值（gateway=" + MIN_OPERATIONS
                        + " / cli=" + cliMin.group(1) + "）");

        for (JsonNode contract : manifest.path("contracts")) {
            String file = contract.path("file").asText();
            Path path = root.resolve(CONTRACT_DIR).resolve(file);
            assertTrue(Files.isRegularFile(path), "契约文件缺失: " + path);
            byte[] bytes = Files.readAllBytes(path);
            assertNotNull(new Yaml().load(new String(bytes, StandardCharsets.UTF_8)),
                    "契约 YAML 解析为空: " + file);
            assertEquals(contract.path("sha256").asText(), sha256Normalized(bytes),
                    "契约 " + file + " 与清单 sha256 不一致（契约已改但清单未重新生成）");
            assertEquals(contract.path("operations").asInt(), countOperations(path),
                    "契约 " + file + " 的 operation 数与清单登记不一致");
        }

        // 清单 ↔ 契约逐条同源：防「手改清单伪造路径/状态」绕过 sha 只绑文件的做法
        Map<String, String> fromYaml = yamlOperationSignatures(root);
        Map<String, String> fromManifest = new LinkedHashMap<>();
        for (JsonNode op : manifest.path("operations")) {
            fromManifest.put(op.path("operationId").asText(),
                    op.path("method").asText() + " " + op.path("path").asText()
                            + " " + op.path("status").asText() + " " + op.path("contract").asText());
        }
        assertEquals(fromYaml, fromManifest,
                "清单与契约逐条不一致（清单被手改？重新生成：mvn -f tools/ecos-cli/pom.xml test -Dcli.gen=true）");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> yamlOperationSignatures(Path root) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(root.resolve(CONTRACT_DIR))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".yaml")).toList()) {
                String contract = file.getFileName().toString();
                Map<String, Object> yaml = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
                Map<String, Object> paths = (Map<String, Object>) yaml.get("paths");
                for (var pathEntry : paths.entrySet()) {
                    for (var methodEntry : ((Map<String, Object>) pathEntry.getValue()).entrySet()) {
                        String method = String.valueOf(methodEntry.getKey()).toUpperCase();
                        if (!HTTP_METHODS.contains(method)) {
                            continue;
                        }
                        Map<String, Object> op = (Map<String, Object>) methodEntry.getValue();
                        out.put(String.valueOf(op.get("operationId")),
                                method + " " + pathEntry.getKey() + " "
                                        + String.valueOf(op.get("x-implementation-status")) + " " + contract);
                    }
                }
            }
        }
        return out;
    }

    // ── Controller 静态解析 ───────────────────────────────────────────

    private record Route(String method, String path) {
    }

    private Map<String, Route> scanControllerRoutes(Path backendRoot) throws Exception {
        Map<String, String> constants = loadPathConstants(backendRoot);
        Map<String, Route> routes = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(backendRoot)) {
            for (Path file : files
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String className = file.getFileName().toString().replace(".java", "");
                Map<String, String> local = new HashMap<>(constants);
                // 控制器内私有常量（如 PATH_CAPABILITIES）也是路径来源；两轮足以解析一层引用
                for (int pass = 0; pass < 2; pass++) {
                    Matcher lm = CONST_DECL.matcher(text);
                    while (lm.find()) {
                        String key = className + "." + lm.group(1);
                        if (local.containsKey(key)) {
                            continue;
                        }
                        String value = evalExpr(lm.group(2).trim(), className, local);
                        if (value != null) {
                            local.put(key, value);
                        }
                    }
                }
                int classIdx = declarationIndex(text);
                if (classIdx < 0) {
                    continue;
                }
                String header = text.substring(0, classIdx);
                String body = text.substring(classIdx);
                String classPrefix = "";
                for (String value : mappingValues(header, local, className)) {
                    classPrefix = value;
                    break;
                }
                for (var entry : mappingValuesWithMethod(body, local, className)) {
                    String full = normalizePath(classPrefix + entry.path());
                    routes.put(entry.method() + " " + full, new Route(entry.method(), full));
                }
            }
        }
        return routes;
    }

    /** 常量表：*ApiPaths.java 中 `String NAME = "a" + CONST + "b";` 形式的拼接求值。
     *  键为 {@code 类名.常量名}——同名常量（如 BASE_PREFIX）在不同 ApiPaths 类中语义不同，
     *  按简单名索引会串味（2026-11-09 实测：wagent 路由被解析成 /api/v1/ai/*）。 */
    private Map<String, String> loadPathConstants(Path backendRoot) throws Exception {
        Map<String, String> raw = new LinkedHashMap<>();
        Map<String, String> owner = new HashMap<>();
        try (Stream<Path> files = Files.walk(backendRoot)) {
            for (Path file : files
                    .filter(p -> p.getFileName().toString().endsWith("ApiPaths.java")
                            || p.getFileName().toString().endsWith("ApiPathsHolder.java"))
                    .toList()) {
                String className = file.getFileName().toString().replace(".java", "");
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = CONST_DECL.matcher(text);
                while (m.find()) {
                    String key = className + "." + m.group(1);
                    raw.put(key, m.group(2).trim());
                    owner.put(key, className);
                }
            }
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (int round = 0; round < 8; round++) {
            boolean changed = false;
            for (var e : raw.entrySet()) {
                if (resolved.containsKey(e.getKey())) {
                    continue;
                }
                String value = evalExpr(e.getValue(), owner.get(e.getKey()), resolved);
                if (value != null) {
                    resolved.put(e.getKey(), value);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return resolved;
    }

    /** 仅支持 "字面量" + CONST + "字面量" 的拼接；不可静态求值返回 null（不猜测）。 */
    private String evalExpr(String expr, String className, Map<String, String> known) {
        StringBuilder out = new StringBuilder();
        for (String part : expr.split("\\+")) {
            String token = part.trim();
            if (token.isEmpty()) {
                return null;
            }
            if (token.startsWith("\"") && token.endsWith("\"")) {
                out.append(token, 1, token.length() - 1);
            } else if (token.matches("[A-Za-z_$][\\w$]*\\.[A-Z_][A-Z0-9_]*")) {
                if (!known.containsKey(token)) {
                    return null;
                }
                out.append(known.get(token));
            } else if (token.matches("[A-Z_][A-Z0-9_]*") && className != null
                    && known.containsKey(className + "." + token)) {
                out.append(known.get(className + "." + token));
            } else {
                String unique = uniqueBare(token, known);
                if (unique == null) {
                    return null;
                }
                out.append(unique);
            }
        }
        return out.toString();
    }

    /** 无类名限定的引用：仅在全局唯一时接受（多义即不可静态判定，返回 null）。 */
    private String uniqueBare(String name, Map<String, String> known) {
        String hit = null;
        for (var e : known.entrySet()) {
            if (e.getKey().endsWith("." + name)) {
                if (hit != null) {
                    return null;
                }
                hit = e.getValue();
            }
        }
        return hit;
    }

    private List<String> mappingValues(String segment, Map<String, String> constants, String className) {
        List<String> values = new ArrayList<>();
        Matcher m = MAPPING.matcher(segment);
        while (m.find()) {
            String args = m.group(2) == null ? "" : m.group(2);
            Matcher q = QUOTED.matcher(args);
            if (q.find()) {
                values.add(q.group(1));
                continue;
            }
            String value = resolveConstant(args, constants, className);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /** 注解实参 → 常量值：支持 Class.CONST 与控制器内无限定 CONST 两种写法。 */
    private String resolveConstant(String args, Map<String, String> constants, String className) {
        Matcher c = CONST_REF.matcher(args);
        if (c.find()) {
            String value = constants.get(c.group(1) + "." + c.group(2));
            return value != null ? value : uniqueBare(c.group(2), constants);
        }
        Matcher bare = Pattern.compile("\\b([A-Z_][A-Z0-9_]{2,})\\b").matcher(args);
        if (bare.find()) {
            String value = constants.get(className + "." + bare.group(1));
            return value != null ? value : uniqueBare(bare.group(1), constants);
        }
        return null;
    }

    private List<Route> mappingValuesWithMethod(String body, Map<String, String> constants, String className) {
        List<Route> out = new ArrayList<>();
        Matcher m = MAPPING.matcher(body);
        while (m.find()) {
            String annotation = m.group(1);
            String args = m.group(2) == null ? "" : m.group(2);
            String path = "";
            Matcher q = QUOTED.matcher(args);
            if (q.find()) {
                path = q.group(1);
            } else {
                String value = resolveConstant(args, constants, className);
                if (value != null) {
                    path = value;
                }
            }
            String method = methodOf(annotation, args);
            if (method != null) {
                out.add(new Route(method, path));
            }
        }
        return out;
    }

    private String methodOf(String annotation, String args) {
        String base = annotation.substring(0, annotation.length() - "Mapping".length()).toUpperCase();
        if (!base.isEmpty()) {
            return base.equals("REQUEST") ? explicitMethod(args) : base;
        }
        return explicitMethod(args);
    }

    private String explicitMethod(String args) {
        Matcher m = Pattern.compile("RequestMethod\\.([A-Z]+)").matcher(args);
        return m.find() ? m.group(1) : null;
    }

    private static String normalizePath(String path) {
        String p = PATH_VAR.matcher(path).replaceAll("{}");
        p = p.replace("//", "/");
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return p;
    }

    /** 类声明关键字位置（避开 Javadoc 里的 "class" 字样；类级注解留在 header 段）。 */
    private static int declarationIndex(String text) {
        Matcher m = Pattern.compile("(?m)^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*"
                + "(?:public\\s+|abstract\\s+|final\\s+)*class\\s+\\w+").matcher(text);
        if (!m.find()) {
            return -1;
        }
        return m.start() + m.group().lastIndexOf("class");
    }

    // ── 读取与工具 ────────────────────────────────────────────────────

    private JsonNode readManifest(Path root) throws Exception {
        Path manifest = root.resolve(MANIFEST_REL);
        assertTrue(Files.isRegularFile(manifest),
                "缺少 CLI 契约清单 " + MANIFEST_REL + "；先执行 mvn -f tools/ecos-cli/pom.xml test -Dcli.gen=true");
        return new ObjectMapper().readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    }

    private Map<String, JsonNode> operationsById(JsonNode manifest) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode op : manifest.path("operations")) {
            map.put(op.path("operationId").asText(), op);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Set<String> parameterNames(Path root, JsonNode op) {
        String contract = op.path("contract").asText();
        String path = op.path("path").asText();
        String method = op.path("method").asText().toLowerCase();
        Path file = root.resolve(CONTRACT_DIR).resolve(contract);
        try {
            Map<String, Object> yaml = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            Map<String, Object> paths = (Map<String, Object>) yaml.get("paths");
            Map<String, Object> byMethod = (Map<String, Object>) paths.get(path);
            Map<String, Object> operation = (Map<String, Object>) byMethod.get(method);
            Object params = operation.get("parameters");
            Set<String> names = new HashSet<>();
            if (params instanceof List<?> list) {
                for (Object p : list) {
                    if (p instanceof Map<?, ?> pm && "query".equals(String.valueOf(pm.get("in")))) {
                        names.add(String.valueOf(pm.get("name")));
                    }
                }
            }
            return names;
        } catch (Exception e) {
            throw new AssertionError("读取契约参数失败: " + contract + " " + method + " " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private int countOperations(Path yamlFile) throws Exception {
        Map<String, Object> yaml = new Yaml().load(Files.readString(yamlFile, StandardCharsets.UTF_8));
        Map<String, Object> paths = (Map<String, Object>) yaml.get("paths");
        int count = 0;
        for (Object value : paths.values()) {
            for (Object method : ((Map<String, Object>) value).keySet()) {
                if (HTTP_METHODS.contains(String.valueOf(method).toUpperCase())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String sha256Normalized(byte[] bytes) {
        try {
            String normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path repoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(CONTRACT_DIR)) && Files.isDirectory(cursor.resolve("ecos_backend"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        fail("无法定位仓根（应含 ecos_backend/ 与 " + CONTRACT_DIR + "），cwd=" + Paths.get("").toAbsolutePath());
        return null;
    }
}
