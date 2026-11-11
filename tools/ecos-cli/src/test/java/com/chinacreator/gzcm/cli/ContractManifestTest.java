package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 契约清单门禁（P-5 的 CLI 侧）：把 3 份 OpenAPI 契约编译成 contract/ops.json，并校验命令树。
 *
 * <p><b>为什么是测试而不是脚本</b>：清单是 CLI「禁手写 URL」的单源——命令只持 operationId，
 * 路径/参数名全部来自本清单。清单与契约不一致时必须在构建期变红，不能靠人工同步。</p>
 *
 * <p><b>反假绿四锁</b>：① 契约文件必须可被 YAML 解析（2026-11-09 实测原文件曾有 1 处语法错误）；
 * ② operationId 与 (method,path) 双双全局唯一；③ 每个命令的 operationId、每个 flag 映射的
 * query 参数必须在契约里逐字存在；④ 清单与契约 sha256 绑定，手改清单即红。</p>
 *
 * <p><b>重新生成</b>：契约变更后执行
 * {@code mvn -f tools/ecos-cli/pom.xml test -Dcli.gen=true}，再提交生成的
 * {@code src/main/resources/contract/ops.json}。</p>
 */
class ContractManifestTest {

    private static final String CONTRACT_DIR = "docs/10-架构/api-contracts/openapi";
    private static final List<String> CONTRACT_FILES = List.of(
            "wagent-v1.yaml",
            "supplementary-cognitive-ai-v1.yaml",
            "platform-v1.yaml");
    private static final Set<String> STATUSES =
            Set.of("implemented", "partial", "planned", "framework");
    private static final String MANIFEST_REL = "tools/ecos-cli/src/main/resources/contract/ops.json";
    private static final String CONTRACT_VERSION = "1.0.0";
    /**
     * 契约 operation 下限。87（MVP 首批）→ 91（CLI 生产化批 B：readiness/evidence 签名收敛后
     * 新增 validateCognitiveModel、startCognitiveModelRun、listLlmModels、resolveModelRoute 四条）
     * → 92（批 D：新增 createApprovalToken）。契约增减须与 gateway
     * {@code CliContractParityTest.MIN_OPERATIONS} 同批同值。
     */
    private static final int MIN_OPERATIONS = 94;

    @Test
    @DisplayName("契约 → command manifest 生成/校验：operationId 与命令映射一致，且清单与契约 sha 绑定")
    void contractCompilesToCommandManifest() throws Exception {
        Path repoRoot = repoRoot();
        Path manifestPath = repoRoot.resolve(MANIFEST_REL);

        Map<String, Object> manifest = buildManifest(repoRoot);

        if (Boolean.getBoolean("cli.gen")) {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, toJson(manifest) + "\n", StandardCharsets.UTF_8);
            System.out.println("[cli.gen] wrote " + manifestPath);
            return;
        }

        assertTrue(Files.exists(manifestPath),
                "缺少契约清单 " + MANIFEST_REL + "；请执行 mvn -f tools/ecos-cli/pom.xml test -Dcli.gen=true");
        String expected = normalize(toJson(manifest));
        String actual = normalize(Files.readString(manifestPath, StandardCharsets.UTF_8)).strip();
        if (!expected.equals(actual)) {
            int idx = 0;
            int max = Math.min(expected.length(), actual.length());
            while (idx < max && expected.charAt(idx) == actual.charAt(idx)) {
                idx++;
            }
            int from = Math.max(0, idx - 60);
            fail("contract/ops.json 与契约不一致（禁止手改生成物）。重新生成：\n"
                    + "  mvn -f tools/ecos-cli/pom.xml test -Dcli.gen=true\n"
                    + "长度 expected=" + expected.length() + " actual=" + actual.length()
                    + " 首个差异下标=" + idx + "\n"
                    + "expected 片段: " + expected.substring(from, Math.min(expected.length(), idx + 60)) + "\n"
                    + "actual   片段: " + actual.substring(from, Math.min(actual.length(), idx + 60)));
        }
    }

    // ── manifest 构建 ────────────────────────────────────────────────

    private Map<String, Object> buildManifest(Path repoRoot) throws Exception {
        Path dir = repoRoot.resolve(CONTRACT_DIR);
        assertTrue(Files.isDirectory(dir), "契约目录不存在: " + dir);

        List<Map<String, Object>> contractRows = new ArrayList<>();
        List<Map<String, Object>> operationRows = new ArrayList<>();
        Map<String, String> idToStatus = new LinkedHashMap<>();
        Map<String, Set<String>> idToParams = new LinkedHashMap<>();
        Map<String, Set<String>> idToPathParams = new LinkedHashMap<>();
        Map<String, Boolean> hasRequestBody = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        Set<String> methodPaths = new HashSet<>();

        for (String file : CONTRACT_FILES) {
            Path path = dir.resolve(file);
            assertTrue(Files.isRegularFile(path), "缺少契约文件: " + path);
            byte[] bytes = Files.readAllBytes(path);
            Map<String, Object> root = parseYaml(path, bytes);

            Map<String, Object> paths = asMap(root.get("paths"), file + " paths");
            int opCount = 0;
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String apiPath = pathEntry.getKey();
                assertTrue(apiPath.startsWith("/"), "path 非法（须以 / 开头）: " + file + " " + apiPath);
                assertTrue(apiPath.startsWith("/api/") || apiPath.startsWith("/actuator/"),
                        "path 前缀不在白名单（/api/**、/actuator/**）: " + file + " " + apiPath);

                Map<String, Object> byMethod = asMap(pathEntry.getValue(), file + " " + apiPath);
                for (Map.Entry<String, Object> methodEntry : byMethod.entrySet()) {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                        continue; // parameters / summary 等非方法键
                    }
                    Map<String, Object> op = asMap(methodEntry.getValue(), file + " " + method + " " + apiPath);
                    String operationId = str(op.get("operationId"));
                    assertTrue(operationId != null && !operationId.isBlank(),
                            "缺少 operationId: " + file + " " + method + " " + apiPath);
                    String status = str(op.get("x-implementation-status"));
                    assertTrue(STATUSES.contains(status),
                            "x-implementation-status 非法: " + operationId + " -> " + status);

                    assertTrue(ids.add(operationId), "operationId 跨契约重复: " + operationId);
                    assertTrue(methodPaths.add(method + " " + apiPath),
                            "(method,path) 跨契约重复: " + method + " " + apiPath);
                    idToStatus.put(operationId, status);
                    idToParams.put(operationId, paramNames(op, "query", root));
                    idToPathParams.put(operationId, paramNames(op, "path", root));
                    hasRequestBody.put(operationId, op.containsKey("requestBody"));

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("operationId", operationId);
                    row.put("method", method);
                    row.put("path", apiPath);
                    row.put("status", status);
                    row.put("contract", file);
                    operationRows.add(row);
                    opCount++;
                }
            }
            Map<String, Object> contractRow = new LinkedHashMap<>();
            contractRow.put("file", file);
            contractRow.put("sha256", sha256Normalized(bytes));
            contractRow.put("paths", paths.size());
            contractRow.put("operations", opCount);
            contractRows.add(contractRow);
        }

        operationRows.sort(Comparator
                .comparing((Map<String, Object> r) -> (String) r.get("path"))
                .thenComparing(r -> (String) r.get("method")));
        contractRows.sort(Comparator.comparing(r -> (String) r.get("file")));

        List<Map<String, Object>> commandRows = new ArrayList<>();
        for (CliCommand command : CliCommands.ALL) {
            assertTrue(Set.of(CliCommands.ACCESS_READ, CliCommands.ACCESS_WRITE).contains(command.accessMode()),
                    "accessMode 非法: " + command.name());
            assertTrue(Set.of("L0", "L1", "L2", "L3").contains(command.automationLevel()),
                    "automationLevel 非法: " + command.name() + " -> " + command.automationLevel());
            if (command.requiresApproval()) {
                assertEquals("L3", command.automationLevel(),
                        "requiresApproval 只允许出现在 L3 命令: " + command.name());
                assertEquals(CliCommands.ACCESS_WRITE, command.accessMode(),
                        "requiresApproval 命令必须是 WRITE: " + command.name());
            }
            for (String operationId : command.operationIds()) {
                assertTrue(ids.contains(operationId),
                        "命令 " + command.name() + " 映射到契约中不存在的 operationId: " + operationId);
            }
            List<Map<String, Object>> flagRows = new ArrayList<>();
            for (CliCommand.Flag flag : command.flags()) {
                assertTrue(ids.contains(flag.operationId()),
                        "flag " + flag.flag() + " 指向不存在的 operationId: " + flag.operationId());
                assertTrue(idToParams.get(flag.operationId()).contains(flag.parameter()),
                        "flag " + flag.flag() + " 的参数名不在契约 " + flag.operationId() + " 的 parameters 中: "
                                + flag.parameter() + "（契约实有 " + idToParams.get(flag.operationId()) + "）");
                Map<String, Object> flagRow = new LinkedHashMap<>();
                flagRow.put("flag", flag.flag());
                flagRow.put("operationId", flag.operationId());
                flagRow.put("parameter", flag.parameter());
                flagRow.put("description", flag.description());
                flagRows.add(flagRow);
            }
            List<Map<String, Object>> pathParamRows = new ArrayList<>();
            for (CliCommand.PathParam p : command.pathParams()) {
                assertTrue(ids.contains(p.operationId()),
                        "pathParam " + p.flag() + " 指向不存在的 operationId: " + p.operationId());
                assertTrue(idToPathParams.get(p.operationId()).contains(p.parameter()),
                        "pathParam " + p.flag() + " 的参数名不在契约 " + p.operationId() + " 的 path 参数中: "
                                + p.parameter() + "（契约实有 " + idToPathParams.get(p.operationId()) + "）");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("flag", p.flag());
                row.put("operationId", p.operationId());
                row.put("parameter", p.parameter());
                row.put("description", p.description());
                pathParamRows.add(row);
            }
            List<Map<String, Object>> bodyParamRows = new ArrayList<>();
            for (CliCommand.BodyParam b : command.bodyParams()) {
                assertTrue(ids.contains(b.operationId()),
                        "bodyParam " + b.flag() + " 指向不存在的 operationId: " + b.operationId());
                assertTrue(hasRequestBody.getOrDefault(b.operationId(), false),
                        "bodyParam " + b.flag() + " 指向的 " + b.operationId()
                                + " 在契约中没有 requestBody（CLI 不得凭空造请求体）");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("flag", b.flag());
                row.put("operationId", b.operationId());
                row.put("parameter", b.parameter());
                row.put("description", b.description());
                bodyParamRows.add(row);
            }
            Map<String, Object> commandRow = new LinkedHashMap<>();
            commandRow.put("name", command.name());
            commandRow.put("description", command.description());
            commandRow.put("accessMode", command.accessMode());
            commandRow.put("automationLevel", command.automationLevel());
            commandRow.put("requiresApproval", command.requiresApproval());
            commandRow.put("wired", command.wired());
            commandRow.put("operationIds", command.operationIds());
            commandRow.put("flags", flagRows);
            commandRow.put("pathParams", pathParamRows);
            commandRow.put("bodyParams", bodyParamRows);
            commandRows.add(commandRow);
        }

        // 未接入的命令必须自证"已登记但不可用"：不得带可调用的 operationId 却不解释原因
        for (CliCommand command : CliCommands.ALL) {
            if (!command.wired()) {
                assertTrue(!command.description().isBlank(),
                        "未接入命令 " + command.name() + " 必须有说明（为何 UNAVAILABLE）");
            }
        }

        // 非空下限：防止"契约全删光 ⇒ 门禁空转通过"。新增契约时上调并在登记册留凭证。
        assertTrue(ids.size() >= MIN_OPERATIONS,
                "契约 operation 数低于下限 " + MIN_OPERATIONS + "（实得 " + ids.size() + "）");

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("contractVersion", CONTRACT_VERSION);
        manifest.put("contracts", contractRows);
        manifest.put("operations", operationRows);
        manifest.put("commands", commandRows);
        return manifest;
    }

    // ── 工具 ────────────────────────────────────────────────────────

    private static Map<String, Object> parseYaml(Path path, byte[] bytes) {
        try {
            Object loaded = new Yaml().load(new String(bytes, StandardCharsets.UTF_8));
            assertNotNull(loaded, "YAML 解析为空: " + path);
            return asMap(loaded, path.toString());
        } catch (RuntimeException e) {
            throw new AssertionError("契约 YAML 不可解析（P-5 硬缺陷）: " + path + " :: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String what) {
        assertNotNull(value, what + " 缺失");
        assertTrue(value instanceof Map, what + " 不是映射");
        return (Map<String, Object>) value;
    }

    /**
     * 契约参数名（含 {@code $ref} 到 components.parameters 的形态——
     * 不展开会把 runId 这类路径参数读成空集，门禁变成"永远没有参数"的假绿）。
     */
    @SuppressWarnings("unchecked")
    private static Set<String> paramNames(Map<String, Object> op, String in, Map<String, Object> root) {
        Object raw = op.get("parameters");
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Map<String, Object> componentParams = componentParameters(root);
        Set<String> names = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<?, ?> p = m;
            Object ref = m.get("$ref");
            if (ref != null) {
                String key = String.valueOf(ref);
                int slash = key.lastIndexOf('/');
                Object resolved = slash >= 0 ? componentParams.get(key.substring(slash + 1)) : null;
                if (!(resolved instanceof Map<?, ?> rm)) {
                    continue;
                }
                p = rm;
            }
            if (in.equals(String.valueOf(p.get("in")))) {
                names.add(String.valueOf(p.get("name")));
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> componentParameters(Map<String, Object> root) {
        Object components = root.get("components");
        if (!(components instanceof Map<?, ?> c)) {
            return Map.of();
        }
        Object params = c.get("parameters");
        if (!(params instanceof Map<?, ?> p)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        p.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String toJson(Map<String, Object> manifest) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // 固定 \n 行尾：生成物入库后跨平台/跨 checkout 必须字节稳定（否则清单与契约比对在 Windows/Linux 间漂移）
        com.fasterxml.jackson.core.util.DefaultPrettyPrinter printer =
                new com.fasterxml.jackson.core.util.DefaultPrettyPrinter();
        printer.indentObjectsWith(new com.fasterxml.jackson.core.util.DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new com.fasterxml.jackson.core.util.DefaultIndenter("  ", "\n"));
        return mapper.writer(printer).writeValueAsString(manifest);
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n");
    }

    private static String sha256Normalized(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] normalized = normalize(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(normalized);
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
        throw new IllegalStateException("无法定位仓根（应含 ecos_backend/ 与 " + CONTRACT_DIR + "），cwd="
                + Paths.get("").toAbsolutePath());
    }

    @Test
    @DisplayName("manifest 资源可被运行时加载（resources 路径与生成位置一致）")
    void manifestResourceIsLoadableFromClasspath() throws Exception {
        if (Boolean.getBoolean("cli.gen")) {
            return; // 生成本轮 process-resources 已过，资源未进 classpath，下一次构建再校
        }
        try (InputStream in = getClass().getResourceAsStream("/contract/ops.json")) {
            assertNotNull(in, "classpath 缺少 /contract/ops.json；请先生成并提交生成物");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"operations\""), "manifest 形态异常");
        }
    }
}
