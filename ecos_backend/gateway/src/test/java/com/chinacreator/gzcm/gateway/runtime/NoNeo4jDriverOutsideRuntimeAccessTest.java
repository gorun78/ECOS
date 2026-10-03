package com.chinacreator.gzcm.gateway.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-06（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F00-06 行 / ARCH_SPEC C5，M1）
 * — 引擎/服务/工作区侧零 {@code org.neo4j.driver} import；图库访问收敛 {@code runtime-access}。
 *
 * <p><b>断言语义（与工程既有 ArchitectureGuardTest 同型）：</b>
 * {@code import org.neo4j.driver} 只允许出现在 {@code runtime/runtime-access/} 子树；
 * 若 engine/services/workspace/gateway 出现 import 即 FAIL。
 *
 * <p>不用 ArchUnit {@code ClassFileImporter.importPackages}：跨 reactor 时 classpath
 * 会到 {@code .m2} 历史 JAR，扫到 legacy 已删档（C6 收口 2026-09-28 前的
 * dccheng/aiming/worldmodel/ontology-kb-impl 4 模块旧 artifact 中的 Neo4j 类），
 * 与源码现状脱节，先前 batches-probe 已定谳。
 * 源码级 import 扫描 = 真实的架构门禁（新增违规 = 源码 diff 加行 → 编译/测试必红）。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest='NoNeo4jDriverOutsideRuntimeAccessTest'}
 */
class NoNeo4jDriverOutsideRuntimeAccessTest {

    private static final String FORBIDDEN_IMPORT_PREFIX = "import org.neo4j.driver";

    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("runtime/runtime-access"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    @Test
    @DisplayName("F00-06：引擎/服务/工作区/网关 · 主源码 import org.neo4j.driver 只允许 runtime-access 下")
    void noNeo4jDriverImportOutsideRuntimeAccessSources() {
        Path root = backendRoot();
        List<Path> scanRoots = List.of(
                root.resolve("engine"),
                root.resolve("services"),
                root.resolve("workspace"),
                root.resolve("gateway"),
                root.resolve("runtime/common-api"),
                root.resolve("runtime/runtime-core"),
                root.resolve("runtime/runtime-task"),
                root.resolve("runtime/runtime-monitor"),
                root.resolve("runtime/runtime-event"),
                root.resolve("runtime/llm-gateway")
        );
        List<String> offenders = new ArrayList<>();
        for (Path base : scanRoots) {
            if (!Files.isDirectory(base)) continue;
            try (var walk = Files.walk(base)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("/test/"))
                    .forEach(p -> {
                        try {
                            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                                if (line.trim().startsWith(FORBIDDEN_IMPORT_PREFIX)) {
                                    offenders.add(root.relativize(p).toString().replace('\\', '/')
                                            + " : " + line.trim());
                                    break;
                                }
                            }
                        } catch (IOException ignored) { }
                    });
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("walk 失败 " + base, e);
            }
        }
        assertTrue(offenders.isEmpty(),
                "F00-06 红线：runtime/access 之外主源码出现 org.neo4j.driver import（图库访问必须收敛 runtime-access）：\n  "
                        + String.join("\n  ", offenders));
    }
}
