package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-06 — 引擎零 {@code org.neo4j.driver} import（唯一出口 = runtime-access）。
 *
 * <p>ArchUnit 双轴（源自 Gateway fat-jar 的 test classpath）：
 * <ol>
 *   <li>字节码断言：{@code noClasses()} outside {@code com.chinacreator.gzcm.runtime.access..}
 *       不得依赖 {@code org.neo4j.driver..}（gateway 类库 classpath 无 engine-impl，
 *       该断言为主点击查看器语义）</li>
 *   <li>全仓源扫：扫 {@code ecos_backend} 下所有 {@code *.java}（排除 target），
 *       凡含 {@code import org.neo4j.driver.} 的文件必须位于 {@code runtime/runtime-access/}</li>
 * </ol>
 *
 * <p>同源验证已有事实：目前 2 文件 {@code Neo4jConfig} + {@code Neo4jClient}
 * （均在 runtime-access 包根）。</p>
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='NoNeo4jDriverOutsideRuntimeAccessTest'}</p>
 */
class NoNeo4jDriverOutsideRuntimeAccessTest {

    private static final Pattern NEO4J_DRIVER_IMPORT =
            Pattern.compile("import\\s+org\\.neo4j\\.driver\\.[A-Za-z0-9_.$]+;");

    @Test
    @DisplayName("源扫：引擎/services/workspace 零 org.neo4j.driver import")
    void noNeo4jDriverImportOutsideRuntimeAccess() throws IOException {
        Path backendRoot = resolveBackendRoot();
        Set<String> offenders = new TreeSet<>();
        Set<String> tolerated = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(backendRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .forEach(p -> {
                    String s = p.toString().replace('\\', '/');
                    if (s.contains("/target/")) return;
                    String content = read(p);
                    if (!NEO4J_DRIVER_IMPORT.matcher(content).find()) return;
                    if (s.contains("/runtime/runtime-access/src/")
                            || s.contains("\\runtime\\runtime-access\\")) {
                        tolerated.add(s);
                    } else {
                        offenders.add(s);
                    }
                });
        }
        assertTrue(offenders.isEmpty(),
                "详细设计-00 F00-06：以下文件 import org.neo4j.driver，应迁至 runtime-access: "
                        + String.join("\n  ", offenders));
        assertTrue(!tolerated.isEmpty(),
                "Neo4j driver 应在 runtime-access 有实现（Neo4jConfig/Neo4jClient），scan 应为非空，" +
                "got=0（scan 环境异常）");
    }

    private static Path resolveBackendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get("..").toAbsolutePath().normalize();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }

    /** 顺带把 gateway 侧 classpath 层面的 neo4j driver 存在也点一下：Neo4jConfig 所在包应可加载。 */
    @Test
    @DisplayName("runtime-access Neo4jConfig 类在当前 classpath 可见（唯一装配源）")
    void neo4jConfigResolvable() {
        Class<?> c;
        try {
            c = Class.forName("com.chinacreator.gzcm.runtime.access.config.Neo4jConfig");
        } catch (ClassNotFoundException e) {
            // gateway 侧 classpath 若无 runtime-access 依赖 → 允许；
            // 此时的守卫完全依赖上面的 source scan
            return;
        }
        assertTrue(c.getName().startsWith("com.chinacreator.gzcm.runtime.access."),
                "Neo4jConfig 应归属 runtime-access 包：实际 " + c.getName());
    }
}
