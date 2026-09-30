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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 详细设计-00 W03 — yml 数据源占位符门禁：禁 bare {@code jdbc:postgresql://localhost} 字面量。
 *
 * <p>红线（MC06）：应用 yml 的 {@code url:} 不得是<b>裸字面量</b> {@code jdbc:postgresql://localhost...}
 * （硬编码本地 DB），必须走 {@code ${DB_URL:...}} 占位符形态，使部署环境可用 env 覆盖。
 * {@code archive/legacy/**}（幽灵模块）与 {@code target/} 排除出扫描；既有的 8 处裸字面量
 * 登记为<b>基线</b>（no-new-violation：只减不增），新增 bare 会立刻 FAIL。
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='DatasourceConfigGuardTest'}</p>
 */
class DatasourceConfigGuardTest {

    /** 相对 ecos_backend 的 bare {@code jdbc:postgresql://localhost} yml 路径（no-new-violation 基线）。 */
    private static final Set<String> BASELINE_BARE_URLS = new TreeSet<>(Set.of(
            "engine/ai-engine/ai-engine-boot/src/main/resources/application.yml",
            "engine/cognitive-engine/cognitive-engine-boot/src/main/resources/application.yml",
            "engine/data-engine/data-engine-boot/src/main/resources/application.yml",
            "engine/kb-engine/kb-engine-boot/src/main/resources/application.yml",
            "engine/ontology-engine/ontology-engine-boot/src/main/resources/application.yml",
            "engine/security-engine/security-engine-boot/src/main/resources/application.yml",
            "gateway/src/main/resources/application.yml",
            "services/agent-service/src/main/resources/application.yml",
            "services/sysman/impl/sysman-boot/src/main/resources/application.yml"
    ));

    /** 匹配"bare" url 行：行内内容去掉缩进后以 jdbc:postgresql://localhost 开头（非 ${ 占位）。 */
    private static final Pattern BARE_URL_LINE =
            Pattern.compile("^\\s*url:\\s*jdbc:postgresql://localhost", Pattern.MULTILINE);

    @Test
    @DisplayName("新增 bare jdbc:postgresql://localhost yml → FAIL（基线外）")
    void noNewBareLocalhostJdbcUrl() throws IOException {
        Path root = resolveBackendRoot();
        Set<Path> bareFiles = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                .forEach(p -> {
                    String s = p.toString().replace('\\', '/');
                    if (s.contains("/target/")) return;
                    if (s.contains("/archive/")) return; // 幽灵模块，已归档，剥离门禁
                    if (BARE_URL_LINE.matcher(read(p)).find()) {
                        bareFiles.add(p);
                    }
                });
        }

        Set<String> offenders = new TreeSet<>();
        for (Path p : bareFiles) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            if (!BASELINE_BARE_URLS.contains(rel)) {
                offenders.add(rel);
            }
        }
        assertTrue(offenders.isEmpty(),
                "W03 违规：以下 yml 新增 bare jdbc:postgresql://localhost 字面量（改 ${DB_URL:...}）: "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("基线反证：每条基线 yml 仍存在且仍命中 bare url（防基线膨胀/已修未删）")
    void baselineStillBare() {
        Path root = resolveBackendRoot();
        for (String rel : BASELINE_BARE_URLS) {
            Path p = root.resolve(rel);
            if (!Files.exists(p)) {
                fail("基线 yml 已不存在 → 应从 BASELINE_BARE_URLS 移除: " + rel);
            }
            if (!BARE_URL_LINE.matcher(read(p)).find()) {
                fail("基线 yml 已改为占位符 → 应从 BASELINE_BARE_URLS 移除: " + rel);
            }
        }
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
}
