package com.chinacreator.gzcm.engine.kb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册04 §E.3 V178 行门禁 —— 知识域 JSONB 列 MC02 止血（W107/C89 验收标识
 * {@code Mc02JsonbRetirementTest#noJsonbReadRemaining}）。
 *
 * <p><b>离线走查（零 live 库，不 Spring 容器）</b>：V178 是 jsonb → {@code _json} TEXT 双写止血
 * 的单源 DDL（15 列清单 + 幂等回填），本护栏把三件事在构建期锁住；live 回填与读写切读本身仍属
 * §14.4 授权项（脚本只落地不实跑）：</p>
 * <ol>
 *   <li><b>V178 结构就位</b>：15 行 VALUES 清单逐行锚定 + {@code ADD COLUMN IF NOT EXISTS %I TEXT}
 *       并建句 / 幂等回填 CAST / 双列 COMMENT 三结构句全在（防未来挪列/删列/漏改结构）；</li>
 *   <li><b>V179~V187 零新增 JSONB 列</b>：新表默认 TEXT/_json 形态，任何非注释 SQL 出现 JSONB
 *       类型定义即红（防新表"带病上线"）；</li>
 *   <li><b>{@code ::jsonb} 强转写点棘轮</b>（K-24 实测 9 处/5 文件 = 基线）：双写过渡期既有态，
 *       <b>只减不增</b>——新增 jsonb 写点即红（提示又绕开了 V178 的 _json 双写通道）。</li>
 * </ol>
 *
 * <p>白线口径：{@code public.ecos_knowledge_graph_node.properties_json}（D-9 定性偏差，
 * _json 名合规、改型=IR03 未授权）不在本扫描清单；embedding_vec 兄弟列（MC05 白线）与
 * 原 embedding 列同前缀，故本测试只锁 <b>{@code ::jsonb} 具体语法</b>而非通配列名。</p>
 */
class Mc02JsonbRetirementTest {

    /** V178 单源 15 列清单（schema.table.column，逐行镜像脚本 VALUES）。 */
    private static final String[][] SPEC = {
        {"ecos_knowledge", "knowledge_article",       "tags"},
        {"ecos_knowledge", "knowledge_embedding",     "embedding"},
        {"ecos_knowledge", "graph_node",              "properties"},
        {"ecos_knowledge", "graph_edge",              "properties"},
        {"ecos_knowledge", "graph_subgraph",          "node_ids"},
        {"ecos_knowledge", "graph_subgraph",          "edge_ids"},
        {"ecos_knowledge", "kb_doc_chunk",            "metadata"},
        {"ecos_knowledge", "kb_scheduled_extract",    "ontology_ids"},
        {"ecos_knowledge", "kg_sync_log",             "report"},
        {"ecos_knowledge", "kb_ontology_snapshot",    "entity_codes"},
        {"ecos_knowledge", "kb_ontology_snapshot",    "relationship_codes"},
        {"public",         "kb_lineage_event",        "nodes"},
        {"public",         "kb_lineage_event",        "edges"},
        {"public",         "kb_cognitive_pipeline",   "config"},
        {"public",         "kb_cognitive_pipeline",   "result"},
    };

    /** K-24 实测 kb 主源码 {@code ::jsonb} 强转写点（双写过渡期既有态，只减不增）。 */
    static final int BASELINE_JSONB_CRITICALIFIED_WRITES = 9;

    @Test
    @DisplayName("V178: 15 列清单逐项就位，_json 并建/幂等回填/定性注释三结构句全在")
    void v178CreatesSiblingJsonColumnForEachListedColumn() throws IOException {
        String d = V178();
        assertTrue(d.contains("ADD COLUMN IF NOT EXISTS %I TEXT"), "V178 _json 并建句缺失");
        assertTrue(d.contains("CAST(%I AS TEXT) WHERE %I IS NULL"), "V178 幂等回填句缺失");
        assertTrue(d.contains("COMMENT ON COLUMN %I.%I.%I IS %L"), "V178 双列定性注释句缺失");
        assertTrue(d.matches("(?s).*udt_name = 'jsonb'.*"), "V178 jsonb 类型辨识探针缺失");
        int rows = 0;
        for (String[] r : SPEC) {
            String v = String.format("('%s','%s','%s')", r[0], r[1], r[2]);
            assertTrue(d.replace(" ", "").contains(v), "V178 清单行丢失: " + v);
            rows++;
        }
        assertEquals(15, rows);
    }

    @Test
    @DisplayName("V179~V187 区间零新增 JSONB 列定义（新表默认 TEXT/_json 形态）")
    void noNewJsonbColumnDefinitionsInV179ToV187() throws IOException {
        for (int v = 179; v <= 187; v++) {
            String d = migrationOrNull(v);
            if (d == null) continue;
            // SQL 单行注释 = 从首个 `--` 起剔除到行尾；单引号字符串字面量同步剔除，
            // 因 COMMENT/VALUES note 字符串内提及 "JSONB" 属既有列定性叙述，非新列类型定义。
            for (String raw : d.lines().toList()) {
                int dash = raw.indexOf("--");
                String noComment = (dash >= 0 ? raw.substring(0, dash) : raw);
                String noLiteral = stripSqlStringLiterals(noComment);
                if (noLiteral.toUpperCase().contains("JSONB")) {
                    fail("V" + v + " 非注释/非字符串 SQL 出现 JSONB 类型定义（新表带病上线违 MC02）: " + raw.trim());
                }
            }
        }
    }

    @Test
    @DisplayName("noJsonbReadRemaining: ::jsonb 强转写点计数 ≤ 基线 9（只减不增）")
    void noJsonbReadRemaining() throws IOException {
        int hits = 0;
        List<String> where = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(mainRoot())) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                var m = Pattern.compile("::jsonb\\b").matcher(body);
                while (m.find()) {
                    hits++;
                    where.add(p.getFileName().toString());
                }
            }
        }
        assertTrue(hits <= BASELINE_JSONB_CRITICALIFIED_WRITES,
            "::jsonb 强转回升到 " + hits + "（基线 " + BASELINE_JSONB_CRITICALIFIED_WRITES
            + "）：新 jsonb 写点绕开了 V178 _json 双写通道 → " + where);
    }

    // ── 文件走查公共件 ──────────────────────────────────────────────────────

    private static String V178() throws IOException {
        String d = migrationOrNull(178);
        if (d == null) fail("V178__kb_jsonb_to_text_columns.sql 不在迁移单源目录");
        return d;
    }

    private static String migrationOrNull(int v) throws IOException {
        Path dir = repoRoot().resolve("ecos_backend/gateway/src/main/resources/db/migration");
        try (Stream<Path> walk = Files.list(dir)) {
            for (Path p : walk.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("V" + v + "__") && n.endsWith(".sql")) {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static Path mainRoot() {
        Path dir = repoRoot().resolve("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java");
        assertTrue(Files.isDirectory(dir), "kb-engine-impl 主源码目录不可达: " + dir);
        return dir;
    }

    /** 递归上溯定位仓根（同时要求 docs/ 与 ecos_backend/ 双目录，防误锁）。 */
    static Path repoRoot() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 16 && cur != null; i++, cur = cur.getParent()) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur;
            }
        }
        fail("repoRoot 不可达（docs/ + ecos_backend/ 双目录未命中）");
        return null;
    }

    /** 剔除 SQL 行注释（从首个 {@code --} 起剔到行尾）+ Java 风格块注释；保留字符串字面量。 */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '-' && i + 1 < n && src.charAt(i + 1) == '-') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 单行内剔除 SQL 单引号字符串字面量（`''` 为转义单引号，保留字面量外的 DDL 结构句）。 */
    static String stripSqlStringLiterals(String line) {
        StringBuilder out = new StringBuilder(line.length());
        boolean inStr = false;
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (inStr) {
                if (c == '\'') {
                    if (i + 1 < n && line.charAt(i + 1) == '\'') { i += 2; continue; }
                    inStr = false; i++; continue;
                }
                i++; continue;
            }
            if (c == '\'') { inStr = true; i++; continue; }
            out.append(c); i++;
        }
        return out.toString();
    }
}
