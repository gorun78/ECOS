package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ST07 / W50（M1）— 迁移目录 DDL 只允许出现 ST07 5+1 单库权威 schema +
 * 已知 knownLegacy 分库 schema 存量允许集（{@code V47__ecos_schema_isolation.sql}
 * 已落地、随 ST07 登记仅作停写清单的 11 个 legacy）。任何 <b>新</b> 越出该白名单
 * 的 {@code CREATE SCHEMA} → FAIL，堵住 W50 描述过的 "生态屋内 11 个越权 schema"
 * 再次扩散。
 *
 * <p>白名单（ST07 5+1 + culım 主控制 + 业务 DW + DQ 治理 + knownLegacy 11）：
 * <pre>
 *   ST07 5+1:  ecos_data / ecos_ontology / ecos_knowledge / ecos_ai /
 *              ecos_cognitive / public（或目标 ecos_control）
 *   业务域:    ecos_dw
 *   DQ 治理:   ecos_dq
 *   knownLegacy V47（已 pre-registered, 只停写）:
 *              ecos_identity / ecos_catalog / ecos_object / ecos_workflow /
 *              ecos_rule / ecos_agent / ecos_mission / ecos_audit /
 *              ecos_config / ecos_pipeline
 * </pre>
 */
@DisplayName("ST07 / W50：迁移脚本里 CREATE SCHEMA 只允许 5+1 + 已知 knownLegacy 集")
class SchemaInventoryGateTest {

    /** ST07 5+1 权威 schema。 */
    private static final Set<String> ST07 = Set.of(
            "ecos_data", "ecos_ontology", "ecos_knowledge", "ecos_ai",
            "ecos_cognitive", "public", "ecos_control", "ecos_dw", "ecos_dq");

    /** knownLegacy 分库 schema（V47 已建，ST07 登记停写不删除，允许保持存量 CREATE SCHEMA 幂等）。 */
    private static final Set<String> KNOWN_LEGACY = Set.of(
            "ecos_identity", "ecos_catalog", "ecos_object", "ecos_workflow",
            "ecos_rule", "ecos_agent", "ecos_mission", "ecos_audit",
            "ecos_config", "ecos_pipeline");

    private static final Set<String> ALLOWED = new LinkedHashSet<>(ST07);

    static {
        ALLOWED.addAll(KNOWN_LEGACY);
    }

    private static final Path MIGRATION_DIR = Path.of(
            "gateway/src/main/resources/db/migration");
    private static final Pattern CREATE_SCHEMA = Pattern.compile(
            "CREATE\\s+SCHEMA\\s+(IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][\\w]*)",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("db/migration/*.sql 里所有 CREATE SCHEMA 都在白名单内")
    void allCreateSchemaInWhitelist() throws IOException {
        Path dir = repoRoot().resolve("ecos_backend").resolve(MIGRATION_DIR);
        assertTrue(Files.isDirectory(dir), "迁移目录缺: " + dir);
        // schema → 首次出现的文件（便于断言信息）
        Map<String, String> found = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path f : walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("V[^/]*\\.sql$"))
                    .toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                // 跳过注释行, 逐行匹配 CREATE SCHEMA
                int lineNo = 0;
                for (String line : text.split("\\R")) {
                    lineNo++;
                    String t = line.trim();
                    if (t.startsWith("--") || t.startsWith("/*")) continue;
                    Matcher lm = CREATE_SCHEMA.matcher(t);
                    if (lm.find()) {
                        String schemaName = lm.group(2).toLowerCase();
                        found.putIfAbsent(schemaName, f.getFileName() + ":" + lineNo);
                    }
                }
            }
        }
        Set<String> offenders = new LinkedHashSet<>(found.keySet());
        offenders.removeAll(ALLOWED);
        assertTrue(offenders.isEmpty(),
                "以下 CREATE SCHEMA 未获 ST07 5+1 或 knownLegacy 白名单（W50 越权 schema）:\n"
                        + offenders.stream().map(s -> "  - " + s + " (" + found.get(s) + ")")
                              .reduce("", (a, b) -> a + "\n" + b)
                        + "\n此次扫描到所有 schema: " + found.keySet()
                        + "\n允许集: " + ALLOWED);
    }

    @Test
    @DisplayName("ST07 5 个引擎 schema 至少有一个在迁移里被显式建立（非空门）")
    void st07EngineSchemasAreReferenced() throws IOException {
        Path dir = repoRoot().resolve("ecos_backend").resolve(MIGRATION_DIR);
        Set<String> created = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path f : walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("V[^/]*\\.sql$"))
                    .toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = CREATE_SCHEMA.matcher(text);
                while (m.find()) created.add(m.group(2).toLowerCase());
            }
        }
        assertTrue(created.contains("ecos_data") && created.contains("ecos_knowledge")
                        && created.contains("ecos_dq") && created.contains("ecos_dw"),
                "ST07 权威 + 业务/DQ schema 应被显式 CREATE；实际: " + created);
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) return cur;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }
}
