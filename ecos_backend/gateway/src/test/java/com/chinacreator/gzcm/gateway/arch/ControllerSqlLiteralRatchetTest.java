package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分层红线棘轮：Controller 禁 SQL 字面量（R7~R11 补册 §6.2.2「分层约束不变」；#147BM）。
 *
 * <p><b>为什么是棘轮而不是零容差门</b>：该约束在存量代码里**并未成立**——全仓 16 个 Controller
 * 共 98 处 SQL 字面量（dev 实扫，2026-11-04）。按 §2.4 C-7「已知偏差按缺口登记待排期、不假装
 * 闭环」，本门只做两件事：<b>禁止新增</b>（新文件带 SQL 即红、同文件再加一处也红），并把当前
 * 债务钉成可核对的基线；清债时须同批下调基线（双向，陈旧亦红）。</p>
 *
 * <p><b>它不证明什么</b>：不证明存量 98 处已合规。它证明的是「从今天起不再增长」，且这条债
 * 有唯一可核对的数字。</p>
 *
 * @author ECOS gateway #147BM
 */
class ControllerSqlLiteralRatchetTest {

    /** SQL 字面量判据：字符串字面量以 SELECT/INSERT/UPDATE/DELETE + 空格开头（与人工复扫同口径）。 */
    private static final Pattern SQL_LITERAL = Pattern.compile("\"(SELECT|INSERT|UPDATE|DELETE) ");

    /**
     * 基线（2026-11-04 实扫；路径相对 ecos_backend，计数=该文件 SQL 字面量处数；合计 <b>72</b>）。
     *
     * <p>2026-11-05 同批下调两处（DI-2 #147CI/#147CJ 把 SQL 收回服务层）：{@code UserController}
     * 9→6、{@code TenantController} 20→1；两处均为「SQL 下沉 DAO/Service」的真清退，非判据放宽。</p>
     */
    private static final Map<String, Integer> BASELINE = new LinkedHashMap<>();

    static {
        BASELINE.put("engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AgentToolController.java", 1);
        BASELINE.put("engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/DiagnosticAgentController.java", 5);
        BASELINE.put("engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/PipelineTaskController.java", 1);
        BASELINE.put("engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/QueryController.java", 1);
        BASELINE.put("engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyMappingController.java", 1);
        BASELINE.put("engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java", 9);
        BASELINE.put("gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/SysConfigController.java", 2);
        BASELINE.put("services/datanet/src/main/java/com/chinacreator/gzcm/services/datanet/controller/DqDashboardController.java", 13);
        BASELINE.put("services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/EntityTableMappingController.java", 7);
        BASELINE.put("services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/TenantController.java", 1);
        BASELINE.put("services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/UserController.java", 6);
        BASELINE.put("workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectActionController.java", 2);
        BASELINE.put("workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectController.java", 21);
        BASELINE.put("workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectQLController.java", 1);
        BASELINE.put("workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectStateMachineController.java", 1);
    }

    private static Path backendRoot() {
        // ecos.ratchet.root 仅供变异自证（喂一份带 SQL 的临时 Controller 目录）；生产/CI 走默认。
        String override = System.getProperty("ecos.ratchet.root");
        if (override != null) {
            return Paths.get(override);
        }
        // surefire 工作目录 = ecos_backend/gateway ⇒ 上一级即 ecos_backend
        Path root = Paths.get("..").normalize();
        assertTrue(Files.isDirectory(root.resolve("gateway")),
                "ecos_backend 根定位失败（surefire 工作目录应为 ecos_backend/gateway）: " + root.toAbsolutePath());
        return root;
    }

    private static Map<String, Integer> scanSqlLiterals(Path backendRoot) throws IOException {
        Map<String, Integer> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(backendRoot)) {
            for (Path file : (Iterable<Path>) files
                    .filter(p -> p.toString().endsWith("Controller.java"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("archive"))
                    .filter(p -> !p.toString().contains("src" + java.io.File.separator + "test"))::iterator) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                int count = 0;
                var m = SQL_LITERAL.matcher(code);
                while (m.find()) {
                    count++;
                }
                if (count > 0) {
                    found.put(backendRoot.relativize(file).toString().replace('\\', '/'), count);
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("Controller 的 SQL 字面量不得增长：文件集与逐文件计数均不得超过基线（双向）")
    void controllerSqlLiteralsMustNotGrow() throws IOException {
        Map<String, Integer> current = scanSqlLiterals(backendRoot());

        java.util.List<String> newFiles = current.keySet().stream()
                .filter(f -> !BASELINE.containsKey(f)).toList();
        assertEquals(java.util.List.of(), newFiles,
                "新增 Controller 不得带 SQL 字面量（§6.2.2 分层约束）：SQL 应下沉到 DAO/Mapper");

        java.util.List<String> grown = current.entrySet().stream()
                .filter(e -> BASELINE.containsKey(e.getKey()) && e.getValue() > BASELINE.get(e.getKey()))
                .map(e -> e.getKey() + " 基线 " + BASELINE.get(e.getKey()) + " → 现 " + e.getValue())
                .toList();
        assertEquals(java.util.List.of(), grown, "存量 Controller 的 SQL 字面量不得增加");

        java.util.List<String> stale = BASELINE.entrySet().stream()
                .filter(e -> !current.containsKey(e.getKey()) || !e.getValue().equals(current.get(e.getKey())))
                .filter(e -> current.containsKey(e.getKey()))
                .map(e -> e.getKey() + " 基线 " + e.getValue() + " → 现 " + current.get(e.getKey()))
                .toList();
        assertEquals(java.util.List.of(), stale,
                "清债是好事：请同批下调本门基线（陈旧基线＝与本表脱钩，禁留）");
    }
}
